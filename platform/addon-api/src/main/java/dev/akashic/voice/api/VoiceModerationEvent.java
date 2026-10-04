package dev.akashic.voice.api;

import java.util.Objects;
import java.util.UUID;

public record VoiceModerationEvent(UUID playerId, boolean muted, long revision) {
    public VoiceModerationEvent {
        Objects.requireNonNull(playerId, "playerId");
        if (revision < 0L) throw new IllegalArgumentException("revision");
    }
}
