package dev.akashic.voice.voice.common.codec;

import org.concentus.OpusException;

public final class ConcentusOpusDecoder implements OpusDecoder {

    private final org.concentus.OpusDecoder delegate;

    public ConcentusOpusDecoder() {
        try {
            this.delegate = new org.concentus.OpusDecoder(AudioFormatSpec.SAMPLE_RATE, AudioFormatSpec.CHANNELS);
        } catch (OpusException e) {
            throw new IllegalStateException("Failed to initialize Concentus decoder", e);
        }
    }

    @Override
    public int decode(byte[] opus, int opusOffset, int opusLength, short[] outPcm) {
        try {
            return delegate.decode(opus, opusOffset, opusLength, outPcm, 0, AudioFormatSpec.FRAME_SAMPLES, false);
        } catch (OpusException e) {
            throw new IllegalStateException("Opus decode failed", e);
        }
    }

    @Override
    public int decodeMissing(short[] outPcm) {
        try {
            return delegate.decode(null, 0, 0, outPcm, 0, AudioFormatSpec.FRAME_SAMPLES, false);
        } catch (OpusException e) {
            throw new IllegalStateException("Opus PLC decode failed", e);
        }
    }

    @Override
    public void close() {}
}
