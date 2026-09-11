package dev.akashic.voice.voice.server;

import java.net.SocketException;

import dev.akashic.voice.AkashicVoice;

public final class VoiceServerHolder {

    private static volatile VoiceServer instance;

    private VoiceServerHolder() {}

    public static synchronized void start(int port) {
        if (instance != null) return;
        VoiceServer server = new VoiceServer(port);
        try {
            server.start();
        } catch (SocketException e) {
            AkashicVoice.LOG.error("Failed to bind voice server on UDP {}", port, e);
            return;
        }
        instance = server;
    }

    public static synchronized void stop() {
        if (instance == null) return;
        instance.stop();
        instance = null;
    }

    public static VoiceServer get() {
        return instance;
    }
}
