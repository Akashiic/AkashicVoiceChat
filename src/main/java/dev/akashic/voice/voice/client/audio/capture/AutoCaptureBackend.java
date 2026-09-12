package dev.akashic.voice.voice.client.audio.capture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AutoCaptureBackend implements AudioCaptureBackend {

    public static final AutoCaptureBackend INSTANCE = new AutoCaptureBackend();

    private AutoCaptureBackend() {}

    @Override
    public AudioCaptureDevice open(String deviceId) throws AudioCaptureException {
        boolean preferOpenAl = deviceId != null && deviceId.startsWith(OpenALCaptureBackend.ID_PREFIX);
        AudioCaptureBackend first = preferOpenAl ? OpenALCaptureBackend.INSTANCE : JavaSoundCaptureBackend.INSTANCE;
        AudioCaptureBackend second = preferOpenAl ? JavaSoundCaptureBackend.INSTANCE : OpenALCaptureBackend.INSTANCE;
        String firstId = deviceId != null ? deviceId : "";

        AudioCaptureException firstFailure;
        try {
            return first.open(firstId);
        } catch (AudioCaptureException e) {
            firstFailure = e;
        }

        try {
            return second.open("");
        } catch (AudioCaptureException secondFailure) {
            AudioCaptureException combined = new AudioCaptureException(
                "No audio capture backend could open a microphone");
            combined.addSuppressed(firstFailure);
            combined.addSuppressed(secondFailure);
            throw combined;
        }
    }

    @Override
    public List<AudioCaptureDeviceInfo> listDevices() {
        List<AudioCaptureDeviceInfo> result = new ArrayList<>();
        appendUnique(result, JavaSoundCaptureBackend.INSTANCE.listDevices());
        appendUnique(result, OpenALCaptureBackend.INSTANCE.listDevices());
        return Collections.unmodifiableList(result);
    }

    @Override
    public String name() {
        return "Auto";
    }

    private static void appendUnique(List<AudioCaptureDeviceInfo> target, List<AudioCaptureDeviceInfo> source) {
        for (AudioCaptureDeviceInfo candidate : source) {
            boolean exists = false;
            for (AudioCaptureDeviceInfo current : target) {
                if (current.id()
                    .equals(candidate.id())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) target.add(candidate);
        }
    }
}
