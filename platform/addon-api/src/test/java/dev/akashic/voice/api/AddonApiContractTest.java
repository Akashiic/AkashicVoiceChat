package dev.akashic.voice.api;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AddonApiContractTest {
    @Test
    void discoveryIsTypedAndNullSafe() {
        AkashicVoiceApi api = new StubApi();
        AkashicVoiceApiProvider provider = () -> api;
        assertSame(api, AkashicVoiceApiDiscovery.requireFromPluginInstance(provider));
        assertTrue(AkashicVoiceApiDiscovery.fromPluginInstance(new Object()).isEmpty());
        assertTrue(AkashicVoiceApiDiscovery.fromPluginInstance(null).isEmpty());
        assertEquals("akashicvoicegateway", AkashicVoiceApiDiscovery.GATEWAY_PLUGIN_ID);
    }

    @Test
    void publicSurfaceDoesNotExposeRawAudioOrTransportTypes() {
        for (Method method : AkashicVoiceApi.class.getMethods()) {
            assertNotEquals(byte[].class, method.getReturnType(), method.toString());
            for (Class<?> parameter : method.getParameterTypes()) {
                assertNotEquals(byte[].class, parameter, method.toString());
            }
        }
    }

    @Test
    void immutableValueContractsRejectInvalidInputs() {
        UUID id = UUID.randomUUID();
        VoicePlayer player = new VoicePlayer(id, "Player_1", "lobby", false);
        assertEquals(id, player.playerId());
        assertThrows(IllegalArgumentException.class, () -> new VoicePlayer(id, "bad name", "lobby", false));
        assertThrows(IllegalArgumentException.class, () -> new VoicePlayer(id, "Player", "", false));
        assertThrows(IllegalArgumentException.class, () -> new VoiceModerationEvent(id, true, -1));
        assertDoesNotThrow(() -> new VoiceSessionEvent(player, VoiceSessionAction.CONNECTED));
    }

    private static final class StubApi implements AkashicVoiceApi {
        public int apiVersion() { return API_VERSION; }
        public boolean available() { return true; }
        public List<VoicePlayer> players() { return List.of(); }
        public Optional<VoicePlayer> player(UUID playerId) { return Optional.empty(); }
        public boolean isGloballyMuted(UUID playerId) { return false; }
        public long moderationRevision() { return 0L; }
        public VoiceListenerRegistration listen(VoiceEventListener listener) { return () -> {}; }
    }
}
