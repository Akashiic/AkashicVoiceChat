package dev.akashic.voice.platform.velocity;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

final class GatewayHandshakeRateLimiter {
    private static final double GLOBAL_RATE = 128.0;
    private static final double GLOBAL_BURST = 256.0;
    private static final double PER_IP_RATE = 10.0;
    private static final double PER_IP_BURST = 20.0;
    private static final int MAX_IP_BUCKETS = 4096;
    private static final long BUCKET_IDLE_NANOS = 60_000_000_000L;
    private static final long PRUNE_INTERVAL_NANOS = 10_000_000_000L;

    private final Map<InetAddress, Bucket> buckets = new HashMap<>();
    private double globalTokens = GLOBAL_BURST;
    private long globalLastNanos = System.nanoTime();
    private long lastPruneNanos = globalLastNanos;

    synchronized boolean allow(SocketAddress remote) {
        if (!(remote instanceof InetSocketAddress inet)) return false;
        InetAddress address = inet.getAddress();
        if (address == null) return false;
        long now = System.nanoTime();
        refillGlobal(now);
        if (globalTokens < 1.0) return false;
        if (now - lastPruneNanos >= PRUNE_INTERVAL_NANOS || buckets.size() >= MAX_IP_BUCKETS) {
            prune(now);
            lastPruneNanos = now;
        }
        Bucket bucket = buckets.get(address);
        if (bucket == null) {
            if (buckets.size() >= MAX_IP_BUCKETS) return false;
            bucket = new Bucket(now);
            buckets.put(address, bucket);
        }
        bucket.refill(now);
        if (bucket.tokens < 1.0) return false;
        globalTokens -= 1.0;
        bucket.tokens -= 1.0;
        bucket.lastUseNanos = now;
        return true;
    }

    private void refillGlobal(long now) {
        double elapsed = (now - globalLastNanos) / 1_000_000_000.0;
        if (elapsed <= 0.0) return;
        globalTokens = Math.min(GLOBAL_BURST, globalTokens + elapsed * GLOBAL_RATE);
        globalLastNanos = now;
    }

    private void prune(long now) {
        Iterator<Map.Entry<InetAddress, Bucket>> iterator = buckets.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue().lastUseNanos > BUCKET_IDLE_NANOS) iterator.remove();
        }
    }

    private static final class Bucket {
        private double tokens = PER_IP_BURST;
        private long lastRefillNanos;
        private long lastUseNanos;

        private Bucket(long now) {
            lastRefillNanos = now;
            lastUseNanos = now;
        }

        private void refill(long now) {
            double elapsed = (now - lastRefillNanos) / 1_000_000_000.0;
            if (elapsed <= 0.0) return;
            tokens = Math.min(PER_IP_BURST, tokens + elapsed * PER_IP_RATE);
            lastRefillNanos = now;
        }
    }
}
