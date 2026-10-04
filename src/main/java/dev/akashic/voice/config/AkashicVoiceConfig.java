package dev.akashic.voice.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public final class AkashicVoiceConfig {

    public static final int DEFAULT_VOICE_PORT = 24454;
    public static final String DEFAULT_SERVER_MODE = "direct";
    public static final String DEFAULT_AGENT_BACKEND_ID = "legacy";
    public static final String DEFAULT_AGENT_GATEWAY_HOST = "127.0.0.1";
    public static final int DEFAULT_AGENT_GATEWAY_PORT = 24456;
    public static final String DEFAULT_AGENT_PSK_BASE64 = "";
    public static final int DEFAULT_VOICE_RADIUS = 48;
    public static final int DEFAULT_OPUS_BITRATE = 48_000;
    public static final String DEFAULT_OPUS_BACKEND = "auto";
    public static final String DEFAULT_CAPTURE_BACKEND = "auto";
    public static final String DEFAULT_ACTIVATION_MODE = "ptt";
    public static final double DEFAULT_THRESHOLD_DB = -50.0;
    public static final boolean DEFAULT_DEBUG_SELF_ECHO = false;
    public static final double DEFAULT_PLAYBACK_VOLUME = 1.0; // shown as 100% - applied to voice sources only
    public static final double DEFAULT_MIC_AMPLIFICATION = 1.0;
    public static final boolean DEFAULT_NOISE_SUPPRESSION = false;
    public static final String DEFAULT_AUDIO_3D_MODE = "normal";
    public static final double DEFAULT_REFERENCE_DISTANCE = 4.0;
    public static final String DEFAULT_SELECTED_MIC_MIXER = "";
    public static final String DEFAULT_SELECTED_SPEAKER_MIXER = "";
    public static final boolean DEFAULT_FIRST_RUN_COMPLETE = false;
    public static final boolean DEFAULT_SHOW_SPEAKER_INDICATORS = true;
    public static final boolean DEFAULT_ENVIRONMENTAL_ACOUSTICS = true;

    private static Configuration cfg;

    private static int voicePort = DEFAULT_VOICE_PORT;
    private static String serverMode = DEFAULT_SERVER_MODE;
    private static String agentBackendId = DEFAULT_AGENT_BACKEND_ID;
    private static String agentGatewayHost = DEFAULT_AGENT_GATEWAY_HOST;
    private static int agentGatewayPort = DEFAULT_AGENT_GATEWAY_PORT;
    private static String agentPskBase64 = DEFAULT_AGENT_PSK_BASE64;
    private static int voiceRadius = DEFAULT_VOICE_RADIUS;
    private static volatile int opusBitrate = DEFAULT_OPUS_BITRATE;
    private static String opusBackend = DEFAULT_OPUS_BACKEND;
    private static String captureBackend = DEFAULT_CAPTURE_BACKEND;
    private static String activationMode = DEFAULT_ACTIVATION_MODE;
    private static double thresholdDb = DEFAULT_THRESHOLD_DB;
    private static boolean debugSelfEcho = DEFAULT_DEBUG_SELF_ECHO;
    private static double playbackVolume = DEFAULT_PLAYBACK_VOLUME;
    private static double micAmplification = DEFAULT_MIC_AMPLIFICATION;
    private static volatile boolean noiseSuppression = DEFAULT_NOISE_SUPPRESSION;
    private static String audio3dMode = DEFAULT_AUDIO_3D_MODE;
    private static double referenceDistance = DEFAULT_REFERENCE_DISTANCE;
    private static String selectedMicMixer = DEFAULT_SELECTED_MIC_MIXER;
    private static String selectedSpeakerMixer = DEFAULT_SELECTED_SPEAKER_MIXER;
    private static boolean firstRunComplete = DEFAULT_FIRST_RUN_COMPLETE;
    private static boolean showSpeakerIndicators = DEFAULT_SHOW_SPEAKER_INDICATORS;
    private static volatile boolean environmentalAcoustics = DEFAULT_ENVIRONMENTAL_ACOUSTICS;

    private AkashicVoiceConfig() {}

    public static synchronized void load(File configFile) {
        cfg = new Configuration(configFile);
        cfg.load();

        voicePort = cfg.getInt(
            "voicePort",
            "voice",
            DEFAULT_VOICE_PORT,
            1024,
            65535,
            "UDP port used only when serverMode=direct.");
        serverMode = cfg.getString(
            "serverMode",
            "voice",
            DEFAULT_SERVER_MODE,
            "Server transport: direct opens local voice UDP; agent routes voice only through the Gateway.",
            new String[] { "direct", "agent" });
        agentBackendId = cfg.getString(
            "backendId",
            "agent",
            DEFAULT_AGENT_BACKEND_ID,
            "Logical backend ID configured in the Gateway (for example forbidden-1).");
        agentGatewayHost = cfg.getString(
            "gatewayHost",
            "agent",
            DEFAULT_AGENT_GATEWAY_HOST,
            "Gateway Agent TCP host. Prefer loopback/private networking; no public inbound port is required here.");
        agentGatewayPort = cfg.getInt(
            "gatewayPort",
            "agent",
            DEFAULT_AGENT_GATEWAY_PORT,
            1,
            65535,
            "Gateway Agent TCP port (the Gateway's agent-port).");
        agentPskBase64 = cfg.getString(
            "pskBase64",
            "agent",
            DEFAULT_AGENT_PSK_BASE64,
            "32 random PSK bytes encoded as Base64. Must match agent-psk.<backend>.base64 on Velocity.");
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
        captureBackend = cfg.getString(
            "captureBackend",
            "client",
            DEFAULT_CAPTURE_BACKEND,
            "Microphone capture: auto (JavaSound then OpenAL fallback) | javasound | openal.",
            new String[] { "auto", "javasound", "openal" });
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
        playbackVolume = cfg.get(
            "client",
            "playbackVolume",
            DEFAULT_PLAYBACK_VOLUME,
            "Voice-only gain (1.0 = 100%, maximum 3.0). Preserves the game master volume; legacy drivers may clip boosted peaks.",
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
            "Legacy key retained. Enables only a one-pole high-pass filter for DC/low-frequency rumble; not noise gating or RNNoise.");
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
            "Capture device ID. Empty = automatic system default; legacy JavaSound mixer names are migrated.");
        if (!selectedMicMixer.isEmpty() && !selectedMicMixer.startsWith("javasound:")
            && !selectedMicMixer.startsWith("openal:")) {
            selectedMicMixer = "javasound:" + selectedMicMixer;
        }
        selectedSpeakerMixer = cfg.getString(
            "selectedSpeakerMixer",
            "client",
            DEFAULT_SELECTED_SPEAKER_MIXER,
            "Legacy compatibility key. Voice output follows the current system-default OpenAL device and recovers when that default changes.");

        firstRunComplete = cfg.getBoolean(
            "firstRunComplete",
            "client",
            DEFAULT_FIRST_RUN_COMPLETE,
            "Set automatically once the user finishes the first-run setup wizard. Reset to false to re-show the wizard.");
        showSpeakerIndicators = cfg.getBoolean(
            "showSpeakerIndicators",
            "client",
            DEFAULT_SHOW_SPEAKER_INDICATORS,
            "Show a microphone icon beside speaking players' nametags.");
        environmentalAcoustics = cfg.getBoolean(
            "environmentalAcoustics",
            "client",
            DEFAULT_ENVIRONMENTAL_ACOUSTICS,
            "Apply room/cave reverb and underwater filtering when OpenAL EFX is available.");

        if (cfg.hasChanged()) {
            cfg.save();
        }
    }

    public static synchronized void save() {
        if (cfg == null) return;
        cfg.get("voice", "voicePort", DEFAULT_VOICE_PORT)
            .set(voicePort);
        cfg.get("voice", "serverMode", DEFAULT_SERVER_MODE)
            .set(serverMode);
        cfg.get("agent", "backendId", DEFAULT_AGENT_BACKEND_ID)
            .set(agentBackendId);
        cfg.get("agent", "gatewayHost", DEFAULT_AGENT_GATEWAY_HOST)
            .set(agentGatewayHost);
        cfg.get("agent", "gatewayPort", DEFAULT_AGENT_GATEWAY_PORT)
            .set(agentGatewayPort);
        cfg.get("agent", "pskBase64", DEFAULT_AGENT_PSK_BASE64)
            .set(agentPskBase64);
        cfg.get("voice", "voiceRadius", DEFAULT_VOICE_RADIUS)
            .set(voiceRadius);
        cfg.get("voice", "opusBitrate", DEFAULT_OPUS_BITRATE)
            .set(opusBitrate);
        cfg.get("client", "opusBackend", DEFAULT_OPUS_BACKEND)
            .set(opusBackend);
        cfg.get("client", "captureBackend", DEFAULT_CAPTURE_BACKEND)
            .set(captureBackend);
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
        cfg.get("client", "environmentalAcoustics", DEFAULT_ENVIRONMENTAL_ACOUSTICS)
            .set(environmentalAcoustics);
        cfg.save();
    }

    public static int voicePort() {
        return voicePort;
    }

    public static boolean agentMode() {
        return "agent".equals(serverMode);
    }

    public static String serverMode() {
        return serverMode;
    }

    public static String agentBackendId() {
        return agentBackendId;
    }

    public static String agentGatewayHost() {
        return agentGatewayHost;
    }

    public static int agentGatewayPort() {
        return agentGatewayPort;
    }

    public static String agentPskBase64() {
        return agentPskBase64;
    }

    public static int voiceRadius() {
        return voiceRadius;
    }

    public static int opusBitrate() {
        return opusBitrate;
    }

    public static synchronized void setOpusBitrate(int bitrate) {
        opusBitrate = VoiceQualityPreset.requireValidBitrate(bitrate);
    }

    public static void setVoiceQuality(VoiceQualityPreset preset) {
        if (preset == null) throw new IllegalArgumentException("Missing quality preset");
        setOpusBitrate(preset.bitrate());
    }

    public static VoiceQualityPreset voiceQuality() {
        return VoiceQualityPreset.fromBitrate(opusBitrate);
    }

    public static String opusBackend() {
        return opusBackend;
    }

    public static void setOpusBackend(String backend) {
        if (backend == null) return;
        if (!"auto".equals(backend) && !"native".equals(backend) && !"java".equals(backend)) return;
        opusBackend = backend;
    }

    public static String captureBackend() {
        return captureBackend;
    }

    public static void setCaptureBackend(String backend) {
        if (backend == null) return;
        if (!"auto".equals(backend) && !"javasound".equals(backend) && !"openal".equals(backend)) return;
        captureBackend = backend;
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

    public static String selectedMicDevice() {
        return selectedMicMixer;
    }

    public static void setSelectedMicMixer(String name) {
        selectedMicMixer = name == null ? "" : name;
    }

    public static void setSelectedMicDevice(String id) {
        selectedMicMixer = id == null ? "" : id;
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

    public static boolean environmentalAcoustics() {
        return environmentalAcoustics;
    }

    public static void setEnvironmentalAcoustics(boolean value) {
        environmentalAcoustics = value;
    }

    public static double thresholdRms() {
        return Math.pow(10.0, thresholdDb / 20.0);
    }

    public static double rmsToDb(double rms) {
        if (rms <= 0.0) return -120.0;
        return 20.0 * Math.log10(rms);
    }
}
