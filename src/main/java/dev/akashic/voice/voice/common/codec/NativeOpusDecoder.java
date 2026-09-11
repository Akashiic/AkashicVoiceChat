package dev.akashic.voice.voice.common.codec;

public final class NativeOpusDecoder implements OpusDecoder {

    private static final int PACKET_CACHE_SLOTS = 8;

    private final com.plasmoverse.opus.OpusDecoder delegate;
    private final byte[][] packetScratch = new byte[PACKET_CACHE_SLOTS][];
    private int packetScratchCursor;

    public NativeOpusDecoder() {
        try {
            delegate = com.plasmoverse.opus.OpusDecoder
                .create(AudioFormatSpec.SAMPLE_RATE, false, AudioFormatSpec.FRAME_SAMPLES);
        } catch (Exception e) {
            throw new IllegalStateException("Native Opus decoder unavailable", e);
        }
    }

    @Override
    public int decode(byte[] opus, int opusOffset, int opusLength, short[] outPcm) {
        if (opus == null || opusOffset < 0
            || opusLength <= 0
            || opusLength > AudioFormatSpec.MAX_OPUS_PAYLOAD
            || opusOffset + opusLength > opus.length) {
            throw new IllegalArgumentException("Invalid Opus payload length: " + opusLength);
        }
        try {
            byte[] packet = exactPacket(opus, opusOffset, opusLength);
            short[] decoded = delegate.decode(packet);
            if (decoded.length > outPcm.length)
                throw new IllegalStateException("Native Opus PCM exceeds output buffer");
            System.arraycopy(decoded, 0, outPcm, 0, decoded.length);
            return decoded.length;
        } catch (com.plasmoverse.opus.OpusException e) {
            throw new IllegalStateException("Native Opus decode failed", e);
        }
    }

    @Override
    public int decodeMissing(short[] outPcm) {
        try {
            short[] decoded = delegate.decode(null);
            if (decoded.length > outPcm.length) throw new IllegalStateException("Native PLC exceeds output buffer");
            System.arraycopy(decoded, 0, outPcm, 0, decoded.length);
            return decoded.length;
        } catch (com.plasmoverse.opus.OpusException e) {
            throw new IllegalStateException("Native Opus PLC failed", e);
        }
    }

    private byte[] exactPacket(byte[] source, int offset, int length) {
        if (offset == 0 && length == source.length) return source;
        for (int i = 0; i < packetScratch.length; i++) {
            byte[] cached = packetScratch[i];
            if (cached != null && cached.length == length) {
                System.arraycopy(source, offset, cached, 0, length);
                return cached;
            }
        }
        byte[] packet = new byte[length];
        packetScratch[packetScratchCursor] = packet;
        packetScratchCursor = (packetScratchCursor + 1) % packetScratch.length;
        System.arraycopy(source, offset, packet, 0, length);
        return packet;
    }

    @Override
    public void close() {
        delegate.close();
    }
}
