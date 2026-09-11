package dev.akashic.voice.voice.client.playback;

import java.util.UUID;

import dev.akashic.voice.voice.common.codec.AudioFormatSpec;

public final class PlaybackFrame {

    private final short[] pcm = new short[AudioFormatSpec.FRAME_SAMPLES];
    UUID senderId;
    boolean sourceSpatial;
    float x;
    float y;
    float z;

    public short[] pcm() {
        return pcm;
    }

    void prepare(UUID senderId, boolean sourceSpatial, float x, float y, float z) {
        this.senderId = senderId;
        this.sourceSpatial = sourceSpatial;
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
