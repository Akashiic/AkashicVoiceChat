package dev.akashic.voice.voice.client.playback;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;

import dev.akashic.voice.AkashicVoice;
import dev.akashic.voice.voice.client.acoustics.AcousticProfile;
import dev.akashic.voice.voice.client.acoustics.AcousticRoutingPolicy;
import dev.akashic.voice.voice.common.StreamEpochTracker;

public final class AudioPlayback {

    interface Channel {

        void setSpatial(boolean value);

        void setPosition(float x, float y, float z);

        void setMaxDistance(float value);

        // Legacy internal test sinks can ignore unity gain; real channels override this.
        default void setGain(float value) {
            if (value != 1f) throw new IllegalStateException("Channel lacks voice gain support");
        }

        default void setEnvironmentEffects(int auxiliarySlot, int directFilter) {}

        void enqueue(short[] pcm);

        void close();
    }

    interface ChannelFactory {

        Channel create(float referenceDistance);
    }

    private final ChannelFactory channelFactory;
    private final Map<UUID, Long> channelEpochs = new HashMap<>();
    private final Map<UUID, Object> channelSpeakerTokens = new HashMap<>();
    private final SpeakerControls speakerControls = new SpeakerControls();
    private SpeakerControls.Snapshot appliedSpeakers = speakerControls.snapshot();

    private static final int MAX_PENDING_TASKS = 64;
    private static final int FRAME_POOL_SIZE = 128;
    private static final long OUTPUT_HEALTH_POLL_NANOS = 500_000_000L;
    private static final int OUTPUT_UNAVAILABLE_CONFIRMATIONS = 3;

    private static volatile AudioPlayback instance;

    private final BlockingQueue<Runnable> tasks = new ArrayBlockingQueue<>(MAX_PENDING_TASKS);
    private final BlockingQueue<PlaybackFrame> pendingFrames = new ArrayBlockingQueue<>(FRAME_POOL_SIZE);
    private final BlockingQueue<PlaybackFrame> freeFrames = new ArrayBlockingQueue<>(FRAME_POOL_SIZE);
    private final Map<UUID, Channel> channels = new HashMap<>();
    private final Map<UUID, Boolean> sourceSpatial = new HashMap<>();
    private final FloatBuffer listenerOrientation = BufferUtils.createFloatBuffer(6);
    private final AtomicLong droppedTasks = new AtomicLong();
    private final AtomicLong droppedFrames = new AtomicLong();
    private final AtomicLong outputRecoveries = new AtomicLong();
    private final OutputRecoveryController outputRecovery = new OutputRecoveryController();
    private final StreamEpochTracker streamEpochs = new StreamEpochTracker();
    private final Object listeningStateLock = new Object();
    private volatile ListeningState listeningState = new ListeningState(false);
    // Audio-thread state. Identity tokens prevent stale PCM after a rapid off/on toggle.
    private ListeningState appliedListeningState = listeningState;

    private static final class ListeningState {

        final boolean deafened;

        ListeningState(boolean deafened) {
            this.deafened = deafened;
        }
    }

    private Thread thread;
    private volatile boolean running;
    private volatile float maxDistance = 48f;
    private volatile float referenceDistance = 4f;
    private volatile float listenerGain = 1f;
    private volatile boolean spatial = true;
    private volatile AcousticProfile acousticProfile = AcousticProfile.OUTDOOR;
    private AcousticProfile appliedAcousticProfile = AcousticProfile.OUTDOOR;
    private VoiceEnvironmentEffects environmentEffects;
    private OpenAlOutputHealth outputHealth;
    private long nextOutputHealthPollNanos;
    private int outputUnavailablePolls;
    private volatile boolean weCreatedAl;
    private volatile boolean outputQuiesced;
    private volatile String outputRecoveryReason = "";
    private volatile String outputDeviceName = "";

    // Latest-value mailbox: producer updates are bounded and independent of lossy tasks.
    // Only the audio worker writes applied* fields or calls the native bindings.
    private final Object poseLock = new Object();
    private boolean poseKnown;
    private long poseRevision;
    private float listenerX;
    private float listenerY;
    private float listenerZ;
    private float listenerPitch;
    private float listenerYaw;
    private float appliedListenerGain = 1f;
    private boolean appliedSpatial = true;
    private boolean appliedPoseKnown;
    private long appliedPoseRevision;
    private boolean gainFailureLogged;
    private boolean spatialFailureLogged;
    private boolean poseFailureLogged;

