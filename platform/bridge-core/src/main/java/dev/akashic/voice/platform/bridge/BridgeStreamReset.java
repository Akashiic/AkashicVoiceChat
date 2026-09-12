package dev.akashic.voice.platform.bridge;

import java.util.Objects;
import java.util.UUID;

public record BridgeStreamReset(UUID recipientId, UUID senderId, UUID channelId) {
    public BridgeStreamReset {
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(channelId, "channelId");
    }
}
