package dev.akashic.voice.voice.common.codec;

public final class AudioFormatSpec {

    public static final int SAMPLE_RATE = 48000;
    public static final int CHANNELS = 1;
    public static final int FRAME_DURATION_MS = 20;
    public static final int FRAME_SAMPLES = SAMPLE_RATE * FRAME_DURATION_MS / 1000; // 960
    public static final int FRAME_BYTES_PCM_S16 = FRAME_SAMPLES * 2; // 1920
    // Session-bound AV v3 carries 48 bytes of media metadata inside a 44-byte AEAD envelope.
    // 1108 + 48 + 44 = 1200. Legacy AV remains below the same transport target.
    public static final int MAX_OPUS_PAYLOAD = 1108;

    private AudioFormatSpec() {}
}
