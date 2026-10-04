package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.agent.AgentMessage;
import dev.akashic.voice.platform.agent.AgentSecureChannel;
import dev.akashic.voice.platform.agent.AgentSocketHandshake;
import dev.akashic.voice.platform.bridge.BridgeDelivery;
import dev.akashic.voice.platform.bridge.PositionSnapshot;
import dev.akashic.voice.platform.bridge.SpatialKind;
import dev.akashic.voice.voice.common.AuthAckPacket;
import dev.akashic.voice.voice.common.SessionHandshakePackets;
import dev.akashic.voice.voice.common.MicFramePacket;
import dev.akashic.voice.voice.common.SoundFramePacket;
import dev.akashic.voice.voice.common.SoundFrameV2Packet;
import dev.akashic.voice.voice.common.SoundResetPacket;
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
    private static final int CLIENT_COUNT = 24;
    private static final int RECONNECT_CYCLES = 10;
    private static final int FRAMES_PER_CLIENT_PER_CYCLE = 50;
    private static final long FRAME_CADENCE_MILLIS = 20L;

    @RepeatedTest(3)
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
                pairRadioClients(runtime, clients);
                assertAttachSet(agent, clientIds(clients));
                GatewayStatusSnapshot initial = runtime.statusSnapshot();
                assertEquals(CLIENT_COUNT, initial.activeUdpSessions());
                assertEquals(CLIENT_COUNT, initial.sessionsOpened());
                assertEquals(CLIENT_COUNT / 2, initial.radioChannels());
                assertEquals(CLIENT_COUNT, initial.radioMembers());

                for (int cycle = 0; cycle < RECONNECT_CYCLES; cycle++) {
                    sendMicBurst(clients, cycle);
                    assertSpeakerFrames(agent, clients, cycle);
                    assertRadioFrames(clients, cycle);
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

                for (TestClient client : clients) runtime.removeRadioPlayer(client.proxyId);
                GatewayStatusSnapshot done = runtime.statusSnapshot();
                assertEquals(CLIENT_COUNT, done.sessionsOpened());
                assertEquals(CLIENT_COUNT, done.sessionsClosed());
                assertEquals(CLIENT_COUNT * RECONNECT_CYCLES * FRAMES_PER_CLIENT_PER_CYCLE,
                    done.micFramesAccepted());
                assertEquals(CLIENT_COUNT * RECONNECT_CYCLES, done.soundFramesSent());
                assertEquals(CLIENT_COUNT * RECONNECT_CYCLES * FRAMES_PER_CLIENT_PER_CYCLE, done.radioFramesSent());
                assertEquals(0L, done.groupFramesSent());
                assertEquals(CLIENT_COUNT, done.soundResetsSent());
                assertEquals(0, done.radioChannels());
                assertEquals(0, done.radioMembers());
                assertEquals(0L, done.agentMediaDropped());
                assertEquals(0L, done.helloRateLimited());
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

    @RepeatedTest(2)
    void clientsKeepRadioAndAudioAcrossBackendTransferStorm() throws Exception {
        int udpPort = freeUdpPort();
        byte[] lobbyPsk = new byte[32], normalPsk = new byte[32], forbiddenPsk = new byte[32];
        SecureRandom random = new SecureRandom();
        random.nextBytes(lobbyPsk); random.nextBytes(normalPsk); random.nextBytes(forbiddenPsk);
        RuntimeListener listener = new RuntimeListener();
        List<TestClient> clients = new ArrayList<>();
        String[] targets = {"atm10-normal", "forbidden", "lobby", "atm10-normal", "forbidden", "lobby"};
        try (BootstrapSessionRegistry registry = new BootstrapSessionRegistry();
             GatewayRuntime runtime = new GatewayRuntime(udpPort,
                 new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                 Map.of("lobby", lobbyPsk, "atm10-normal", normalPsk, "forbidden", forbiddenPsk),
                 registry, listener)) {
            runtime.start();
            try (AgentSecureChannel lobby = connectAgent(runtime.agentPort(), "lobby", lobbyPsk);
                 AgentSecureChannel normal = connectAgent(runtime.agentPort(), "atm10-normal", normalPsk);
                 AgentSecureChannel forbidden = connectAgent(runtime.agentPort(), "forbidden", forbiddenPsk)) {
                Map<String, AgentSecureChannel> agents = Map.of(
                    "lobby", lobby, "atm10-normal", normal, "forbidden", forbidden);
                await(() -> runtime.isBackendReady("lobby"), "lobby route unavailable");
                await(() -> runtime.isBackendReady("atm10-normal"), "normal route unavailable");
                await(() -> runtime.isBackendReady("forbidden"), "forbidden route unavailable");
                for (int i = 0; i < 12; i++) {
                    UUID proxyId = UUID.randomUUID();
                    BootstrapGrant grant = runtime.grantFor(proxyId, "StormPlayer" + i, "lobby");
                    assertNotNull(grant);
                    clients.add(TestClient.connect(runtime.udpPort(), proxyId, grant));
                }
                await(() -> runtime.statusSnapshot().activeUdpSessions() == clients.size(),
                    "initial storm sessions did not authenticate");
                pairRadioClients(runtime, clients);
                assertAttachSet(lobby, clientIds(clients), "lobby");
                String previousBackend = "lobby";
                for (int cycle = 0; cycle < targets.length; cycle++) {
                    String targetBackend = targets[cycle];
                    AgentSecureChannel oldAgent = agents.get(previousBackend);
                    AgentSecureChannel targetAgent = agents.get(targetBackend);
                    List<BootstrapGrant> grants = new ArrayList<>();
                    for (int i = 0; i < clients.size(); i++) {
                        BootstrapGrant grant = runtime.grantFor(clients.get(i).proxyId,
                            "StormPlayer" + i, targetBackend);
                        assertNotNull(grant);
                        grants.add(grant);
                    }
                    assertDetachSet(oldAgent, clientIds(clients));
                    assertEquals(0, runtime.statusSnapshot().activeUdpSessions(),
                        "old UDP sessions survived bulk grant rotation");
                    oldAgent.socket().setSoTimeout(350);
                    for (int i = 0; i < clients.size(); i++) {
                        clients.get(i).sendMic(90_000 + cycle * 100 + i,
                            new byte[] {(byte) cycle, (byte) i, 6, 6});
                    }
                    assertThrows(java.net.SocketTimeoutException.class, oldAgent::receive,
                        "stale storm generation routed media after transfer");
                    ExecutorService reauth = Executors.newFixedThreadPool(clients.size());
                    try {
                        List<Future<?>> futures = new ArrayList<>();
                        for (int i = 0; i < clients.size(); i++) {
                            final int index = i;
                            futures.add(reauth.submit(() -> {
                                clients.get(index).reauthenticate(grants.get(index));
                                return null;
                            }));
                        }
                        for (Future<?> future : futures) future.get(5, TimeUnit.SECONDS);
                    } finally {
                        reauth.shutdownNow();
                    }
                    await(() -> runtime.statusSnapshot().activeUdpSessions() == clients.size(),
                        "storm sessions did not reauthenticate");
                    assertAttachSet(targetAgent, clientIds(clients), targetBackend);
                    for (TestClient client : clients) {
                        assertTrue(runtime.hasVoiceSession(client.proxyId, targetBackend));
                        for (String backend : agents.keySet()) if (!backend.equals(targetBackend))
                            assertFalse(runtime.hasVoiceSession(client.proxyId, backend));
                    }
                    sendMicBurst(clients, cycle);
                    assertSpeakerFrames(targetAgent, clients, cycle);
                    assertRadioFrames(clients, cycle);
                    sendAndAssertDownlinks(targetAgent, clients, cycle);
                    previousBackend = targetBackend;
                }
                for (TestClient client : clients) client.disconnect();
                assertDetachSet(agents.get(previousBackend), clientIds(clients));
                await(() -> runtime.statusSnapshot().activeUdpSessions() == 0,
                    "storm UDP sessions did not drain");
                for (TestClient client : clients) runtime.removeRadioPlayer(client.proxyId);
                GatewayStatusSnapshot done = runtime.statusSnapshot();
                long expectedMedia = (long) clients.size() * targets.length * FRAMES_PER_CLIENT_PER_CYCLE;
                assertEquals((long) clients.size() * (targets.length + 1), done.sessionsOpened());
                assertEquals(done.sessionsOpened(), done.sessionsClosed());
                assertEquals(expectedMedia, done.micFramesAccepted());
                assertEquals(expectedMedia, done.radioFramesSent());
                assertEquals((long) clients.size() * targets.length, done.soundFramesSent());
                assertEquals(0L, done.agentMediaDropped());
                assertEquals(0L, done.encryptedRejected());
                assertTrue(listener.errors.isEmpty(), () -> "Runtime errors: " + listener.errors);
            }
        } finally {
            for (TestClient client : clients) client.close();
            Arrays.fill(lobbyPsk, (byte) 0); Arrays.fill(normalPsk, (byte) 0); Arrays.fill(forbiddenPsk, (byte) 0);
        }
    }

    private static void createClients(GatewayRuntime runtime, BootstrapSessionRegistry registry,
                                      List<TestClient> clients) throws Exception {
        for (int i = 0; i < CLIENT_COUNT; i++) {
            UUID proxyId = UUID.randomUUID();
            BootstrapGrant grant = runtime.grantFor(proxyId, "SoakPlayer" + i, "lobby");
            assertNotNull(grant);
            clients.add(TestClient.connect(runtime.udpPort(), proxyId, grant));
            if (i + 1 < CLIENT_COUNT) Thread.sleep(60L);
        }
        await(() -> runtime.statusSnapshot().activeUdpSessions() == CLIENT_COUNT,
            "not all UDP clients authenticated");
    }

    private static void pairRadioClients(GatewayRuntime runtime, List<TestClient> clients) throws Exception {
        assertEquals(0, clients.size() & 1, "soak client count must be even");
        for (int i = 0; i < clients.size(); i += 2) {
            TestClient first = clients.get(i);
            TestClient second = clients.get(i + 1);
            String channel = "soak-" + (i / 2);
            assertTrue(runtime.tuneRadio(first.proxyId, channel).changed());
            assertTrue(runtime.tuneRadio(second.proxyId, channel).changed());
            first.assertReset(second.proxyId);
            second.assertReset(first.proxyId);
        }
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
                        Thread.sleep(FRAME_CADENCE_MILLIS);
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) future.get(10, TimeUnit.SECONDS);
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

    private static void assertRadioFrames(List<TestClient> clients, int cycle) throws Exception {
        for (int index = 0; index < clients.size(); index++) {
            TestClient recipient = clients.get(index);
            TestClient sender = clients.get(index ^ 1);
            Set<Integer> sequences = new HashSet<>();
            for (int i = 0; i < FRAMES_PER_CLIENT_PER_CYCLE; i++) {
                SoundFramePacket frame = recipient.receiveRadio();
                assertEquals(sender.proxyId, frame.senderId);
                assertFalse(frame.isSpatial());
                sequences.add(frame.sequence);
                int frameIndex = frame.sequence - cycle * 1_000;
                assertTrue(frameIndex >= 0 && frameIndex < FRAMES_PER_CLIENT_PER_CYCLE);
                assertArrayEquals(new byte[] {(byte) cycle, (byte) frameIndex, (byte) (index ^ 1), 42},
                    Arrays.copyOfRange(frame.opusData, frame.opusOffset, frame.opusOffset + frame.opusLength));
            }
            assertEquals(FRAMES_PER_CLIENT_PER_CYCLE, sequences.size());
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
        assertAttachSet(agent, expected, "lobby");
    }

    private static void assertAttachSet(AgentSecureChannel agent, Set<UUID> expected, String backend) throws Exception {
        Set<UUID> actual = new HashSet<>();
        for (int i = 0; i < expected.size(); i++) {
            AgentMessage.Attach attach = assertInstanceOf(AgentMessage.Attach.class, agent.receive());
            actual.add(attach.player().playerId());
            assertEquals(backend, attach.player().backendId());
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
        return connectAgent(port, "lobby", psk);
    }

    private static AgentSecureChannel connectAgent(int port, String backend, byte[] psk) throws Exception {
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
        socket.setSoTimeout(3_000);
        return AgentSocketHandshake.connect(socket, backend, psk, new SecureRandom());
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
        private SessionCrypto crypto;
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
                SessionCrypto crypto = authenticate(socket, grant, secret, clientNonce);
                return new TestClient(proxyId, socket, crypto);
            } catch (Exception failure) {
                socket.close();
                throw failure;
            } finally {
                Arrays.fill(secret, (byte) 0);
                Arrays.fill(clientNonce, (byte) 0);
            }
        }
        void reauthenticate(BootstrapGrant grant) throws Exception {
            byte[] secret = grant.masterSecretCopy();
            byte[] clientNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
            new SecureRandom().nextBytes(clientNonce);
            SessionCrypto next = null;
            try {
                next = authenticate(socket, grant, secret, clientNonce);
                SessionCrypto old = crypto;
                crypto = next;
                next = null;
                old.close();
            } finally {
                if (next != null) next.close();
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

        SoundFramePacket receiveRadio() throws Exception {
            ByteBuffer plain = ByteBuffer.allocate(1_500);
            assertTrue(crypto.open(receive(socket), plain));
            assertEquals(WirePacketType.SOUND_FRAME, plain.get());
            SoundFramePacket sound = SoundFramePacket.read(plain);
            assertNotNull(sound);
            return sound;
        }

        void assertReset(UUID senderId) throws Exception {
            ByteBuffer plain = ByteBuffer.allocate(256);
            assertTrue(crypto.open(receive(socket), plain));
            assertEquals(WirePacketType.SOUND_RESET, plain.get());
            SoundResetPacket reset = SoundResetPacket.read(plain);
            assertNotNull(reset);
            assertEquals(senderId, reset.senderId);
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

    private static SessionCrypto authenticate(DatagramSocket client, BootstrapGrant grant,
                                              byte[] secret, byte[] clientNonce) throws Exception {
        ByteBuffer hello = ByteBuffer.allocate(SessionHandshakePackets.HELLO_BYTES);
        SessionHandshakePackets.writeHello(hello, grant.sessionId(), grant.voiceIdentity(), clientNonce);
        send(client, Arrays.copyOf(hello.array(), hello.position()));

        SessionHandshakePackets.Challenge challenge = SessionHandshakePackets.readChallenge(receive(client));
        assertNotNull(challenge);
        assertEquals(grant.sessionId(), challenge.sessionId);
        SessionKeys keys = SessionKeys.derive(secret, clientNonce, challenge.serverNonce, grant.sessionId());
        byte[] clientProof = null;
        SessionCrypto crypto = null;
        try {
            assertTrue(HandshakeProof.verifyServer(
                keys, grant.voiceIdentity(), clientNonce, challenge.serverNonce, challenge.serverProof));
            clientProof = HandshakeProof.computeClient(
                keys, grant.voiceIdentity(), clientNonce, challenge.serverNonce);
            crypto = SessionCrypto.forClient(keys, grant.sessionId());
        } finally {
            keys.close();
            Arrays.fill(challenge.serverNonce, (byte) 0);
            Arrays.fill(challenge.serverProof, (byte) 0);
        }
        ByteBuffer response = ByteBuffer.allocate(SessionHandshakePackets.AUTH_RESPONSE_BYTES);
        SessionHandshakePackets.writeAuthResponse(response, grant.sessionId(), clientProof);
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
