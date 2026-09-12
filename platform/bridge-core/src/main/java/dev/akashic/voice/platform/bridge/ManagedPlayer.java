package dev.akashic.voice.platform.bridge;

import java.util.Objects;
import java.util.UUID;

public record ManagedPlayer(UUID playerId, String backendId) {
    public ManagedPlayer {
        Objects.requireNonNull(playerId, "playerId");
        if (backendId == null || backendId.isBlank()) {
            throw new IllegalArgumentException("backendId");
        }
    }
}
