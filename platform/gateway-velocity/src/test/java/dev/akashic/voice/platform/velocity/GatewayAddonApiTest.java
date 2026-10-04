package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.api.VoiceEventListener;
import dev.akashic.voice.api.VoiceListenerRegistration;
import dev.akashic.voice.api.VoiceModerationEvent;
import dev.akashic.voice.api.VoiceSessionAction;
import dev.akashic.voice.api.VoiceSessionEvent;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GatewayAddonApiTest {
    @Test
    void gatewayPluginExposesPublicProviderContract() {
        assertTrue(dev.akashic.voice.api.AkashicVoiceApiProvider.class
            .isAssignableFrom(AkashicVoiceGatewayPlugin.class));
    }

    @Test
    void runtimeLifecyclePublishesTypedSessionSnapshots() throws Exception {
        ArrayList<Throwable> errors = new ArrayList<>();
        AtomicReference<Set<UUID>> muted = new AtomicReference<>(Set.of());
        AtomicLong revision = new AtomicLong(4L);
        GatewayAddonApi api = new GatewayAddonApi(errors::add);
        api.activate(id -> muted.get().contains(id), revision::get);

        UUID playerId = UUID.randomUUID();
        ArrayList<VoiceSessionEvent> sessions = new ArrayList<>();
        try (VoiceListenerRegistration registration = api.listen(new VoiceEventListener() {
            @Override public void onSession(VoiceSessionEvent event) { sessions.add(event); }
        }); BootstrapSessionRegistry registry = new BootstrapSessionRegistry();
             GatewayRuntime runtime = new GatewayRuntime(
                 24454, new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                 Map.of("lobby", new byte[32]), registry, new QuietRuntimeListener())) {
            assertNotNull(registration);
            runtime.setAddonApi(api);
            BootstrapGrant grant = registry.grantFor(playerId, "AddonUser", "lobby", 24454);
            runtime.onConnected(playerId, "lobby", grant.voiceIdentity());

            assertTrue(api.available());
            assertEquals(1, api.players().size());
            assertEquals("AddonUser", api.player(playerId).orElseThrow().playerName());
            assertFalse(api.player(playerId).orElseThrow().globallyMuted());
            assertEquals(VoiceSessionAction.CONNECTED, sessions.get(0).action());

            muted.set(Set.of(playerId));
            revision.incrementAndGet();
            assertTrue(api.isGloballyMuted(playerId));
            assertTrue(api.player(playerId).orElseThrow().globallyMuted());
            assertEquals(5L, api.moderationRevision());

            runtime.onDisconnected(playerId, "lobby", grant.voiceIdentity());
            assertTrue(api.players().isEmpty());
            assertEquals(VoiceSessionAction.DISCONNECTED, sessions.get(1).action());
            assertTrue(errors.isEmpty());
        }
    }

    @Test
    void listenerFailuresAreIsolatedAndRegistrationIsIdempotent() {
        ArrayList<Throwable> errors = new ArrayList<>();
        GatewayAddonApi api = new GatewayAddonApi(errors::add);
        api.activate(ignored -> false, () -> 0L);
        AtomicLong delivered = new AtomicLong();

        VoiceListenerRegistration broken = api.listen(new VoiceEventListener() {
            @Override public void onModeration(VoiceModerationEvent event) {
                throw new IllegalStateException("synthetic addon failure");
            }
        });
        VoiceListenerRegistration healthy = api.listen(new VoiceEventListener() {
            @Override public void onModeration(VoiceModerationEvent event) { delivered.incrementAndGet(); }
        });

        UUID player = UUID.randomUUID();
        api.moderationChanged(player, true, 1L);
        assertEquals(1L, delivered.get());
        assertEquals(1, errors.size());

        broken.close();
        broken.close();
        healthy.close();
        healthy.close();
        api.moderationChanged(player, false, 2L);
        assertEquals(1L, delivered.get());
    }

    @Test
    void deactivateClearsStateAndSuppressesFutureCallbacks() {
        GatewayAddonApi api = new GatewayAddonApi(failure -> fail(failure));
        api.activate(ignored -> false, () -> 7L);
        AtomicLong events = new AtomicLong();
        api.listen(new VoiceEventListener() {
            @Override public void onSession(VoiceSessionEvent event) { events.incrementAndGet(); }
        });
        UUID first = UUID.randomUUID();
        api.connected(first, "First", "lobby");
        assertEquals(1L, events.get());
        assertThrows(UnsupportedOperationException.class, () -> api.players().clear());

        api.deactivate();
        api.connected(UUID.randomUUID(), "Second", "lobby");
        assertFalse(api.available());
        assertTrue(api.players().isEmpty());
        assertEquals(0L, api.moderationRevision());
        assertEquals(1L, events.get());

        UUID third = UUID.randomUUID();
        api.activate(ignored -> false, () -> 8L);
        api.connected(third, "Third", "lobby");
        assertEquals(1, api.players().size());
        assertEquals(third, api.players().get(0).playerId());
        assertEquals(1L, events.get(), "deactivated listener survived reactivation");
    }

    private static final class QuietRuntimeListener implements GatewayRuntime.Listener {
        @Override public void onRouteUp(String backendName) {}
        @Override public void onRouteDown(String backendName) {}
        @Override public void onError(String component, Throwable error) { fail(component, error); }
    }
}
