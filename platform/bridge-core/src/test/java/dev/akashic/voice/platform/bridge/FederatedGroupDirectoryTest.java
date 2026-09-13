package dev.akashic.voice.platform.bridge;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FederatedGroupDirectoryTest {
    @Test
    void preservesOneGroupRuleAndTransfersOwnerDeterministically() {
        UUID groupId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        FederatedGroupDirectory groups = new FederatedGroupDirectory(() -> groupId);

        FederatedGroup created = groups.create(owner, "  Akashic  ").orElseThrow();
        assertEquals("Akashic", created.name());
        assertEquals(owner, created.ownerId());
        assertEquals(Set.of(owner), created.members());

        assertTrue(groups.invite(owner, groupId, second));
        assertEquals(FederatedGroupDirectory.InviteResponse.JOINED,
            groups.respondToInvite(second, groupId, true));
        assertTrue(groups.invite(second, groupId, third));
        assertEquals(FederatedGroupDirectory.InviteResponse.JOINED,
            groups.respondToInvite(third, groupId, true));
        assertTrue(groups.haveCommonGroup(owner, third));
        assertEquals(3, groups.memberCount());

        assertTrue(groups.leave(owner, groupId));
        FederatedGroup afterOwnerLeave = groups.group(groupId).orElseThrow();
        assertEquals(second, afterOwnerLeave.ownerId());
        assertEquals(List.of(second, third), List.copyOf(afterOwnerLeave.members()));

        assertTrue(groups.leave(second, groupId));
        assertEquals(third, groups.group(groupId).orElseThrow().ownerId());
        assertTrue(groups.leave(third, groupId));
        assertTrue(groups.group(groupId).isEmpty());
        assertEquals(0, groups.groupCount());
        assertEquals(0, groups.memberCount());
    }

    @Test
    void acceptedInviteCannotBreakOneGroupInvariant() {
        UUID firstGroup = UUID.randomUUID();
        UUID secondGroup = UUID.randomUUID();
        Iterator<UUID> ids = List.of(firstGroup, secondGroup).iterator();
        FederatedGroupDirectory groups = new FederatedGroupDirectory(ids::next);
        UUID inviter = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        groups.create(inviter, "First").orElseThrow();
        assertTrue(groups.invite(inviter, firstGroup, target));
        groups.create(target, "Second").orElseThrow();

        assertEquals(FederatedGroupDirectory.InviteResponse.ALREADY_GROUPED,
            groups.respondToInvite(target, firstGroup, true));
        assertEquals(secondGroup, groups.groupFor(target).orElseThrow().id());
        assertFalse(groups.group(firstGroup).orElseThrow().members().contains(target));
        assertEquals(0, groups.pendingInviteCount());
    }

    @Test
    void replaceAllIsAtomicAndRejectsDuplicateMembership() {
        UUID groupA = UUID.randomUUID();
        UUID groupB = UUID.randomUUID();
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        UUID shared = UUID.randomUUID();

        FederatedGroup a = new FederatedGroup(
            groupA, "A", ownerA, linked(ownerA, shared));
        FederatedGroup b = new FederatedGroup(
            groupB, "B", ownerB, linked(ownerB, shared));
        FederatedGroupDirectory groups = new FederatedGroupDirectory();

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class, () -> groups.replaceAll(List.of(a, b)));
        assertTrue(error.getMessage().contains("multiple groups"));
        assertEquals(0, groups.groupCount());
        assertEquals(0, groups.memberCount());
    }

    @Test
    void snapshotsAreDefensiveAndReplaceAllClearsPendingInvites() {
        UUID groupId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID invited = UUID.randomUUID();
        FederatedGroupDirectory groups = new FederatedGroupDirectory(() -> groupId);
        groups.create(owner, "Persisted").orElseThrow();
        assertTrue(groups.invite(owner, groupId, invited));
        assertEquals(1, groups.pendingInviteCount());

        List<FederatedGroup> snapshot = groups.snapshot();
        assertThrows(UnsupportedOperationException.class,
            () -> snapshot.get(0).members().add(UUID.randomUUID()));
        groups.replaceAll(snapshot);
        assertEquals(1, groups.groupCount());
        assertEquals(1, groups.memberCount());
        assertEquals(0, groups.pendingInviteCount());
        assertEquals(owner, groups.groupFor(owner).orElseThrow().ownerId());
    }

    private static Set<UUID> linked(UUID... ids) {
        LinkedHashSet<UUID> result = new LinkedHashSet<>();
        result.addAll(List.of(ids));
        return result;
    }
}
