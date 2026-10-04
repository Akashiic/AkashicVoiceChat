package dev.akashic.voice.platform.velocity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GatewayRadioDirectoryTest {
    @Test
    void tuneSwitchLeavePublishesImmutableSnapshots() {
        GatewayRadioDirectory radio = new GatewayRadioDirectory();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertEquals(GatewayRadioDirectory.TuneResult.JOINED, radio.tune(a, " Ops_1 ").result());
        assertEquals("ops_1", radio.channel(a).orElseThrow());
        assertEquals(GatewayRadioDirectory.TuneResult.UNCHANGED, radio.tune(a, "OPS_1").result());
        radio.tune(b, "ops_1");
        assertTrue(radio.sameChannel(a, b));
        Set<UUID> peers = radio.peers(a);
        assertEquals(Set.of(b), peers);
        assertThrows(UnsupportedOperationException.class, () -> peers.add(UUID.randomUUID()));

        GatewayRadioDirectory.TuneOutcome switched = radio.tune(a, "staff");
        assertEquals(GatewayRadioDirectory.TuneResult.SWITCHED, switched.result());
        assertEquals("ops_1", switched.previousChannel());
        assertFalse(radio.sameChannel(a, b));
        assertEquals("staff", radio.leave(a).orElseThrow());
        assertTrue(radio.channel(a).isEmpty());
        assertEquals(1, radio.channelCount());
    }

    @Test
    void invalidNamesAndCapacityFailuresDoNotDestroyPriorTuning() {
        GatewayRadioDirectory radio = new GatewayRadioDirectory();
        UUID actor = UUID.randomUUID();
        radio.tune(actor, "home");
        for (String invalid : new String[]{"", " ", "bad name", "x!", "abcdefghijklmnopqrstuvwxyz"}) {
            assertEquals(GatewayRadioDirectory.TuneResult.INVALID, radio.tune(actor, invalid).result());
            assertEquals("home", radio.channel(actor).orElseThrow());
        }

        for (int i = 0; i < GatewayRadioDirectory.MAX_MEMBERS_PER_CHANNEL; i++) {
            assertTrue(radio.tune(new UUID(10L, i + 1L), "full").changed());
        }
        assertEquals(GatewayRadioDirectory.TuneResult.CHANNEL_FULL, radio.tune(actor, "full").result());
        assertEquals("home", radio.channel(actor).orElseThrow());
    }

    @Test
    void channelLimitIsBounded() {
        GatewayRadioDirectory radio = new GatewayRadioDirectory();
        for (int i = 0; i < GatewayRadioDirectory.MAX_CHANNELS; i++) {
            assertEquals(GatewayRadioDirectory.TuneResult.JOINED,
                radio.tune(new UUID(20L, i + 1L), "r" + i).result());
        }
        UUID extra = new UUID(20L, 10_000L);
        assertEquals(GatewayRadioDirectory.TuneResult.CHANNEL_LIMIT, radio.tune(extra, "overflow").result());
        assertTrue(radio.channel(extra).isEmpty());
        assertEquals(GatewayRadioDirectory.MAX_CHANNELS, radio.channelCount());
    }

    @Test
    void playerLimitIsBoundedAndExistingMembersMayRetune() {
        GatewayRadioDirectory radio = new GatewayRadioDirectory();
        ArrayList<UUID> ids = new ArrayList<>();
        int created = 0;
        for (int channel = 0; channel < 65 && created < GatewayRadioDirectory.MAX_TUNED_PLAYERS; channel++) {
            int take = Math.min(GatewayRadioDirectory.MAX_MEMBERS_PER_CHANNEL,
                GatewayRadioDirectory.MAX_TUNED_PLAYERS - created);
            for (int member = 0; member < take; member++) {
                UUID id = new UUID(30L + channel, member + 1L);
                ids.add(id);
                assertTrue(radio.tune(id, "cap" + channel).changed());
                created++;
            }
        }
        assertEquals(GatewayRadioDirectory.MAX_TUNED_PLAYERS, radio.memberCount());
        UUID extra = new UUID(999L, 999L);
        assertEquals(GatewayRadioDirectory.TuneResult.PLAYER_LIMIT,
            radio.tune(extra, "cap64").result());
        assertTrue(radio.channel(extra).isEmpty());

        UUID existing = ids.get(0);
        assertEquals(GatewayRadioDirectory.TuneResult.SWITCHED,
            radio.tune(existing, "cap64").result());
        assertEquals(GatewayRadioDirectory.MAX_TUNED_PLAYERS, radio.memberCount());
    }

    @Test
    void normalizationIsLocaleIndependentAndStrict() {
        assertEquals("alpha-1_test", GatewayRadioDirectory.normalize(" Alpha-1_Test "));
        assertNull(GatewayRadioDirectory.normalize(null));
        assertNull(GatewayRadioDirectory.normalize("çanal"));
        assertNull(GatewayRadioDirectory.normalize("radio/ops"));
    }
}
