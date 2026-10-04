package dev.akashic.voice.config;

/** Named bitrate choices only. Sample rate, frame duration, channels and DSP are unchanged. */
public enum VoiceQualityPreset {

    ECONOMY("economy", 24_000),
    BALANCED("balanced", 32_000),
    HD("hd", 48_000),
    DETAIL("detail", 64_000);

    public static final int MIN_BITRATE = 6_000;
    public static final int MAX_BITRATE = 128_000;
    private final String id;
    private final int bitrate;

    VoiceQualityPreset(String id, int bitrate) {
        this.id = id;
        this.bitrate = bitrate;
    }

    public String id() {
        return id;
    }

    public int bitrate() {
        return bitrate;
    }

    /** A null result means the existing numeric configuration is custom, not invalid. */
    public static VoiceQualityPreset fromBitrate(int bitrate) {
        for (VoiceQualityPreset preset : values()) if (preset.bitrate == bitrate) return preset;
        return null;
    }

    public static int requireValidBitrate(int bitrate) {
        if (bitrate < MIN_BITRATE || bitrate > MAX_BITRATE) {
            throw new IllegalArgumentException("Voice bitrate must be between 6000 and 128000 bits/s");
        }
        return bitrate;
    }
}
