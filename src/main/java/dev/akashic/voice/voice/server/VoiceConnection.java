package dev.akashic.voice.voice.server;

import java.net.SocketAddress;
import java.util.UUID;

import dev.akashic.voice.voice.common.crypto.SessionCrypto;

public final class VoiceConnection implements AutoCloseable {

    public final UUID playerId;
    public final SocketAddress remoteAddress;
    final SessionCrypto crypto;
    private final MediaRateLimiter inboundRateLimiter = new MediaRateLimiter();
    private volatile long lastSeenNanos;

    public VoiceConnection(UUID playerId, SocketAddress remoteAddress, SessionCrypto crypto) {
        if (crypto == null) throw new IllegalArgumentException("crypto");
        this.playerId = playerId;
        this.remoteAddress = remoteAddress;
        this.crypto = crypto;
        this.lastSeenNanos = System.nanoTime();
    }

    public void touch() {
        lastSeenNanos = System.nanoTime();
    }

    boolean allowInboundDatagram(int bytes) {
        return inboundRateLimiter.allow(bytes);
    }

    public boolean isIdle(long timeoutMillis) {
        long timeoutNanos = timeoutMillis * 1_000_000L;
        return System.nanoTime() - lastSeenNanos > timeoutNanos;
    }

    @Override
    public void close() {
        crypto.close();
    }
}
