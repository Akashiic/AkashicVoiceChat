package dev.jm.proxychat.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public final class ProxyChatConfig {

    public static final int DEFAULT_VOICE_PORT = 24454;
    public static final int DEFAULT_VOICE_RADIUS = 48;

    private static int voicePort = DEFAULT_VOICE_PORT;
    private static int voiceRadius = DEFAULT_VOICE_RADIUS;

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
}
