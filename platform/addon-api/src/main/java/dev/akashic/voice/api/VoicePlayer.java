package dev.akashic.voice.api;

import java.util.Objects;
import java.util.UUID;

/** Immutable snapshot of one authenticated Akashic Voice session. */
public record VoicePlayer(UUID playerId, String playerName, String backendId, boolean globallyMuted) {
    public VoicePlayer {
        Objects.requireNonNull(playerId, "playerId");
        if (playerName == null || !playerName.matches("[A-Za-z0-9_]{1,16}")) {
            throw new IllegalArgumentException("playerName");
        }
        if (backendId == null || backendId.isBlank() || backendId.length() > 64) {
            throw new IllegalArgumentException("backendId");
        }
    }
}
