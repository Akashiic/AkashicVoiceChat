package dev.akashic.voice.voice.common;

import java.nio.ByteBuffer;
import java.util.UUID;

import dev.akashic.voice.voice.common.codec.AudioFormatSpec;

public final class SoundFramePacket {

    public static final byte FLAG_SPATIAL = 0x01;
    private static final byte KNOWN_FLAGS = FLAG_SPATIAL;

    public final UUID senderId;
    public final byte flags;
    public final float x;
    public final float y;
    public final float z;
    public final int sequence;
    public final byte[] opusPayload;

    public SoundFramePacket(UUID senderId, boolean spatial, float x, float y, float z, int sequence,
        byte[] opusPayload) {
        this.senderId = senderId;
        this.flags = spatial ? FLAG_SPATIAL : 0;
        this.x = x;
        this.y = y;
        this.z = z;
        this.sequence = sequence;
        this.opusPayload = opusPayload;
    }

    public static SoundFramePacket read(ByteBuffer buf) {
        if (buf.remaining() < 35) return null;
        UUID id = new UUID(buf.getLong(), buf.getLong());
        byte flags = buf.get();
        if ((flags & ~KNOWN_FLAGS) != 0) return null;
        float x = buf.getFloat();
        float y = buf.getFloat();
        float z = buf.getFloat();
        int seq = buf.getInt();
        int len = buf.getShort() & 0xFFFF;
        if (len > AudioFormatSpec.MAX_OPUS_PAYLOAD || len != buf.remaining()) return null;
        byte[] payload = new byte[len];
        buf.get(payload);
        return new SoundFramePacket(id, (flags & FLAG_SPATIAL) != 0, x, y, z, seq, payload);
    }

    public boolean isSpatial() {
        return (flags & FLAG_SPATIAL) != 0;
    }

    public void write(ByteBuffer buf) {
        buf.put(WirePacketType.SOUND_FRAME);
        buf.putLong(senderId.getMostSignificantBits());
        buf.putLong(senderId.getLeastSignificantBits());
        buf.put(flags);
        buf.putFloat(x);
        buf.putFloat(y);
        buf.putFloat(z);
        buf.putInt(sequence);
        buf.putShort((short) opusPayload.length);
        buf.put(opusPayload);
    }

    public int byteSize() {
        return 1 + 16 + 1 + 12 + 4 + 2 + opusPayload.length;
    }
}
