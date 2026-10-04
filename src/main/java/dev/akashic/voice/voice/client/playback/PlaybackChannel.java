package dev.akashic.voice.voice.client.playback;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EFX10;

import dev.akashic.voice.voice.common.codec.AudioFormatSpec;

public final class PlaybackChannel implements AudioPlayback.Channel {

    private static final int MAX_QUEUED_BUFFERS = 8;

    private final int sourceId;
    private final VoiceGain.Backend gainBackend;
    private final FloatBuffer floatScratch;
    private final ByteBuffer floatBytes;
    private float voiceGain = Float.NaN;
    private float pcmBoost = 1f;
    private long clippedSamples;
    private long discardedGainChangeBuffers;
    private static final java.util.concurrent.atomic.AtomicBoolean fallbackWarning = new java.util.concurrent.atomic.AtomicBoolean();
    private final Deque<Integer> recycledBuffers = new ArrayDeque<>();
    private final IntBuffer idScratch = BufferUtils.createIntBuffer(1);
    private final ShortBuffer pcmScratch = BufferUtils.createShortBuffer(AudioFormatSpec.FRAME_SAMPLES);
    private float x;
    private float y;
    private float z;
    private boolean spatial = true;
    private float maxDistance = Float.NaN;

    public PlaybackChannel(float referenceDistance) {
        gainBackend = detectGainBackend();
        floatBytes = gainBackend == VoiceGain.Backend.FLOAT_HEADROOM
            ? BufferUtils.createByteBuffer(AudioFormatSpec.FRAME_SAMPLES * 4)
                .order(ByteOrder.nativeOrder())
            : null;
        floatScratch = floatBytes == null ? null : floatBytes.asFloatBuffer();
        IntBuffer src = BufferUtils.createIntBuffer(1);
        AL10.alGenSources(src);
        this.sourceId = src.get(0);

        try {
            AL10.alSourcef(sourceId, AL10.AL_REFERENCE_DISTANCE, referenceDistance);
            AL10.alSourcef(sourceId, AL10.AL_ROLLOFF_FACTOR, 1.0f);
            AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
            if (gainBackend == VoiceGain.Backend.EXTENDED_SOURCE) {
                AL10.alSourcef(sourceId, AL10.AL_MAX_GAIN, VoiceGain.MAX_GAIN);
                if (AL10.alGetSourcef(sourceId, AL10.AL_MAX_GAIN) != VoiceGain.MAX_GAIN)
                    throw new IllegalStateException("Driver rejected extended voice gain");
            }
            setGain(1f);
        } catch (RuntimeException | LinkageError failure) {
            try {
                src.clear();
                src.put(sourceId)
                    .flip();
                AL10.alDeleteSources(src);
            } catch (RuntimeException | LinkageError cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private static VoiceGain.Backend detectGainBackend() {
        if (AL10.alIsExtensionPresent("AL_SOFT_gain_clamp_ex")) {
            float limit = AL10.alGetFloat(VoiceGain.GAIN_LIMIT_SOFT);
            if (Float.isFinite(limit) && limit >= VoiceGain.MAX_GAIN) return VoiceGain.Backend.EXTENDED_SOURCE;
        }
        // Out-of-unit float headroom is verified on OpenAL Soft, not assumed for other drivers.
        if (AL10.alIsExtensionPresent("AL_EXT_FLOAT32")
            && "OpenAL Soft".equalsIgnoreCase(AL10.alGetString(AL10.AL_RENDERER)))
            return VoiceGain.Backend.FLOAT_HEADROOM;
        return VoiceGain.Backend.PCM16_FALLBACK;
    }

    public void setGain(float gain) {
        if (!VoiceGain.valid(gain) || gain == voiceGain) return;
        float nativeGain = VoiceGain.sourceGain(gainBackend, gain);
        float boost = VoiceGain.pcmBoost(gainBackend, gain);
        AL10.alSourcef(sourceId, AL10.AL_GAIN, nativeGain);
        if (AL10.alGetSourcef(sourceId, AL10.AL_GAIN) != nativeGain)
            throw new IllegalStateException("Driver rejected voice source gain");
        if (boost != pcmBoost) {
            // Core-only fallback cannot rescale attached S16 buffers. Drop stale boosted PCM.
            int queued = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_QUEUED);
            if (queued > 0) {
                AL10.alSourceStop(sourceId);
                for (int i = 0; i < queued; i++) {
                    idScratch.clear();
                    AL10.alSourceUnqueueBuffers(sourceId, idScratch);
                    recycledBuffers.add(idScratch.get(0));
                    discardedGainChangeBuffers++;
                }
            }
            if (boost > 1f && fallbackWarning.compareAndSet(false, true)) dev.akashic.voice.AkashicVoice.LOG
                .warn("Voice gain uses bounded S16 boost on this OpenAL driver; peaks may clip above 100%");
        }
        pcmBoost = boost;
        voiceGain = gain;
    }

    public String gainBackendName() {
        return gainBackend.name();
    }

    public long clippedSampleCount() {
        return clippedSamples;
    }

    public long discardedGainChangeBufferCount() {
        return discardedGainChangeBuffers;
    }

    @Override
    public void setEnvironmentEffects(int auxiliarySlot, int directFilter) {
        int slot = auxiliarySlot > 0 ? auxiliarySlot : EFX10.AL_EFFECTSLOT_NULL;
        int filter = directFilter > 0 ? directFilter : EFX10.AL_FILTER_NULL;
        AL11.alSource3i(sourceId, EFX10.AL_AUXILIARY_SEND_FILTER, slot, 0, EFX10.AL_FILTER_NULL);
        AL10.alSourcei(sourceId, EFX10.AL_DIRECT_FILTER, filter);
    }

    public void setMaxDistance(float maxDistance) {
        if (!Float.isFinite(maxDistance) || maxDistance <= 0F || maxDistance == this.maxDistance) return;
        AL10.alSourcef(sourceId, AL10.AL_MAX_DISTANCE, maxDistance);
        this.maxDistance = maxDistance;
    }

    /**
     * Toggle 3D positional audio. When false, the source plays as if at the listener's
     * head — flat, no falloff. Useful for non-spatial mode (e.g. group chat).
     */
    public void setSpatial(boolean spatial) {
        if (this.spatial == spatial) return;
        AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, spatial ? AL10.AL_FALSE : AL10.AL_TRUE);
        // Cached world coordinates and the native source position differ in flat mode.
        // Reapply on every mode transition, even when the speaker has not moved.
        AL10.alSource3f(sourceId, AL10.AL_POSITION, spatial ? x : 0f, spatial ? y : 0f, spatial ? z : 0f);
        // A failed/partial binding call must not suppress the next retry.
        this.spatial = spatial;
    }

    public void setPosition(float x, float y, float z) {
        if (x != this.x || y != this.y || z != this.z) {
            // Remember movement while flat without moving a listener-relative source.
            // For a spatial source, commit the cache only after the binding call returns.
            if (spatial) AL10.alSource3f(sourceId, AL10.AL_POSITION, x, y, z);
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public void enqueue(short[] pcm) {
        recycleProcessed();

        int queued = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_QUEUED);
        if (queued >= MAX_QUEUED_BUFFERS) {
            // Drop frame to keep latency bounded.
            return;
        }

        int bufferId;
        if (!recycledBuffers.isEmpty()) {
            bufferId = recycledBuffers.poll();
        } else {
            idScratch.clear();
            AL10.alGenBuffers(idScratch);
            bufferId = idScratch.get(0);
        }

        if (gainBackend == VoiceGain.Backend.FLOAT_HEADROOM) {
            VoiceGain.writeFloat(pcm, floatScratch);
            // LWJGL2 exposes ByteBuffer, not the later FloatBuffer overload.
            floatBytes.clear();
            floatBytes.limit(floatScratch.remaining() * 4);
            AL10.alBufferData(bufferId, VoiceGain.MONO_FLOAT32, floatBytes, AudioFormatSpec.SAMPLE_RATE);
        } else {
            clippedSamples += VoiceGain.writeS16(pcm, pcmBoost, pcmScratch);
            AL10.alBufferData(bufferId, AL10.AL_FORMAT_MONO16, pcmScratch, AudioFormatSpec.SAMPLE_RATE);
        }

        idScratch.clear();
        idScratch.put(bufferId)
            .flip();
        AL10.alSourceQueueBuffers(sourceId, idScratch);

        if (AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
            AL10.alSourcePlay(sourceId);
        }
    }

    public void close() {
        AL10.alSourceStop(sourceId);

        int queued = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_QUEUED);
        for (int i = 0; i < queued; i++) {
            idScratch.clear();
            AL10.alSourceUnqueueBuffers(sourceId, idScratch);
            recycledBuffers.add(idScratch.get(0));
        }

        for (Integer bufId : recycledBuffers) {
            idScratch.clear();
            idScratch.put(bufId)
                .flip();
            AL10.alDeleteBuffers(idScratch);
        }
        recycledBuffers.clear();

        IntBuffer src = BufferUtils.createIntBuffer(1);
        src.put(sourceId)
            .flip();
        AL10.alDeleteSources(src);
    }

    private void recycleProcessed() {
        int processed = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_PROCESSED);
        for (int i = 0; i < processed; i++) {
            idScratch.clear();
            AL10.alSourceUnqueueBuffers(sourceId, idScratch);
            recycledBuffers.add(idScratch.get(0));
        }
    }
}
