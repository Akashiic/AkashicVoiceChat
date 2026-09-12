package dev.akashic.voice.voice.common;

import java.nio.ByteBuffer;

import dev.akashic.voice.voice.common.codec.AudioFormatSpec;

public final class MicFramePacket {

    public final int sequence;
    public final byte[] opusData;
    public final int opusOffset;
    public final int opusLength;

    private MicFramePacket(int sequence, byte[] opusData, int opusOffset, int opusLength) {
        this.sequence = sequence;
        this.opusData = opusData;
        this.opusOffset = opusOffset;
        this.opusLength = opusLength;
    }

    public static MicFramePacket read(ByteBuffer buf) {
        if (!buf.hasArray() || buf.remaining() < 6) return null;
        int seq = buf.getInt();
        int len = buf.getShort() & 0xFFFF;
        if (len <= 0 || len > AudioFormatSpec.MAX_OPUS_PAYLOAD || len != buf.remaining()) return null;
        int offset = buf.arrayOffset() + buf.position();
        byte[] data = buf.array();
        buf.position(buf.limit());
        return new MicFramePacket(seq, data, offset, len);
    }

    public static void write(ByteBuffer buf, int sequence, byte[] opusData, int opusOffset, int opusLength) {
        if (opusLength <= 0 || opusLength > AudioFormatSpec.MAX_OPUS_PAYLOAD
            || opusOffset < 0
            || opusOffset + opusLength > opusData.length) {
            throw new IllegalArgumentException("Invalid Opus payload range");
        }
        buf.put(WirePacketType.MIC_FRAME);
        buf.putInt(sequence);
        buf.putShort((short) opusLength);
        buf.put(opusData, opusOffset, opusLength);
    }

    public static void write(ByteBuffer buf, int sequence, byte[] opusData, int opusLength) {
        write(buf, sequence, opusData, 0, opusLength);
    }

    public static int byteSize(int payloadLength) {
        return 1 + 4 + 2 + payloadLength;
    }
}
