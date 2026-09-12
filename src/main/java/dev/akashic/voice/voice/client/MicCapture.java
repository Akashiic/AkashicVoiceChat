package dev.akashic.voice.voice.client;

import dev.akashic.voice.AkashicVoice;
import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.client.audio.capture.AudioCaptureBackends;
import dev.akashic.voice.voice.client.audio.capture.AudioCaptureDevice;
import dev.akashic.voice.voice.client.audio.capture.AudioCaptureException;
import dev.akashic.voice.voice.common.codec.AudioFormatSpec;
import dev.akashic.voice.voice.common.codec.OpusCodecFactory;
import dev.akashic.voice.voice.common.codec.OpusEncoder;

public final class MicCapture {

    public interface FrameSink {

        void onFrame(int sequence, byte[] opusPayload, int payloadLength);
    }

    private final FrameSink sink;
    private final String deviceId;
    private AudioCaptureDevice capture;
    private OpusEncoder encoder;
    private Thread thread;
    private volatile boolean running;

    // Single-pole high-pass state for the basic noise gate.
    private double hpPrevX;
    private double hpPrevY;

    public MicCapture(FrameSink sink) {
        this(sink, "");
    }

    public MicCapture(FrameSink sink, String deviceId) {
        this.sink = sink;
        this.deviceId = deviceId != null ? deviceId : "";
    }

    public synchronized void start() throws AudioCaptureException {
        if (running) return;
        AudioCaptureDevice opened = AudioCaptureBackends.get()
            .open(deviceId);
        try {
            encoder = OpusCodecFactory.createEncoder();
        } catch (RuntimeException e) {
            opened.close();
            throw e;
        }
        capture = opened;
        running = true;
        thread = new Thread(this::loop, "AkashicVoice-Mic");
        thread.setDaemon(true);
        thread.start();
        AkashicVoice.LOG.info(
            "Microphone capture started ({} Hz, {}-sample frames, backend={}, device={})",
            AudioFormatSpec.SAMPLE_RATE,
            AudioFormatSpec.FRAME_SAMPLES,
            capture.backendName(),
            capture.description());
    }

    public boolean isRunning() {
        return running;
    }

    public synchronized void stop() {
        boolean hadResources = running || capture != null || encoder != null;
        running = false;
        if (thread != null && thread != Thread.currentThread()) thread.interrupt();
        thread = null;
        if (capture != null) {
            try {
                capture.close();
            } catch (RuntimeException e) {
                AkashicVoice.LOG.debug("Microphone backend close failed", e);
            }
            capture = null;
        }
        if (encoder != null) {
            encoder.close();
            encoder = null;
        }
        if (hadResources) AkashicVoice.LOG.info("Microphone capture stopped");
    }

    private void loop() {
        byte[] pcmBytes = new byte[AudioFormatSpec.FRAME_BYTES_PCM_S16];
        short[] pcm = new short[AudioFormatSpec.FRAME_SAMPLES];
        byte[] opusOut = new byte[AudioFormatSpec.MAX_OPUS_PAYLOAD];
        int sequence = 0;

        while (running) {
            int read = 0;
            while (read < pcmBytes.length && running) {
                int n;
                try {
                    n = capture.read(pcmBytes, read, pcmBytes.length - read);
                } catch (RuntimeException e) {
                    if (running) AkashicVoice.LOG.warn("Microphone capture backend failed", e);
                    stop();
                    return;
                }
                if (n < 0) {
                    stop();
                    return;
                }
                if (n == 0) {
                    Thread.yield();
                    continue;
                }
                read += n;
            }
            if (!running) return;

            for (int i = 0; i < AudioFormatSpec.FRAME_SAMPLES; i++) {
                int lo = pcmBytes[i * 2] & 0xFF;
                int hi = pcmBytes[i * 2 + 1];
                pcm[i] = (short) ((hi << 8) | lo);
            }

            applyAmplification(pcm, AkashicVoiceConfig.micAmplification());
            if (AkashicVoiceConfig.noiseSuppression()) {
                applyHighPass(pcm);
            }

            if (!MicGate.shouldTransmit(pcm)) continue;

            try {
                int len = encoder.encode(pcm, opusOut);
                sink.onFrame(sequence++, opusOut, len);
                MicGate.markTransmitted();
            } catch (RuntimeException e) {
                AkashicVoice.LOG.warn("Mic frame encode failed", e);
            }
        }
    }

    private static void applyAmplification(short[] pcm, double gain) {
        if (Math.abs(gain - 1.0) < 1e-4) return;
        for (int i = 0; i < pcm.length; i++) {
            int v = (int) Math.round(pcm[i] * gain);
            if (v > Short.MAX_VALUE) v = Short.MAX_VALUE;
            else if (v < Short.MIN_VALUE) v = Short.MIN_VALUE;
            pcm[i] = (short) v;
        }
    }

    /**
     * Single-pole high-pass at ~120 Hz. Removes DC bias and low-frequency rumble — the
     * cheap-but-noticeable kind of "noise suppression". Not RNNoise.
     */
    private void applyHighPass(short[] pcm) {
        final double a = 0.985;
        for (int i = 0; i < pcm.length; i++) {
            double x = pcm[i];
            double y = a * (hpPrevY + x - hpPrevX);
            hpPrevX = x;
            hpPrevY = y;
            int iy = (int) Math.round(y);
            if (iy > Short.MAX_VALUE) iy = Short.MAX_VALUE;
            else if (iy < Short.MIN_VALUE) iy = Short.MIN_VALUE;
            pcm[i] = (short) iy;
        }
    }
}
