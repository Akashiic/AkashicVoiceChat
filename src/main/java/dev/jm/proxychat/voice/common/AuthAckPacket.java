package dev.jm.proxychat.voice.common;

import java.nio.ByteBuffer;

public final class AuthAckPacket {

    public static final byte STATUS_OK = 0;
    public static final byte STATUS_INVALID_SECRET = 1;
    public static final byte STATUS_UNKNOWN_PLAYER = 2;

    public final byte status;

    public AuthAckPacket(byte status) {
        this.status = status;
    }

    public static AuthAckPacket read(ByteBuffer buf) {
        return new AuthAckPacket(buf.get());
    }

    public void write(ByteBuffer buf) {
        buf.put(WirePacketType.AUTH_ACK);
        buf.put(status);
    }

    public static int byteSize() {
        return 1 + 1;
    }
}
