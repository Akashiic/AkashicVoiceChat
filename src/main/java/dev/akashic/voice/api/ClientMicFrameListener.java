package dev.akashic.voice.api;

/**
 * Read-only listener for processed client microphone PCM frames (48 kHz mono signed 16-bit).
 *
 * <p>
 * Invoked on the microphone capture thread at ~50 Hz. Implementations MUST be non-blocking
 * (copy/enqueue audio to a dedicated worker thread for heavy work such as speech recognition).
 * Exceptions thrown by a listener are isolated and will not interrupt voice capture.
 */
@FunctionalInterface
public interface ClientMicFrameListener {

    /**
     * Receives a defensive copy of a processed microphone frame.
     *
     * @param pcmCopy      defensive copy of the 20 ms mono S16 PCM frame (960 samples at 48 kHz)
     * @param sampleRate   sample rate in Hz (48000)
     * @param rms          precomputed root-mean-square level in [0.0, 1.0]
     * @param transmitting {@code true} if the frame passed {@code MicGate} and will be encoded/sent
     */
    void onMicFrame(short[] pcmCopy, int sampleRate, double rms, boolean transmitting);
}
