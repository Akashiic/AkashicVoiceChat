package dev.jm.proxychat.voice.client;

import dev.jm.proxychat.config.ProxyChatConfig;
import dev.jm.proxychat.voice.common.codec.AudioFormatSpec;

public final class MicGate {

    public enum Mode {
        PTT,
        VOICE,
        ALWAYS
    }

    private static volatile Mode mode = Mode.PTT;
    private static volatile boolean pttPressed = false;
    private static volatile boolean muted = false;

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

    public static boolean shouldTransmit(short[] pcm) {
        if (muted) return false;
        switch (mode) {
            case ALWAYS:
                return true;
            case PTT:
                return pttPressed;
            case VOICE:
                return computeRms(pcm) >= ProxyChatConfig.vadThreshold();
            default:
                return false;
        }
    }

    public static double computeRms(short[] pcm) {
        if (pcm.length == 0) return 0.0;
        double sum = 0.0;
        for (int i = 0; i < AudioFormatSpec.FRAME_SAMPLES && i < pcm.length; i++) {
            double s = pcm[i] / 32768.0;
            sum += s * s;
        }
        return Math.sqrt(sum / AudioFormatSpec.FRAME_SAMPLES);
    }
}
