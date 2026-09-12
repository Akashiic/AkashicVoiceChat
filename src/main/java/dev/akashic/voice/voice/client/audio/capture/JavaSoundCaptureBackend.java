package dev.akashic.voice.voice.client.audio.capture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

import dev.akashic.voice.voice.common.codec.AudioFormatSpec;

public final class JavaSoundCaptureBackend implements AudioCaptureBackend {

    public static final JavaSoundCaptureBackend INSTANCE = new JavaSoundCaptureBackend();
    public static final String ID_PREFIX = "javasound:";

    private JavaSoundCaptureBackend() {}

    @Override
    public AudioCaptureDevice open(String deviceId) throws AudioCaptureException {
        AudioFormat format = captureFormat();
        DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, format);
        String mixerName = mixerName(deviceId);
        Mixer.Info mixerInfo = findMixer(mixerName);
        if (!mixerName.isEmpty() && mixerInfo == null) {
            throw new AudioCaptureException("Selected JavaSound microphone was not found: " + mixerName);
        }
        try {
            TargetDataLine line;
            if (mixerInfo != null) {
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                if (!mixer.isLineSupported(lineInfo)) {
                    throw new AudioCaptureException("Selected mixer does not support 48 kHz mono PCM: " + mixerName);
                }
                line = (TargetDataLine) mixer.getLine(lineInfo);
            } else {
                if (!AudioSystem.isLineSupported(lineInfo)) {
                    throw new AudioCaptureException("No JavaSound microphone supports 48 kHz mono PCM");
                }
                line = (TargetDataLine) AudioSystem.getLine(lineInfo);
            }
            line.open(format, AudioFormatSpec.FRAME_BYTES_PCM_S16 * 4);
            line.start();
            return new Device(line, mixerInfo != null ? mixerInfo.getName() : "<system default>");
        } catch (AudioCaptureException e) {
            throw e;
        } catch (LineUnavailableException e) {
            throw new AudioCaptureException("JavaSound microphone line unavailable", e);
        } catch (IllegalArgumentException e) {
            throw new AudioCaptureException("JavaSound microphone device unavailable", e);
        } catch (Throwable failure) {
            rethrowFatal(failure);
            throw new AudioCaptureException("JavaSound capture unavailable", failure);
        }
    }

    @Override
    public List<AudioCaptureDeviceInfo> listDevices() {
        AudioFormat format = captureFormat();
        DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, format);
        List<AudioCaptureDeviceInfo> result = new ArrayList<>();
        Mixer.Info[] mixerInfos;
        try {
            mixerInfos = AudioSystem.getMixerInfo();
        } catch (Throwable failure) {
            rethrowFatal(failure);
            return Collections.emptyList();
        }
        for (Mixer.Info info : mixerInfos) {
            try {
                Mixer mixer = AudioSystem.getMixer(info);
                if (mixer.isLineSupported(lineInfo)) {
                    result.add(new AudioCaptureDeviceInfo(ID_PREFIX + info.getName(), info.getName()));
                }
            } catch (Throwable failure) {
                rethrowFatal(failure);
                // Unsupported/broken mixers are omitted from the selectable list.
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public String name() {
        return "JavaSound";
    }

    private static void rethrowFatal(Throwable failure) {
        if (failure instanceof ThreadDeath) throw (ThreadDeath) failure;
        if (failure instanceof VirtualMachineError) throw (VirtualMachineError) failure;
    }

    private static AudioFormat captureFormat() {
        return new AudioFormat(AudioFormatSpec.SAMPLE_RATE, 16, AudioFormatSpec.CHANNELS, true, false);
    }

    private static String mixerName(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) return "";
        if (deviceId.startsWith(ID_PREFIX)) return deviceId.substring(ID_PREFIX.length());
        // Compatibility with alpha configs that persisted the raw JavaSound mixer name.
        return deviceId;
    }

    private static Mixer.Info findMixer(String name) throws AudioCaptureException {
        if (name == null || name.isEmpty()) return null;
        try {
            for (Mixer.Info info : AudioSystem.getMixerInfo()) {
                if (name.equals(info.getName())) return info;
            }
            return null;
        } catch (Throwable failure) {
            rethrowFatal(failure);
            throw new AudioCaptureException("JavaSound device enumeration unavailable", failure);
        }
    }

    private static final class Device implements AudioCaptureDevice {

        private final TargetDataLine line;
        private final String description;

        private Device(TargetDataLine line, String description) {
            this.line = line;
            this.description = description;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            return line.read(buffer, offset, length);
        }

        @Override
        public String backendName() {
            return "JavaSound";
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public void close() {
            try {
                line.stop();
            } finally {
                line.close();
            }
        }
    }
}
