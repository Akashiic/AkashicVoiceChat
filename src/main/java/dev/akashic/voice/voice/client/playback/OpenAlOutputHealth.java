package dev.akashic.voice.voice.client.playback;

import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;
import org.lwjgl.openal.ALCdevice;

/** Lightweight health probe for the OpenAL playback device used by voice. */
final class OpenAlOutputHealth {

    enum Status {
        HEALTHY,
        UNAVAILABLE,
        DISCONNECTED,
        DEFAULT_CHANGED
    }

    private enum ConnectionState {
        CONNECTED,
        DISCONNECTED,
        UNKNOWN
    }

    private final IntBuffer intScratch = BufferUtils.createIntBuffer(1);
    private String expectedDefaultDevice;
    private String boundDevice;

    private OpenAlOutputHealth(String expectedDefaultDevice, String boundDevice) {
        this.expectedDefaultDevice = expectedDefaultDevice;
        this.boundDevice = boundDevice;
    }

    static OpenAlOutputHealth capture() {
        ALCdevice device = currentDevice();
        return new OpenAlOutputHealth(defaultDeviceName(), deviceName(device));
    }

    Status poll(boolean followSystemDefault) {
        if (!AL.isCreated()) return Status.UNAVAILABLE;
        ALCdevice device;
        try {
            device = AL.getDevice();
        } catch (RuntimeException | LinkageError failure) {
            return Status.UNAVAILABLE;
        }
        if (device == null) return Status.UNAVAILABLE;
        ConnectionState connection = connectionState(device);
        if (connection == ConnectionState.DISCONNECTED) return Status.DISCONNECTED;
        if (connection == ConnectionState.UNKNOWN) return Status.UNAVAILABLE;

        boundDevice = deviceName(device);
        if (followSystemDefault) {
            String currentDefault = defaultDeviceName();
            Status decision = decide(expectedDefaultDevice, currentDefault, boundDevice, true);
            if (decision == Status.HEALTHY && meaningful(currentDefault)
                && !same(expectedDefaultDevice, currentDefault)
                && same(boundDevice, currentDefault)) {
                expectedDefaultDevice = currentDefault;
            }
            return decision;
        }
        return Status.HEALTHY;
    }

    void rebaselineDefaultDevice() {
        expectedDefaultDevice = defaultDeviceName();
    }

    String expectedDefaultDevice() {
        return expectedDefaultDevice;
    }

    private ConnectionState connectionState(ALCdevice device) {
        try {
            if (!ALC10.alcIsExtensionPresent(device, "ALC_EXT_disconnect")) return ConnectionState.CONNECTED;
            int connectedEnum = ALC10.alcGetEnumValue(device, "ALC_CONNECTED");
            if (connectedEnum == 0) connectedEnum = 0x0313;
            intScratch.clear();
            ALC10.alcGetInteger(device, connectedEnum, intScratch);
            return intScratch.get(0) == ALC10.ALC_FALSE ? ConnectionState.DISCONNECTED : ConnectionState.CONNECTED;
        } catch (RuntimeException | LinkageError failure) {
            return ConnectionState.UNKNOWN;
        }
    }

    static Status decide(String expectedDefault, String currentDefault, String currentBound, boolean connected) {
        if (!connected) return Status.DISCONNECTED;
        if (!meaningful(expectedDefault) || !meaningful(currentDefault)) return Status.HEALTHY;
        if (same(expectedDefault, currentDefault)) return Status.HEALTHY;
        return same(currentBound, currentDefault) ? Status.HEALTHY : Status.DEFAULT_CHANGED;
    }

    static boolean same(String first, String second) {
        return meaningful(first) && meaningful(second)
            && first.trim()
                .equalsIgnoreCase(second.trim());
    }

    private static boolean meaningful(String value) {
        return value != null && !value.trim()
            .isEmpty();
    }

    private static ALCdevice currentDevice() {
        try {
            if (!AL.isCreated()) return null;
            ALCdevice device = AL.getDevice();
            if (device != null) return device;
            return ALC10.alcGetContextsDevice(ALC10.alcGetCurrentContext());
        } catch (RuntimeException | LinkageError failure) {
            return null;
        }
    }

    private static String deviceName(ALCdevice device) {
        if (device == null) return null;
        try {
            return normalize(ALC10.alcGetString(device, ALC10.ALC_DEVICE_SPECIFIER));
        } catch (RuntimeException | LinkageError failure) {
            return null;
        }
    }

    static String defaultDeviceName() {
        try {
            if (!AL.isCreated()) return null;
            ALCdevice device = AL.getDevice();
            boolean enumerateAll = device != null && ALC10.alcIsExtensionPresent(device, "ALC_ENUMERATE_ALL_EXT");
            int token = enumerateAll ? ALC11.ALC_DEFAULT_ALL_DEVICES_SPECIFIER : ALC10.ALC_DEFAULT_DEVICE_SPECIFIER;
            return normalize(ALC10.alcGetString(null, token));
        } catch (RuntimeException | LinkageError failure) {
            return null;
        }
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
