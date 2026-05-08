package dev.jm.proxychat.voice.client.groups;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import dev.jm.proxychat.voice.common.groups.GroupSnapshot;

public final class ClientGroupCache {

    private static volatile List<GroupSnapshot> groups = Collections.emptyList();

    private ClientGroupCache() {}

    public static void update(List<GroupSnapshot> g) {
        groups = g != null ? Collections.unmodifiableList(g) : Collections.emptyList();
    }

    public static void clear() {
        groups = Collections.emptyList();
    }

    public static List<GroupSnapshot> all() {
        return groups;
    }

    public static GroupSnapshot myGroup(UUID myId) {
        for (GroupSnapshot g : groups) {
            for (GroupSnapshot.MemberEntry m : g.members) {
                if (m.id.equals(myId)) return g;
            }
        }
        return null;
    }
}
