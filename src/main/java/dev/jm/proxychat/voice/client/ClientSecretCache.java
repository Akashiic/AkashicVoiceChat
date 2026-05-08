package dev.jm.proxychat.voice.client;

import java.util.UUID;
import java.util.function.BiConsumer;

public final class ClientSecretCache {

    private static volatile UUID secret;
    private static volatile int udpPort;
    private static volatile BiConsumer<UUID, Integer> onAvailable;

    private ClientSecretCache() {}

    public static synchronized void store(UUID secret, int udpPort) {
        ClientSecretCache.secret = secret;
        ClientSecretCache.udpPort = udpPort;
        BiConsumer<UUID, Integer> cb = onAvailable;
        if (cb != null) {
            cb.accept(secret, udpPort);
        }
    }

    public static synchronized void clear() {
        secret = null;
        udpPort = 0;
    }

    public static void setOnAvailable(BiConsumer<UUID, Integer> callback) {
        onAvailable = callback;
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
