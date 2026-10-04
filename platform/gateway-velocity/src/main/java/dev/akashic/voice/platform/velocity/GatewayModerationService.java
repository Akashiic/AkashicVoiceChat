package dev.akashic.voice.platform.velocity;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Persist-before-publish moderation state; audio-thread reads are lock-free immutable snapshots. */
final class GatewayModerationService {
    private final GatewayModerationStore store;
    private volatile Set<UUID> muted;
    private long revision;

    GatewayModerationService(GatewayModerationStore store) throws IOException {
        this.store = Objects.requireNonNull(store, "store");
        this.muted = Set.copyOf(store.load());
    }

    boolean isMuted(UUID playerId) {
        return playerId != null && muted.contains(playerId);
    }

    int mutedCount() {
        return muted.size();
    }

    long revision() {
        synchronized (this) { return revision; }
    }

    Set<UUID> snapshot() {
        return muted;
    }

    synchronized boolean mute(UUID playerId) throws IOException {
        Objects.requireNonNull(playerId, "playerId");
        if (muted.contains(playerId)) return false;
        if (muted.size() >= GatewayModerationStore.MAX_ENTRIES) {
            throw new IOException("Moderation entry limit reached");
        }
        LinkedHashSet<UUID> next = new LinkedHashSet<>(muted);
        next.add(playerId);
        Set<UUID> published = Set.copyOf(next);
        store.save(published);
        muted = published;
        revision++;
        return true;
    }

    synchronized boolean unmute(UUID playerId) throws IOException {
        Objects.requireNonNull(playerId, "playerId");
        if (!muted.contains(playerId)) return false;
        LinkedHashSet<UUID> next = new LinkedHashSet<>(muted);
        next.remove(playerId);
        Set<UUID> published = Set.copyOf(next);
        store.save(published);
        muted = published;
        revision++;
        return true;
    }
}
