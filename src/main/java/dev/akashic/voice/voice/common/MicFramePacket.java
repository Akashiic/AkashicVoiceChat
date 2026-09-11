package dev.akashic.voice.voice.common;

import java.nio.ByteBuffer;

import dev.akashic.voice.voice.common.codec.AudioFormatSpec;

public final class MicFramePacket {

    public final int sequence;
    public final byte[] opusPayload;

    public MicFramePacket(int sequence, byte[] opusPayload) {
        this.sequence = sequence;
        this.opusPayload = opusPayload;
    }

    public static MicFramePacket read(ByteBuffer buf) {
        if (buf.remaining() < 6) return null;
        int seq = buf.getInt();
        int len = buf.getShort() & 0xFFFF;
        if (len > AudioFormatSpec.MAX_OPUS_PAYLOAD || len != buf.remaining()) return null;
        byte[] payload = new byte[len];
        buf.get(payload);
        return new MicFramePacket(seq, payload);
    }

    public static void write(ByteBuffer buf, int sequence, byte[] opusPayload, int payloadLength) {
        if (payloadLength < 0 || payloadLength > AudioFormatSpec.MAX_OPUS_PAYLOAD
            || payloadLength > opusPayload.length) {
            throw new IllegalArgumentException("Invalid Opus payload length: " + payloadLength);
        }
        buf.put(WirePacketType.MIC_FRAME);
        buf.putInt(sequence);
        buf.putShort((short) payloadLength);
        buf.put(opusPayload, 0, payloadLength);
    }

    public static int byteSize(int payloadLength) {
        return 1 + 4 + 2 + payloadLength;
    }
}
