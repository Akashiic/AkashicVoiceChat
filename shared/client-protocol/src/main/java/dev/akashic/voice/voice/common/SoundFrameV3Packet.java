package dev.akashic.voice.voice.common;

import java.nio.ByteBuffer;

/** One positive receiver-session epoch followed by the established v2 frame body. */
public final class SoundFrameV3Packet {
    public final long epoch;
    public final SoundFrameV2Packet frame;

    public SoundFrameV3Packet(long epoch, SoundFrameV2Packet frame) {
        if (epoch <= 0 || frame == null) throw new IllegalArgumentException("invalid epoch frame");
        this.epoch = epoch;
        this.frame = frame;
    }

    public static SoundFrameV3Packet read(ByteBuffer buffer) {
        if (buffer == null || !buffer.hasArray() || buffer.remaining() < 8) return null;
        long epoch = buffer.getLong();
        if (epoch <= 0) return null;
        SoundFrameV2Packet frame = SoundFrameV2Packet.read(buffer);
        return frame == null ? null : new SoundFrameV3Packet(epoch, frame);
    }

    public int byteSize() { return 8 + frame.byteSize(); }

    public void write(ByteBuffer buffer) {
        if (buffer == null || buffer.remaining() < byteSize()) throw new IllegalArgumentException("short output");
        buffer.put(WirePacketType.SOUND_FRAME_V3).putLong(epoch);
        buffer.putLong(frame.senderId.getMostSignificantBits()).putLong(frame.senderId.getLeastSignificantBits());
        buffer.put(frame.flags).putFloat(frame.x).putFloat(frame.y).putFloat(frame.z);
        buffer.putFloat(frame.maxDistance).putInt(frame.sequence).putShort((short) frame.opusLength);
        buffer.put(frame.opusData, frame.opusOffset, frame.opusLength);
    }
}
