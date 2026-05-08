package dev.jm.proxychat.voice.server;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SecretRegistry {

    private static final Map<UUID, UUID> SECRETS = new ConcurrentHashMap<>();

    private SecretRegistry() {}

    public static UUID getOrCreate(UUID playerId) {
        UUID existing = SECRETS.get(playerId);
        if (existing != null) {
            return existing;
        }
        UUID generated = UUID.randomUUID();
        UUID prev = SECRETS.putIfAbsent(playerId, generated);
        return prev != null ? prev : generated;
    }

    public static UUID get(UUID playerId) {
        return SECRETS.get(playerId);
    }

    public static void invalidate(UUID playerId) {
        SECRETS.remove(playerId);
    }

    public static Map<UUID, UUID> snapshot() {
        return Collections.unmodifiableMap(SECRETS);
    }

    public static int size() {
        return SECRETS.size();
    }
}
