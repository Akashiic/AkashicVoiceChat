package dev.akashic.voice.voice.server.groups;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Bounded, ephemeral invitation grants for the legacy Forge group authority. */
final class LegacyGroupInviteBook {

    static final long DEFAULT_TTL_NANOS = 300_000_000_000L;
    static final int DEFAULT_MAX_PER_GROUP = 64;
    static final int DEFAULT_MAX_PER_RECIPIENT = 8;
    static final int DEFAULT_MAX_PENDING = 4096;

    private static final class Invite {

        final UUID inviter;
        final long created;

        Invite(UUID inviter, long created) {
            this.inviter = inviter;
            this.created = created;
        }
    }

    private final Map<UUID, LinkedHashMap<UUID, Invite>> byGroup = new HashMap<>();
    private final Map<UUID, Integer> byRecipient = new HashMap<>();
    private final LongSupplier clock;
    private final long ttlNanos;
    private final int maxPerGroup;
    private final int maxPerRecipient;
    private final int maxPending;
    private int pending;

    LegacyGroupInviteBook() {
        this(
            System::nanoTime,
            DEFAULT_TTL_NANOS,
            DEFAULT_MAX_PER_GROUP,
            DEFAULT_MAX_PER_RECIPIENT,
            DEFAULT_MAX_PENDING);
    }

    LegacyGroupInviteBook(LongSupplier clock, long ttlNanos, int maxPerGroup, int maxPerRecipient, int maxPending) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (ttlNanos <= 0 || maxPerGroup < 1
            || maxPerRecipient < 1
            || maxPending < 1
            || maxPerGroup > maxPending
            || maxPerRecipient > maxPending) {
            throw new IllegalArgumentException("invalid legacy invite limits");
        }
        this.ttlNanos = ttlNanos;
        this.maxPerGroup = maxPerGroup;
        this.maxPerRecipient = maxPerRecipient;
        this.maxPending = maxPending;
    }

    synchronized boolean offer(UUID groupId, UUID inviterId, UUID targetId) {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(inviterId, "inviterId");
        Objects.requireNonNull(targetId, "targetId");
        if (inviterId.equals(targetId)) return false;
        long now = clock.getAsLong();
        prune(now);
        LinkedHashMap<UUID, Invite> group = byGroup.get(groupId);
        if (group != null && (group.containsKey(targetId) || group.size() >= maxPerGroup)) return false;
        if (pending >= maxPending || byRecipient.getOrDefault(targetId, 0) >= maxPerRecipient) return false;
        if (group == null) {
            group = new LinkedHashMap<>();
            byGroup.put(groupId, group);
        }
        group.put(targetId, new Invite(inviterId, now));
        byRecipient.put(targetId, byRecipient.getOrDefault(targetId, 0) + 1);
        pending++;
        return true;
    }

    synchronized UUID consume(UUID groupId, UUID targetId) {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(targetId, "targetId");
        prune(clock.getAsLong());
        LinkedHashMap<UUID, Invite> group = byGroup.get(groupId);
        if (group == null) return null;
        Invite invite = group.remove(targetId);
        if (invite == null) return null;
        forgetRecipient(targetId);
        if (group.isEmpty()) byGroup.remove(groupId);
        return invite.inviter;
    }

    synchronized void removePlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Iterator<Map.Entry<UUID, LinkedHashMap<UUID, Invite>>> groups = byGroup.entrySet()
            .iterator();
        while (groups.hasNext()) {
            LinkedHashMap<UUID, Invite> invites = groups.next()
                .getValue();
            Iterator<Map.Entry<UUID, Invite>> entries = invites.entrySet()
                .iterator();
            while (entries.hasNext()) {
                Map.Entry<UUID, Invite> entry = entries.next();
                if (!entry.getKey()
                    .equals(playerId) && !entry.getValue().inviter.equals(playerId)) continue;
                forgetRecipient(entry.getKey());
                entries.remove();
            }
            if (invites.isEmpty()) groups.remove();
        }
    }

    synchronized void removeGroup(UUID groupId) {
        Objects.requireNonNull(groupId, "groupId");
        LinkedHashMap<UUID, Invite> removed = byGroup.remove(groupId);
        if (removed == null) return;
        for (UUID targetId : removed.keySet()) forgetRecipient(targetId);
    }

    synchronized int expire() {
        return prune(clock.getAsLong());
    }

    synchronized int pendingCount() {
        prune(clock.getAsLong());
        return pending;
    }

    private int prune(long now) {
        int before = pending;
        Iterator<Map.Entry<UUID, LinkedHashMap<UUID, Invite>>> groups = byGroup.entrySet()
            .iterator();
        while (groups.hasNext()) {
            LinkedHashMap<UUID, Invite> invites = groups.next()
                .getValue();
            Iterator<Map.Entry<UUID, Invite>> entries = invites.entrySet()
                .iterator();
            while (entries.hasNext()) {
                Map.Entry<UUID, Invite> entry = entries.next();
                if (now - entry.getValue().created < ttlNanos) continue;
                forgetRecipient(entry.getKey());
                entries.remove();
            }
            if (invites.isEmpty()) groups.remove();
        }
        return before - pending;
    }

    private void forgetRecipient(UUID targetId) {
        Integer count = byRecipient.get(targetId);
        if (count == null || count <= 0 || pending <= 0) {
            throw new IllegalStateException("legacy invite counters corrupted");
        }
        if (count == 1) byRecipient.remove(targetId);
        else byRecipient.put(targetId, count - 1);
        pending--;
    }
}
