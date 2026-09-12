package dev.akashic.voice.voice.common;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class SoundResetPacket {
    public final UUID senderId;

    public SoundResetPacket(UUID senderId) {
        if (senderId == null) throw new IllegalArgumentException("senderId");
        this.senderId = senderId;
    }

    public static SoundResetPacket read(ByteBuffer buf) {
        if (buf.remaining() != 16) return null;
        return new SoundResetPacket(new UUID(buf.getLong(), buf.getLong()));
    }

    public void write(ByteBuffer buf) {
        buf.put(WirePacketType.SOUND_RESET);
        buf.putLong(senderId.getMostSignificantBits());
        buf.putLong(senderId.getLeastSignificantBits());
    }
}
