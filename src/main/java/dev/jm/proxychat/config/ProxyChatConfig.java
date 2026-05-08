package dev.jm.proxychat.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public final class ProxyChatConfig {

    public static final int DEFAULT_VOICE_PORT = 24454;
    public static final int DEFAULT_VOICE_RADIUS = 48;
    public static final int DEFAULT_OPUS_BITRATE = 24_000;
    public static final String DEFAULT_ACTIVATION_MODE = "ptt";
    public static final double DEFAULT_VAD_THRESHOLD = 0.02;
    public static final boolean DEFAULT_DEBUG_SELF_ECHO = false;
    public static final double DEFAULT_PLAYBACK_VOLUME = 3.0;
    public static final double DEFAULT_REFERENCE_DISTANCE = 4.0;
    public static final String DEFAULT_SELECTED_MIC_MIXER = "";
    public static final boolean DEFAULT_FIRST_RUN_COMPLETE = false;

    private static Configuration cfg;

    private static int voicePort = DEFAULT_VOICE_PORT;
    private static int voiceRadius = DEFAULT_VOICE_RADIUS;
    private static int opusBitrate = DEFAULT_OPUS_BITRATE;
    private static String activationMode = DEFAULT_ACTIVATION_MODE;
    private static double vadThreshold = DEFAULT_VAD_THRESHOLD;
    private static boolean debugSelfEcho = DEFAULT_DEBUG_SELF_ECHO;
    private static double playbackVolume = DEFAULT_PLAYBACK_VOLUME;
    private static double referenceDistance = DEFAULT_REFERENCE_DISTANCE;
    private static String selectedMicMixer = DEFAULT_SELECTED_MIC_MIXER;
    private static boolean firstRunComplete = DEFAULT_FIRST_RUN_COMPLETE;

    private ProxyChatConfig() {}

    public static synchronized void load(File configFile) {
        cfg = new Configuration(configFile);
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

        debugSelfEcho = cfg.getBoolean(
            "debugSelfEcho",
            "voice",
            DEFAULT_DEBUG_SELF_ECHO,
            "If true, the server also echoes mic frames back to the sender. Solo-testing only — turn off in production.");

        playbackVolume = cfg.get(
            "client",
            "playbackVolume",
            DEFAULT_PLAYBACK_VOLUME,
            "OpenAL listener gain applied to all incoming voices. 1.0 = normal, 3.0 = +9 dB (default). Raise if voices sound too quiet.",
            0.0,
            10.0)
            .getDouble();

        referenceDistance = cfg
            .get(
                "client",
                "referenceDistance",
                DEFAULT_REFERENCE_DISTANCE,
                "Distance in blocks within which a voice plays at full volume before attenuation begins.",
                1.0,
                64.0)
            .getDouble();

        selectedMicMixer = cfg.getString(
            "selectedMicMixer",
            "client",
            DEFAULT_SELECTED_MIC_MIXER,
            "Name of the audio Mixer used for microphone input. Empty = system default.");

        firstRunComplete = cfg.getBoolean(
            "firstRunComplete",
            "client",
            DEFAULT_FIRST_RUN_COMPLETE,
            "Set automatically once the user finishes the first-run setup wizard. Reset to false to re-show the wizard.");

        if (cfg.hasChanged()) {
            cfg.save();
        }
    }

    public static synchronized void save() {
        if (cfg == null) return;
        cfg.get("voice", "voicePort", DEFAULT_VOICE_PORT)
            .set(voicePort);
        cfg.get("voice", "voiceRadius", DEFAULT_VOICE_RADIUS)
            .set(voiceRadius);
        cfg.get("voice", "opusBitrate", DEFAULT_OPUS_BITRATE)
            .set(opusBitrate);
        cfg.get("voice", "debugSelfEcho", DEFAULT_DEBUG_SELF_ECHO)
            .set(debugSelfEcho);

        cfg.get("client", "activationMode", DEFAULT_ACTIVATION_MODE)
            .set(activationMode);
        cfg.get("client", "vadThreshold", DEFAULT_VAD_THRESHOLD)
            .set(vadThreshold);
        cfg.get("client", "playbackVolume", DEFAULT_PLAYBACK_VOLUME)
            .set(playbackVolume);
        cfg.get("client", "referenceDistance", DEFAULT_REFERENCE_DISTANCE)
            .set(referenceDistance);
        cfg.get("client", "selectedMicMixer", DEFAULT_SELECTED_MIC_MIXER)
            .set(selectedMicMixer);
        cfg.get("client", "firstRunComplete", DEFAULT_FIRST_RUN_COMPLETE)
            .set(firstRunComplete);
        cfg.save();
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

    public static void setActivationMode(String mode) {
        activationMode = mode;
    }

    public static double vadThreshold() {
        return vadThreshold;
    }

    public static void setVadThreshold(double v) {
        vadThreshold = v;
    }

    public static boolean debugSelfEcho() {
        return debugSelfEcho;
    }

    public static double playbackVolume() {
        return playbackVolume;
    }

    public static void setPlaybackVolume(double v) {
        playbackVolume = v;
    }

    public static double referenceDistance() {
        return referenceDistance;
    }

    public static String selectedMicMixer() {
        return selectedMicMixer;
    }

    public static void setSelectedMicMixer(String name) {
        selectedMicMixer = name == null ? "" : name;
    }

    public static boolean firstRunComplete() {
        return firstRunComplete;
    }

    public static void setFirstRunComplete(boolean v) {
        firstRunComplete = v;
    }
}
