package dev.akashic.voice.voice.common;

import java.nio.ByteBuffer;

/** Optional features offered only inside an authenticated UDP envelope. */
public final class VoiceCapabilities {
    public static final int STREAM_EPOCHS = 1;
    public static final int SUPPORTED = STREAM_EPOCHS;
    private VoiceCapabilities() {}

    public static int read(ByteBuffer buffer) {
        if (buffer == null || buffer.remaining() != 4) return -1;
        int features = buffer.getInt();
        return features >= 0 ? features : -1;
    }

    public static void write(ByteBuffer buffer, boolean acknowledgement, int features) {
        if (features < 0) throw new IllegalArgumentException("negative capabilities");
        if (buffer == null || buffer.remaining() < 5) throw new IllegalArgumentException("short output");
        buffer.put(acknowledgement ? WirePacketType.CAPABILITIES_ACK : WirePacketType.CAPABILITIES);
        buffer.putInt(features);
    }
}