    private AudioPlayback() {
        this(PlaybackChannel::new);
    }

    AudioPlayback(ChannelFactory channelFactory) {
        this.channelFactory = java.util.Objects.requireNonNull(channelFactory, "channelFactory");
        for (int i = 0; i < FRAME_POOL_SIZE; i++) {
            freeFrames.add(new PlaybackFrame());
        }
    }

    public static synchronized AudioPlayback start() {
        if (instance != null) return instance;
        AudioPlayback p = new AudioPlayback();
        p.startInternal();
        instance = p;
        return p;
    }

    public static synchronized void stop() {
        if (instance == null) return;
        instance.stopInternal();
        instance = null;
    }

    public static AudioPlayback get() {
        return instance;
    }

    public void setMaxDistance(float maxDistance) {
        this.maxDistance = maxDistance;
    }

    public void setReferenceDistance(float referenceDistance) {
        this.referenceDistance = referenceDistance;
    }

    public void setListenerGain(float gain) {
        // Reject invalid values without discarding the last valid requested setting.
        // Preserve the existing above-unity gain policy; do not silently clamp to 1.
        if (!VoiceGain.valid(gain)) return;
        this.listenerGain = gain;
    }

    /** Publishes desired voice-listening state; does not enqueue a lossy task or call OpenAL. */
    public void setDeafened(boolean deafened) {
        synchronized (listeningStateLock) {
            if (listeningState.deafened != deafened) {
                listeningState = new ListeningState(deafened);
            }
        }
    }

    public void setSpatial(boolean spatial) {
        this.spatial = spatial;
    }

    public void setAcousticProfile(AcousticProfile profile) {
        if (profile != null) this.acousticProfile = profile;
    }

    public boolean claimSharedOutputReload() {
        return outputQuiesced && !weCreatedAl && outputRecovery.claimSharedReload();
    }

    public void sharedOutputReloadIssued() {
        outputRecovery.sharedReloadIssued(System.nanoTime());
    }

    public void sharedOutputReloadFailed() {
        outputRecovery.sharedReloadFailed(System.nanoTime());
    }

    public boolean isOutputRecovering() {
        return outputRecovery.recovering();
    }

    public long outputRecoveryCount() {
        return outputRecoveries.get();
    }

    public String outputRecoveryReason() {
        return outputRecoveryReason;
    }

    public String outputDeviceName() {
        return outputDeviceName;
    }

    public PlaybackFrame acquireFrame() {
        ListeningState state = listeningState;
        if (!running || state.deafened || outputRecovery.recovering()) return null;
        PlaybackFrame frame = freeFrames.poll();
        if (frame != null) {
            frame.listeningStateToken = state;
            frame.controlledSender = null;
            frame.speakerStateToken = null;
        }
        return frame;
    }

    /** Internal decoder stamp, not a wire/session credential. */
    public Object listeningIdentity() {
        return listeningState;
    }

    public SpeakerControls speakerControls() {
        return speakerControls;
    }

    /** Stable per-speaker listening identity; observing here avoids a false first-frame boundary. */
    public Object speakerListeningIdentity(UUID senderId) {
        return speakerControls.observe(senderId)
            .listeningIdentity();
    }

    /** Captures individual listening identity before decoding begins. */
    public PlaybackFrame acquireFrame(UUID sender) {
        SpeakerControls.State state = speakerControls.observe(sender);
        if (!state.audible()) return null;
        PlaybackFrame frame = acquireFrame();
        if (frame != null) {
            frame.controlledSender = sender;
            frame.speakerStateToken = state.token;
        }
        return frame;
    }

    private boolean acceptSpeakerState(PlaybackFrame frame) {
        SpeakerControls.State state = speakerControls.state(frame.senderId);
        return state.audible() && frame.senderId.equals(frame.controlledSender)
            && frame.speakerStateToken == state.token;
    }

