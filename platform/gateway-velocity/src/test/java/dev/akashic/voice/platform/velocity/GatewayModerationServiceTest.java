package dev.akashic.voice.platform.velocity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class GatewayModerationServiceTest {
    @TempDir Path directory;

    @Test
    void missingStoreStartsEmptyAndRoundTrips() throws Exception {
        Path file = directory.resolve("moderation.dat");
        GatewayModerationService service = new GatewayModerationService(new GatewayModerationStore(file));
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertEquals(0, service.mutedCount());
        assertTrue(service.mute(a));
        assertFalse(service.mute(a));
        assertTrue(service.mute(b));
        assertEquals(Set.of(a, b), service.snapshot());
        GatewayModerationService restored = new GatewayModerationService(new GatewayModerationStore(file));
        assertEquals(Set.of(a, b), restored.snapshot());
        assertTrue(restored.unmute(a));
        assertFalse(restored.unmute(a));
        assertEquals(Set.of(b), new GatewayModerationStore(file).load());
    }

    @Test
    void failedAtomicMoveDoesNotPublishOrReplaceOldState() throws Exception {
        Path file = directory.resolve("moderation.dat");
        UUID existing = UUID.randomUUID(), attempted = UUID.randomUUID();
        GatewayModerationStore normal = new GatewayModerationStore(file);
        normal.save(Set.of(existing));
        byte[] before = Files.readAllBytes(file);
        GatewayModerationStore failing = new GatewayModerationStore(file,
            (source, destination) -> { throw new IOException("synthetic atomic move failure"); });
        GatewayModerationService service = new GatewayModerationService(failing);
        assertThrows(IOException.class, () -> service.mute(attempted));
        assertEquals(Set.of(existing), service.snapshot());
        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals(Set.of(existing), normal.load());
    }

    @Test
    void corruptionFailsClosedInsteadOfClearingMuteSet() throws Exception {
        Path file = directory.resolve("moderation.dat");
        GatewayModerationStore store = new GatewayModerationStore(file);
        store.save(Set.of(UUID.randomUUID()));
        byte[] corrupt = Files.readAllBytes(file);
        corrupt[12] ^= 0x40;
        Files.write(file, corrupt);
        IOException failure = assertThrows(IOException.class, store::load);
        assertTrue(failure.getMessage().contains("checksum"));
        assertArrayEquals(corrupt, Files.readAllBytes(file));
    }
}
