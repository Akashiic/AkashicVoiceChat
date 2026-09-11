package dev.akashic.voice.voice.client.audio.capture;

public interface AudioCaptureDevice {

    int read(byte[] buffer, int offset, int length);

    String description();

    void close();
}
