package dev.jm.proxychat.voice.common;

import java.nio.ByteBuffer;

public final class MicFramePacket {

    public final int sequence;
    public final float x;
    public final float y;
    public final float z;
    public final byte[] opusPayload;

    public MicFramePacket(int sequence, float x, float y, float z, byte[] opusPayload) {
        this.sequence = sequence;
        this.x = x;
        this.y = y;
        this.z = z;
        this.opusPayload = opusPayload;
    }

    public static MicFramePacket read(ByteBuffer buf) {
        int seq = buf.getInt();
        float x = buf.getFloat();
        float y = buf.getFloat();
        float z = buf.getFloat();
        int len = buf.getShort() & 0xFFFF;
        byte[] payload = new byte[len];
        buf.get(payload);
        return new MicFramePacket(seq, x, y, z, payload);
    }

    public void write(ByteBuffer buf) {
        buf.put(WirePacketType.MIC_FRAME);
        buf.putInt(sequence);
        buf.putFloat(x);
        buf.putFloat(y);
        buf.putFloat(z);
        buf.putShort((short) opusPayload.length);
        buf.put(opusPayload);
    }

    public int byteSize() {
        return 1 + 4 + 12 + 2 + opusPayload.length;
    }
}
