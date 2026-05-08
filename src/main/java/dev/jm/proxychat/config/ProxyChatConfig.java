package dev.jm.proxychat.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public final class ProxyChatConfig {

    public static final int DEFAULT_VOICE_PORT = 24454;
    public static final int DEFAULT_VOICE_RADIUS = 48;
    public static final int DEFAULT_OPUS_BITRATE = 24_000;
    public static final String DEFAULT_ACTIVATION_MODE = "ptt";
    public static final double DEFAULT_VAD_THRESHOLD = 0.02;

    private static int voicePort = DEFAULT_VOICE_PORT;
    private static int voiceRadius = DEFAULT_VOICE_RADIUS;
    private static int opusBitrate = DEFAULT_OPUS_BITRATE;
    private static String activationMode = DEFAULT_ACTIVATION_MODE;
    private static double vadThreshold = DEFAULT_VAD_THRESHOLD;

    private ProxyChatConfig() {}

    public static void load(File configFile) {
        Configuration cfg = new Configuration(configFile);
        cfg.load();

        voicePort = cfg
            .getInt("voicePort", "voice", DEFAULT_VOICE_PORT, 1024, 65535, "UDP port the voice server binds to.");
        voiceRadius = cfg.getInt(
            "voiceRadius",
            "voice",
            DEFAULT_VOICE_RADIUS,
            1,
            512,
            "Maximum distance in blocks at which players can hear each other.");
        opusBitrate = cfg.getInt(
            "opusBitrate",
            "voice",
            DEFAULT_OPUS_BITRATE,
            6_000,
            128_000,
            "Opus encoder bitrate in bits per second.");

        activationMode = cfg.getString(
            "activationMode",
            "client",
            DEFAULT_ACTIVATION_MODE,
            "Microphone gating mode: ptt | voice | always.",
            new String[] { "ptt", "voice", "always" });
        vadThreshold = cfg
            .get(
                "client",
                "vadThreshold",
                DEFAULT_VAD_THRESHOLD,
                "RMS threshold (0.0-1.0) above which a frame is considered speech in 'voice' mode.",
                0.0,
                1.0)
            .getDouble();

        if (cfg.hasChanged()) {
            cfg.save();
        }
    }

    public static int voicePort() {
        return voicePort;
    }

    public static int voiceRadius() {
        return voiceRadius;
    }

    public static int opusBitrate() {
        return opusBitrate;
    }

    public static String activationMode() {
        return activationMode;
    }

    public static double vadThreshold() {
        return vadThreshold;
    }
}
