package dev.jm.proxychat.voice.client;

import java.io.IOException;
import java.net.InetAddress;
import java.util.UUID;

import dev.jm.proxychat.ProxyChat;

public final class VoiceClientHolder {

    private static volatile VoiceClient instance;

    private VoiceClientHolder() {}

    public static synchronized void connect(UUID playerId, UUID secret, InetAddress host, int udpPort,
        PositionSupplier positionSupplier) {
        stop();
        VoiceClient client = new VoiceClient(playerId, secret, host, udpPort, positionSupplier);
        try {
            client.start();
            instance = client;
        } catch (IOException e) {
            ProxyChat.LOG.warn("Failed to start VoiceClient", e);
        }
    }

    public static synchronized void stop() {
        if (instance == null) return;
        instance.stop();
        instance = null;
    }

    public static VoiceClient get() {
        return instance;
    }

    public static synchronized void notifyMicChanged() {
        VoiceClient v = instance;
        if (v != null) v.restartMicCapture();
    }
}
