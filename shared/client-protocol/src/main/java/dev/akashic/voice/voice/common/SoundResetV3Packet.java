package dev.akashic.voice.voice.common;

import java.nio.ByteBuffer;
import java.util.UUID;

/** A tombstone applies to precisely this sender epoch, never an arbitrary successor. */
public final class SoundResetV3Packet {
    public final UUID senderId;
    public final long epoch;
    public SoundResetV3Packet(UUID senderId, long epoch) {
        if (senderId == null || epoch <= 0) throw new IllegalArgumentException("invalid epoch reset");
        this.senderId = senderId;
        this.epoch = epoch;
    }
    public static SoundResetV3Packet read(ByteBuffer buffer) {
        if (buffer == null || buffer.remaining() != 24) return null;
        UUID sender = new UUID(buffer.getLong(), buffer.getLong());
        long epoch = buffer.getLong();
        return epoch <= 0 ? null : new SoundResetV3Packet(sender, epoch);
    }
    public int byteSize() { return 25; }
    public void write(ByteBuffer buffer) {
        if (buffer == null || buffer.remaining() < byteSize()) throw new IllegalArgumentException("short output");
        buffer.put(WirePacketType.SOUND_RESET_V3);
        buffer.putLong(senderId.getMostSignificantBits()).putLong(senderId.getLeastSignificantBits());
        buffer.putLong(epoch);
    }
}
