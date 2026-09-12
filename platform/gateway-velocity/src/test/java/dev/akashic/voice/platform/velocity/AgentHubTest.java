package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.agent.AgentMessage;
import dev.akashic.voice.platform.agent.AgentSecureChannel;
import dev.akashic.voice.platform.agent.AgentSocketHandshake;
import dev.akashic.voice.platform.bridge.AkashicSpeakerFrame;
import dev.akashic.voice.platform.bridge.BridgeDelivery;
import dev.akashic.voice.platform.bridge.BridgeStreamReset;
import dev.akashic.voice.platform.bridge.ManagedPlayer;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AgentHubTest {
    @Test
    void reconnectReplacesRouteWithoutFalseRouteDown() throws Exception {
        byte[] psk = new byte[32];
        new SecureRandom().nextBytes(psk);
        BackendRouteRegistry routes = new BackendRouteRegistry();
        RecordingListener listener = new RecordingListener();
        try (AgentHub hub = new AgentHub(
            new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
            Map.of("lobby", psk),
            routes,
            listener)) {
            hub.start();
            try (AgentSecureChannel first = connect(hub.localPort(), psk)) {
                assertTrue(listener.firstUp.await(2, TimeUnit.SECONDS));
                BackendRouteRegistry.Route firstRoute = routes.readyRoute("lobby");
                assertNotNull(firstRoute);

                UUID playerId = UUID.randomUUID();
                assertTrue(firstRoute.sendAttach(new ManagedPlayer(playerId, "lobby", "TestPlayer")));
                AgentMessage firstMessage = first.receive();
                assertInstanceOf(AgentMessage.Attach.class, firstMessage);
                assertEquals(playerId, ((AgentMessage.Attach) firstMessage).player().playerId());

                try (AgentSecureChannel second = connect(hub.localPort(), psk)) {
                    assertTrue(listener.secondUp.await(2, TimeUnit.SECONDS));
                    Thread.sleep(100L);
                    assertEquals(0, listener.routeDowns.get(), "replacement emitted false routeDown");
                    assertNotNull(routes.readyRoute("lobby"));
                    second.socket().close();
                    assertTrue(listener.down.await(2, TimeUnit.SECONDS));
                    assertEquals(1, listener.routeDowns.get());
                }
            }
        } finally {
            routes.close();
        }
    }

    @Test
    void exposesBoundedAgentBackpressureMetrics() throws Exception {
        byte[] psk = new byte[32];
        new SecureRandom().nextBytes(psk);
        BackendRouteRegistry routes = new BackendRouteRegistry();
        RecordingListener listener = new RecordingListener();
        try (AgentHub hub = new AgentHub(
            new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
            Map.of("lobby", psk), routes, listener)) {
            hub.start();
            try (AgentSecureChannel agent = connect(hub.localPort(), psk)) {
                assertTrue(listener.firstUp.await(2, TimeUnit.SECONDS));
                agent.socket().setReceiveBufferSize(4 * 1024);
                BackendRouteRegistry.Route route = routes.readyRoute("lobby");
                assertNotNull(route);
                byte[] opus = new byte[1_200];
                Arrays.fill(opus, (byte) 0x33);
                UUID speaker = UUID.randomUUID();
                long start = System.nanoTime();
                for (int i = 0; i < 5_000; i++) {
                    assertTrue(route.sendFrame(new AkashicSpeakerFrame(speaker, i, false, opus)));
                }
                long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                assertTrue(elapsedMillis < 2_000L, "Gateway producer blocked for " + elapsedMillis + " ms");
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (hub.droppedMediaFrames() == 0L && System.nanoTime() < deadline) Thread.sleep(10L);
                assertTrue(hub.droppedMediaFrames() > 0L);
                assertTrue(hub.queuedMediaMessages() <= 256);
                assertTrue(hub.queuedControlMessages() <= 128);
            }
        } finally {
            routes.close();
            Arrays.fill(psk, (byte) 0);
        }
    }

    private static AgentSecureChannel connect(int port, byte[] psk) throws Exception {
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
        return AgentSocketHandshake.connect(socket, "lobby", psk, new SecureRandom());
    }

    private static final class RecordingListener implements AgentHub.Listener {
        final CountDownLatch firstUp = new CountDownLatch(1);
        final CountDownLatch secondUp = new CountDownLatch(2);
        final CountDownLatch down = new CountDownLatch(1);
        final AtomicInteger routeUps = new AtomicInteger();
        final AtomicInteger routeDowns = new AtomicInteger();

        @Override
        public void onRouteUp(String backendName) {
            assertEquals("lobby", backendName);
            routeUps.incrementAndGet();
            firstUp.countDown();
            secondUp.countDown();
        }

        @Override
        public void onRouteDown(String backendName) {
            assertEquals("lobby", backendName);
            routeDowns.incrementAndGet();
            down.countDown();
        }

        @Override
        public void onSvcDelivery(String backendName, BridgeDelivery delivery) {
            fail("unexpected SVC delivery");
        }

        @Override
        public void onSvcReset(String backendName, BridgeStreamReset reset) {
            fail("unexpected SVC reset");
        }

        @Override
        public void onError(String backendName, Throwable error) {
            if (error instanceof java.io.EOFException) return;
            if (error instanceof java.net.SocketException) return;
            fail("unexpected agent error for " + backendName, error);
        }
    }
}
