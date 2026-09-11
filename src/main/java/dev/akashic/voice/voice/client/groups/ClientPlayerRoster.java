package dev.akashic.voice.voice.client.groups;

import java.util.Collections;
import java.util.List;

import dev.akashic.voice.voice.common.groups.RosterEntry;

public final class ClientPlayerRoster {

    private static volatile List<RosterEntry> roster = Collections.emptyList();

    private ClientPlayerRoster() {}

    public static void update(List<RosterEntry> r) {
        roster = r != null ? Collections.unmodifiableList(r) : Collections.emptyList();
    }

    public static void clear() {
        roster = Collections.emptyList();
    }

    public static List<RosterEntry> all() {
        return roster;
    }
}
