package dev.jm.proxychat.voice.client;

import java.util.UUID;

public final class ClientSecretCache {

    private static volatile UUID secret;
    private static volatile int udpPort;

    private ClientSecretCache() {}

    public static synchronized void store(UUID secret, int udpPort) {
        ClientSecretCache.secret = secret;
        ClientSecretCache.udpPort = udpPort;
    }

    public static synchronized void clear() {
        secret = null;
        udpPort = 0;
    }

    public static UUID getSecret() {
        return secret;
    }

    public static int getUdpPort() {
        return udpPort;
    }

    public static boolean hasSecret() {
        return secret != null;
    }
}
