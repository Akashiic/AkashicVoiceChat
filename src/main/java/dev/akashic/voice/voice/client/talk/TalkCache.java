package dev.akashic.voice.voice.client.talk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class TalkCache {

    private static final long SPEAKING_WINDOW_NANOS = 120_000_000L;
    private static final long PRUNE_INTERVAL_NANOS = 1_000_000_000L;
    public static final int MAX_ENTRIES = 512;

    private static final Map<UUID, Long> LAST_FRAME = new ConcurrentHashMap<>();
    private static final Object ADMISSION_LOCK = new Object();
    private static final AtomicLong NEXT_PRUNE_NANOS = new AtomicLong(System.nanoTime() + PRUNE_INTERVAL_NANOS);

    private TalkCache() {}

    public static void markSpeaking(UUID playerId) {
        if (playerId == null) return;
        long now = System.nanoTime();
        Long previous = LAST_FRAME.get(playerId);
        if (previous != null) {
            LAST_FRAME.put(playerId, now);
            pruneIfDue(now);
            return;
        }
        synchronized (ADMISSION_LOCK) {
            previous = LAST_FRAME.get(playerId);
            if (previous != null) {
                LAST_FRAME.put(playerId, now);
                return;
            }
            pruneIfDue(now);
            if (LAST_FRAME.size() >= MAX_ENTRIES) pruneExpired(now);
            if (LAST_FRAME.size() >= MAX_ENTRIES) return;
            LAST_FRAME.put(playerId, now);
        }
    }

    public static boolean isSpeaking(UUID playerId) {
        if (playerId == null) return false;
        Long last = LAST_FRAME.get(playerId);
        if (last == null) return false;
        long now = System.nanoTime();
        if (now - last <= SPEAKING_WINDOW_NANOS) return true;
        LAST_FRAME.remove(playerId, last);
        return false;
    }

    public static void clear() {
        LAST_FRAME.clear();
        NEXT_PRUNE_NANOS.set(System.nanoTime() + PRUNE_INTERVAL_NANOS);
    }

    private static void pruneIfDue(long now) {
        long deadline = NEXT_PRUNE_NANOS.get();
        if (now - deadline < 0L) return;
        if (!NEXT_PRUNE_NANOS.compareAndSet(deadline, now + PRUNE_INTERVAL_NANOS)) return;
        pruneExpired(now);
    }

    private static void pruneExpired(long now) {
        for (Map.Entry<UUID, Long> entry : LAST_FRAME.entrySet()) {
            Long stamp = entry.getValue();
            if (stamp != null && now - stamp > SPEAKING_WINDOW_NANOS) {
                LAST_FRAME.remove(entry.getKey(), stamp);
            }
        }
    }
}
