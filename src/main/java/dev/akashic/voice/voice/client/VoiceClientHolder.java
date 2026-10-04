package dev.akashic.voice.voice.client;

import java.io.IOException;
import java.net.InetAddress;
import java.util.UUID;

import dev.akashic.voice.AkashicVoice;

public final class VoiceClientHolder {

    private static volatile VoiceClient instance;

    private VoiceClientHolder() {}

    public static synchronized boolean connect(UUID playerId, byte[] masterSecret, InetAddress host, int udpPort) {
        return connect(playerId, masterSecret, host, udpPort, null);
    }

    public static synchronized boolean connect(UUID playerId, UUID sessionId, byte[] masterSecret, InetAddress host,
        int udpPort) {
        return connect(playerId, masterSecret, host, udpPort, sessionId);
    }

    public static synchronized boolean connect(UUID playerId, byte[] masterSecret, InetAddress host, int udpPort,
        UUID sessionId) {
        stop();
        VoiceClient client = new VoiceClient(playerId, masterSecret, host, udpPort, sessionId);
        try {
            client.start();
            instance = client;
            return true;
        } catch (IOException e) {
            client.stop();
            AkashicVoice.LOG.warn("Failed to start VoiceClient", e);
            return false;
        }
    }

    public static synchronized void stop() {
        MicGate.cancelTouchPtt();
        if (instance == null) return;
        instance.stop();
        instance = null;
    }

    public static VoiceClient get() {
        return instance;
    }

    public static VoiceClientStatusSnapshot statusSnapshot() {
        VoiceClient current = instance;
        return current != null ? current.statusSnapshot() : VoiceClientStatusSnapshot.disconnected();
    }

    public static synchronized void notifyMicChanged() {
        VoiceClient current = instance;
        if (current != null) current.restartMicCapture();
    }
}
