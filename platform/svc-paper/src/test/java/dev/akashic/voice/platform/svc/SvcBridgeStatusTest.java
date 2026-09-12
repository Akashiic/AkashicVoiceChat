package dev.akashic.voice.platform.svc;

import dev.akashic.voice.platform.bridge.DisconnectedGatewayLink;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SvcBridgeStatusTest {
    @Test
    void reportsSafeInitialStateWithoutExposingIdentity() {
        SvcBridgeEngine engine = new SvcBridgeEngine(
            new DisconnectedGatewayLink(), "lobby", new BukkitPositionSnapshots());
        try {
            SvcBridgeStatus status = engine.statusSnapshot();
            assertFalse(status.gatewayConnected());
            assertFalse(status.voicechatBound());
            assertEquals(0, status.desiredPlayers());
            assertEquals(0, status.activeSessions());
            assertEquals(0, status.identityBindings());
        } finally {
            engine.close();
        }
    }
}
