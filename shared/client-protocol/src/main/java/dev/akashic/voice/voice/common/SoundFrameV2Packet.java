package dev.akashic.voice.voice.common;

import java.nio.ByteBuffer;
import java.util.UUID;

import dev.akashic.voice.voice.common.codec.AudioFormatSpec;

public final class SoundFrameV2Packet {

    public static final byte FLAG_SPATIAL = 0x01;
    public static final byte FLAG_WHISPERING = 0x02;
    private static final byte KNOWN_FLAGS = FLAG_SPATIAL | FLAG_WHISPERING;
    private static final int METADATA_BYTES = 16 + 1 + 12 + 4 + 4 + 2;

    public final UUID senderId;
    public final byte flags;
    public final float x;
    public final float y;
    public final float z;
    public final float maxDistance;
    public final int sequence;
    public final byte[] opusData;
    public final int opusOffset;
    public final int opusLength;

    public SoundFrameV2Packet(UUID senderId, boolean spatial, boolean whispering,
        float x, float y, float z, float maxDistance, int sequence, byte[] opusData) {
        this(senderId, spatial, whispering, x, y, z, maxDistance, sequence,
            opusData, 0, opusData.length);
    }
    public SoundFrameV2Packet(UUID senderId, boolean spatial, boolean whispering,
        float x, float y, float z, float maxDistance, int sequence,
        byte[] opusData, int opusOffset, int opusLength) {
        if (senderId == null) throw new IllegalArgumentException("senderId");
        if (opusData == null || opusLength <= 0 || opusLength > AudioFormatSpec.MAX_OPUS_PAYLOAD
            || opusOffset < 0 || opusOffset + opusLength > opusData.length) {
            throw new IllegalArgumentException("Invalid Opus payload range");
        }
        if (!Float.isFinite(maxDistance) || maxDistance < 0F) {
            throw new IllegalArgumentException("Invalid maxDistance");
        }
        if (spatial && (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))) {
            throw new IllegalArgumentException("Invalid spatial position");
        }
        this.senderId = senderId;
        this.flags = (byte) ((spatial ? FLAG_SPATIAL : 0) | (whispering ? FLAG_WHISPERING : 0));
        this.x = x;
        this.y = y;
        this.z = z;
        this.maxDistance = maxDistance;
        this.sequence = sequence;
        this.opusData = opusData;
        this.opusOffset = opusOffset;
        this.opusLength = opusLength;
    }

    public boolean isSpatial() {
        return (flags & FLAG_SPATIAL) != 0;
    }

    public boolean isWhispering() {
        return (flags & FLAG_WHISPERING) != 0;
    }
    public static SoundFrameV2Packet read(ByteBuffer buf) {
        if (!buf.hasArray() || buf.remaining() < METADATA_BYTES) return null;
        UUID id = new UUID(buf.getLong(), buf.getLong());
        byte flags = buf.get();
        if ((flags & ~KNOWN_FLAGS) != 0) return null;
        float x = buf.getFloat();
        float y = buf.getFloat();
        float z = buf.getFloat();
        float maxDistance = buf.getFloat();
        int sequence = buf.getInt();
        int length = buf.getShort() & 0xffff;
        if (!Float.isFinite(maxDistance) || maxDistance < 0F) return null;
        if ((flags & FLAG_SPATIAL) != 0
            && (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))) return null;
        if (length <= 0 || length > AudioFormatSpec.MAX_OPUS_PAYLOAD || length != buf.remaining()) return null;
        int offset = buf.arrayOffset() + buf.position();
        byte[] data = buf.array();
        buf.position(buf.limit());
        return new SoundFrameV2Packet(
            id,
            (flags & FLAG_SPATIAL) != 0,
            (flags & FLAG_WHISPERING) != 0,
            x, y, z, maxDistance, sequence, data, offset, length);
    }
    public void write(ByteBuffer buf) {
        buf.put(WirePacketType.SOUND_FRAME_V2);
        buf.putLong(senderId.getMostSignificantBits());
        buf.putLong(senderId.getLeastSignificantBits());
        buf.put(flags);
        buf.putFloat(x).putFloat(y).putFloat(z);
        buf.putFloat(maxDistance);
        buf.putInt(sequence);
        buf.putShort((short) opusLength);
        buf.put(opusData, opusOffset, opusLength);
    }

    public int byteSize() {
        return 1 + METADATA_BYTES + opusLength;
    }
}
