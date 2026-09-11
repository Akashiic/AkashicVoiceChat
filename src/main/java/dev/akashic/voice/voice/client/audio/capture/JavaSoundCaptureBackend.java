package dev.akashic.voice.voice.client.audio.capture;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

import dev.akashic.voice.voice.common.codec.AudioFormatSpec;

public final class JavaSoundCaptureBackend implements AudioCaptureBackend {

    public static final JavaSoundCaptureBackend INSTANCE = new JavaSoundCaptureBackend();

    private JavaSoundCaptureBackend() {}

    @Override
    public AudioCaptureDevice open(String deviceId) throws AudioCaptureException {
        AudioFormat format = new AudioFormat(AudioFormatSpec.SAMPLE_RATE, 16, AudioFormatSpec.CHANNELS, true, false);
        DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, format);
        Mixer.Info mixerInfo = findMixer(deviceId);
        try {
            TargetDataLine line;
            if (mixerInfo != null) {
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                if (!mixer.isLineSupported(lineInfo)) {
                    throw new AudioCaptureException("Selected mixer does not support 48 kHz mono PCM: " + deviceId);
                }
                line = (TargetDataLine) mixer.getLine(lineInfo);
            } else {
                if (!AudioSystem.isLineSupported(lineInfo)) {
                    throw new AudioCaptureException("No microphone supports 48 kHz mono PCM");
                }
                line = (TargetDataLine) AudioSystem.getLine(lineInfo);
            }
            line.open(format, AudioFormatSpec.FRAME_BYTES_PCM_S16 * 4);
            line.start();
            String description = mixerInfo != null ? mixerInfo.getName() : "<system default>";
            return new Device(line, description);
        } catch (LineUnavailableException e) {
            throw new AudioCaptureException("Microphone line unavailable", e);
        } catch (IllegalArgumentException e) {
            throw new AudioCaptureException("Microphone device unavailable", e);
        }
    }

    @Override
    public String name() {
        return "JavaSound";
    }

    private static Mixer.Info findMixer(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) return null;
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (deviceId.equals(info.getName())) return info;
        }
        return null;
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
        public String description() {
            return description;
        }

        @Override
        public void close() {
            line.stop();
            line.close();
        }
    }
}
