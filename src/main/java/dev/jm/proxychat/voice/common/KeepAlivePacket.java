package dev.jm.proxychat.voice.common;

import java.nio.ByteBuffer;

public final class KeepAlivePacket {

    public final long timestamp;

    public KeepAlivePacket(long timestamp) {
        this.timestamp = timestamp;
    }

    public static KeepAlivePacket read(ByteBuffer buf) {
        return new KeepAlivePacket(buf.getLong());
    }

    public void write(ByteBuffer buf) {
        buf.put(WirePacketType.KEEPALIVE);
        buf.putLong(timestamp);
    }

    public static int byteSize() {
        return 1 + 8;
    }
}
