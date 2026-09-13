package dev.akashic.voice.platform.bridge;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record FederatedGroup(UUID id, String name, UUID ownerId, Set<UUID> members) {
    public FederatedGroup {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(members, "members");
        String normalized = name.trim();
        if (normalized.isEmpty() || normalized.length() > 64) {
            throw new IllegalArgumentException("invalid group name");
        }
        if (!members.contains(ownerId)) {
            throw new IllegalArgumentException("owner must be a member");
        }
        name = normalized;
        members = Collections.unmodifiableSet(new LinkedHashSet<>(members));
    }
}
