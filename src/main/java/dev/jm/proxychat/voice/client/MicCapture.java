package dev.jm.proxychat.voice.client;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

import dev.jm.proxychat.ProxyChat;
import dev.jm.proxychat.voice.common.codec.AudioFormatSpec;
import dev.jm.proxychat.voice.common.codec.OpusCodecFactory;
import dev.jm.proxychat.voice.common.codec.OpusEncoder;

public final class MicCapture {

    public interface FrameSink {

        void onFrame(int sequence, byte[] opusPayload, int payloadLength);
    }

    private final FrameSink sink;
    private TargetDataLine line;
    private OpusEncoder encoder;
    private Thread thread;
    private volatile boolean running;

    public MicCapture(FrameSink sink) {
        this.sink = sink;
    }

    public synchronized void start() throws LineUnavailableException {
        if (running) return;
        AudioFormat format = new AudioFormat(AudioFormatSpec.SAMPLE_RATE, 16, AudioFormatSpec.CHANNELS, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("No microphone line supports " + format);
        }
        line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(format, AudioFormatSpec.FRAME_BYTES_PCM_S16 * 4);
        line.start();

        encoder = OpusCodecFactory.createEncoder();
        running = true;
        thread = new Thread(this::loop, "ProxyChat-Mic");
        thread.setDaemon(true);
        thread.start();
        ProxyChat.LOG.info(
            "Microphone capture started ({} Hz, {}-sample frames)",
            AudioFormatSpec.SAMPLE_RATE,
            AudioFormatSpec.FRAME_SAMPLES);
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (thread != null) thread.interrupt();
        if (line != null) {
            line.stop();
            line.close();
            line = null;
        }
        if (encoder != null) {
            encoder.close();
            encoder = null;
        }
        ProxyChat.LOG.info("Microphone capture stopped");
    }

    private void loop() {
        byte[] pcmBytes = new byte[AudioFormatSpec.FRAME_BYTES_PCM_S16];
        short[] pcm = new short[AudioFormatSpec.FRAME_SAMPLES];
        byte[] opusOut = new byte[AudioFormatSpec.MAX_OPUS_PAYLOAD];
        int sequence = 0;

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

            if (!MicGate.shouldTransmit(pcm)) continue;

            try {
                int len = encoder.encode(pcm, opusOut);
                sink.onFrame(sequence++, opusOut, len);
            } catch (RuntimeException e) {
                ProxyChat.LOG.warn("Mic frame encode failed", e);
            }
        }
    }
}
