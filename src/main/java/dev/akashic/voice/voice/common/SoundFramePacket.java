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
    public final byte[] opusData;
    public final int opusOffset;
    public final int opusLength;

    public SoundFramePacket(UUID senderId, boolean spatial, float x, float y, float z, int sequence, byte[] opusData) {
        this(senderId, spatial, x, y, z, sequence, opusData, 0, opusData.length);
    }

    public SoundFramePacket(UUID senderId, boolean spatial, float x, float y, float z, int sequence, byte[] opusData,
        int opusOffset, int opusLength) {
        if (opusLength <= 0 || opusLength > AudioFormatSpec.MAX_OPUS_PAYLOAD
            || opusOffset < 0
            || opusOffset + opusLength > opusData.length) {
            throw new IllegalArgumentException("Invalid Opus payload range");
        }
        this.senderId = senderId;
        this.flags = spatial ? FLAG_SPATIAL : 0;
        this.x = x;
        this.y = y;
        this.z = z;
        this.sequence = sequence;
        this.opusData = opusData;
        this.opusOffset = opusOffset;
        this.opusLength = opusLength;
    }

    public static SoundFramePacket read(ByteBuffer buf) {
        if (!buf.hasArray() || buf.remaining() < 35) return null;
        UUID id = new UUID(buf.getLong(), buf.getLong());
        byte flags = buf.get();
        if ((flags & ~KNOWN_FLAGS) != 0) return null;
        float x = buf.getFloat();
        float y = buf.getFloat();
        float z = buf.getFloat();
        int seq = buf.getInt();
        int len = buf.getShort() & 0xFFFF;
        if (len <= 0 || len > AudioFormatSpec.MAX_OPUS_PAYLOAD || len != buf.remaining()) return null;
        int offset = buf.arrayOffset() + buf.position();
        byte[] data = buf.array();
        buf.position(buf.limit());
        return new SoundFramePacket(id, (flags & FLAG_SPATIAL) != 0, x, y, z, seq, data, offset, len);
    }

    public boolean isSpatial() {
        return (flags & FLAG_SPATIAL) != 0;
    }

    public void write(ByteBuffer buf) {
        buf.put(WirePacketType.SOUND_FRAME);
        buf.putLong(senderId.getMostSignificantBits());
        buf.putLong(senderId.getLeastSignificantBits());
        buf.put(flags);
        buf.putFloat(x)
            .putFloat(y)
            .putFloat(z);
        buf.putInt(sequence);
        buf.putShort((short) opusLength);
        buf.put(opusData, opusOffset, opusLength);
    }

    public int byteSize() {
        return 1 + 16 + 1 + 12 + 4 + 2 + opusLength;
    }
}
