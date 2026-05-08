package dev.jm.proxychat.voice.client.audio;

import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

import dev.jm.proxychat.voice.common.codec.AudioFormatSpec;

public final class MicDeviceRegistry {

    private MicDeviceRegistry() {}

    public static List<Mixer.Info> listInputMixers() {
        AudioFormat format = new AudioFormat(AudioFormatSpec.SAMPLE_RATE, 16, AudioFormatSpec.CHANNELS, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        List<Mixer.Info> result = new ArrayList<>();
        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                if (mixer.isLineSupported(info)) {
                    result.add(mixerInfo);
                }
            } catch (Throwable ignored) {
                // skip unsupported / failing mixers
            }
        }
        return result;
    }

    public static Mixer.Info findByName(String name) {
        if (name == null || name.isEmpty()) return null;
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (name.equals(info.getName())) {
                return info;
            }
        }
        return null;
    }
}
