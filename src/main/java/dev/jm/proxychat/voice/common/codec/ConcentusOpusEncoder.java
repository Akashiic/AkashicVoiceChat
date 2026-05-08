package dev.jm.proxychat.voice.common.codec;

import org.concentus.OpusApplication;
import org.concentus.OpusException;

public final class ConcentusOpusEncoder implements OpusEncoder {

    private final org.concentus.OpusEncoder delegate;

    public ConcentusOpusEncoder() {
        try {
            this.delegate = new org.concentus.OpusEncoder(
                AudioFormatSpec.SAMPLE_RATE,
                AudioFormatSpec.CHANNELS,
                OpusApplication.OPUS_APPLICATION_VOIP);
        } catch (OpusException e) {
            throw new IllegalStateException("Failed to initialize Concentus encoder", e);
        }
    }

    @Override
    public int encode(short[] pcm, byte[] out) {
        try {
            return delegate.encode(pcm, 0, AudioFormatSpec.FRAME_SAMPLES, out, 0, out.length);
        } catch (OpusException e) {
            throw new IllegalStateException("Opus encode failed", e);
        }
    }

    @Override
    public void close() {}
}
