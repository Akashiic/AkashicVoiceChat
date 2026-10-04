package dev.akashic.voice.voice.client.talk;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded/expiring talking-indicator cache contract. No sockets, audio, or Minecraft window. */
public final class TalkCacheBoundedSmoke {
    private TalkCacheBoundedSmoke() {}

    public static void main(String[] args) throws Exception {
        activeSpeakerIsVisible();
        staleLookupEvictsEntry();
        repeatedSpeakerDoesNotGrow();
        floodRemainsBounded();
        staleCapacityIsRecoverable();
        concurrentFloodRemainsBounded();
        periodicPruneCompactsStaleEntries();
        clearReleasesEverything();
        System.out.println("TALK_CACHE_BOUNDED_OK scenarios=8");
    }

    private static void activeSpeakerIsVisible() throws Exception {
        TalkCache.clear();
        UUID id = new UUID(1L, 1L);
        TalkCache.markSpeaking(id);
        check(TalkCache.isSpeaking(id), "fresh speaker not visible");
        check(entries().size() == 1, "fresh speaker not cached once");
    }

    private static void staleLookupEvictsEntry() throws Exception {
        TalkCache.clear();
        UUID id = new UUID(2L, 2L);
        TalkCache.markSpeaking(id);
        Thread.sleep(320L);
        check(!TalkCache.isSpeaking(id), "stale speaker still visible");
        check(!entries().containsKey(id), "stale lookup retained useless entry");
    }

    private static void repeatedSpeakerDoesNotGrow() throws Exception {
        TalkCache.clear();
        UUID id = new UUID(3L, 3L);
        for (int i = 0; i < 10_000; i++) TalkCache.markSpeaking(id);
        check(entries().size() == 1, "repeated speaker grew cache");
        check(TalkCache.isSpeaking(id), "repeated speaker lost freshness");
    }

    private static void floodRemainsBounded() throws Exception {
        TalkCache.clear();
        for (int i = 0; i < 10_000; i++) {
            TalkCache.markSpeaking(new UUID(4L, i + 1L));
        }
        int size = entries().size();
        check(size <= 512, "speaker flood grew cache to " + size);
    }

    private static void staleCapacityIsRecoverable() throws Exception {
        TalkCache.clear();
        for (int i = 0; i < 512; i++) TalkCache.markSpeaking(new UUID(6L, i + 1L));
        check(entries().size() == 512, "fixture did not reach cap");
        Thread.sleep(320L);
        UUID replacement = new UUID(6L, 10_000L);
        TalkCache.markSpeaking(replacement);
        check(TalkCache.isSpeaking(replacement), "stale capacity rejected fresh speaker");
        check(entries().size() <= 512, "capacity recovery exceeded cap");
    }

    private static void concurrentFloodRemainsBounded() throws Exception {
        TalkCache.clear();
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread[] threads = new Thread[8];
        for (int t = 0; t < threads.length; t++) {
            final int owner = t;
            threads[t] = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 1_000; i++) TalkCache.markSpeaking(new UUID(100L + owner, i + 1L));
                } catch (Throwable error) {
                    failure.compareAndSet(null, error);
                }
            }, "AkashicVoice-QA-TalkCache-" + t);
            threads[t].start();
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join(10_000L);
            check(!thread.isAlive(), "concurrent writer stuck");
        }
        if (failure.get() != null) throw new AssertionError("concurrent writer failed", failure.get());
        check(entries().size() <= 512, "concurrent flood exceeded cap: " + entries().size());
    }

    private static void periodicPruneCompactsStaleEntries() throws Exception {
        TalkCache.clear();
        for (int i = 0; i < 64; i++) TalkCache.markSpeaking(new UUID(200L, i + 1L));
        check(entries().size() == 64, "periodic fixture size wrong");
        Thread.sleep(1_050L);
        UUID survivor = new UUID(200L, 1L);
        TalkCache.markSpeaking(survivor);
        check(TalkCache.isSpeaking(survivor), "periodic prune removed fresh speaker");
        check(entries().size() == 1, "periodic prune retained stale entries: " + entries().size());
    }

    private static void clearReleasesEverything() throws Exception {
        TalkCache.clear();
        for (int i = 0; i < 32; i++) TalkCache.markSpeaking(new UUID(5L, i + 1L));
        check(!entries().isEmpty(), "fixture did not populate cache");
        TalkCache.clear();
        check(entries().isEmpty(), "clear retained speaker entries");
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Long> entries() throws Exception {
        Field field = TalkCache.class.getDeclaredField("LAST_FRAME");
        field.setAccessible(true);
        return (Map<UUID, Long>) field.get(null);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
