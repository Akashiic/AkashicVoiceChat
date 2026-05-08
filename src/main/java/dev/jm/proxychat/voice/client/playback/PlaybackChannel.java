package dev.jm.proxychat.voice.client.playback;

import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;

import dev.jm.proxychat.voice.common.codec.AudioFormatSpec;

public final class PlaybackChannel {

    private static final int MAX_QUEUED_BUFFERS = 8;

    private final int sourceId;
    private final Deque<Integer> recycledBuffers = new ArrayDeque<>();
    private float x;
    private float y;
    private float z;

    public PlaybackChannel() {
        IntBuffer src = BufferUtils.createIntBuffer(1);
        AL10.alGenSources(src);
        this.sourceId = src.get(0);

        AL10.alSourcef(sourceId, AL10.AL_REFERENCE_DISTANCE, 1.0f);
        AL10.alSourcef(sourceId, AL10.AL_ROLLOFF_FACTOR, 1.0f);
        AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
    }

    public void setMaxDistance(float maxDistance) {
        AL10.alSourcef(sourceId, AL10.AL_MAX_DISTANCE, maxDistance);
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
            IntBuffer b = BufferUtils.createIntBuffer(1);
            AL10.alGenBuffers(b);
            bufferId = b.get(0);
        }

        ShortBuffer buf = BufferUtils.createShortBuffer(pcm.length);
        buf.put(pcm)
            .flip();
        AL10.alBufferData(bufferId, AL10.AL_FORMAT_MONO16, buf, AudioFormatSpec.SAMPLE_RATE);

        IntBuffer toQueue = BufferUtils.createIntBuffer(1);
        toQueue.put(bufferId)
            .flip();
        AL10.alSourceQueueBuffers(sourceId, toQueue);

        if (AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
            AL10.alSourcePlay(sourceId);
        }
    }

    public void close() {
        AL10.alSourceStop(sourceId);

        int queued = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_QUEUED);
        for (int i = 0; i < queued; i++) {
            IntBuffer b = BufferUtils.createIntBuffer(1);
            AL10.alSourceUnqueueBuffers(sourceId, b);
            recycledBuffers.add(b.get(0));
        }

        for (Integer bufId : recycledBuffers) {
            IntBuffer b = BufferUtils.createIntBuffer(1);
            b.put(bufId)
                .flip();
            AL10.alDeleteBuffers(b);
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
            IntBuffer b = BufferUtils.createIntBuffer(1);
            AL10.alSourceUnqueueBuffers(sourceId, b);
            recycledBuffers.add(b.get(0));
        }
    }
}
