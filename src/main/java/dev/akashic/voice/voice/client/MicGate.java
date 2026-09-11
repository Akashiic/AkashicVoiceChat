package dev.akashic.voice.voice.client;

import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.common.codec.AudioFormatSpec;

public final class MicGate {

    public enum Mode {
        PTT,
        VOICE,
        ALWAYS
    }

    private static volatile Mode mode = Mode.PTT;
    private static volatile boolean pttPressed = false;
    private static volatile boolean muted = false;
    private static volatile boolean deafened = false;
    private static volatile long lastTransmitMillis = 0L;

    private MicGate() {}

    public static void setMode(Mode m) {
        mode = m;
    }

    public static Mode mode() {
        return mode;
    }

    public static void setPttPressed(boolean p) {
        pttPressed = p;
    }

    public static boolean isPttPressed() {
        return pttPressed;
    }

    public static void toggleMute() {
        muted = !muted;
    }

    public static void setMuted(boolean m) {
        muted = m;
    }

    public static boolean isMuted() {
        return muted;
    }

    public static void toggleDeafened() {
        deafened = !deafened;
    }

    public static void setDeafened(boolean d) {
        deafened = d;
    }

    public static boolean isDeafened() {
        return deafened;
    }

    public static void markTransmitted() {
        lastTransmitMillis = System.currentTimeMillis();
    }

    public static long lastTransmitMillis() {
        return lastTransmitMillis;
    }

    public static boolean shouldTransmit(short[] pcm) {
        if (muted) return false;
        switch (mode) {
            case ALWAYS:
                return true;
            case PTT:
                return pttPressed;
            case VOICE:
                return computeRms(pcm) >= AkashicVoiceConfig.thresholdRms();
            default:
                return false;
        }
    }

    public static double computeRms(short[] pcm) {
        if (pcm.length == 0) return 0.0;
        double sum = 0.0;
        int n = Math.min(AudioFormatSpec.FRAME_SAMPLES, pcm.length);
        for (int i = 0; i < n; i++) {
            double s = pcm[i] / 32768.0;
            sum += s * s;
        }
        return Math.sqrt(sum / n);
    }
}
