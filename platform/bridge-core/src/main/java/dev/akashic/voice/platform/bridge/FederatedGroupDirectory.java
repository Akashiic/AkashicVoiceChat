package dev.akashic.voice.platform.bridge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Authoritative, bounded membership and ephemeral invitation state. No disk/network I/O. */
public final class FederatedGroupDirectory {
    public enum InviteResponse {
        NO_INVITE, DECLINED, JOINED, GROUP_MISSING, ALREADY_GROUPED, GROUP_FULL
    }

    /** Explicit policy choices for this candidate, not pre-existing product requirements. */
    public record Limits(int maxGroups, int maxMembers, int maxInvitesPerGroup,
                         int maxInvitesPerRecipient, int maxPendingInvites, long inviteTtlNanos) {
        public Limits {
            if (maxGroups < 1 || maxGroups > 512 || maxMembers < 1 || maxMembers > 64
                || maxInvitesPerGroup < 1 || maxInvitesPerGroup > 64
                || maxInvitesPerRecipient < 1 || maxInvitesPerRecipient > 8
                || maxPendingInvites < 1 || maxPendingInvites > 4096
                || inviteTtlNanos <= 0 || inviteTtlNanos > 604_800_000_000_000L) {
                throw new IllegalArgumentException("invalid federated group limits");
            }
        }
    }

    public static final Limits DEFAULT_LIMITS = new Limits(512, 64, 64, 8, 4096, 300_000_000_000L);
    private record PendingInvite(UUID inviterId, long createdNanos) {}

    private final Map<UUID, MutableGroup> groups = new HashMap<>();
    private final Map<UUID, UUID> groupByMember = new HashMap<>();
    private final Map<UUID, LinkedHashMap<UUID, PendingInvite>> pendingInvites = new HashMap<>();
    private final Map<UUID, Integer> pendingByRecipient = new HashMap<>();
    private final Supplier<UUID> idSupplier;
    private final LongSupplier clock;
    private final Limits limits;
    private int pendingCount;

    public FederatedGroupDirectory() {
        this(UUID::randomUUID, System::nanoTime, DEFAULT_LIMITS);
    }

    public FederatedGroupDirectory(Limits limits) {
        this(UUID::randomUUID, System::nanoTime, limits);
    }

    FederatedGroupDirectory(Supplier<UUID> idSupplier) {
        this(idSupplier, System::nanoTime, DEFAULT_LIMITS);
    }

