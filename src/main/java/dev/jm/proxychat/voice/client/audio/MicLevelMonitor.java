package dev.jm.proxychat.voice.client.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

import dev.jm.proxychat.ProxyChat;
import dev.jm.proxychat.voice.client.MicGate;
import dev.jm.proxychat.voice.common.codec.AudioFormatSpec;

/**
 * Test-only mic capture: opens a TargetDataLine on the configured mixer, computes RMS over each
 * 20 ms frame, and exposes the latest value via {@link #latestRms()}. Does not transmit anything
 * over the network. Used by the setup wizard / settings GUI to drive a live level meter.
 */
public final class MicLevelMonitor {

    private final Mixer.Info mixerInfo;
    private TargetDataLine line;
    private Thread thread;
    private volatile boolean running;
    private volatile double latestRms = 0.0;
    private volatile String error = null;

    public MicLevelMonitor(Mixer.Info mixerInfo) {
        this.mixerInfo = mixerInfo;
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
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (mixerInfo != null) {
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                line = (TargetDataLine) mixer.getLine(info);
            } else {
                line = (TargetDataLine) AudioSystem.getLine(info);
            }
            line.open(format, AudioFormatSpec.FRAME_BYTES_PCM_S16 * 4);
            line.start();
            running = true;
            thread = new Thread(this::loop, "ProxyChat-MicTest");
            thread.setDaemon(true);
            thread.start();
        } catch (LineUnavailableException e) {
            error = e.getMessage();
            ProxyChat.LOG.warn("Mic level monitor failed to open: {}", e.getMessage());
        } catch (Throwable t) {
            error = t.getMessage();
            ProxyChat.LOG.warn("Mic level monitor failed", t);
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
        }
    }
}
