package dev.akashic.voice.platform.velocity;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class BootstrapGrant {
    private final UUID voiceIdentity;
    private final byte[] masterSecret;
    private final int udpPort;
    private final UUID sessionId;

    BootstrapGrant(UUID voiceIdentity, byte[] masterSecret, int udpPort, UUID sessionId) {
        this.voiceIdentity = Objects.requireNonNull(voiceIdentity, "voiceIdentity");
        this.masterSecret = Arrays.copyOf(masterSecret, masterSecret.length);
        this.udpPort = udpPort;
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
    }

    public UUID voiceIdentity() {
        return voiceIdentity;
    }

    public byte[] masterSecretCopy() {
        return Arrays.copyOf(masterSecret, masterSecret.length);
    }

    public int udpPort() {
        return udpPort;
    }

    public UUID sessionId() {
        return sessionId;
    }
}
