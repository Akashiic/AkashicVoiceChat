package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.agent.AgentMessage;
import dev.akashic.voice.platform.agent.AgentSecureChannel;
import dev.akashic.voice.platform.agent.AgentSocketHandshake;
import dev.akashic.voice.platform.bridge.BridgeDelivery;
import dev.akashic.voice.platform.bridge.PositionSnapshot;
import dev.akashic.voice.platform.bridge.SpatialKind;
import dev.akashic.voice.voice.common.AuthAckPacket;
import dev.akashic.voice.voice.common.HandshakePackets;
import dev.akashic.voice.voice.common.MicFramePacket;
import dev.akashic.voice.voice.common.SoundFrameV2Packet;
import dev.akashic.voice.voice.common.WirePacketType;
import dev.akashic.voice.voice.common.crypto.HandshakeProof;
import dev.akashic.voice.voice.common.crypto.SessionCrypto;
import dev.akashic.voice.voice.common.crypto.SessionKeys;
import org.junit.jupiter.api.RepeatedTest;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

final class GatewayRuntimeSoakTest {
    private static final int CLIENT_COUNT = 20;
    private static final int RECONNECT_CYCLES = 4;
    private static final int FRAMES_PER_CLIENT_PER_CYCLE = 8;

    @RepeatedTest(5)
    void clientsSurviveRepeatedAgentReconnectsWithoutSessionLeaks() throws Exception {
        int udpPort = freeUdpPort();
        byte[] psk = new byte[32];
        new SecureRandom().nextBytes(psk);
        RuntimeListener listener = new RuntimeListener();
        List<TestClient> clients = new ArrayList<>();

        try (BootstrapSessionRegistry registry = new BootstrapSessionRegistry();
             GatewayRuntime runtime = new GatewayRuntime(
                 udpPort,
                 new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                 Map.of("lobby", psk), registry, listener)) {
            runtime.start();
            AgentSecureChannel agent = connectAgent(runtime.agentPort(), psk);
            try {
                listener.awaitRouteUps(1);
                createClients(runtime, registry, clients);
                assertAttachSet(agent, clientIds(clients));
                GatewayStatusSnapshot initial = runtime.statusSnapshot();
                assertEquals(CLIENT_COUNT, initial.activeUdpSessions());
                assertEquals(CLIENT_COUNT, initial.sessionsOpened());

                for (int cycle = 0; cycle < RECONNECT_CYCLES; cycle++) {
                    sendMicBurst(clients, cycle);
                    assertSpeakerFrames(agent, clients, cycle);
                    sendAndAssertDownlinks(agent, clients, cycle);

                    if (cycle + 1 < RECONNECT_CYCLES) {
                        int expectedDowns = cycle + 1;
                        int expectedUps = cycle + 2;
                        agent.socket().close();
                        agent.close();
                        listener.awaitRouteDowns(expectedDowns);

                        agent = connectAgent(runtime.agentPort(), psk);
                        listener.awaitRouteUps(expectedUps);
                        assertAttachSet(agent, clientIds(clients));
                        assertEquals(CLIENT_COUNT, runtime.statusSnapshot().activeUdpSessions());
                    }
                }

                for (TestClient client : clients) client.disconnect();
                assertDetachSet(agent, clientIds(clients));
                await(() -> runtime.statusSnapshot().activeUdpSessions() == 0,
                    "UDP sessions did not drain after disconnect");

                GatewayStatusSnapshot done = runtime.statusSnapshot();
                assertEquals(CLIENT_COUNT, done.sessionsOpened());
                assertEquals(CLIENT_COUNT, done.sessionsClosed());
                assertEquals(CLIENT_COUNT * RECONNECT_CYCLES * FRAMES_PER_CLIENT_PER_CYCLE,
                    done.micFramesAccepted());
                assertEquals(CLIENT_COUNT * RECONNECT_CYCLES, done.soundFramesSent());
                assertEquals(0L, done.soundResetsSent());
                assertEquals(0L, done.mediaRateLimited());
                assertEquals(0L, done.encryptedRejected());
                assertTrue(listener.errors.isEmpty(), () -> "Runtime errors: " + listener.errors);
            } finally {
                agent.close();
            }
        } finally {
            for (TestClient client : clients) client.close();
            Arrays.fill(psk, (byte) 0);
        }
    }

    private static void createClients(GatewayRuntime runtime, BootstrapSessionRegistry registry,
                                      List<TestClient> clients) throws Exception {
        for (int i = 0; i < CLIENT_COUNT; i++) {
            UUID proxyId = UUID.randomUUID();
            BootstrapGrant grant = runtime.grantFor(proxyId, "SoakPlayer" + i, "lobby");
            assertNotNull(grant);
            clients.add(TestClient.connect(runtime.udpPort(), proxyId, grant));
        }
        await(() -> runtime.statusSnapshot().activeUdpSessions() == CLIENT_COUNT,
            "not all UDP clients authenticated");
    }

