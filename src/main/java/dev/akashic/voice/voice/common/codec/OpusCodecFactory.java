package dev.akashic.voice.voice.common.codec;

import dev.akashic.voice.config.AkashicVoiceConfig;

public final class OpusCodecFactory {

    private static boolean nativeDisabled;
    private static String nativeFailure = "";
    private static String lastEncoderBackend = "uninitialized";
    private static String lastDecoderBackend = "uninitialized";

    private OpusCodecFactory() {}

    public static synchronized OpusEncoder createEncoder() {
        if (shouldTryNative()) {
            try {
                OpusEncoder encoder = new NativeOpusEncoder(AkashicVoiceConfig.opusBitrate());
                lastEncoderBackend = "native-libopus";
                return encoder;
            } catch (Throwable t) {
                handleNativeFailure(t, "encoder");
            }
        }
        lastEncoderBackend = "java-concentus";
        return new ConcentusOpusEncoder(AkashicVoiceConfig.opusBitrate());
    }

    public static synchronized OpusDecoder createDecoder() {
        if (shouldTryNative()) {
            try {
                OpusDecoder decoder = new NativeOpusDecoder();
                lastDecoderBackend = "native-libopus";
                return decoder;
            } catch (Throwable t) {
                handleNativeFailure(t, "decoder");
            }
        }
        lastDecoderBackend = "java-concentus";
        return new ConcentusOpusDecoder();
    }

    public static synchronized String backendStatus() {
        return "encoder=" + lastEncoderBackend
            + ", decoder="
            + lastDecoderBackend
            + (nativeFailure.isEmpty() ? "" : ", nativeFallback=" + nativeFailure);
    }

    public static synchronized void resetBackendProbeForTests() {
        nativeDisabled = false;
        nativeFailure = "";
        lastEncoderBackend = "uninitialized";
        lastDecoderBackend = "uninitialized";
    }

    private static boolean shouldTryNative() {
        String backend = AkashicVoiceConfig.opusBackend();
        if ("java".equals(backend)) return false;
        return "native".equals(backend) || !nativeDisabled;
    }

    private static void handleNativeFailure(Throwable failure, String component) {
        rethrowFatal(failure);
        if ("native".equals(AkashicVoiceConfig.opusBackend())) {
            throw new IllegalStateException("Native Opus " + component + " required but unavailable", failure);
        }
        disableNative(failure);
    }

    private static void rethrowFatal(Throwable failure) {
        if (failure instanceof ThreadDeath) throw (ThreadDeath) failure;
        if (failure instanceof VirtualMachineError) throw (VirtualMachineError) failure;
    }

    private static void disableNative(Throwable failure) {
        nativeDisabled = true;
        String message = failure.getMessage();
        nativeFailure = failure.getClass()
            .getSimpleName() + (message == null || message.isEmpty() ? "" : ": " + message);
    }
}
