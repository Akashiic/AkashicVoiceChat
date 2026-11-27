package dev.akashic.voice.voice.client.playback;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import dev.akashic.voice.voice.client.MicCapture;
import dev.akashic.voice.voice.client.audio.capture.*;
import dev.akashic.voice.voice.common.codec.OpusEncoder;

/**
 * Issue #3 (OpenAL natives unlinked after a Minecraft sound-system reload) and issue #17 (output-health poll
 * terminating the playback thread).
 *
 * <p>
 * The JVM running this smoke never loads the OpenAL natives, so every real LWJGL 2 AL/ALC/EFX entry point
 * raises {@link UnsatisfiedLinkError} exactly as it does between {@code AL.destroy()} and {@code AL.create()}
 * in the field. That gives a faithful reproduction of the unlinked state without audio hardware. It does
 * not prove the behaviour against a real reload; that still needs the live G6 check.
 */
public final class OpenAlBindingLossSmoke {
    interface Scenario { void run() throws Exception; }
    static int passed, failed;
    static final UUID A = new UUID(1, 2), B = new UUID(3, 4);
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void run(String name, Scenario scenario) {
        try { scenario.run(); passed++; System.out.println("PASS " + name); }
        catch (Throwable error) { failed++; System.out.println("FAIL " + name + ": " + error); error.printStackTrace(System.out); }
    }

    public static void main(String[] args) throws Exception {
        run("binding_loss_classifier", OpenAlBindingLossSmoke::classifier);
        run("real_lwjgl_is_unlinked_in_this_jvm", OpenAlBindingLossSmoke::realUnlinked);
        run("controller_shared_binding_loss_waits_without_reload", OpenAlBindingLossSmoke::controllerShared);
        run("controller_owned_binding_loss_restarts_after_settle", OpenAlBindingLossSmoke::controllerOwned);
        run("binding_loss_abandons_channels_without_native_close", OpenAlBindingLossSmoke::abandon);
        run("binding_loss_recovery_is_requested_once", OpenAlBindingLossSmoke::once);
        run("efx_probe_on_unlinked_natives_is_retryable", OpenAlBindingLossSmoke::efxRetryable);
        run("health_poll_failure_never_escapes_loop_guard", OpenAlBindingLossSmoke::healthGuard);
        run("unlinked_capture_device_raises_binding_lost_and_skips_close", OpenAlBindingLossSmoke::captureDevice);
        run("mic_loop_ends_cleanly_on_binding_loss", OpenAlBindingLossSmoke::micLoop);
        System.out.println("OPENAL_BINDING_LOSS_RESULT passed=" + passed + " failed=" + failed);
        if (failed != 0) throw new AssertionError("binding-loss regressions=" + failed);
    }

    static void classifier() {
        check(AudioPlayback.isNativeBindingLoss(new UnsatisfiedLinkError("x")), "direct ULE not classified");
        check(AudioPlayback.isNativeBindingLoss(new IllegalStateException(new UnsatisfiedLinkError("x"))),
            "wrapped ULE not classified");
        check(!AudioPlayback.isNativeBindingLoss(new IllegalStateException("x")), "plain failure misclassified");
        check(!AudioPlayback.isNativeBindingLoss(new NoClassDefFoundError("x")), "class error misclassified");
        check(!AudioPlayback.isNativeBindingLoss(null), "null misclassified");
    }

    static void realUnlinked() {
        try {
            org.lwjgl.openal.ALC10.alcGetCurrentContext();
            throw new AssertionError("OpenAL natives unexpectedly linked; this smoke needs a JVM without them");
        } catch (UnsatisfiedLinkError expected) {
            // the field condition, reproduced
        }
    }

    static void controllerShared() {
        OutputRecoveryController c = new OutputRecoveryController();
        check(c.requestAfterBindingLoss(false, 1000L), "request refused");
        check(c.state() == OutputRecoveryController.State.WAITING_CONTEXT, "shared loss must wait, got " + c.state());
        check(!c.claimSharedReload(), "shared loss issued a second sound reload");
        check(!c.shouldProbeContext(1000L), "probed before settle");
        check(c.shouldProbeContext(1000L + OutputRecoveryController.CONTEXT_SETTLE_NANOS), "no probe after settle");
        check(!c.retrySharedIfDue(1000L + OutputRecoveryController.CONTEXT_SETTLE_NANOS), "fallback too early");
        check(c.retrySharedIfDue(1000L + OutputRecoveryController.SHARED_RETRY_NANOS), "no reload fallback");
        check(c.claimSharedReload(), "fallback reload not claimable");
    }

