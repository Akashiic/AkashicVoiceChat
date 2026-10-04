package dev.akashic.voice.voice.client;

import dev.akashic.voice.AkashicVoice;
import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.client.audio.capture.AudioCaptureBackends;
import dev.akashic.voice.voice.client.audio.capture.AudioCaptureDevice;
import dev.akashic.voice.voice.client.audio.capture.AudioCaptureException;
import dev.akashic.voice.voice.client.audio.capture.MicCaptureSession;
import dev.akashic.voice.voice.common.codec.AudioFormatSpec;
import dev.akashic.voice.voice.common.codec.OpusCodecFactory;
import dev.akashic.voice.voice.common.codec.OpusEncoder;

public final class MicCapture {

    public interface FrameSink {

        void onFrame(int sequence, byte[] opusPayload, int payloadLength);
    }

    private final FrameSink sink;
    private final String deviceId;
    private volatile MicCaptureSession session;
    private Thread thread;

    public MicCapture(FrameSink sink) {
        this(sink, "");
    }

    public MicCapture(FrameSink sink, String deviceId) {
        this.sink = sink;
        this.deviceId = deviceId != null ? deviceId : "";
    }

    public synchronized void start() throws AudioCaptureException {
        if (isRunning()) return;
        AudioCaptureDevice opened = AudioCaptureBackends.get()
            .open(deviceId);
        final OpusEncoder created;
        try {
            created = OpusCodecFactory.createEncoder();
        } catch (RuntimeException failure) {
            try {
                opened.close();
            } catch (RuntimeException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
        final MicCaptureSession started = new MicCaptureSession(opened, created);
        Thread worker = new Thread(() -> loop(started), "AkashicVoice-Mic");
        worker.setDaemon(true);
        session = started;
        thread = worker;
        try {
            worker.start();
        } catch (RuntimeException | Error failure) {
            session = null;
            thread = null;
            try {
                started.finish();
            } catch (RuntimeException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
        AkashicVoice.LOG.info(
            "Microphone capture started ({} Hz, {}-sample frames, backend={}, device={})",
            AudioFormatSpec.SAMPLE_RATE,
            AudioFormatSpec.FRAME_SAMPLES,
            opened.backendName(),
            opened.description());
    }

    public boolean isRunning() {
        MicCaptureSession current = session;
        return current != null && current.isRunning();
    }

    public synchronized void stop() {
        MicCaptureSession current = session;
        session = null;
        Thread worker = thread;
        thread = null;
        if (current == null) return;
        if (worker != null && worker != Thread.currentThread()) worker.interrupt();
        try {
            current.requestStop();
        } catch (RuntimeException failure) {
            AkashicVoice.LOG.debug("Microphone backend close failed", failure);
        }
        AkashicVoice.LOG.info("Microphone capture stopped");
    }

    private void loop(MicCaptureSession current) {
        try {
            byte[] pcmBytes = new byte[AudioFormatSpec.FRAME_BYTES_PCM_S16];
            short[] pcm = new short[AudioFormatSpec.FRAME_SAMPLES];
            byte[] opusOut = new byte[AudioFormatSpec.MAX_OPUS_PAYLOAD];
            double[] highPassState = new double[2];
            int sequence = 0;
            while (current.isRunning()) {
                int read = 0;
                while (read < pcmBytes.length && current.isRunning()) {
                    int remaining = pcmBytes.length - read;
                    int n = current.read(pcmBytes, read, remaining);
                    if (n < 0) return;
                    if (n > remaining) throw new IllegalStateException("Invalid microphone read length");
                    if (n == 0) {
                        Thread.sleep(2L);
                        continue;
                    }
                    read += n;
                }
                if (!current.isRunning()) return;
                for (int i = 0; i < AudioFormatSpec.FRAME_SAMPLES; i++) {
                    int lo = pcmBytes[i * 2] & 0xFF;
                    int hi = pcmBytes[i * 2 + 1];
                    pcm[i] = (short) ((hi << 8) | lo);
                }
                applyAmplification(pcm, AkashicVoiceConfig.micAmplification());
                if (AkashicVoiceConfig.noiseSuppression()) applyHighPass(pcm, highPassState);
                if (!MicGate.shouldTransmit(pcm)) continue;
                int length = current.encode(pcm, opusOut);
                if (!current.isRunning()) return;
                sink.onFrame(sequence++, opusOut, length);
                MicGate.markTransmitted();
            }
        } catch (InterruptedException expected) {
            Thread.currentThread()
                .interrupt();
        } catch (RuntimeException failure) {
            if (current.isRunning()) {
                AkashicVoice.LOG.warn("Microphone capture/encode failed; recovery will reopen it", failure);
            }
        } finally {
            try {
                current.finish();
            } catch (RuntimeException failure) {
                AkashicVoice.LOG.debug("Microphone resource cleanup failed", failure);
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
    private static void applyHighPass(short[] pcm, double[] state) {
        final double a = 0.985;
        for (int i = 0; i < pcm.length; i++) {
            double x = pcm[i];
            double y = a * (state[1] + x - state[0]);
            state[0] = x;
            state[1] = y;
            int iy = (int) Math.round(y);
            if (iy > Short.MAX_VALUE) iy = Short.MAX_VALUE;
            else if (iy < Short.MIN_VALUE) iy = Short.MIN_VALUE;
            pcm[i] = (short) iy;
        }
    }
}
