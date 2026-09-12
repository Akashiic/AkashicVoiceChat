package dev.akashic.voice.platform.bridge;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class AkashicSpeakerFrame {
    private final UUID speakerId;
    private final long sequence;
    private final boolean whispering;
    private final byte[] opus;

    public AkashicSpeakerFrame(UUID speakerId, long sequence, boolean whispering, byte[] opus) {
        this.speakerId = Objects.requireNonNull(speakerId, "speakerId");
        if (sequence < 0L) throw new IllegalArgumentException("sequence");
        validateOpus(opus);
        this.sequence = sequence;
        this.whispering = whispering;
        this.opus = Arrays.copyOf(opus, opus.length);
    }

    public UUID speakerId() { return speakerId; }
    public long sequence() { return sequence; }
    public boolean whispering() { return whispering; }
    public byte[] opusUnsafe() { return opus; }

    private static void validateOpus(byte[] opus) {
        if (opus == null || opus.length == 0 || opus.length > BridgeConstants.MAX_OPUS_PAYLOAD) {
            throw new IllegalArgumentException("invalid Opus payload length");
        }
    }
}
