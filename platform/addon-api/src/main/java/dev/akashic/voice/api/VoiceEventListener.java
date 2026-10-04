package dev.akashic.voice.api;

public interface VoiceEventListener {
    default void onSession(VoiceSessionEvent event) {
        // Optional callback.
    }

    default void onModeration(VoiceModerationEvent event) {
        // Optional callback.
    }
}
