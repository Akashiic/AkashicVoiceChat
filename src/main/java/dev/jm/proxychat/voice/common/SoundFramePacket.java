package dev.jm.proxychat.voice.common;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class SoundFramePacket {

    public final UUID senderId;
    public final float x;
    public final float y;
    public final float z;
    public final int sequence;
    public final byte[] opusPayload;

    public SoundFramePacket(UUID senderId, float x, float y, float z, int sequence, byte[] opusPayload) {
        this.senderId = senderId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.sequence = sequence;
        this.opusPayload = opusPayload;
    }

    public static SoundFramePacket read(ByteBuffer buf) {
        UUID id = new UUID(buf.getLong(), buf.getLong());
        float x = buf.getFloat();
        float y = buf.getFloat();
        float z = buf.getFloat();
        int seq = buf.getInt();
        int len = buf.getShort() & 0xFFFF;
        byte[] payload = new byte[len];
        buf.get(payload);
        return new SoundFramePacket(id, x, y, z, seq, payload);
    }

    public void write(ByteBuffer buf) {
        buf.put(WirePacketType.SOUND_FRAME);
        buf.putLong(senderId.getMostSignificantBits());
        buf.putLong(senderId.getLeastSignificantBits());
        buf.putFloat(x);
        buf.putFloat(y);
        buf.putFloat(z);
        buf.putInt(sequence);
        buf.putShort((short) opusPayload.length);
        buf.put(opusPayload);
    }

    public int byteSize() {
        return 1 + 16 + 12 + 4 + 2 + opusPayload.length;
    }
}