    /** Audio worker only: a mute/unmute cycle retires just that speaker's native queue. */
    private void reconcileSpeakerChannels() {
        SpeakerControls.Snapshot current = speakerControls.snapshot();
        java.util.Iterator<Map.Entry<UUID, Channel>> iterator = channels.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Channel> entry = iterator.next();
            UUID sender = entry.getKey();
            SpeakerControls.State state = current.state(sender);
            if (state.audible() && channelSpeakerTokens.get(sender) == state.token) continue;
            try {
                releaseChannel(entry.getValue());
            } finally {
                iterator.remove();
                channelEpochs.remove(sender);
                channelSpeakerTokens.remove(sender);
                sourceSpatial.remove(sender);
            }
        }
    }

    public boolean submitFrame(PlaybackFrame frame, UUID senderId, boolean sourceIsSpatial, float x, float y, float z) {
        return submitFrame(frame, senderId, sourceIsSpatial, x, y, z, Float.NaN, false);
    }

    public boolean submitFrame(PlaybackFrame frame, UUID senderId, boolean sourceIsSpatial, float x, float y, float z,
        float sourceMaxDistance, boolean whispering) {
        return submitFrame(frame, senderId, sourceIsSpatial, x, y, z, sourceMaxDistance, whispering, 0L);
    }

    public boolean submitFrame(PlaybackFrame frame, UUID senderId, boolean sourceIsSpatial, float x, float y, float z,
        float sourceMaxDistance, boolean whispering, long streamEpoch) {
        if (frame == null) return false;
        if (!running) {
            releaseFrame(frame);
            return false;
        }
        if (!acceptListeningState(frame)) {
            droppedFrames.incrementAndGet();
            releaseFrame(frame);
            return false;
        }
        if (senderId == null || (frame.controlledSender != null && !senderId.equals(frame.controlledSender))) {
            droppedFrames.incrementAndGet();
            releaseFrame(frame);
            return false;
        }
        if (frame.controlledSender == null) {
            frame.controlledSender = senderId;
            frame.speakerStateToken = speakerControls.observe(senderId).token;
        }
        frame.prepare(senderId, sourceIsSpatial, x, y, z, sourceMaxDistance, whispering);
        frame.streamEpoch = streamEpoch;
        synchronized (streamEpochs) {
            if (!running) {
                releaseFrame(frame);
                return false;
            }
            if (!acceptListeningState(frame) || !acceptSpeakerState(frame)) {
                droppedFrames.incrementAndGet();
                releaseFrame(frame);
                return false;
            }
            boolean valid = streamEpoch == 0L ? !streamEpochs.hasEpoch(senderId)
                : streamEpochs.frame(senderId, streamEpoch) != StreamEpochTracker.Decision.REJECT;
            if (valid && pendingFrames.offer(frame)) return true;
        }
        droppedFrames.incrementAndGet();
        releaseFrame(frame);
        return false;
    }

    public void releaseFrame(PlaybackFrame frame) {
        if (frame != null) freeFrames.offer(frame);
    }

    public void updateListener(float x, float y, float z, float pitchRad, float yawRad) {
        if (!running || !Float.isFinite(x)
            || !Float.isFinite(y)
            || !Float.isFinite(z)
            || !Float.isFinite(pitchRad)
            || !Float.isFinite(yawRad)) return;
        synchronized (poseLock) {
            if (poseKnown && x == listenerX
                && y == listenerY
                && z == listenerZ
                && pitchRad == listenerPitch
                && yawRad == listenerYaw) return;
            listenerX = x;
            listenerY = y;
            listenerZ = z;
            listenerPitch = pitchRad;
            listenerYaw = yawRad;
            poseKnown = true;
            poseRevision++;
        }
    }

    /** Called on every audio cycle, even when no new PCM arrives. */
    private void reconcileOutputControls() {
        if (!running) return;
        reconcileSpeakerChannels();
        SpeakerControls.Snapshot requestedSpeakers = speakerControls.snapshot();
        float requestedGain = listenerGain;
        // Partial per-source failures remain dirty even if the user reverts to the old value.
        if (requestedGain != appliedListenerGain || requestedSpeakers != appliedSpeakers || gainFailureLogged) {
            boolean complete = true;
            for (Map.Entry<UUID, Channel> entry : channels.entrySet()) {
                try {
                    entry.getValue()
                        .setGain(
                            requestedGain * requestedSpeakers.state(entry.getKey())
                                .scale());
                } catch (RuntimeException | LinkageError failure) {
                    complete = false;
                    if (!gainFailureLogged) {
                        gainFailureLogged = true;
                        AkashicVoice.LOG.warn("Voice source gain update failed; retrying", failure);
                    }
                }
            }
            if (complete) {
                appliedListenerGain = requestedGain;
                appliedSpeakers = requestedSpeakers;
                gainFailureLogged = false;
            }
        }

        AcousticProfile requestedProfile = acousticProfile;
        boolean requestedSpatial = spatial;
        boolean profileChanged = requestedProfile != appliedAcousticProfile;
        boolean spatialChanged = requestedSpatial != appliedSpatial;
        boolean profileReady = !profileChanged || environmentEffects == null
            || !environmentEffects.supported()
            || environmentEffects.ensureApplied(requestedProfile);
        AcousticProfile routeProfile = profileReady ? requestedProfile : appliedAcousticProfile;
        if (spatialChanged || profileChanged || spatialFailureLogged) {
            try {
                for (Map.Entry<UUID, Channel> entry : channels.entrySet()) {
                    boolean sourceIsSpatial = Boolean.TRUE.equals(sourceSpatial.get(entry.getKey()));
                    boolean effectiveSpatial = requestedSpatial && sourceIsSpatial;
                    entry.getValue()
                        .setSpatial(effectiveSpatial);
                    applyEnvironmentRoute(entry.getValue(), requestedSpatial, sourceIsSpatial, routeProfile);
                }
                appliedSpatial = requestedSpatial;
                if (profileReady) appliedAcousticProfile = requestedProfile;
                spatialFailureLogged = false;
            } catch (RuntimeException | LinkageError failure) {
                if (!spatialFailureLogged) {
                    spatialFailureLogged = true;
                    AkashicVoice.LOG
                        .warn("Voice spatial/acoustic routing update failed; retrying on a later audio cycle", failure);
                }
            }
        }

        final long revision;
        final float x;
        final float y;
        final float z;
        final float pitch;
        final float yaw;
        synchronized (poseLock) {
            if (!poseKnown || (appliedPoseKnown && poseRevision == appliedPoseRevision)) return;
            revision = poseRevision;
            x = listenerX;
            y = listenerY;
            z = listenerZ;
            pitch = listenerPitch;
            yaw = listenerYaw;
        }
        // No native operation runs while holding the producer's publication lock.
        try {
            AL10.alListener3f(AL10.AL_POSITION, x, y, z);
            float cosPitch = (float) Math.cos(pitch);
            float lookX = -(float) Math.sin(yaw) * cosPitch;
            float lookY = -(float) Math.sin(pitch);
            float lookZ = (float) Math.cos(yaw) * cosPitch;
            listenerOrientation.clear();
            listenerOrientation.put(lookX)
                .put(lookY)
                .put(lookZ)
                .put(0f)
                .put(1f)
                .put(0f)
                .flip();
            AL10.alListener(AL10.AL_ORIENTATION, listenerOrientation);
            // A newer publication remains pending; a partial failure is not acknowledged.
            appliedPoseRevision = revision;
            appliedPoseKnown = true;
            poseFailureLogged = false;
        } catch (RuntimeException | LinkageError failure) {
            if (!poseFailureLogged) {
                poseFailureLogged = true;
                AkashicVoice.LOG.warn("Voice listener pose update failed; retrying on a later audio cycle", failure);
            }
        }
    }

    public void dropChannel(UUID senderId) {
        if (!running) return;
        speakerControls.retireDefault(senderId);
        offerTask(() -> {
            Channel ch = channels.remove(senderId);
            channelEpochs.remove(senderId);
            channelSpeakerTokens.remove(senderId);
            sourceSpatial.remove(senderId);
            releaseChannel(ch);
        });
    }

    public void retireStream(UUID senderId, long epoch) {
        if (!running || epoch <= 0) return;
        // Retirement must survive a saturated (lossy) general control queue.
        streamEpochs.reset(senderId, epoch);
        speakerControls.retireDefault(senderId);
    }

    private void closeChannel(UUID senderId) {
        Channel channel = channels.remove(senderId);
        channelEpochs.remove(senderId);
        channelSpeakerTokens.remove(senderId);
        sourceSpatial.remove(senderId);
        releaseChannel(channel);
    }

    private void releaseChannel(Channel channel) {
        if (channel == null) return;
        try {
            channel.close();
        } catch (RuntimeException | LinkageError failure) {
            // Retire once; one failed native sink must not stop unrelated speakers.
            AkashicVoice.LOG.warn("Voice channel cleanup failed; channel retired", failure);
        }
    }

    private boolean acceptFrameEpoch(PlaybackFrame frame) {
        if (frame.streamEpoch == 0L) return !streamEpochs.hasEpoch(frame.senderId);
        if (!streamEpochs.isActive(frame.senderId, frame.streamEpoch)) return false;
        Long rendered = channelEpochs.get(frame.senderId);
        if (rendered != null && rendered.longValue() != frame.streamEpoch) closeChannel(frame.senderId);
        return true;
    }

    private boolean acceptListeningState(PlaybackFrame frame) {
        ListeningState state = listeningState;
        return !state.deafened && frame.listeningStateToken == state;
    }

    /** Runs only on the audio worker, including when no new PCM arrives. */
    private void reconcileListeningState() {
        ListeningState state = listeningState;
        if (state == appliedListeningState) return;
        // Stop only voice-owned sources, including their already-queued native PCM.
        // Preserve wire-epoch tombstones: deafening must not resurrect a retired sender.
        for (Channel channel : channels.values()) {
            releaseChannel(channel);
        }
        channels.clear();
        channelEpochs.clear();
        channelSpeakerTokens.clear();
        sourceSpatial.clear();
        appliedListeningState = state;
    }

    private void reconcileRetiredChannels() {
        java.util.Iterator<Map.Entry<UUID, Channel>> iterator = channels.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Channel> entry = iterator.next();
            UUID sender = entry.getKey();
            Long epoch = channelEpochs.get(sender);
            boolean active = epoch != null && (epoch.longValue() == 0L ? !streamEpochs.hasEpoch(sender)
                : streamEpochs.isActive(sender, epoch.longValue()));
            if (active) continue;
            try {
                releaseChannel(entry.getValue());
            } finally {
                iterator.remove();
                channelEpochs.remove(sender);
                channelSpeakerTokens.remove(sender);
                sourceSpatial.remove(sender);
            }
        }
    }

    public long droppedTaskCount() {
        return droppedTasks.get();
    }

    public long droppedFrameCount() {
        return droppedFrames.get();
    }

    private void offerTask(Runnable task) {
        if (!tasks.offer(task)) droppedTasks.incrementAndGet();
    }

    private void startInternal() {
        running = true;
        thread = new Thread(this::loop, "AkashicVoice-Audio");
        thread.setDaemon(true);
        thread.start();
    }

    private void stopInternal() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    private void loop() {
        try {
            initOpenAl();
        } catch (Throwable t) {
            AkashicVoice.LOG.error("OpenAL init failed; voice playback disabled", t);
            running = false;
            return;
        }

        try {
            while (running) {
                if (serviceOutputRecovery()) {
                    Thread.sleep(10L);
                    continue;
                }
                pollOutputHealth();
                if (outputRecovery.recovering()) continue;
                reconcileListeningState();
                Runnable task;
                int taskBudget = MAX_PENDING_TASKS;
                while (taskBudget-- > 0 && (task = tasks.poll()) != null) {
                    runTask(task);
                }
                // A continuously replenished task queue must not starve voice state or PCM.
                reconcileListeningState();
                reconcileOutputControls();
                reconcileRetiredChannels();
                PlaybackFrame frame = pendingFrames.poll(10, TimeUnit.MILLISECONDS);
                if (frame == null) continue;
                try {
                    processFrame(frame);
                } catch (Throwable t) {
                    AkashicVoice.LOG.warn("Audio frame failed", t);
                } finally {
                    releaseFrame(frame);
                }
            }
        } catch (InterruptedException ignored) {
            // shutting down
        } finally {
            shutdownOpenAl();
        }
    }

    private void pollOutputHealth() {
        long now = System.nanoTime();
        if (now < nextOutputHealthPollNanos) return;
        nextOutputHealthPollNanos = now + OUTPUT_HEALTH_POLL_NANOS;
        OpenAlOutputHealth health = outputHealth;
        if (health == null) return;
        OpenAlOutputHealth.Status status = health.poll(true);
        if (status == OpenAlOutputHealth.Status.HEALTHY) {
            outputUnavailablePolls = 0;
            outputDeviceName = safeDeviceName(health.expectedDefaultDevice());
            return;
        }
        if (status == OpenAlOutputHealth.Status.UNAVAILABLE
            && ++outputUnavailablePolls < OUTPUT_UNAVAILABLE_CONFIRMATIONS) return;
        outputUnavailablePolls = 0;
        beginOutputRecovery(status.name(), now);
    }

    private void beginOutputRecovery(String reason, long now) {
        if (!outputRecovery.request(weCreatedAl, now)) return;
        outputQuiesced = false;
        outputRecoveryReason = reason;
        outputRecoveries.incrementAndGet();
        quiesceVoiceOutput();
        outputQuiesced = true;
        AkashicVoice.LOG.warn("Voice output recovery requested: {} (ownsOpenAL={})", reason, weCreatedAl);
    }

    private boolean serviceOutputRecovery() {
        if (!outputRecovery.recovering()) return false;
        long now = System.nanoTime();
        if (weCreatedAl && outputRecovery.ownedRestartDue(now)) {
            try {
                if (AL.isCreated()) AL.destroy();
                AL.create();
                initOpenAlBindings();
                outputHealth = OpenAlOutputHealth.capture();
                outputDeviceName = safeDeviceName(outputHealth.expectedDefaultDevice());
                outputRecovery.complete();
                outputQuiesced = false;
                outputUnavailablePolls = 0;
                AkashicVoice.LOG.info("Voice-owned OpenAL output recovered");
            } catch (Throwable failure) {
                outputRecovery.ownedRestartFailed(now);
                AkashicVoice.LOG.warn("Voice-owned OpenAL recovery failed; retrying", failure);
            }
            return true;
        }
        if (!weCreatedAl && outputRecovery.shouldProbeContext(now) && AL.isCreated()) {
            OpenAlOutputHealth candidate = OpenAlOutputHealth.capture();
            if (candidate.poll(true) == OpenAlOutputHealth.Status.HEALTHY) {
                try {
                    initOpenAlBindings();
                    outputHealth = candidate;
                    outputDeviceName = safeDeviceName(candidate.expectedDefaultDevice());
                    outputRecovery.complete();
                    outputQuiesced = false;
                    outputUnavailablePolls = 0;
                    AkashicVoice.LOG.info("Shared OpenAL output recovered after Minecraft sound reload");
                    return true;
                } catch (Throwable failure) {
                    AkashicVoice.LOG.warn("Shared OpenAL context returned but voice rebind failed", failure);
                }
            }
        }
        outputRecovery.retrySharedIfDue(now);
        return true;
    }

    private void quiesceVoiceOutput() {
        for (Channel channel : channels.values()) releaseChannel(channel);
        channels.clear();
        channelEpochs.clear();
        channelSpeakerTokens.clear();
        sourceSpatial.clear();
        PlaybackFrame pending;
        while ((pending = pendingFrames.poll()) != null) freeFrames.offer(pending);
        if (environmentEffects != null) {
            environmentEffects.close();
            environmentEffects = null;
        }
        appliedListenerGain = Float.NaN;
        appliedSpatial = !spatial;
        appliedPoseKnown = false;
        appliedAcousticProfile = null;
    }

    private void runTask(Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            AkashicVoice.LOG.warn("Audio task failed", t);
        }
    }

    private void applyEnvironmentRoute(Channel channel, boolean globalSpatial, boolean sourceIsSpatial,
        AcousticProfile profile) {
        VoiceEnvironmentEffects effects = environmentEffects;
        boolean supported = effects != null && effects.supported();
        if (!supported) return;
        boolean enabled = AcousticRoutingPolicy.effectsEnabled(globalSpatial, sourceIsSpatial, profile, true);
        channel.setEnvironmentEffects(enabled ? effects.auxiliarySlot() : 0, enabled ? effects.directFilter() : 0);
    }

    private void processFrame(PlaybackFrame frame) {
        reconcileListeningState();
        reconcileSpeakerChannels();
        if (!acceptListeningState(frame) || !acceptSpeakerState(frame) || !acceptFrameEpoch(frame)) {
            droppedFrames.incrementAndGet();
            return;
        }
        Channel ch = channels.get(frame.senderId);
        if (ch == null) {
            ch = channelFactory.create(referenceDistance);
            try {
                ch.setGain(
                    listenerGain * speakerControls.state(frame.senderId)
                        .scale());
            } catch (RuntimeException | LinkageError failure) {
                releaseChannel(ch);
                throw failure;
            }
            channels.put(frame.senderId, ch);
            channelEpochs.put(frame.senderId, frame.streamEpoch);
            channelSpeakerTokens.put(frame.senderId, frame.speakerStateToken);
        }
        sourceSpatial.put(frame.senderId, frame.sourceSpatial);
        boolean effectiveSpatial = spatial && frame.sourceSpatial;
        ch.setSpatial(effectiveSpatial);
        applyEnvironmentRoute(ch, spatial, frame.sourceSpatial, appliedAcousticProfile);
        // Preserve the latest world position even while global 3D playback is disabled.
        ch.setPosition(frame.x, frame.y, frame.z);
        if (effectiveSpatial) {
            float effectiveMaxDistance = Float.isFinite(frame.maxDistance) && frame.maxDistance > 0F ? frame.maxDistance
                : maxDistance;
            ch.setMaxDistance(effectiveMaxDistance);
        }
        if (!acceptSpeakerState(frame)) {
            closeChannel(frame.senderId);
            droppedFrames.incrementAndGet();
            return;
        }
        ch.enqueue(frame.pcm());
    }

    private void initOpenAl() throws Exception {
        if (!AL.isCreated()) {
            AL.create();
            weCreatedAl = true;
            AkashicVoice.LOG.info("OpenAL created by AkashicVoice");
        } else {
            AkashicVoice.LOG.info("OpenAL already initialized; reusing existing context");
        }
        initOpenAlBindings();
        outputHealth = OpenAlOutputHealth.capture();
        outputDeviceName = safeDeviceName(outputHealth.expectedDefaultDevice());
        outputUnavailablePolls = 0;
        nextOutputHealthPollNanos = System.nanoTime() + OUTPUT_HEALTH_POLL_NANOS;
    }

    private static String safeDeviceName(String value) {
        return value == null || value.trim()
            .isEmpty() ? "System default" : value.trim();
    }

    private void initOpenAlBindings() {
        AL10.alDistanceModel(AL10.AL_INVERSE_DISTANCE_CLAMPED);
        if (environmentEffects != null) environmentEffects.close();
        environmentEffects = VoiceEnvironmentEffects.create();
        if (environmentEffects.supported()) environmentEffects.apply(acousticProfile);
        appliedAcousticProfile = acousticProfile;
        appliedListenerGain = listenerGain;
        appliedSpatial = spatial;
        appliedPoseKnown = false;
        gainFailureLogged = false;
        spatialFailureLogged = false;
        poseFailureLogged = false;
    }

    private void shutdownOpenAl() {
        synchronized (streamEpochs) {
            // Serialize shutdown with submitFrame: no PCM may arrive after draining.
            running = false;
        }
        for (Channel ch : channels.values()) {
            releaseChannel(ch);
        }
        channels.clear();
        channelEpochs.clear();
        channelSpeakerTokens.clear();
        sourceSpatial.clear();
        streamEpochs.clear();
        tasks.clear();
        PlaybackFrame pending;
        while ((pending = pendingFrames.poll()) != null) {
            freeFrames.offer(pending);
        }

        if (environmentEffects != null) {
            environmentEffects.close();
            environmentEffects = null;
        }

        if (weCreatedAl && AL.isCreated()) {
            try {
                AL.destroy();
            } catch (Throwable t) {
                AkashicVoice.LOG.debug("AL.destroy failed", t);
            }
        }
    }
}