    static void controllerOwned() {
        OutputRecoveryController c = new OutputRecoveryController();
        check(c.requestAfterBindingLoss(true, 0L), "request refused");
        check(c.state() == OutputRecoveryController.State.OWNED_PENDING, "owned loss state " + c.state());
        check(!c.ownedRestartDue(0L), "owned restart before settle");
        check(c.ownedRestartDue(OutputRecoveryController.CONTEXT_SETTLE_NANOS), "owned restart not due");
    }

    static void abandon() throws Exception {
        try (Fixture f = new Fixture()) {
            f.frame(A, 1); f.frame(B, 1);
            begin(f.output, new UnsatisfiedLinkError("org.lwjgl.openal.AL11.nalSource3i(IIIII)V"));
            check(f.channels().isEmpty(), "channels kept after binding loss");
            for (Sink sink : f.made) check(sink.closes == 0, "native close attempted on a vanished context");
            check("UNLINKED".equals(f.output.outputRecoveryReason()), "reason " + f.output.outputRecoveryReason());
            check(f.output.isOutputRecovering(), "recovery not requested");
            check(f.queue("pendingFrames").isEmpty(), "pending PCM kept");
            check(f.queue("freeFrames").size() == 128, "PCM pool lost entries");
        }
    }

    static void once() throws Exception {
        try (Fixture f = new Fixture()) {
            long before = f.output.outputRecoveryCount();
            begin(f.output, new UnsatisfiedLinkError("a"));
            begin(f.output, new UnsatisfiedLinkError("b"));
            check(f.output.outputRecoveryCount() == before + 1, "per-frame failures re-requested recovery");
        }
    }

    static void efxRetryable() throws Exception {
        Method create = VoiceEnvironmentEffects.class.getDeclaredMethod("create");
        create.setAccessible(true);
        VoiceEnvironmentEffects effects = (VoiceEnvironmentEffects) create.invoke(null);
        check(!effects.supported(), "EFX reported supported on unlinked natives");
        check(effects.retryable(), "unlinked EFX probe disabled acoustics for the whole session");
    }

    /** With natives unlinked the real health monitor and recovery services must not throw out of the guard. */
    static void healthGuard() throws Exception {
        try (Fixture f = new Fixture()) {
            Method poll = AudioPlayback.class.getDeclaredMethod("guardedPollOutputHealth");
            Method service = AudioPlayback.class.getDeclaredMethod("guardedServiceOutputRecovery");
            Field next = AudioPlayback.class.getDeclaredField("nextOutputHealthPollNanos");
            next.setAccessible(true);
            Field health = AudioPlayback.class.getDeclaredField("outputHealth");
            health.setAccessible(true);
            Method capture = OpenAlOutputHealth.class.getDeclaredMethod("capture");
            capture.setAccessible(true);
            health.set(f.output, capture.invoke(null));
            for (int i = 0; i < 10; i++) {
                next.setLong(f.output, 0L);
                invoke(f.output, poll);
                invoke(f.output, service);
            }
        }
    }

    static void captureDevice() throws Exception {
        Class<?> deviceClass = Class.forName(
            "dev.akashic.voice.voice.client.audio.capture.OpenALCaptureBackend$Device");
        Constructor<org.lwjgl.openal.ALCdevice> handle =
            org.lwjgl.openal.ALCdevice.class.getDeclaredConstructor(long.class);
        handle.setAccessible(true);
        Constructor<?> ctor = deviceClass.getDeclaredConstructor(org.lwjgl.openal.ALCdevice.class, String.class);
        ctor.setAccessible(true);
        AudioCaptureDevice device = (AudioCaptureDevice) ctor.newInstance(handle.newInstance(0x1234L), "qa");
        try {
            device.read(new byte[1920], 0, 1920);
            throw new AssertionError("unlinked read returned");
        } catch (OpenALCaptureBackend.NativeBindingLostException expected) {
            check(expected.getCause() instanceof UnsatisfiedLinkError, "cause lost");
        }
        Field lost = deviceClass.getDeclaredField("nativeBindingLost");
        lost.setAccessible(true);
        check(lost.getBoolean(device), "stale handle not marked");
        device.close();
        device.close();
    }

