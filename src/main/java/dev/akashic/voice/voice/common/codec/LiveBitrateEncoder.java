package dev.akashic.voice.voice.common.codec;

import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import dev.akashic.voice.config.VoiceQualityPreset;

/** Applies requested bitrate between frames, on the encoding caller, without recreating codec state. */
public final class LiveBitrateEncoder implements OpusEncoder {

    private final OpusEncoder delegate;
    private final IntConsumer bitrateSetter;
    private final IntSupplier requestedBitrate;
    private volatile int appliedBitrate;
    private boolean closed;
    private volatile boolean bitrateConfirmed = true;

    public LiveBitrateEncoder(OpusEncoder delegate, IntConsumer bitrateSetter, IntSupplier requestedBitrate,
        int initialBitrate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.bitrateSetter = Objects.requireNonNull(bitrateSetter, "bitrateSetter");
        this.requestedBitrate = Objects.requireNonNull(requestedBitrate, "requestedBitrate");
        this.appliedBitrate = VoiceQualityPreset.requireValidBitrate(initialBitrate);
    }

    public int appliedBitrate() {
        return appliedBitrate;
    }

    public boolean isBitrateConfirmed() {
        return bitrateConfirmed;
    }

    @Override
    public synchronized int encode(short[] pcm, byte[] out) {
        if (closed) throw new IllegalStateException("Encoder closed");
        int requested = VoiceQualityPreset.requireValidBitrate(requestedBitrate.getAsInt());
        if (!bitrateConfirmed || requested != appliedBitrate) {
            bitrateConfirmed = false;
            bitrateSetter.accept(requested);
            // Publish only after the underlying control succeeds. Failure is not a false success.
            appliedBitrate = requested;
            bitrateConfirmed = true;
        }
        return delegate.encode(pcm, out);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        delegate.close();
    }
}
