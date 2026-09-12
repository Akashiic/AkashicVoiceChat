package dev.akashic.voice.platform.bridge;

public final class BridgeConstants {
    public static final int SAMPLE_RATE = 48_000;
    public static final int FRAME_SAMPLES = 960;
    public static final int FRAME_MILLIS = 20;
    public static final int MAX_OPUS_PAYLOAD = 1_275;

    private BridgeConstants() {
    }
}
