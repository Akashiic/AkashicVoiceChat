package dev.akashic.voice.voice.client;

import java.util.Objects;
import java.util.function.LongSupplier;

import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.common.codec.AudioFormatSpec;

public final class MicGate {

    public enum Mode {
        PTT,
        VOICE,
        ALWAYS
    }

    /** Immutable capture authorization, replaced on a meaningful transmission-control change. */
    public static final class TransmissionState {

        private final Mode mode;
        private final boolean pressed;
        private final boolean muted;
        private final TouchPttLease touch;

        private TransmissionState(Mode mode, boolean pressed, boolean muted, TouchPttLease touch) {
            this.mode = mode;
            this.pressed = pressed;
            this.muted = muted;
            this.touch = touch;
        }
    }

    private static volatile TransmissionState transmission = new TransmissionState(Mode.PTT, false, false, null);
    private static volatile boolean pttPressed;
    private static TouchPttLease touchPress;
    private static volatile boolean deafened;
    private static volatile long lastTransmitMillis;

    private MicGate() {}

    public static synchronized void setMode(Mode mode) {
        Objects.requireNonNull(mode, "mode");
        TransmissionState current = transmission;
        if (current.mode != mode) {
            cancelTouchLocked();
            transmission = new TransmissionState(mode, pttPressed, current.muted, null);
        }
    }

    public static Mode mode() {
        return transmission.mode;
    }

    public static synchronized void setPttPressed(boolean pressed) {
        if (pttPressed == pressed) return;
        pttPressed = pressed;
        TransmissionState current = transmission;
        if (current.mode == Mode.PTT)
            transmission = new TransmissionState(current.mode, pressed, current.muted, touchPress);
    }

    static synchronized TouchPttLease beginTouchPtt(LongSupplier clock) {
        TransmissionState current = transmission;
        if (current.mode != Mode.PTT || current.muted) return null;
        if (touchPress != null && touchPress.isLive()) return null;
        cancelTouchLocked();
        touchPress = new TouchPttLease(clock);
        transmission = new TransmissionState(current.mode, pttPressed, current.muted, touchPress);
        return touchPress;
    }

    static synchronized boolean renewTouchPtt(TouchPttLease expected) {
        if (expected == null || expected != touchPress) return false;
        if (transmission.mode == Mode.PTT && !transmission.muted && expected.renew()) return true;
        endTouchPtt(expected);
        return false;
    }

    static synchronized boolean isTouchPttHeld(TouchPttLease expected) {
        return expected != null && expected == touchPress && expected.isLive();
    }

    static synchronized void endTouchPtt(TouchPttLease expected) {
        if (expected != null && expected == touchPress) cancelTouchPtt();
    }

    /** Invalidate transient screen input on stop, mute, mode change or transport replacement. */
    public static synchronized void cancelTouchPtt() {
        if (touchPress == null) return;
        cancelTouchLocked();
        TransmissionState current = transmission;
        transmission = new TransmissionState(current.mode, pttPressed, current.muted, null);
    }

    private static void cancelTouchLocked() {
        if (touchPress != null) touchPress.cancel();
        touchPress = null;
    }

    public static boolean isPttPressed() {
        return pttPressed;
    }

    /** Immediate UI state for physical or touch PTT; independent of first encoded frame. */
    public static boolean isPttEngaged() {
        TransmissionState current = transmission;
        return current.mode == Mode.PTT && !current.muted
            && (current.pressed || (current.touch != null && current.touch.isLive()));
    }

    public static synchronized void toggleMute() {
        setMuted(!transmission.muted);
    }

    public static synchronized void setMuted(boolean muted) {
        TransmissionState current = transmission;
        if (current.muted != muted) {
            if (muted) cancelTouchLocked();
            transmission = new TransmissionState(current.mode, pttPressed, muted, touchPress);
        }
    }

    public static boolean isMuted() {
        return transmission.muted;
    }

    public static synchronized void toggleDeafened() {
        deafened = !deafened;
    }

    public static void setDeafened(boolean value) {
        deafened = value;
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

    public static TransmissionState transmissionState() {
        return transmission;
    }

    /** No allocation and no lock: capture/codec work never holds a UI control monitor. */
    public static boolean permitsTransmission(TransmissionState expected) {
        return expected != null && expected == transmission
            && !expected.muted
            && (expected.mode != Mode.PTT || expected.pressed || (expected.touch != null && expected.touch.isLive()));
    }

    public static boolean shouldTransmit(short[] pcm) {
        return shouldTransmit(pcm, transmission);
    }

    public static boolean shouldTransmit(short[] pcm, TransmissionState expected) {
        if (!permitsTransmission(expected)) return false;
        return expected.mode != Mode.VOICE || computeRms(pcm) >= AkashicVoiceConfig.thresholdRms();
    }

    public static double computeRms(short[] pcm) {
        if (pcm.length == 0) return 0.0;
        double sum = 0.0;
        int n = Math.min(AudioFormatSpec.FRAME_SAMPLES, pcm.length);
        for (int i = 0; i < n; i++) {
            double sample = pcm[i] / 32768.0;
            sum += sample * sample;
        }
        return Math.sqrt(sum / n);
    }
}
