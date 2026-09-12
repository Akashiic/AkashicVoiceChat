package dev.akashic.voice.voice.client.playback;

import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;

import dev.akashic.voice.voice.common.codec.AudioFormatSpec;

public final class PlaybackChannel {

    private static final int MAX_QUEUED_BUFFERS = 8;

    private final int sourceId;
    private final Deque<Integer> recycledBuffers = new ArrayDeque<>();
    private final IntBuffer idScratch = BufferUtils.createIntBuffer(1);
    private final ShortBuffer pcmScratch = BufferUtils.createShortBuffer(AudioFormatSpec.FRAME_SAMPLES);
    private float x;
    private float y;
    private float z;
    private boolean spatial = true;
    private float maxDistance = Float.NaN;

    public PlaybackChannel(float referenceDistance) {
        IntBuffer src = BufferUtils.createIntBuffer(1);
        AL10.alGenSources(src);
        this.sourceId = src.get(0);

        AL10.alSourcef(sourceId, AL10.AL_REFERENCE_DISTANCE, referenceDistance);
        AL10.alSourcef(sourceId, AL10.AL_ROLLOFF_FACTOR, 1.0f);
        AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
    }

    public void setMaxDistance(float maxDistance) {
        if (!Float.isFinite(maxDistance) || maxDistance <= 0F || maxDistance == this.maxDistance) return;
        this.maxDistance = maxDistance;
        AL10.alSourcef(sourceId, AL10.AL_MAX_DISTANCE, maxDistance);
    }

    /**
     * Toggle 3D positional audio. When false, the source plays as if at the listener's
     * head — flat, no falloff. Useful for non-spatial mode (e.g. group chat).
     */
    public void setSpatial(boolean spatial) {
        if (this.spatial == spatial) return;
        this.spatial = spatial;
        AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, spatial ? AL10.AL_FALSE : AL10.AL_TRUE);
        if (!spatial) {
            AL10.alSource3f(sourceId, AL10.AL_POSITION, 0f, 0f, 0f);
        }
    }

    public void setPosition(float x, float y, float z) {
        if (x != this.x || y != this.y || z != this.z) {
            this.x = x;
            this.y = y;
            this.z = z;
            AL10.alSource3f(sourceId, AL10.AL_POSITION, x, y, z);
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

        pcmScratch.clear();
        pcmScratch.put(pcm)
            .flip();
        AL10.alBufferData(bufferId, AL10.AL_FORMAT_MONO16, pcmScratch, AudioFormatSpec.SAMPLE_RATE);

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
