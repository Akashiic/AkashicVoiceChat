package dev.akashic.voice.voice.client.audio.capture;

public interface AudioCaptureBackend {

    AudioCaptureDevice open(String deviceId) throws AudioCaptureException;

    String name();
}
