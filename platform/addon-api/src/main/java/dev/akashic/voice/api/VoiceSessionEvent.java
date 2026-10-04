package dev.akashic.voice.api;

import java.util.Objects;

public record VoiceSessionEvent(VoicePlayer player, VoiceSessionAction action) {
    public VoiceSessionEvent {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(action, "action");
    }
}
