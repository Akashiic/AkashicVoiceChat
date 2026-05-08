package dev.jm.proxychat.voice.common;

import java.nio.ByteBuffer;

public final class MicFramePacket {

    public final int sequence;
    public final byte[] opusPayload;

    public MicFramePacket(int sequence, byte[] opusPayload) {
        this.sequence = sequence;
        this.opusPayload = opusPayload;
    }

    public static MicFramePacket read(ByteBuffer buf) {
        int seq = buf.getInt();
        int len = buf.getShort() & 0xFFFF;
        byte[] payload = new byte[len];
        buf.get(payload);
        return new MicFramePacket(seq, payload);
    }

    public void write(ByteBuffer buf) {
        buf.put(WirePacketType.MIC_FRAME);
        buf.putInt(sequence);
        buf.putShort((short) opusPayload.length);
        buf.put(opusPayload);
    }

    public int byteSize() {
        return 1 + 4 + 2 + opusPayload.length;
    }
}
