package dev.akashic.voice.voice.client.audio.capture;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.akashic.voice.voice.common.codec.OpusEncoder;

/** Resources belong to one capture generation, never to a replacement microphone. */
public final class MicCaptureSession {

    private final AudioCaptureDevice capture;
    private final OpusEncoder encoder;
    private final AtomicBoolean captureClosed = new AtomicBoolean();
    private final AtomicBoolean finishing = new AtomicBoolean();
    private volatile boolean running = true;
    private volatile boolean finished;

    public MicCaptureSession(AudioCaptureDevice capture, OpusEncoder encoder) {
        this.capture = Objects.requireNonNull(capture, "capture");
        this.encoder = Objects.requireNonNull(encoder, "encoder");
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isFinished() {
        return finished;
    }

    public int read(byte[] buffer, int offset, int length) {
        return running ? capture.read(buffer, offset, length) : -1;
    }

    /** Only the capture worker calls encode and finish. */
    public int encode(short[] pcm, byte[] out) {
        if (!running) throw new IllegalStateException("Capture session stopped");
        return encoder.encode(pcm, out);
    }

    /** May be called by another thread; does not dispose an in-flight encoder. */
    public void requestStop() {
        running = false;
        if (captureClosed.compareAndSet(false, true)) capture.close();
    }

    /** Worker finally block (or failed thread start), never a concurrent stop caller. */
    public void finish() {
        if (!finishing.compareAndSet(false, true)) return;
        RuntimeException failure = null;
        try {
            try {
                requestStop();
            } catch (RuntimeException closeFailure) {
                failure = closeFailure;
            } finally {
                // Even a failing device close must not leak the encoder.
                try {
                    encoder.close();
                } catch (RuntimeException closeFailure) {
                    if (failure == null) failure = closeFailure;
                    else failure.addSuppressed(closeFailure);
                }
            }
        } finally {
            finished = true;
        }
        if (failure != null) throw failure;
    }
}
