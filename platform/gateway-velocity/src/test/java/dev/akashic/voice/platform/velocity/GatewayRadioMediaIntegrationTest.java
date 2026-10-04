package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.velocity.GatewayGroupMediaSmoke.Fixture;
import dev.akashic.voice.platform.velocity.GatewayGroupMediaSmoke.Peer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class GatewayRadioMediaIntegrationTest {
    @BeforeAll
    static void fixture() {
        GatewayGroupMediaIntegrationTest.fixture();
    }

    @Test
    void crossBackendRadioIsNonSpatialAndDoesNotReachOutsider() throws Exception {
        try (Fixture f = new Fixture()) {
            Peer a = f.peer("A", "one");
            Peer b = f.peer("B", "two");
            Peer outsider = f.peer("C", "two");
            assertTrue(f.runtime.tuneRadio(a.id, "ops").changed());
            assertTrue(f.runtime.tuneRadio(b.id, "OPS").changed());
            drainJoinResets(a, b);

            a.mic(7);
            GatewayGroupMediaSmoke.same(b.group(), a, 7);
            f.frameAtAgent("one", a, 7);
            outsider.silence();
            assertEquals(1L, f.runtime.statusSnapshot().radioFramesSent());
            assertEquals(0L, f.runtime.statusSnapshot().groupFramesSent());
            f.healthy();
        }
    }

    @Test
    void groupAndRadioUnionSendsOnlyOneDirectFrame() throws Exception {
        try (Fixture f = new Fixture()) {
            Peer a = f.peer("A", "one");
            Peer b = f.peer("B", "two");
            f.join(a, b);
            f.runtime.tuneRadio(a.id, "ops");
            f.runtime.tuneRadio(b.id, "ops");
            // Group already provides the effective direct route, so radio tuning emits no reset.
            a.mic(10);
            GatewayGroupMediaSmoke.same(b.group(), a, 10);
            f.frameAtAgent("one", a, 10);
            b.silence();
            GatewayStatusSnapshot status = f.runtime.statusSnapshot();
            assertEquals(1L, status.groupFramesSent());
            assertEquals(0L, status.radioFramesSent());
            f.healthy();
        }
    }

    @Test
    void radioSuppressesSvcDuplicateAndReset() throws Exception {
        try (Fixture f = new Fixture()) {
            Peer a = f.peer("A", "one");
            Peer b = f.peer("B", "two");
            f.runtime.tuneRadio(a.id, "ops");
            f.runtime.tuneRadio(b.id, "ops");
            drainJoinResets(a, b);

            f.deliver("two", a, b, 20);
            f.reset("two", a, b);
            f.agents.get("two").barrier();
            b.silence();
            assertEquals(0L, f.runtime.statusSnapshot().soundFramesSent());
            f.healthy();
        }
    }

    @Test
    void leavingRadioResetsBothDirectionsAndRestoresSvc() throws Exception {
        try (Fixture f = new Fixture()) {
            Peer a = f.peer("A", "one");
            Peer b = f.peer("B", "two");
            f.runtime.tuneRadio(a.id, "ops");
            f.runtime.tuneRadio(b.id, "ops");
            drainJoinResets(a, b);
            a.mic(30);
            GatewayGroupMediaSmoke.same(b.group(), a, 30);
            f.frameAtAgent("one", a, 30);

            long resets = f.runtime.statusSnapshot().soundResetsSent();
            assertEquals("ops", f.runtime.leaveRadio(b.id).orElseThrow());
            assertEquals(a.id, b.reset().senderId);
            assertEquals(b.id, a.reset().senderId);
            assertEquals(resets + 2, f.runtime.statusSnapshot().soundResetsSent());

            f.deliver("two", a, b, 31);
            assertTrue(b.svc().isSpatial());
            f.healthy();
        }
    }

    @Test
    void backendHandoffPreservesRadioTuning() throws Exception {
        try (Fixture f = new Fixture()) {
            UUID sharedId = UUID.randomUUID();
            Peer a = f.peer(sharedId, "A", "one");
            Peer b = f.peer("B", "two");
            f.runtime.tuneRadio(a.id, "ops");
            f.runtime.tuneRadio(b.id, "ops");
            drainJoinResets(a, b);

            f.runtime.disconnectProxyPlayer(a.id);
            assertEquals("ops", f.runtime.radioChannel(a.id).orElseThrow());
            Peer replacement = f.peer(sharedId, "A", "two");
            replacement.mic(0);
            GatewayGroupMediaSmoke.same(b.group(), replacement, 0);
            f.frameAtAgent("two", replacement, 0);
            f.healthy();
        }
    }

    @Test
    void permissionPruneRemovesTuningAndResetsAffectedPair() throws Exception {
        try (Fixture f = new Fixture()) {
            Peer a = f.peer("A", "one");
            Peer b = f.peer("B", "two");
            f.runtime.tuneRadio(a.id, "ops");
            f.runtime.tuneRadio(b.id, "ops");
            drainJoinResets(a, b);

            f.runtime.pruneRadio(id -> !id.equals(a.id));
            assertTrue(f.runtime.radioChannel(a.id).isEmpty());
            assertEquals("ops", f.runtime.radioChannel(b.id).orElseThrow());
            assertEquals(a.id, b.reset().senderId);
            assertEquals(b.id, a.reset().senderId);
            f.healthy();
        }
    }

    private static void drainJoinResets(Peer a, Peer b) throws Exception {
        assertEquals(b.id, a.reset().senderId);
        assertEquals(a.id, b.reset().senderId);
    }
}
