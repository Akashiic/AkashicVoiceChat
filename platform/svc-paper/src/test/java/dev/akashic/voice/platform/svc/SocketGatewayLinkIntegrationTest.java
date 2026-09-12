package dev.akashic.voice.platform.svc;

import dev.akashic.voice.platform.agent.AgentMessage;
import dev.akashic.voice.platform.agent.AgentSecureChannel;
import dev.akashic.voice.platform.agent.AgentSocketHandshake;
import dev.akashic.voice.platform.bridge.AkashicSpeakerFrame;
import dev.akashic.voice.platform.bridge.BridgeDelivery;
import dev.akashic.voice.platform.bridge.BridgeStreamReset;
import dev.akashic.voice.platform.bridge.GatewayLink;
import dev.akashic.voice.platform.bridge.ManagedPlayer;
import dev.akashic.voice.platform.bridge.PositionSnapshot;
import dev.akashic.voice.platform.bridge.SpatialKind;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
final class SocketGatewayLinkIntegrationTest {

    @Test
    void reconnectsAndCarriesControlAndMediaBothDirections() throws Exception {
        byte[] psk = new byte[32];
        new SecureRandom().nextBytes(psk);
        UUID proxyId = UUID.randomUUID();
        UUID speakerId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        RecordingListener listener = new RecordingListener();
        ArrayBlockingQueue<AgentSecureChannel> accepted = new ArrayBlockingQueue<>(2);

        try (ServerSocket server = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> acceptFuture = executor.submit(() -> {
                try {
                    for (int i = 0; i < 2; i++) {
                        Socket socket = server.accept();
                        AgentSocketHandshake.Accepted route = AgentSocketHandshake.accept(
                            socket,
                            backend -> "lobby".equals(backend) ? Arrays.copyOf(psk, psk.length) : null,
                            new SecureRandom());
                        accepted.put(route.channel());
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            try (SocketGatewayLink link = new SocketGatewayLink(
                "127.0.0.1", server.getLocalPort(), "lobby", psk)) {
                link.start(listener);
                AgentSecureChannel first = accepted.poll(3, TimeUnit.SECONDS);
                assertNotNull(first, "first agent route did not authenticate");
                await(link::isConnected, "SocketGatewayLink did not report connected");

                ManagedPlayer managed = new ManagedPlayer(proxyId, "lobby", "OfflinePlayer");
                assertTrue(first.send(new AgentMessage.Attach(managed)));
                assertEquals(managed, listener.attached.poll(2, TimeUnit.SECONDS));

                byte[] micOpus = {1, 2, 3, 4};
                AkashicSpeakerFrame frame = new AkashicSpeakerFrame(proxyId, 41L, false, micOpus);
                assertTrue(first.send(new AgentMessage.SpeakerFrame(frame)));
                AkashicSpeakerFrame receivedFrame = listener.frames.poll(2, TimeUnit.SECONDS);
                assertNotNull(receivedFrame);
                assertEquals(proxyId, receivedFrame.speakerId());
                assertArrayEquals(micOpus, receivedFrame.opusUnsafe());

                assertTrue(first.send(new AgentMessage.SpeakerReset(proxyId)));
                assertEquals(proxyId, listener.resets.poll(2, TimeUnit.SECONDS));

                byte[] svcOpus = {9, 8, 7, 6};
                BridgeDelivery delivery = new BridgeDelivery(
                    proxyId,
                    speakerId,
                    channelId,
                    77L,
                    SpatialKind.LOCATION,
                    null,
                    new PositionSnapshot("minecraft:overworld", 1.5, 64.0, -3.5),
                    24F,
                    true,
                    "proximity",
                    svcOpus);
                link.publishSvcDelivery(delivery);
                AgentMessage.SvcDelivery svcDelivery = assertInstanceOf(
                    AgentMessage.SvcDelivery.class, first.receive());
                assertEquals(proxyId, svcDelivery.delivery().recipientId());
                assertEquals(speakerId, svcDelivery.delivery().senderId());
                assertArrayEquals(svcOpus, svcDelivery.delivery().opusUnsafe());

                BridgeStreamReset reset = new BridgeStreamReset(proxyId, speakerId, channelId);
                link.publishSvcReset(reset);
                AgentMessage.SvcReset svcReset = assertInstanceOf(
                    AgentMessage.SvcReset.class, first.receive());
                assertEquals(speakerId, svcReset.reset().senderId());

                first.close();
                assertEquals(proxyId, listener.detached.poll(3, TimeUnit.SECONDS));

                AgentSecureChannel second = accepted.poll(5, TimeUnit.SECONDS);
                assertNotNull(second, "SocketGatewayLink did not reconnect");
                await(link::isConnected, "SocketGatewayLink did not become connected after reconnect");

                assertTrue(second.send(new AgentMessage.Attach(managed)));
                assertEquals(managed, listener.attached.poll(2, TimeUnit.SECONDS));

                second.close();
                acceptFuture.get(3, TimeUnit.SECONDS);
            }
        } finally {
            Arrays.fill(psk, (byte) 0);
        }
    }

    private static void await(java.util.function.BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertTrue(condition.getAsBoolean(), message);
    }

    private static final class RecordingListener implements GatewayLink.Listener {
        private final ArrayBlockingQueue<ManagedPlayer> attached = new ArrayBlockingQueue<>(8);
        private final ArrayBlockingQueue<UUID> detached = new ArrayBlockingQueue<>(8);
        private final ArrayBlockingQueue<AkashicSpeakerFrame> frames = new ArrayBlockingQueue<>(8);
        private final ArrayBlockingQueue<UUID> resets = new ArrayBlockingQueue<>(8);

        @Override
        public void onManagedPlayerAttached(ManagedPlayer player) {
            attached.offer(player);
        }

        @Override
        public void onManagedPlayerDetached(UUID playerId) {
            detached.offer(playerId);
        }

        @Override
        public void onAkashicSpeakerFrame(AkashicSpeakerFrame frame) {
            frames.offer(frame);
        }

        @Override
        public void onAkashicSpeakerReset(UUID speakerId) {
            resets.offer(speakerId);
        }
    }
}
