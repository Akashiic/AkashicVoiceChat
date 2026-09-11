package dev.akashic.voice.voice.client;

import java.io.IOException;
import java.net.InetAddress;
import java.util.UUID;

import dev.akashic.voice.AkashicVoice;

public final class VoiceClientHolder {

    private static volatile VoiceClient instance;

    private VoiceClientHolder() {}

    public static synchronized boolean connect(UUID playerId, byte[] masterSecret, InetAddress host, int udpPort) {
        stop();
        VoiceClient client = new VoiceClient(playerId, masterSecret, host, udpPort);
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
        if (instance == null) return;
        instance.stop();
        instance = null;
    }

    public static VoiceClient get() {
        return instance;
    }

    public static synchronized void notifyMicChanged() {
        VoiceClient current = instance;
        if (current != null) current.restartMicCapture();
    }
}