    private static void sendMicBurst(List<TestClient> clients, int cycle) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(clients.size());
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int clientIndex = 0; clientIndex < clients.size(); clientIndex++) {
                final int index = clientIndex;
                futures.add(executor.submit(() -> {
                    for (int frame = 0; frame < FRAMES_PER_CLIENT_PER_CYCLE; frame++) {
                        int sequence = cycle * 1_000 + frame;
                        byte[] opus = {(byte) cycle, (byte) frame, (byte) index, 42};
                        clients.get(index).sendMic(sequence, opus);
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) future.get(3, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void assertSpeakerFrames(AgentSecureChannel agent,
                                            List<TestClient> clients, int cycle) throws Exception {
        int expected = clients.size() * FRAMES_PER_CLIENT_PER_CYCLE;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < expected; i++) {
            AgentMessage.SpeakerFrame message = assertInstanceOf(
                AgentMessage.SpeakerFrame.class, agent.receive());
            seen.add(message.frame().speakerId() + ":" + message.frame().sequence());
        }
        assertEquals(expected, seen.size(), "duplicate/missing speaker frames in cycle " + cycle);
        for (TestClient client : clients) {
            for (int frame = 0; frame < FRAMES_PER_CLIENT_PER_CYCLE; frame++) {
                long sequence = cycle * 1_000L + frame;
                assertTrue(seen.contains(client.proxyId + ":" + sequence));
            }
        }
    }

    private static void sendAndAssertDownlinks(AgentSecureChannel agent,
                                                List<TestClient> clients, int cycle) throws Exception {
        for (int index = 0; index < clients.size(); index++) {
            TestClient client = clients.get(index);
            UUID senderId = UUID.nameUUIDFromBytes(("svc-" + cycle + "-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            long sequence = 50_000L + cycle * 100L + index;
            byte[] opus = {(byte) 90, (byte) cycle, (byte) index, 7};
            boolean whispering = (cycle & 1) != 0;
            BridgeDelivery delivery = new BridgeDelivery(
                client.proxyId, senderId, UUID.randomUUID(), sequence,
                SpatialKind.LOCATION, null,
                new PositionSnapshot("minecraft:overworld", 10.0 + index, 64.0, -5.0),
                24F, whispering, "proximity", opus);
            assertTrue(agent.send(new AgentMessage.SvcDelivery(delivery)));
            client.assertSound(senderId, (int) sequence, 24F, whispering, opus);
        }
    }

    private static Set<UUID> clientIds(List<TestClient> clients) {
        Set<UUID> ids = new HashSet<>();
        for (TestClient client : clients) ids.add(client.proxyId);
        return ids;
    }

    private static void assertAttachSet(AgentSecureChannel agent, Set<UUID> expected) throws Exception {
        Set<UUID> actual = new HashSet<>();
        for (int i = 0; i < expected.size(); i++) {
            AgentMessage.Attach attach = assertInstanceOf(AgentMessage.Attach.class, agent.receive());
            actual.add(attach.player().playerId());
            assertEquals("lobby", attach.player().backendId());
        }
        assertEquals(expected, actual);
    }

    private static void assertDetachSet(AgentSecureChannel agent, Set<UUID> expected) throws Exception {
        Set<UUID> actual = new HashSet<>();
        for (int i = 0; i < expected.size(); i++) {
            AgentMessage.Detach detach = assertInstanceOf(AgentMessage.Detach.class, agent.receive());
            actual.add(detach.playerId());
        }
        assertEquals(expected, actual);
    }

    private static AgentSecureChannel connectAgent(int port, byte[] psk) throws Exception {
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
        socket.setSoTimeout(3_000);
        return AgentSocketHandshake.connect(socket, "lobby", psk, new SecureRandom());
    }
    private static void await(BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertTrue(condition.getAsBoolean(), message);
    }

    private static int freeUdpPort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static final class TestClient implements AutoCloseable {
        private final UUID proxyId;
        private final DatagramSocket socket;
        private final SessionCrypto crypto;
        private boolean disconnected;

        private TestClient(UUID proxyId, DatagramSocket socket, SessionCrypto crypto) {
            this.proxyId = proxyId;
            this.socket = socket;
            this.crypto = crypto;
        }

        static TestClient connect(int port, UUID proxyId, BootstrapGrant grant) throws Exception {
            DatagramSocket socket = new DatagramSocket();
            socket.connect(InetAddress.getLoopbackAddress(), port);
            socket.setSoTimeout(3_000);
            byte[] secret = grant.masterSecretCopy();
            byte[] clientNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
            new SecureRandom().nextBytes(clientNonce);
            try {
                SessionCrypto crypto = authenticate(socket, grant.voiceIdentity(), secret, clientNonce);
                return new TestClient(proxyId, socket, crypto);
            } catch (Exception failure) {
                socket.close();
                throw failure;
            } finally {
                Arrays.fill(secret, (byte) 0);
                Arrays.fill(clientNonce, (byte) 0);
            }
        }
        void sendMic(int sequence, byte[] opus) throws Exception {
            ByteBuffer inner = ByteBuffer.allocate(1_500);
            MicFramePacket.write(inner, sequence, opus, opus.length);
            ByteBuffer encrypted = ByteBuffer.allocate(1_500);
            assertTrue(crypto.seal(inner, encrypted));
            send(socket, Arrays.copyOf(encrypted.array(), encrypted.position()));
        }

        void assertSound(UUID senderId, int sequence, float maxDistance,
                         boolean whispering, byte[] opus) throws Exception {
            ByteBuffer plain = ByteBuffer.allocate(1_500);
            assertTrue(crypto.open(receive(socket), plain));
            assertEquals(WirePacketType.SOUND_FRAME_V2, plain.get());
            SoundFrameV2Packet sound = SoundFrameV2Packet.read(plain);
            assertNotNull(sound);
            assertEquals(senderId, sound.senderId);
            assertEquals(sequence, sound.sequence);
            assertEquals(maxDistance, sound.maxDistance);
            assertEquals(whispering, sound.isWhispering());
            assertArrayEquals(opus, Arrays.copyOfRange(
                sound.opusData, sound.opusOffset, sound.opusOffset + sound.opusLength));
        }

        void disconnect() throws Exception {
            if (disconnected) return;
            ByteBuffer inner = ByteBuffer.allocate(16);
            inner.put(WirePacketType.DISCONNECT);
            ByteBuffer encrypted = ByteBuffer.allocate(128);
            assertTrue(crypto.seal(inner, encrypted));
            send(socket, Arrays.copyOf(encrypted.array(), encrypted.position()));
            disconnected = true;
        }

        @Override
        public void close() {
            crypto.close();
            socket.close();
        }
    }

    private static SessionCrypto authenticate(DatagramSocket client, UUID voiceIdentity,
                                              byte[] secret, byte[] clientNonce) throws Exception {
        ByteBuffer hello = ByteBuffer.allocate(HandshakePackets.HELLO_BYTES);
        HandshakePackets.writeHello(hello, voiceIdentity, clientNonce);
        send(client, Arrays.copyOf(hello.array(), hello.position()));

        HandshakePackets.Challenge challenge = HandshakePackets.readChallenge(receive(client));
        assertNotNull(challenge);
        SessionKeys keys = SessionKeys.derive(secret, clientNonce, challenge.serverNonce);
        byte[] clientProof = null;
        SessionCrypto crypto = null;
        try {
            assertTrue(HandshakeProof.verifyServer(
                keys, voiceIdentity, clientNonce, challenge.serverNonce, challenge.serverProof));
            clientProof = HandshakeProof.computeClient(
                keys, voiceIdentity, clientNonce, challenge.serverNonce);
            crypto = SessionCrypto.forClient(keys);
        } finally {
            keys.close();
            Arrays.fill(challenge.serverNonce, (byte) 0);
            Arrays.fill(challenge.serverProof, (byte) 0);
        }
        ByteBuffer response = ByteBuffer.allocate(HandshakePackets.AUTH_RESPONSE_BYTES);
        HandshakePackets.writeAuthResponse(response, clientProof);
        send(client, Arrays.copyOf(response.array(), response.position()));
        Arrays.fill(clientProof, (byte) 0);

        ByteBuffer plain = ByteBuffer.allocate(256);
        assertTrue(crypto.open(receive(client), plain));
        assertEquals(WirePacketType.AUTH_ACK, plain.get());
        AuthAckPacket ack = AuthAckPacket.read(plain);
        assertNotNull(ack);
        assertEquals(AuthAckPacket.STATUS_OK, ack.status);
        return crypto;
    }

    private static void send(DatagramSocket socket, byte[] bytes) throws Exception {
        socket.send(new DatagramPacket(bytes, bytes.length));
    }

    private static ByteBuffer receive(DatagramSocket socket) throws Exception {
        byte[] buffer = new byte[1_500];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        return ByteBuffer.wrap(Arrays.copyOf(packet.getData(), packet.getLength()));
    }

    private static final class RuntimeListener implements GatewayRuntime.Listener {
        private final AtomicInteger routeUps = new AtomicInteger();
        private final AtomicInteger routeDowns = new AtomicInteger();
        private final List<Throwable> errors = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void onRouteUp(String backendName) {
            if ("lobby".equals(backendName)) routeUps.incrementAndGet();
        }

        @Override
        public void onRouteDown(String backendName) {
            if ("lobby".equals(backendName)) routeDowns.incrementAndGet();
        }
        @Override
        public void onError(String component, Throwable error) {
            errors.add(new RuntimeException(component, error));
        }

        void awaitRouteUps(int expected) throws Exception {
            await(() -> routeUps.get() >= expected,
                "routeUp count did not reach " + expected + ", got " + routeUps.get());
        }

        void awaitRouteDowns(int expected) throws Exception {
            await(() -> routeDowns.get() >= expected,
                "routeDown count did not reach " + expected + ", got " + routeDowns.get());
        }
    }
}
