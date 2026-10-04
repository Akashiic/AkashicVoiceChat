package dev.akashic.voice.voice.server;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.akashic.voice.voice.common.crypto.SessionKeys;

public final class SecretRegistry {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();

    private SecretRegistry() {}

    public static final class Material implements AutoCloseable {

        private final UUID sessionId;
        private final byte[] secret;

        private Material(UUID sessionId, byte[] secret) {
            this.sessionId = sessionId;
            this.secret = Arrays.copyOf(secret, secret.length);
        }

        public UUID sessionId() {
            return sessionId;
        }

        public byte[] secretCopy() {
            return Arrays.copyOf(secret, secret.length);
        }

        @Override
        public void close() {
            Arrays.fill(secret, (byte) 0);
        }
    }

    private static final class Entry {

        final UUID sessionId;
        final byte[] secret;

        Entry(UUID sessionId, byte[] secret) {
            this.sessionId = sessionId;
            this.secret = Arrays.copyOf(secret, secret.length);
        }

        Material material() {
            return new Material(sessionId, secret);
        }

        void clear() {
            Arrays.fill(secret, (byte) 0);
        }
    }

    public static Material getOrCreateMaterial(UUID playerId) {
        if (playerId == null) throw new IllegalArgumentException("playerId");
        Entry existing = ENTRIES.get(playerId);
        if (existing != null) return existing.material();

        byte[] generated = new byte[SessionKeys.MASTER_SECRET_BYTES];
        RANDOM.nextBytes(generated);
        Entry candidate = new Entry(UUID.randomUUID(), generated);
        Arrays.fill(generated, (byte) 0);
        Entry previous = ENTRIES.putIfAbsent(playerId, candidate);
        if (previous != null) {
            candidate.clear();
            return previous.material();
        }
        return candidate.material();
    }

    public static Material getMaterial(UUID playerId) {
        Entry value = playerId != null ? ENTRIES.get(playerId) : null;
        return value != null ? value.material() : null;
    }

    public static byte[] getOrCreate(UUID playerId) {
        try (Material material = getOrCreateMaterial(playerId)) {
            return material.secretCopy();
        }
    }

    public static byte[] get(UUID playerId) {
        Entry value = playerId != null ? ENTRIES.get(playerId) : null;
        return value != null ? Arrays.copyOf(value.secret, value.secret.length) : null;
    }

    public static UUID getSessionId(UUID playerId) {
        Entry value = playerId != null ? ENTRIES.get(playerId) : null;
        return value != null ? value.sessionId : null;
    }

    public static UUID getOrCreateSessionId(UUID playerId) {
        try (Material material = getOrCreateMaterial(playerId)) {
            return material.sessionId();
        }
    }

    public static void invalidate(UUID playerId) {
        Entry removed = ENTRIES.remove(playerId);
        if (removed != null) removed.clear();
    }

    public static void clear() {
        for (Entry value : ENTRIES.values()) value.clear();
        ENTRIES.clear();
    }

    public static int size() {
        return ENTRIES.size();
    }
}