    static void micLoop() throws Exception {
        final AtomicInteger closes = new AtomicInteger(), sent = new AtomicInteger();
        Constructor<OpenALCaptureBackend.NativeBindingLostException> lost =
            OpenALCaptureBackend.NativeBindingLostException.class.getDeclaredConstructor(LinkageError.class);
        lost.setAccessible(true);
        final RuntimeException failure = lost.newInstance(new UnsatisfiedLinkError("org.lwjgl.openal.ALC10.nalcGetError(J)I"));
        AudioCaptureDevice device = new AudioCaptureDevice() {
            public int read(byte[] b, int o, int l) { throw failure; }
            public String backendName() { return "qa"; }
            public String description() { return "unlinked"; }
            public void close() { closes.incrementAndGet(); }
        };
        OpusEncoder encoder = new OpusEncoder() {
            public int encode(short[] pcm, byte[] out) { out[0] = 1; return 1; }
            public void close() {}
        };
        MicCaptureSession session = new MicCaptureSession(device, encoder);
        MicCapture mic = new MicCapture((seq, payload, len) -> sent.incrementAndGet());
        Method loop = MicCapture.class.getDeclaredMethod("loop", MicCaptureSession.class);
        loop.setAccessible(true);
        final AtomicReference<Throwable> escaped = new AtomicReference<Throwable>();
        Thread worker = new Thread(() -> {
            try { loop.invoke(mic, session); }
            catch (InvocationTargetException e) { escaped.set(e.getCause()); }
            catch (Throwable e) { escaped.set(e); }
        }, "AkashicVoice-QA-Unlinked");
        worker.start(); worker.join(10000);
        check(!worker.isAlive(), "mic loop stuck");
        check(escaped.get() == null, "binding loss escaped the mic loop: " + escaped.get());
        check(closes.get() == 1, "capture device not finished once");
        check(!session.isRunning(), "session still running; recovery could not reopen");
        check(sent.get() == 0, "frame sent from unlinked device");
    }

    // --- fixture (mirrors PlaybackCleanupSmoke) ---

    static void begin(AudioPlayback output, Throwable cause) throws Exception {
        invoke(output, AudioPlayback.class.getDeclaredMethod("beginBindingLossRecovery", Throwable.class, long.class),
            cause, System.nanoTime());
    }
    static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    static Object invoke(Object target, Method method, Object... args) throws Exception {
        method.setAccessible(true);
        try { return method.invoke(target, args); }
        catch (InvocationTargetException e) {
            Throwable t = e.getCause(); if (t instanceof Error) throw (Error) t; throw (Exception) t;
        }
    }
    static final class Sink implements AudioPlayback.Channel {
        int closes, frames;
        public void setSpatial(boolean value) {}
        public void setPosition(float x, float y, float z) {}
        public void setMaxDistance(float value) {}
        public void enqueue(short[] pcm) { frames++; }
        public void close() { closes++; }
    }
    static final class Fixture implements AutoCloseable {
        final List<Sink> made = new ArrayList<Sink>();
        final AudioPlayback output = new AudioPlayback(distance -> { Sink sink = new Sink(); made.add(sink); return sink; });
        Fixture() throws Exception {
            Field running = AudioPlayback.class.getDeclaredField("running"); running.setAccessible(true);
            running.setBoolean(output, true);
        }
        @SuppressWarnings("unchecked") java.util.concurrent.BlockingQueue<PlaybackFrame> queue(String name) throws Exception {
            return (java.util.concurrent.BlockingQueue<PlaybackFrame>) field(output, name);
        }
        @SuppressWarnings("unchecked") Map<UUID, ?> channels() throws Exception {
            return (Map<UUID, ?>) field(output, "channels");
        }
        void frame(UUID sender, long epoch) throws Exception {
            PlaybackFrame frame = output.acquireFrame(); check(frame != null, "empty pool");
            check(output.submitFrame(frame, sender, true, 0, 0, 0, 48, false, epoch), "submission rejected");
            PlaybackFrame next = queue("pendingFrames").poll(); check(next != null, "empty queue");
            try { invoke(output, AudioPlayback.class.getDeclaredMethod("processFrame", PlaybackFrame.class), next); }
            finally { output.releaseFrame(next); }
        }
        public void close() throws Exception {
            invoke(output, AudioPlayback.class.getDeclaredMethod("shutdownOpenAl"));
        }
    }
}
