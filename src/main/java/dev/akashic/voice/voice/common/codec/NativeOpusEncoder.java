package dev.akashic.voice.voice.common.codec;

import com.plasmoverse.opus.OpusMode;

public final class NativeOpusEncoder implements OpusEncoder {

    private final com.plasmoverse.opus.OpusEncoder delegate;

    public NativeOpusEncoder(int bitrate) {
        com.plasmoverse.opus.OpusEncoder created = null;
        try {
            created = com.plasmoverse.opus.OpusEncoder
                .create(AudioFormatSpec.SAMPLE_RATE, false, AudioFormatSpec.MAX_OPUS_PAYLOAD, OpusMode.VOIP);
            created.setBitrate(bitrate);
            delegate = created;
        } catch (Throwable failure) {
            if (created != null) {
                try {
                    created.close();
                } catch (Throwable ignored) {
                    // Preserve the original construction failure.
                }
            }
            rethrowFatal(failure);
            throw new IllegalStateException("Native Opus encoder unavailable", failure);
        }
    }

    /** Invoked only by the encoding worker between frames. */
    public void setBitrate(int bitrate) {
        delegate.setBitrate(dev.akashic.voice.config.VoiceQualityPreset.requireValidBitrate(bitrate));
    }

    @Override
    public int encode(short[] pcm, byte[] out) {
        try {
            byte[] encoded = delegate.encode(pcm);
            if (encoded.length > out.length)
                throw new IllegalStateException("Native Opus payload exceeds output buffer");
            System.arraycopy(encoded, 0, out, 0, encoded.length);
            return encoded.length;
        } catch (com.plasmoverse.opus.OpusException e) {
            throw new IllegalStateException("Native Opus encode failed", e);
        }
    }

    @Override
    public void close() {
        delegate.close();
    }

    private static void rethrowFatal(Throwable failure) {
        if (failure instanceof ThreadDeath) throw (ThreadDeath) failure;
        if (failure instanceof VirtualMachineError) throw (VirtualMachineError) failure;
    }
}
