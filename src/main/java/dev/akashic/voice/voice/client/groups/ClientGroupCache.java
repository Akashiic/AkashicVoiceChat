package dev.akashic.voice.voice.client.groups;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import dev.akashic.voice.voice.common.groups.GroupSnapshot;

/** Server-personalized snapshots; the client-local UUID is not a proxy identity authority. */
public final class ClientGroupCache {

    private static volatile List<GroupSnapshot> groups = Collections.emptyList();

    private ClientGroupCache() {}

    public static void update(List<GroupSnapshot> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            clear();
            return;
        }
        List<GroupSnapshot> copy = new ArrayList<GroupSnapshot>(incoming.size());
        for (GroupSnapshot group : incoming) {
            if (group == null || group.members == null) {
                clear();
                return;
            }
            List<GroupSnapshot.MemberEntry> members = new ArrayList<GroupSnapshot.MemberEntry>(group.members);
            if (members.contains(null)) {
                clear();
                return;
            }
            copy.add(
                new GroupSnapshot(group.groupId, group.name, group.ownerId, Collections.unmodifiableList(members)));
        }
        groups = Collections.unmodifiableList(copy);
    }

    public static void clear() {
        groups = Collections.emptyList();
    }

    public static List<GroupSnapshot> all() {
        return groups;
    }

    public static GroupSnapshot personalGroup() {
        List<GroupSnapshot> snapshot = groups;
        return snapshot.size() == 1 ? snapshot.get(0) : null;
    }

    public static GroupSnapshot myGroup(UUID id) {
        if (id == null) return null;
        for (GroupSnapshot group : groups) {
            for (GroupSnapshot.MemberEntry member : group.members) {
                if (id.equals(member.id)) return group;
            }
        }
        return null;
    }
}