    FederatedGroupDirectory(Supplier<UUID> idSupplier, LongSupplier clock, Limits limits) {
        this.idSupplier = Objects.requireNonNull(idSupplier, "idSupplier");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public Limits limits() {
        return limits;
    }

    public synchronized Optional<FederatedGroup> create(UUID ownerId, String requestedName) {
        Objects.requireNonNull(ownerId, "ownerId");
        String name = normalizeName(requestedName);
        if (groupByMember.containsKey(ownerId) || groups.size() >= limits.maxGroups()) {
            return Optional.empty();
        }
        UUID groupId = Objects.requireNonNull(idSupplier.get(), "generated group id");
        if (groups.containsKey(groupId)) throw new IllegalStateException("group id collision");
        MutableGroup group = new MutableGroup(groupId, name, ownerId);
        group.members.add(ownerId);
        groups.put(groupId, group);
        groupByMember.put(ownerId, groupId);
        return Optional.of(group.snapshot());
    }

    public synchronized boolean invite(UUID inviterId, UUID groupId, UUID targetId) {
        Objects.requireNonNull(inviterId, "inviterId");
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(targetId, "targetId");
        MutableGroup group = groups.get(groupId);
        if (group == null || !group.members.contains(inviterId)
            || group.members.size() >= limits.maxMembers() || groupByMember.containsKey(targetId)) {
            return false;
        }
        long now = clock.getAsLong();
        pruneInvites(now);
        Map<UUID, PendingInvite> pending = pendingInvites.get(groupId);
        if (pending != null && (pending.containsKey(targetId) || pending.size() >= limits.maxInvitesPerGroup())) {
            return false; // Duplicate invites never refresh their lifetime.
        }
        if (pendingCount >= limits.maxPendingInvites()
            || pendingByRecipient.getOrDefault(targetId, 0) >= limits.maxInvitesPerRecipient()) return false;
        pendingInvites.computeIfAbsent(groupId, ignored -> new LinkedHashMap<>())
            .put(targetId, new PendingInvite(inviterId, now));
        pendingByRecipient.merge(targetId, 1, Integer::sum);
        pendingCount++;
        return true;
    }

    /** Read current grant authority; used by the Gateway to recheck live permissions on acceptance. */
    public synchronized Optional<UUID> inviterFor(UUID groupId, UUID targetId) {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(targetId, "targetId");
        pruneInvites(clock.getAsLong());
        Map<UUID, PendingInvite> pending = pendingInvites.get(groupId);
        PendingInvite invite = pending == null ? null : pending.get(targetId);
        return invite == null ? Optional.empty() : Optional.of(invite.inviterId());
    }

    public synchronized InviteResponse respondToInvite(UUID targetId, UUID groupId, boolean accepted) {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(groupId, "groupId");
        pruneInvites(clock.getAsLong());
        Map<UUID, PendingInvite> pending = pendingInvites.get(groupId);
        if (pending == null || pending.remove(targetId) == null) return InviteResponse.NO_INVITE;
        forgetRecipient(targetId);
        if (pending.isEmpty()) pendingInvites.remove(groupId);
        if (!accepted) return InviteResponse.DECLINED;
        MutableGroup group = groups.get(groupId);
        if (group == null) return InviteResponse.GROUP_MISSING;
        if (groupByMember.containsKey(targetId)) return InviteResponse.ALREADY_GROUPED;
        // Multiple outstanding invites can compete for the last slot.
        if (group.members.size() >= limits.maxMembers()) return InviteResponse.GROUP_FULL;
        group.members.add(targetId);
        group.cached = null;
        groupByMember.put(targetId, groupId);
        removeInvitesFor(targetId);
        return InviteResponse.JOINED;
    }

    public synchronized boolean leave(UUID playerId, UUID groupId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(groupId, "groupId");
        MutableGroup group = groups.get(groupId);
        if (group == null || !group.members.remove(playerId)) return false;
        groupByMember.remove(playerId, groupId);
        group.cached = null;
        removeInvitesFor(playerId);
        if (group.members.isEmpty()) groups.remove(groupId);
        else if (group.ownerId.equals(playerId)) group.ownerId = group.members.iterator().next();
        pruneInvites(clock.getAsLong());
        return true;
    }

    /** Keeps membership for reconnect, but never keeps outstanding grants involving this connection. */
    public synchronized void onPlayerDisconnected(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        removeInvitesFor(playerId);
    }

    /** Call from maintenance if eager expiry is desired; control methods also expire on use. */
    public synchronized int expireInvites() {
        return pruneInvites(clock.getAsLong());
    }

    public synchronized boolean haveCommonGroup(UUID first, UUID second) {
        if (first == null || second == null) return false;
        UUID firstGroup = groupByMember.get(first);
        return firstGroup != null && firstGroup.equals(groupByMember.get(second));
    }

    public synchronized Optional<FederatedGroup> groupFor(UUID playerId) {
        if (playerId == null) return Optional.empty();
        UUID groupId = groupByMember.get(playerId);
        MutableGroup group = groupId != null ? groups.get(groupId) : null;
        return group == null ? Optional.empty() : Optional.of(group.snapshot());
    }

    public synchronized Optional<FederatedGroup> group(UUID groupId) {
        MutableGroup group = groupId != null ? groups.get(groupId) : null;
        return group == null ? Optional.empty() : Optional.of(group.snapshot());
    }

    public synchronized List<FederatedGroup> snapshot() {
        ArrayList<FederatedGroup> result = new ArrayList<>(groups.size());
        for (MutableGroup group : groups.values()) result.add(group.snapshot());
        result.sort((a, b) -> a.id().compareTo(b.id()));
        return List.copyOf(result);
    }

    public synchronized int groupCount() { return groups.size(); }
    public synchronized int memberCount() { return groupByMember.size(); }
    public synchronized int pendingInviteCount() {
        pruneInvites(clock.getAsLong());
        return pendingCount;
    }

    /** Validate everything before replacing live state; malformed or oversized imports leave it intact. */
    public synchronized void replaceAll(Collection<FederatedGroup> snapshots) {
        Objects.requireNonNull(snapshots, "snapshots");
        if (snapshots.size() > limits.maxGroups()) throw new IllegalArgumentException("too many groups");
        Map<UUID, MutableGroup> nextGroups = new HashMap<>();
        Map<UUID, UUID> nextMembership = new HashMap<>();
        for (FederatedGroup snapshot : snapshots) {
            Objects.requireNonNull(snapshot, "group snapshot");
            if (nextGroups.containsKey(snapshot.id())) throw new IllegalArgumentException("duplicate group id");
            if (nextGroups.size() >= limits.maxGroups()) throw new IllegalArgumentException("too many groups");
            if (snapshot.members().size() > limits.maxMembers()) throw new IllegalArgumentException("too many members");
            MutableGroup group = new MutableGroup(snapshot.id(), snapshot.name(), snapshot.ownerId());
            group.members.addAll(snapshot.members());
            for (UUID member : group.members) {
                Objects.requireNonNull(member, "member");
                UUID previous = nextMembership.putIfAbsent(member, group.id);
                if (previous != null) throw new IllegalArgumentException("member belongs to multiple groups");
            }
            nextGroups.put(group.id, group);
        }
        groups.clear();
        groups.putAll(nextGroups);
        groupByMember.clear();
        groupByMember.putAll(nextMembership);
        pendingInvites.clear();
        pendingByRecipient.clear();
        pendingCount = 0;
    }

    private int pruneInvites(long now) {
        int previous = pendingCount;
        var groupsIterator = pendingInvites.entrySet().iterator();
        while (groupsIterator.hasNext()) {
            var entry = groupsIterator.next();
            MutableGroup group = groups.get(entry.getKey());
            var invites = entry.getValue().entrySet().iterator();
            while (invites.hasNext()) {
                var invite = invites.next();
                // nanoTime subtraction remains valid across signed wrap for these bounded lifetimes.
                if (group == null || !group.members.contains(invite.getValue().inviterId())
                    || now - invite.getValue().createdNanos() >= limits.inviteTtlNanos()) {
                    forgetRecipient(invite.getKey());
                    invites.remove();
                }
            }
            if (entry.getValue().isEmpty()) groupsIterator.remove();
        }
        return previous - pendingCount;
    }

    private void removeInvitesFor(UUID playerId) {
        var groupsIterator = pendingInvites.values().iterator();
        while (groupsIterator.hasNext()) {
            Map<UUID, PendingInvite> pending = groupsIterator.next();
            var iterator = pending.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (entry.getKey().equals(playerId) || entry.getValue().inviterId().equals(playerId)) {
                    forgetRecipient(entry.getKey());
                    iterator.remove();
                }
            }
            if (pending.isEmpty()) groupsIterator.remove();
        }
    }

    private void forgetRecipient(UUID targetId) {
        int previous = pendingByRecipient.get(targetId);
        if (previous == 1) pendingByRecipient.remove(targetId);
        else pendingByRecipient.put(targetId, previous - 1);
        pendingCount--;
    }

    private static String normalizeName(String requestedName) {
        if (requestedName == null) throw new IllegalArgumentException("group name is required");
        String normalized = requestedName.trim();
        if (normalized.isEmpty() || normalized.length() > 64) throw new IllegalArgumentException("invalid group name");
        return normalized;
    }

    private static final class MutableGroup {
        final UUID id;
        final String name;
        UUID ownerId;
        final LinkedHashSet<UUID> members = new LinkedHashSet<>();
        FederatedGroup cached;

        MutableGroup(UUID id, String name, UUID ownerId) {
            this.id = Objects.requireNonNull(id, "id");
            this.name = Objects.requireNonNull(name, "name");
            this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        }

        FederatedGroup snapshot() {
            if (cached == null) cached = new FederatedGroup(id, name, ownerId, members);
            return cached;
        }
    }
}
