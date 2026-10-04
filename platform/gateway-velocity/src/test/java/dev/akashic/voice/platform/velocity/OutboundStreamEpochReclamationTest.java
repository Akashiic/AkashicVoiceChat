package dev.akashic.voice.platform.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class OutboundStreamEpochReclamationTest {
    @Test
    void retiredEntriesReleaseCapacityForLongLivedReceiver() {
        OutboundStreamEpochs tracker = new OutboundStreamEpochs();
        UUID source = new UUID(10L, 10L);
        UUID[] speakers = new UUID[OutboundStreamEpochs.CAPACITY];
        long highest = 0L;
        for (int i = 0; i < speakers.length; i++) {
            speakers[i] = new UUID(11L, i + 1L);
            highest = tracker.frame(speakers[i], source, false);
            assertTrue(highest > 0L);
        }
        assertEquals(OutboundStreamEpochs.CAPACITY, tracker.size());
        for (UUID speaker : speakers) assertTrue(tracker.retire(speaker, source, false) > 0L);
        assertEquals(0, tracker.size(), "retired outbound tombstones consumed receiver capacity");
        long replacement = tracker.frame(new UUID(12L, 1L), source, false);
        assertTrue(replacement > highest, "epoch sequence rewound after reclamation");
    }

    @Test
    void activeCapacityRejectsOverflowUntilOneStreamIsRetired() {
        OutboundStreamEpochs tracker = new OutboundStreamEpochs();
        UUID source = new UUID(13L, 1L);
        UUID first = null;
        long highest = 0L;
        for (int i = 0; i < OutboundStreamEpochs.CAPACITY; i++) {
            UUID sender = new UUID(13L, i + 2L);
            if (first == null) first = sender;
            highest = tracker.frame(sender, source, false);
            assertTrue(highest > 0L);
        }
        assertEquals(0L, tracker.frame(new UUID(14L, 1L), source, false));
        assertTrue(tracker.retire(first, source, false) > 0L);
        long admitted = tracker.frame(new UUID(14L, 1L), source, false);
        assertTrue(admitted > highest);
        assertEquals(OutboundStreamEpochs.CAPACITY, tracker.size());
    }

    @Test
    void groupMismatchDoesNotRetireAnotherRoute() {
        OutboundStreamEpochs tracker = new OutboundStreamEpochs();
        UUID speaker = new UUID(15L, 1L);
        UUID source = new UUID(15L, 2L);
        long epoch = tracker.frame(speaker, source, true);
        assertEquals(0L, tracker.retire(speaker, source, false));
        assertEquals(1, tracker.size());
        assertEquals(epoch, tracker.frame(speaker, source, true));
        assertEquals(epoch, tracker.retire(speaker, source, true));
        assertEquals(0, tracker.size());
    }

    @Test
    void sourceSpecificRetireOnlyReclaimsMatchingIncarnation() {
        OutboundStreamEpochs tracker = new OutboundStreamEpochs();
        UUID speaker = new UUID(20L, 1L);
        UUID source = new UUID(20L, 2L);
        long epoch = tracker.frame(speaker, source, false);
        assertTrue(epoch > 0L);
        assertEquals(0L, tracker.retire(speaker, new UUID(20L, 3L), false));
        assertEquals(1, tracker.size());
        assertEquals(epoch, tracker.frame(speaker, source, false));
        assertEquals(epoch, tracker.retire(speaker, source, false));
        assertEquals(0, tracker.size());
        assertEquals(0L, tracker.retire(speaker, source, false));
    }

    @Test
    void hardRetireReclaimsAndReentryGetsNewEpoch() {
        OutboundStreamEpochs tracker = new OutboundStreamEpochs();
        UUID speaker = new UUID(30L, 1L);
        UUID source = new UUID(30L, 2L);
        long first = tracker.frame(speaker, source, true);
        assertEquals(first, tracker.retire(speaker));
        assertEquals(0, tracker.size());
        long second = tracker.frame(speaker, source, true);
        assertTrue(second > first);
    }

    @Test
    void reclaimDoesNotEvictUnretiredStreams() {
        OutboundStreamEpochs tracker = new OutboundStreamEpochs();
        UUID source = new UUID(40L, 1L);
        UUID survivor = new UUID(40L, 2L);
        long survivorEpoch = tracker.frame(survivor, source, false);
        for (int i = 0; i < 64; i++) {
            UUID transientSpeaker = new UUID(41L, i + 1L);
            long epoch = tracker.frame(transientSpeaker, source, false);
            assertTrue(epoch > 0L);
            assertEquals(epoch, tracker.retire(transientSpeaker, source, false));
        }
        assertEquals(1, tracker.size());
        assertEquals(survivorEpoch, tracker.frame(survivor, source, false));
    }
}
