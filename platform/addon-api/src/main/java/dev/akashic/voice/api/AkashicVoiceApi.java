package dev.akashic.voice.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stable, low-frequency server-side addon surface. No secrets, sockets or raw audio are exposed. */
public interface AkashicVoiceApi {
    int API_VERSION = 1;

    int apiVersion();

    boolean available();

    List<VoicePlayer> players();

    Optional<VoicePlayer> player(UUID playerId);

    boolean isGloballyMuted(UUID playerId);

    long moderationRevision();

    VoiceListenerRegistration listen(VoiceEventListener listener);
}
