package dev.akashic.voice.voice.client.audio.capture;

public final class AudioCaptureBackends {

    private static volatile AudioCaptureBackend backend = JavaSoundCaptureBackend.INSTANCE;

    private AudioCaptureBackends() {}

    public static AudioCaptureBackend get() {
        return backend;
    }

    public static void install(AudioCaptureBackend replacement) {
        if (replacement == null) throw new IllegalArgumentException("replacement");
        backend = replacement;
    }
}
