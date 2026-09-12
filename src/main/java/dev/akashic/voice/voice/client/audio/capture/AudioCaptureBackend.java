package dev.akashic.voice.voice.client.audio.capture;

import java.util.List;

public interface AudioCaptureBackend {

    AudioCaptureDevice open(String deviceId) throws AudioCaptureException;

    List<AudioCaptureDeviceInfo> listDevices();

    String name();
}
