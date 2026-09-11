package dev.akashic.voice.voice.server;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

final class HandshakeRateLimiter {

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

    boolean allow(SocketAddress remote) {
        if (!(remote instanceof InetSocketAddress)) return false;
        InetAddress address = ((InetSocketAddress) remote).getAddress();
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

    int bucketCount() {
        return buckets.size();
    }

    private void refillGlobal(long now) {
        double elapsed = (now - globalLastNanos) / 1_000_000_000.0;
        if (elapsed > 0.0) {
            globalTokens = Math.min(GLOBAL_BURST, globalTokens + elapsed * GLOBAL_RATE);
            globalLastNanos = now;
        }
    }

    private void prune(long now) {
        Iterator<Map.Entry<InetAddress, Bucket>> it = buckets.entrySet()
            .iterator();
        while (it.hasNext()) {
            Map.Entry<InetAddress, Bucket> entry = it.next();
            if (now - entry.getValue().lastUseNanos > BUCKET_IDLE_NANOS) it.remove();
        }
    }

    private static final class Bucket {

        double tokens = PER_IP_BURST;
        long lastRefillNanos;
        long lastUseNanos;

        Bucket(long now) {
            lastRefillNanos = now;
            lastUseNanos = now;
        }

        void refill(long now) {
            double elapsed = (now - lastRefillNanos) / 1_000_000_000.0;
            if (elapsed > 0.0) {
                tokens = Math.min(PER_IP_BURST, tokens + elapsed * PER_IP_RATE);
                lastRefillNanos = now;
            }
        }
    }
}
