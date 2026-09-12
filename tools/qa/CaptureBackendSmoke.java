import java.util.Collections;
import java.util.List;

import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.client.audio.capture.AudioCaptureBackend;
import dev.akashic.voice.voice.client.audio.capture.AudioCaptureBackends;
import dev.akashic.voice.voice.client.audio.capture.AudioCaptureDevice;
import dev.akashic.voice.voice.client.audio.capture.AudioCaptureDeviceInfo;
import dev.akashic.voice.voice.client.audio.capture.AudioCaptureException;
import dev.akashic.voice.voice.client.audio.capture.OpenALCaptureBackend;

public final class CaptureBackendSmoke {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        AkashicVoiceConfig.setCaptureBackend("auto");
        require("Auto".equals(AudioCaptureBackends.get().name()), "auto selection");

        List<AudioCaptureDeviceInfo> devices = AudioCaptureBackends.listDevices();
        require(!devices.isEmpty(), "capture list empty");
        require("".equals(devices.get(0).id()), "system default must be first");
        require(devices.get(0).displayName().contains("default"), "system default label");
        AkashicVoiceConfig.setCaptureBackend("javasound");
        require("JavaSound".equals(AudioCaptureBackends.get().name()), "javasound selection");
        AkashicVoiceConfig.setCaptureBackend("openal");
        require("OpenAL Capture".equals(AudioCaptureBackends.get().name()), "openal selection");

        List<AudioCaptureDeviceInfo> openAl = OpenALCaptureBackend.INSTANCE.listDevices();
        require(openAl.size() == 1, "OpenAL default enumeration");
        require(openAl.get(0).id().startsWith("openal:"), "OpenAL device id");

        AudioCaptureBackend fake = new AudioCaptureBackend() {
            @Override
            public AudioCaptureDevice open(String deviceId) throws AudioCaptureException {
                throw new AudioCaptureException("not opened by this smoke test");
            }

            @Override
            public List<AudioCaptureDeviceInfo> listDevices() {
                return Collections.singletonList(new AudioCaptureDeviceInfo("fake:one", "Fake microphone"));
            }

            @Override
            public String name() {
                return "Fake";
            }
        };
        AudioCaptureBackends.install(fake);
        require("Fake".equals(AudioCaptureBackends.get().name()), "override selection");
        List<AudioCaptureDeviceInfo> overridden = AudioCaptureBackends.listDevices();
        require(overridden.size() == 2, "override device list size");
        require("".equals(overridden.get(0).id()), "override system default first");
        require("fake:one".equals(overridden.get(1).id()), "override device missing");
        AudioCaptureBackends.clearOverride();

        AkashicVoiceConfig.setCaptureBackend("auto");
        require("Auto".equals(AudioCaptureBackends.get().name()), "override did not clear");
        System.out.println("capture-backend-smoke-ok devices=" + devices.size());
    }
}
