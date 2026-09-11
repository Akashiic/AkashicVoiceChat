package dev.akashic.voice.voice.server;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.akashic.voice.voice.common.crypto.SessionKeys;

public final class SecretRegistry {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<UUID, byte[]> SECRETS = new ConcurrentHashMap<>();

    private SecretRegistry() {}

    public static byte[] getOrCreate(UUID playerId) {
        byte[] existing = SECRETS.get(playerId);
        if (existing != null) return copy(existing);

        byte[] generated = new byte[SessionKeys.MASTER_SECRET_BYTES];
        RANDOM.nextBytes(generated);
        byte[] previous = SECRETS.putIfAbsent(playerId, generated);
        if (previous != null) {
            Arrays.fill(generated, (byte) 0);
            return copy(previous);
        }
        return copy(generated);
    }

    public static byte[] get(UUID playerId) {
        byte[] value = SECRETS.get(playerId);
        return value != null ? copy(value) : null;
    }

    public static void invalidate(UUID playerId) {
        byte[] removed = SECRETS.remove(playerId);
        if (removed != null) Arrays.fill(removed, (byte) 0);
    }

    public static void clear() {
        for (byte[] secret : SECRETS.values()) {
            Arrays.fill(secret, (byte) 0);
        }
        SECRETS.clear();
    }

    public static int size() {
        return SECRETS.size();
    }

    private static byte[] copy(byte[] value) {
        return Arrays.copyOf(value, value.length);
    }
}
