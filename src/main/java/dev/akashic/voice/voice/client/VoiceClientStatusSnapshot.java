package dev.akashic.voice.voice.client;

/** Immutable, non-sensitive client diagnostics for UI/homologation. */
public final class VoiceClientStatusSnapshot {

    private final boolean present;
    private final boolean authenticated;
    private final boolean freshBootstrapRequired;
    private final long receivedFrames;
    private final long decoderCapacityDrops;
    private final int activeDecoderStreams;
    private final int maxDecoderStreams;

    VoiceClientStatusSnapshot(boolean present, boolean authenticated, boolean freshBootstrapRequired,
        long receivedFrames, long decoderCapacityDrops, int activeDecoderStreams, int maxDecoderStreams) {
        this.present = present;
        this.authenticated = authenticated;
        this.freshBootstrapRequired = freshBootstrapRequired;
        this.receivedFrames = receivedFrames;
        this.decoderCapacityDrops = decoderCapacityDrops;
        this.activeDecoderStreams = activeDecoderStreams;
        this.maxDecoderStreams = maxDecoderStreams;
    }

    public boolean isPresent() {
        return present;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public boolean needsFreshBootstrap() {
        return freshBootstrapRequired;
    }

    public long receivedFrames() {
        return receivedFrames;
    }

    public long decoderCapacityDrops() {
        return decoderCapacityDrops;
    }

    public int activeDecoderStreams() {
        return activeDecoderStreams;
    }

    public int maxDecoderStreams() {
        return maxDecoderStreams;
    }

    public static VoiceClientStatusSnapshot disconnected() {
        return new VoiceClientStatusSnapshot(false, false, false, 0L, 0L, 0, VoiceClient.MAX_DECODER_STREAMS);
    }
}
