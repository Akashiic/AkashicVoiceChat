package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.bridge.BridgeDelivery;
import dev.akashic.voice.platform.bridge.BridgeStreamReset;
import dev.akashic.voice.platform.bridge.SpatialKind;
import org.junit.jupiter.api.Test;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class GatewayModerationRuntimeTest {
    @Test
    void mutedSenderIsDroppedBeforeAgentGroupAndSvcRouting() throws Exception {
        byte[] psk = new byte[32]; Arrays.fill(psk, (byte) 7);
        UUID muted = UUID.randomUUID(), recipient = UUID.randomUUID();
        try (BootstrapSessionRegistry registry = new BootstrapSessionRegistry();
             GatewayRuntime runtime = new GatewayRuntime(freeUdpPort(),
                 new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                 Map.of("lobby", psk), registry, new Listener())) {
            runtime.setModeration(id -> !muted.equals(id), () -> 1);
            runtime.start();
            runtime.onMicFrame(muted, "lobby", UUID.randomUUID(), 1L, new byte[] {1,2,3});
            runtime.onSvcDelivery("lobby", new BridgeDelivery(recipient, muted, UUID.randomUUID(), 2L,
                SpatialKind.STATIC, null, null, 0F, false, null, new byte[] {4,5}));
            GatewayStatusSnapshot status = runtime.statusSnapshot();
            assertEquals(1, status.moderatedPlayers());
            assertEquals(2L, status.moderatedFramesDropped());
            runtime.onMicFrame(recipient, "lobby", UUID.randomUUID(), 3L, new byte[] {6});
            runtime.onSvcReset("lobby", new BridgeStreamReset(recipient, muted, UUID.randomUUID()));
            assertEquals(2L, runtime.statusSnapshot().moderatedFramesDropped(),
                "unmuted traffic/reset incorrectly counted as moderation drop");
        } finally {
            Arrays.fill(psk, (byte) 0);
        }
    }

    private static int freeUdpPort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static final class Listener implements GatewayRuntime.Listener {
        @Override public void onRouteUp(String backendName) { }
        @Override public void onRouteDown(String backendName) { }
        @Override public void onError(String component, Throwable error) {
            if (error instanceof java.net.SocketException) return;
            fail("unexpected runtime error from " + component, error);
        }
    }
}
