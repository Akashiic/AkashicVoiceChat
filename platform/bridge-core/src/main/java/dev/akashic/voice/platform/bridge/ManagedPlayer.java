package dev.akashic.voice.platform.bridge;

import java.util.Objects;
import java.util.UUID;

public record ManagedPlayer(UUID playerId, String backendId, String playerName) {
    public ManagedPlayer {
        Objects.requireNonNull(playerId, "playerId");
        if (backendId == null || backendId.isBlank() || backendId.length() > 64) {
            throw new IllegalArgumentException("backendId");
        }
        if (playerName == null || !playerName.matches("[A-Za-z0-9_]{1,16}")) {
            throw new IllegalArgumentException("playerName");
        }
    }
}
