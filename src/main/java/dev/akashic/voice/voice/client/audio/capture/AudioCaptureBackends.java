package dev.akashic.voice.voice.client.audio.capture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.akashic.voice.config.AkashicVoiceConfig;

public final class AudioCaptureBackends {

    private static volatile AudioCaptureBackend override;

    private AudioCaptureBackends() {}

    public static AudioCaptureBackend get() {
        AudioCaptureBackend replacement = override;
        if (replacement != null) return replacement;
        String configured = AkashicVoiceConfig.captureBackend();
        if ("javasound".equals(configured)) return JavaSoundCaptureBackend.INSTANCE;
        if ("openal".equals(configured)) return OpenALCaptureBackend.INSTANCE;
        return AutoCaptureBackend.INSTANCE;
    }

    public static List<AudioCaptureDeviceInfo> listDevices() {
        List<AudioCaptureDeviceInfo> result = new ArrayList<>();
        result.add(new AudioCaptureDeviceInfo("", "System default (auto)"));
        appendUnique(result, get().listDevices());
        return Collections.unmodifiableList(result);
    }

    public static void install(AudioCaptureBackend replacement) {
        if (replacement == null) throw new IllegalArgumentException("replacement");
        override = replacement;
    }

    public static void clearOverride() {
        override = null;
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
