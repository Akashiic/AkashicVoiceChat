package dev.akashic.voice.platform.velocity;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

public final class LegacyForgeBootstrapCodec {
    public static final String CHANNEL = "AkashicVoice";
    public static final byte REQUEST_DISCRIMINATOR = 0;
    public static final byte RESPONSE_DISCRIMINATOR = 1;
    public static final int MASTER_SECRET_BYTES = 32;
    public static final int RESPONSE_BYTES = 1 + 16 + MASTER_SECRET_BYTES + 4;

    private LegacyForgeBootstrapCodec() {
    }

    public static boolean isBootstrapRequest(byte[] data) {
        return data != null && data.length == 1 && data[0] == REQUEST_DISCRIMINATOR;
    }

    public static byte[] encodeResponse(UUID voiceIdentity, byte[] masterSecret, int udpPort) {
        Objects.requireNonNull(voiceIdentity, "voiceIdentity");
        if (masterSecret == null || masterSecret.length != MASTER_SECRET_BYTES) {
            throw new IllegalArgumentException("masterSecret must be 32 bytes");
        }
        if (udpPort < 1 || udpPort > 65_535) {
            throw new IllegalArgumentException("invalid UDP port");
        }
        ByteBuffer out = ByteBuffer.allocate(RESPONSE_BYTES);
        out.put(RESPONSE_DISCRIMINATOR);
        out.putLong(voiceIdentity.getMostSignificantBits());
        out.putLong(voiceIdentity.getLeastSignificantBits());
        out.put(masterSecret);
        out.putInt(udpPort);
        return out.array();
    }
}
