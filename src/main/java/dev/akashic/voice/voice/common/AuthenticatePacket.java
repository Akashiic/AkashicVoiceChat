package dev.akashic.voice.voice.common;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class AuthenticatePacket {

    public final UUID playerId;
    public final UUID secret;

    public AuthenticatePacket(UUID playerId, UUID secret) {
        this.playerId = playerId;
        this.secret = secret;
    }

    public static AuthenticatePacket read(ByteBuffer buf) {
        if (buf.remaining() < 32) return null;
        UUID player = new UUID(buf.getLong(), buf.getLong());
        UUID sec = new UUID(buf.getLong(), buf.getLong());
        return new AuthenticatePacket(player, sec);
    }

    public void write(ByteBuffer buf) {
        buf.put(WirePacketType.AUTHENTICATE);
        buf.putLong(playerId.getMostSignificantBits());
        buf.putLong(playerId.getLeastSignificantBits());
        buf.putLong(secret.getMostSignificantBits());
        buf.putLong(secret.getLeastSignificantBits());
    }

    public static int byteSize() {
        return 1 + 16 + 16;
    }
}
