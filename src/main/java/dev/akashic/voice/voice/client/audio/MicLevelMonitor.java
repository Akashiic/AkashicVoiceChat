package dev.akashic.voice.voice.client.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import dev.akashic.voice.AkashicVoice;
import dev.akashic.voice.voice.client.MicGate;
import dev.akashic.voice.voice.client.audio.capture.AudioCaptureBackends;
import dev.akashic.voice.voice.client.audio.capture.AudioCaptureDevice;
import dev.akashic.voice.voice.client.audio.capture.AudioCaptureException;
import dev.akashic.voice.voice.common.codec.AudioFormatSpec;

/** Test-only capture meter using the same capture backend SPI as live voice. */
public final class MicLevelMonitor {

    private final String deviceId;
    private final boolean loopback;

    private AudioCaptureDevice capture;
    private SourceDataLine outLine;
    private Thread thread;
    private volatile boolean running;
    private volatile double latestRms;
    private volatile String error;

    public MicLevelMonitor(String deviceId) {
        this(deviceId, false);
    }

    public MicLevelMonitor(String deviceId, boolean loopback) {
        this.deviceId = deviceId != null ? deviceId : "";
        this.loopback = loopback;
    }

    public synchronized void start() {
        if (running) return;
        error = null;
        try {
            capture = AudioCaptureBackends.get()
                .open(deviceId);
            if (loopback) openLoopback();
            running = true;
            thread = new Thread(this::loop, "AkashicVoice-MicTest");
            thread.setDaemon(true);
            thread.start();
        } catch (AudioCaptureException e) {
            error = e.getMessage();
            closeCapture();
            AkashicVoice.LOG.debug("Mic level monitor capture unavailable: {}", e.getMessage());
        } catch (Throwable t) {
            rethrowFatal(t);
            error = t.getMessage() != null ? t.getMessage()
                : t.getClass()
                    .getSimpleName();
            closeCapture();
            AkashicVoice.LOG.warn("Mic level monitor failed", t);
        }
    }

    private void openLoopback() {
        try {
            AudioFormat format = new AudioFormat(
                AudioFormatSpec.SAMPLE_RATE,
                16,
                AudioFormatSpec.CHANNELS,
                true,
                false);
            DataLine.Info outInfo = new DataLine.Info(SourceDataLine.class, format);
            outLine = (SourceDataLine) AudioSystem.getLine(outInfo);
            outLine.open(format, AudioFormatSpec.FRAME_BYTES_PCM_S16 * 8);
            outLine.start();
        } catch (Throwable t) {
            rethrowFatal(t);
            AkashicVoice.LOG.debug("Mic test loopback unavailable: {}", t.getMessage());
            closeLoopback();
        }
    }

    public synchronized void stop() {
        running = false;
        if (thread != null && thread != Thread.currentThread()) thread.interrupt();
        thread = null;
        closeCapture();
        closeLoopback();
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
                int n;
                try {
                    n = capture.read(pcmBytes, read, pcmBytes.length - read);
                } catch (RuntimeException e) {
                    error = e.getMessage();
                    running = false;
                    return;
                }
                if (n < 0) {
                    running = false;
                    return;
                }
                if (n == 0) {
                    Thread.yield();
                    continue;
                }
                read += n;
            }
            if (!running) return;
            decodePcm(pcmBytes, pcm);
            latestRms = MicGate.computeRms(pcm);
            writeLoopback(pcmBytes);
        }
    }

    private static void decodePcm(byte[] bytes, short[] pcm) {
        for (int i = 0; i < AudioFormatSpec.FRAME_SAMPLES; i++) {
            int lo = bytes[i * 2] & 0xFF;
            int hi = bytes[i * 2 + 1];
            pcm[i] = (short) ((hi << 8) | lo);
        }
    }

    private void writeLoopback(byte[] pcmBytes) {
        SourceDataLine output = outLine;
        if (output == null) return;
        try {
            output.write(pcmBytes, 0, pcmBytes.length);
        } catch (Throwable t) {
            rethrowFatal(t);
            AkashicVoice.LOG.debug("Loopback write failed", t);
        }
    }

    private void closeCapture() {
        AudioCaptureDevice current = capture;
        capture = null;
        if (current == null) return;
        try {
            current.close();
        } catch (RuntimeException ignored) {
            // best effort test cleanup
        }
    }

    private void closeLoopback() {
        SourceDataLine output = outLine;
        outLine = null;
        if (output == null) return;
        try {
            output.stop();
        } catch (RuntimeException ignored) {
            // best effort
        }
        try {
            output.close();
        } catch (RuntimeException ignored) {
            // best effort
        }
    }

    private static void rethrowFatal(Throwable failure) {
        if (failure instanceof ThreadDeath) throw (ThreadDeath) failure;
        if (failure instanceof VirtualMachineError) throw (VirtualMachineError) failure;
    }
}
