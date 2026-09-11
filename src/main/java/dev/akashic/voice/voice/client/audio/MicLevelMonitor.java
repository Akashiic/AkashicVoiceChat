package dev.akashic.voice.voice.client.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

import dev.akashic.voice.AkashicVoice;
import dev.akashic.voice.voice.client.MicGate;
import dev.akashic.voice.voice.common.codec.AudioFormatSpec;

/**
 * Test-only mic capture with optional self-loopback.
 *
 * - Always: opens a TargetDataLine, computes 20 ms RMS, exposes via {@link #latestRms()}.
 * - With {@code loopback=true}: also opens a SourceDataLine and writes the captured
 * PCM straight to it, so the user hears their own voice while testing — the standard
 * "is my mic working" loop. Bypasses the OpenAL voice path entirely.
 */
public final class MicLevelMonitor {

    private final Mixer.Info mixerInfo;
    private final boolean loopback;

    private TargetDataLine line;
    private SourceDataLine outLine;
    private Thread thread;
    private volatile boolean running;
    private volatile double latestRms = 0.0;
    private volatile String error = null;

    public MicLevelMonitor(Mixer.Info mixerInfo) {
        this(mixerInfo, false);
    }

    public MicLevelMonitor(Mixer.Info mixerInfo, boolean loopback) {
        this.mixerInfo = mixerInfo;
        this.loopback = loopback;
    }

    public synchronized void start() {
        if (running) return;
        try {
            AudioFormat format = new AudioFormat(
                AudioFormatSpec.SAMPLE_RATE,
                16,
                AudioFormatSpec.CHANNELS,
                true,
                false);

            DataLine.Info inInfo = new DataLine.Info(TargetDataLine.class, format);
            if (mixerInfo != null) {
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                line = (TargetDataLine) mixer.getLine(inInfo);
            } else {
                line = (TargetDataLine) AudioSystem.getLine(inInfo);
            }
            line.open(format, AudioFormatSpec.FRAME_BYTES_PCM_S16 * 4);
            line.start();

            if (loopback) {
                try {
                    DataLine.Info outInfo = new DataLine.Info(SourceDataLine.class, format);
                    outLine = (SourceDataLine) AudioSystem.getLine(outInfo);
                    outLine.open(format, AudioFormatSpec.FRAME_BYTES_PCM_S16 * 8);
                    outLine.start();
                } catch (Throwable t) {
                    AkashicVoice.LOG.warn("Mic test loopback unavailable: {}", t.getMessage());
                    outLine = null;
                }
            }

            running = true;
            thread = new Thread(this::loop, "AkashicVoice-MicTest");
            thread.setDaemon(true);
            thread.start();
        } catch (LineUnavailableException e) {
            error = e.getMessage();
            AkashicVoice.LOG.warn("Mic level monitor failed to open: {}", e.getMessage());
        } catch (Throwable t) {
            error = t.getMessage();
            AkashicVoice.LOG.warn("Mic level monitor failed", t);
        }
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) thread.interrupt();
        if (line != null) {
            try {
                line.stop();
                line.close();
            } catch (Throwable ignored) {
                // ignored
            }
            line = null;
        }
        if (outLine != null) {
            try {
                outLine.drain();
                outLine.stop();
                outLine.close();
            } catch (Throwable ignored) {
                // ignored
            }
            outLine = null;
        }
        latestRms = 0.0;
    }

    public double latestRms() {
        return latestRms;
    }

    public String error() {
        return error;
    }

    private void loop() {
        byte[] pcmBytes = new byte[AudioFormatSpec.FRAME_BYTES_PCM_S16];
        short[] pcm = new short[AudioFormatSpec.FRAME_SAMPLES];
        while (running) {
            int read = 0;
            while (read < pcmBytes.length && running) {
                int n = line.read(pcmBytes, read, pcmBytes.length - read);
                if (n < 0) {
                    running = false;
                    return;
                }
                read += n;
            }
            if (!running) return;
            for (int i = 0; i < AudioFormatSpec.FRAME_SAMPLES; i++) {
                int lo = pcmBytes[i * 2] & 0xFF;
                int hi = pcmBytes[i * 2 + 1];
                pcm[i] = (short) ((hi << 8) | lo);
            }
            latestRms = MicGate.computeRms(pcm);

            if (outLine != null) {
                try {
                    outLine.write(pcmBytes, 0, pcmBytes.length);
                } catch (Throwable t) {
                    AkashicVoice.LOG.debug("Loopback write failed", t);
                }
            }
        }
    }
}
