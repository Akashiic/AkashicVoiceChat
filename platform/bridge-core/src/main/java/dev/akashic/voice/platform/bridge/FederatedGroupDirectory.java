package dev.akashic.voice.platform.bridge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class FederatedGroupDirectory {
    public enum InviteResponse {
        NO_INVITE,
        DECLINED,
        JOINED,
        GROUP_MISSING,
        ALREADY_GROUPED
    }

    private final Map<UUID, MutableGroup> groups = new HashMap<>();
    private final Map<UUID, UUID> groupByMember = new HashMap<>();
    private final Map<UUID, LinkedHashSet<UUID>> pendingInvites = new HashMap<>();
    private final Supplier<UUID> idSupplier;

    public FederatedGroupDirectory() {
        this(UUID::randomUUID);
    }

    FederatedGroupDirectory(Supplier<UUID> idSupplier) {
        this.idSupplier = Objects.requireNonNull(idSupplier, "idSupplier");
    }

    public synchronized Optional<FederatedGroup> create(UUID ownerId, String requestedName) {
        Objects.requireNonNull(ownerId, "ownerId");
        String name = normalizeName(requestedName);
        if (groupByMember.containsKey(ownerId)) return Optional.empty();

        UUID groupId = Objects.requireNonNull(idSupplier.get(), "generated group id");
        if (groups.containsKey(groupId)) {
            throw new IllegalStateException("group id collision");
        }
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
        if (group == null || !group.members.contains(inviterId)) return false;
        if (groupByMember.containsKey(targetId)) return false;
        return pendingInvites.computeIfAbsent(groupId, ignored -> new LinkedHashSet<>()).add(targetId);
    }

    public synchronized InviteResponse respondToInvite(UUID targetId, UUID groupId, boolean accepted) {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(groupId, "groupId");
        LinkedHashSet<UUID> pending = pendingInvites.get(groupId);
        if (pending == null || !pending.remove(targetId)) return InviteResponse.NO_INVITE;
        if (pending.isEmpty()) pendingInvites.remove(groupId);
        if (!accepted) return InviteResponse.DECLINED;

        MutableGroup group = groups.get(groupId);
        if (group == null) return InviteResponse.GROUP_MISSING;
        if (groupByMember.containsKey(targetId)) return InviteResponse.ALREADY_GROUPED;
        group.members.add(targetId);
        groupByMember.put(targetId, groupId);
        return InviteResponse.JOINED;
    }

    public synchronized boolean leave(UUID playerId, UUID groupId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(groupId, "groupId");
        MutableGroup group = groups.get(groupId);
        if (group == null || !group.members.remove(playerId)) return false;
        groupByMember.remove(playerId, groupId);
        removePendingFor(playerId);

        if (group.members.isEmpty()) {
            groups.remove(groupId);
            pendingInvites.remove(groupId);
            return true;
        }
        if (group.ownerId.equals(playerId)) group.ownerId = group.members.iterator().next();
        return true;
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

    public synchronized int groupCount() {
        return groups.size();
    }

    public synchronized int memberCount() {
        return groupByMember.size();
    }

    public synchronized int pendingInviteCount() {
        int count = 0;
        for (Set<UUID> pending : pendingInvites.values()) count += pending.size();
        return count;
    }

    public synchronized void replaceAll(Collection<FederatedGroup> snapshots) {
        Objects.requireNonNull(snapshots, "snapshots");
        Map<UUID, MutableGroup> nextGroups = new HashMap<>();
        Map<UUID, UUID> nextMembership = new HashMap<>();

        for (FederatedGroup snapshot : snapshots) {
            Objects.requireNonNull(snapshot, "group snapshot");
            if (nextGroups.containsKey(snapshot.id())) {
                throw new IllegalArgumentException("duplicate group id");
            }
            MutableGroup group = new MutableGroup(snapshot.id(), snapshot.name(), snapshot.ownerId());
            group.members.addAll(snapshot.members());
            for (UUID member : group.members) {
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
    }

    private void removePendingFor(UUID playerId) {
        var iterator = pendingInvites.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, LinkedHashSet<UUID>> entry = iterator.next();
            entry.getValue().remove(playerId);
            if (entry.getValue().isEmpty()) iterator.remove();
        }
    }

    private static String normalizeName(String requestedName) {
        if (requestedName == null) throw new IllegalArgumentException("group name is required");
        String normalized = requestedName.trim();
        if (normalized.isEmpty() || normalized.length() > 64) {
            throw new IllegalArgumentException("invalid group name");
        }
        return normalized;
    }

    private static final class MutableGroup {
        final UUID id;
        final String name;
        UUID ownerId;
        final LinkedHashSet<UUID> members = new LinkedHashSet<>();

        MutableGroup(UUID id, String name, UUID ownerId) {
            this.id = Objects.requireNonNull(id, "id");
            this.name = Objects.requireNonNull(name, "name");
            this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        }

        FederatedGroup snapshot() {
            return new FederatedGroup(id, name, ownerId, members);
        }
    }
}
