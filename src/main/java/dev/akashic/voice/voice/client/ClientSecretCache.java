package dev.akashic.voice.voice.client;

import java.util.Arrays;
import java.util.UUID;

import dev.akashic.voice.voice.common.crypto.SessionKeys;

public final class ClientSecretCache {

    private static volatile UUID playerId;
    private static volatile byte[] masterSecret;
    private static volatile int udpPort;

    private ClientSecretCache() {}

    public static synchronized void store(UUID playerId, byte[] secret, int udpPort) {
        if (secret == null || secret.length != SessionKeys.MASTER_SECRET_BYTES) {
            throw new IllegalArgumentException("invalid master secret");
        }
        clearSecret();
        ClientSecretCache.playerId = playerId;
        ClientSecretCache.masterSecret = Arrays.copyOf(secret, secret.length);
        ClientSecretCache.udpPort = udpPort;
    }

    public static synchronized void clear() {
        clearSecret();
        playerId = null;
        udpPort = 0;
    }

    public static UUID getPlayerId() {
        return playerId;
    }

    public static synchronized byte[] getMasterSecret() {
        return masterSecret != null ? Arrays.copyOf(masterSecret, masterSecret.length) : null;
    }

    public static int getUdpPort() {
        return udpPort;
    }

    public static boolean hasSecret() {
        return playerId != null && masterSecret != null;
    }

    private static void clearSecret() {
        if (masterSecret != null) Arrays.fill(masterSecret, (byte) 0);
        masterSecret = null;
    }
}
