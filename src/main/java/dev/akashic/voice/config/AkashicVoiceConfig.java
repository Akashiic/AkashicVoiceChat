package dev.akashic.voice.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public final class AkashicVoiceConfig {

    public static final int DEFAULT_VOICE_PORT = 24454;
    public static final int DEFAULT_VOICE_RADIUS = 48;
    public static final int DEFAULT_OPUS_BITRATE = 48_000;
    public static final String DEFAULT_OPUS_BACKEND = "auto";
    public static final String DEFAULT_ACTIVATION_MODE = "ptt";
    public static final double DEFAULT_THRESHOLD_DB = -50.0;
    public static final boolean DEFAULT_DEBUG_SELF_ECHO = false;
    public static final double DEFAULT_PLAYBACK_VOLUME = 1.0; // shown to user as 100% - applied as listener gain
    public static final double DEFAULT_MIC_AMPLIFICATION = 1.0;
    public static final boolean DEFAULT_NOISE_SUPPRESSION = false;
    public static final String DEFAULT_AUDIO_3D_MODE = "normal";
    public static final double DEFAULT_REFERENCE_DISTANCE = 4.0;
    public static final String DEFAULT_SELECTED_MIC_MIXER = "";
    public static final String DEFAULT_SELECTED_SPEAKER_MIXER = "";
    public static final boolean DEFAULT_FIRST_RUN_COMPLETE = false;
    public static final boolean DEFAULT_SHOW_SPEAKER_INDICATORS = true;

    private static Configuration cfg;

    private static int voicePort = DEFAULT_VOICE_PORT;
    private static int voiceRadius = DEFAULT_VOICE_RADIUS;
    private static int opusBitrate = DEFAULT_OPUS_BITRATE;
    private static String opusBackend = DEFAULT_OPUS_BACKEND;
    private static String activationMode = DEFAULT_ACTIVATION_MODE;
    private static double thresholdDb = DEFAULT_THRESHOLD_DB;
    private static boolean debugSelfEcho = DEFAULT_DEBUG_SELF_ECHO;
    private static double playbackVolume = DEFAULT_PLAYBACK_VOLUME;
    private static double micAmplification = DEFAULT_MIC_AMPLIFICATION;
    private static boolean noiseSuppression = DEFAULT_NOISE_SUPPRESSION;
    private static String audio3dMode = DEFAULT_AUDIO_3D_MODE;
    private static double referenceDistance = DEFAULT_REFERENCE_DISTANCE;
    private static String selectedMicMixer = DEFAULT_SELECTED_MIC_MIXER;
    private static String selectedSpeakerMixer = DEFAULT_SELECTED_SPEAKER_MIXER;
    private static boolean firstRunComplete = DEFAULT_FIRST_RUN_COMPLETE;
    private static boolean showSpeakerIndicators = DEFAULT_SHOW_SPEAKER_INDICATORS;

    private AkashicVoiceConfig() {}

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
        opusBackend = cfg.getString(
            "opusBackend",
            "client",
            DEFAULT_OPUS_BACKEND,
            "Opus implementation: auto (native when available, Java fallback) | native (required) | java.",
            new String[] { "auto", "native", "java" });
        debugSelfEcho = cfg.getBoolean(
            "debugSelfEcho",
            "voice",
            DEFAULT_DEBUG_SELF_ECHO,
            "If true, the server echoes mic frames back to the sender. Solo-testing only — turn off in production.");

        activationMode = cfg.getString(
            "activationMode",
            "client",
            DEFAULT_ACTIVATION_MODE,
            "Microphone gating mode: ptt | voice | always.",
            new String[] { "ptt", "voice", "always" });
        thresholdDb = cfg.get(
            "client",
            "voiceActivationThresholdDb",
            DEFAULT_THRESHOLD_DB,
            "Voice activation threshold in dBFS. Frames louder than this trigger transmission in 'voice' mode. -50 is typical for normal speech.",
            -80.0,
            0.0)
            .getDouble();
        playbackVolume = cfg
            .get(
                "client",
                "playbackVolume",
                DEFAULT_PLAYBACK_VOLUME,
                "Master listener gain (1.0 = 100%). Applied to all incoming voices.",
                0.0,
                3.0)
            .getDouble();
        micAmplification = cfg
            .get(
                "client",
                "microphoneAmplification",
                DEFAULT_MIC_AMPLIFICATION,
                "Pre-encode gain applied to your mic PCM. 1.0 = unchanged, 2.0 = +6 dB.",
                0.0,
                3.0)
            .getDouble();
        noiseSuppression = cfg.getBoolean(
            "noiseSuppression",
            "client",
            DEFAULT_NOISE_SUPPRESSION,
            "Apply a basic high-pass noise gate to the mic before encoding. (Lightweight; not RNNoise.)");
        audio3dMode = cfg.getString(
            "audio3dMode",
            "client",
            DEFAULT_AUDIO_3D_MODE,
            "How incoming voices are rendered: normal (3D positional) | off (centered, flat).",
            new String[] { "normal", "off" });
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
        selectedSpeakerMixer = cfg.getString(
            "selectedSpeakerMixer",
            "client",
            DEFAULT_SELECTED_SPEAKER_MIXER,
            "Name of the audio Mixer used for speaker output. Empty = system default. (Currently informational; OpenAL picks the system default.)");

        firstRunComplete = cfg.getBoolean(
            "firstRunComplete",
            "client",
            DEFAULT_FIRST_RUN_COMPLETE,
            "Set automatically once the user finishes the first-run setup wizard. Reset to false to re-show the wizard.");
        showSpeakerIndicators = cfg.getBoolean(
            "showSpeakerIndicators",
            "client",
            DEFAULT_SHOW_SPEAKER_INDICATORS,
            "Show the green speaking indicator above other players' heads.");

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
        cfg.get("client", "opusBackend", DEFAULT_OPUS_BACKEND)
            .set(opusBackend);
        cfg.get("voice", "debugSelfEcho", DEFAULT_DEBUG_SELF_ECHO)
            .set(debugSelfEcho);

        cfg.get("client", "activationMode", DEFAULT_ACTIVATION_MODE)
            .set(activationMode);
        cfg.get("client", "voiceActivationThresholdDb", DEFAULT_THRESHOLD_DB)
            .set(thresholdDb);
        cfg.get("client", "playbackVolume", DEFAULT_PLAYBACK_VOLUME)
            .set(playbackVolume);
        cfg.get("client", "microphoneAmplification", DEFAULT_MIC_AMPLIFICATION)
            .set(micAmplification);
        cfg.get("client", "noiseSuppression", DEFAULT_NOISE_SUPPRESSION)
            .set(noiseSuppression);
        cfg.get("client", "audio3dMode", DEFAULT_AUDIO_3D_MODE)
            .set(audio3dMode);
        cfg.get("client", "referenceDistance", DEFAULT_REFERENCE_DISTANCE)
            .set(referenceDistance);
        cfg.get("client", "selectedMicMixer", DEFAULT_SELECTED_MIC_MIXER)
            .set(selectedMicMixer);
        cfg.get("client", "selectedSpeakerMixer", DEFAULT_SELECTED_SPEAKER_MIXER)
            .set(selectedSpeakerMixer);
        cfg.get("client", "firstRunComplete", DEFAULT_FIRST_RUN_COMPLETE)
            .set(firstRunComplete);
        cfg.get("client", "showSpeakerIndicators", DEFAULT_SHOW_SPEAKER_INDICATORS)
            .set(showSpeakerIndicators);
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

    public static String opusBackend() {
        return opusBackend;
    }

    public static void setOpusBackend(String backend) {
        if (backend == null) return;
        if (!"auto".equals(backend) && !"native".equals(backend) && !"java".equals(backend)) return;
        opusBackend = backend;
    }

    public static String activationMode() {
        return activationMode;
    }

    public static void setActivationMode(String mode) {
        activationMode = mode;
    }

    public static double thresholdDb() {
        return thresholdDb;
    }

    public static void setThresholdDb(double db) {
        thresholdDb = db;
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

    public static double micAmplification() {
        return micAmplification;
    }

    public static void setMicAmplification(double v) {
        micAmplification = v;
    }

    public static boolean noiseSuppression() {
        return noiseSuppression;
    }

    public static void setNoiseSuppression(boolean v) {
        noiseSuppression = v;
    }

    public static String audio3dMode() {
        return audio3dMode;
    }

    public static void setAudio3dMode(String s) {
        audio3dMode = s;
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

    public static String selectedSpeakerMixer() {
        return selectedSpeakerMixer;
    }

    public static void setSelectedSpeakerMixer(String name) {
        selectedSpeakerMixer = name == null ? "" : name;
    }

    public static boolean firstRunComplete() {
        return firstRunComplete;
    }

    public static void setFirstRunComplete(boolean v) {
        firstRunComplete = v;
    }

    public static boolean showSpeakerIndicators() {
        return showSpeakerIndicators;
    }

    public static void setShowSpeakerIndicators(boolean v) {
        showSpeakerIndicators = v;
    }

    public static double thresholdRms() {
        return Math.pow(10.0, thresholdDb / 20.0);
    }

    public static double rmsToDb(double rms) {
        if (rms <= 0.0) return -120.0;
        return 20.0 * Math.log10(rms);
    }
}
