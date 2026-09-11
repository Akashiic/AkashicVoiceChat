package dev.akashic.voice.voice.server;

final class MediaRateLimiter {

    static final double PACKETS_PER_SECOND = 80.0;
    static final double PACKET_BURST = 120.0;
    static final double BYTES_PER_SECOND = 96.0 * 1024.0;
    static final double BYTE_BURST = 192.0 * 1024.0;

    private double packetTokens = PACKET_BURST;
    private double byteTokens = BYTE_BURST;
    private long lastRefillNanos = System.nanoTime();

    synchronized boolean allow(int bytes) {
        return allow(bytes, System.nanoTime());
    }

    synchronized boolean allow(int bytes, long nowNanos) {
        if (bytes <= 0 || bytes > 1500) return false;
        refill(nowNanos);
        if (packetTokens < 1.0 || byteTokens < bytes) return false;
        packetTokens -= 1.0;
        byteTokens -= bytes;
        return true;
    }

    private void refill(long nowNanos) {
        long elapsedNanos = nowNanos - lastRefillNanos;
        if (elapsedNanos <= 0L) return;
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        packetTokens = Math.min(PACKET_BURST, packetTokens + elapsedSeconds * PACKETS_PER_SECOND);
        byteTokens = Math.min(BYTE_BURST, byteTokens + elapsedSeconds * BYTES_PER_SECOND);
        lastRefillNanos = nowNanos;
    }
}
