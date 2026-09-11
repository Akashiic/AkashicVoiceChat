package dev.akashic.voice.voice.client;

import java.util.UUID;

public final class ClientSecretCache {

    private static volatile UUID playerId;
    private static volatile UUID secret;
    private static volatile int udpPort;

    private ClientSecretCache() {}

    public static synchronized void store(UUID playerId, UUID secret, int udpPort) {
        ClientSecretCache.playerId = playerId;
        ClientSecretCache.secret = secret;
        ClientSecretCache.udpPort = udpPort;
    }

    public static synchronized void clear() {
        playerId = null;
        secret = null;
        udpPort = 0;
    }

    public static UUID getPlayerId() {
        return playerId;
    }

    public static UUID getSecret() {
        return secret;
    }

    public static int getUdpPort() {
        return udpPort;
    }

    public static boolean hasSecret() {
        return playerId != null && secret != null;
    }
}
