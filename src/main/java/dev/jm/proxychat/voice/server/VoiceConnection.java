package dev.jm.proxychat.voice.server;

import java.net.SocketAddress;
import java.util.UUID;

public final class VoiceConnection {

    public final UUID playerId;
    public final SocketAddress remoteAddress;
    private volatile long lastSeenMillis;

    public VoiceConnection(UUID playerId, SocketAddress remoteAddress) {
        this.playerId = playerId;
        this.remoteAddress = remoteAddress;
        this.lastSeenMillis = System.currentTimeMillis();
    }

    public void touch() {
        lastSeenMillis = System.currentTimeMillis();
    }

    public long lastSeenMillis() {
        return lastSeenMillis;
    }

    public boolean isIdle(long timeoutMillis) {
        return System.currentTimeMillis() - lastSeenMillis > timeoutMillis;
    }
}
