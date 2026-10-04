package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.agent.AgentMessage;
import dev.akashic.voice.platform.agent.AgentSecureChannel;
import dev.akashic.voice.platform.agent.AgentSocketHandshake;
import dev.akashic.voice.platform.bridge.BridgeDelivery;
import dev.akashic.voice.platform.bridge.FederatedGroup;
import dev.akashic.voice.platform.bridge.PositionSnapshot;
import dev.akashic.voice.platform.bridge.SpatialKind;
import dev.akashic.voice.voice.common.AuthAckPacket;
import dev.akashic.voice.voice.common.HandshakePackets;
import dev.akashic.voice.voice.common.MicFramePacket;
import dev.akashic.voice.voice.common.SoundFramePacket;
import dev.akashic.voice.voice.common.SoundFrameV2Packet;
import dev.akashic.voice.voice.common.WirePacketType;
import dev.akashic.voice.voice.common.crypto.HandshakeProof;
import dev.akashic.voice.voice.common.crypto.SessionCrypto;
import dev.akashic.voice.voice.common.crypto.SessionKeys;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

final class GatewayFederatedGroupRoutingTest {
    @Test
    void groupFanoutIsNonSpatialAndSuppressesSvcDuplicateOnlyForMembers() throws Exception {
        int udpPort = freeUdpPort();
        byte[] psk = new byte[32];
        new SecureRandom().nextBytes(psk);
        Listener listener = new Listener();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        try (BootstrapSessionRegistry registry = new BootstrapSessionRegistry();
             GatewayRuntime runtime = new GatewayRuntime(
                 udpPort,
                 new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                 Map.of("lobby", psk), registry, listener)) {
            runtime.start();
            try (AgentSecureChannel agent = connectAgent(runtime.agentPort(), psk)) {
                assertTrue(listener.routeUp.await(2, TimeUnit.SECONDS));
                try (Peer first = Peer.connect(runtime, firstId, "First");
                     Peer second = Peer.connect(runtime, secondId, "Second")) {
                    assertAttachSet(agent, Set.of(firstId, secondId));

                FederatedGroup group = runtime.groupDirectory().create(firstId, "Squad").orElseThrow();
                assertTrue(runtime.groupDirectory().invite(firstId, group.id(), secondId));
                assertEquals(
                    dev.akashic.voice.platform.bridge.FederatedGroupDirectory.InviteResponse.JOINED,
                    runtime.groupDirectory().respondToInvite(secondId, group.id(), true));

                byte[] mic = {4, 2, 4, 2};
                first.sendMic(77, mic);
                AgentMessage.SpeakerFrame speaker = assertInstanceOf(
                    AgentMessage.SpeakerFrame.class, agent.receive());
                assertEquals(firstId, speaker.frame().speakerId());
                assertEquals(77L, speaker.frame().sequence());

                SoundFramePacket grouped = second.receiveGroupFrame();
                assertEquals(firstId, grouped.senderId);
                assertEquals(77, grouped.sequence);
                assertFalse(grouped.isSpatial());
                assertArrayEquals(mic, Arrays.copyOfRange(
                    grouped.opusData, grouped.opusOffset, grouped.opusOffset + grouped.opusLength));
                first.assertNoDatagram();

                BridgeDelivery duplicate = new BridgeDelivery(
                    secondId, firstId, UUID.randomUUID(), 900L,
                    SpatialKind.LOCATION, null,
                    new PositionSnapshot("minecraft:overworld", 1, 64, 1),
                    48F, false, "proximity", new byte[] {9, 9, 9});
                assertTrue(agent.send(new AgentMessage.SvcDelivery(duplicate)));
                second.assertNoDatagram();

                assertTrue(runtime.groupDirectory().leave(secondId, group.id()));
                BridgeDelivery nowAllowed = new BridgeDelivery(
                    secondId, firstId, UUID.randomUUID(), 901L,
                    SpatialKind.LOCATION, null,
                    new PositionSnapshot("minecraft:overworld", 2, 64, 2),
                    48F, false, "proximity", new byte[] {8, 8, 8});
                assertTrue(agent.send(new AgentMessage.SvcDelivery(nowAllowed)));
                SoundFrameV2Packet spatial = second.receiveSvcFrame();
                assertEquals(firstId, spatial.senderId);
                assertEquals(901, spatial.sequence);
                assertTrue(spatial.isSpatial());

                GatewayStatusSnapshot status = runtime.statusSnapshot();
                assertEquals(1L, status.groupFramesSent());
                assertEquals(1L, status.soundFramesSent());
                assertEquals(1, status.federatedGroups());
                assertEquals(1, status.federatedMembers());
                assertTrue(listener.errors.isEmpty(), () -> "Runtime errors: " + listener.errors);
                }
            }
        } finally {
            Arrays.fill(psk, (byte) 0);
        }
    }

    private static void assertAttachSet(AgentSecureChannel agent, Set<UUID> expected) throws Exception {
        Set<UUID> actual = new HashSet<>();
        for (int i = 0; i < expected.size(); i++) {
            AgentMessage.Attach attach = assertInstanceOf(AgentMessage.Attach.class, agent.receive());
            actual.add(attach.player().playerId());
        }
        assertEquals(expected, actual);
    }

    private static AgentSecureChannel connectAgent(int port, byte[] psk) throws Exception {
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
        socket.setSoTimeout(2_000);
        return AgentSocketHandshake.connect(socket, "lobby", psk, new SecureRandom());
    }

    private static int freeUdpPort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static final class Peer implements AutoCloseable {
        final UUID proxyId;
        final DatagramSocket socket;
        final SessionCrypto crypto;

        private Peer(UUID proxyId, DatagramSocket socket, SessionCrypto crypto) {
            this.proxyId = proxyId;
            this.socket = socket;
            this.crypto = crypto;
        }

        static Peer connect(GatewayRuntime runtime, UUID proxyId, String name) throws Exception {
            BootstrapGrant grant = runtime.grantFor(proxyId, name, "lobby");
            assertNotNull(grant);
            DatagramSocket socket = new DatagramSocket();
            socket.connect(InetAddress.getLoopbackAddress(), runtime.udpPort());
            socket.setSoTimeout(2_000);
            byte[] secret = grant.masterSecretCopy();
            byte[] clientNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
            new SecureRandom().nextBytes(clientNonce);
            try {
                return new Peer(proxyId, socket,
                    authenticate(socket, grant.voiceIdentity(), secret, clientNonce));
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

        SoundFramePacket receiveGroupFrame() throws Exception {
            ByteBuffer plain = openNext();
            assertEquals(WirePacketType.SOUND_FRAME, plain.get());
            SoundFramePacket packet = SoundFramePacket.read(plain);
            assertNotNull(packet);
            return packet;
        }

        SoundFrameV2Packet receiveSvcFrame() throws Exception {
            ByteBuffer plain = openNext();
            assertEquals(WirePacketType.SOUND_FRAME_V2, plain.get());
            SoundFrameV2Packet packet = SoundFrameV2Packet.read(plain);
            assertNotNull(packet);
            return packet;
        }

        void assertNoDatagram() throws Exception {
            socket.setSoTimeout(250);
            try {
                assertThrows(SocketTimeoutException.class, () -> receive(socket));
            } finally {
                socket.setSoTimeout(2_000);
            }
        }

        private ByteBuffer openNext() throws Exception {
            ByteBuffer plain = ByteBuffer.allocate(1_500);
            assertTrue(crypto.open(receive(socket), plain));
            return plain;
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

    private static final class Listener implements GatewayRuntime.Listener {
        final CountDownLatch routeUp = new CountDownLatch(1);
        final java.util.concurrent.CopyOnWriteArrayList<Throwable> errors =
            new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void onRouteUp(String backendName) {
            if ("lobby".equals(backendName)) routeUp.countDown();
        }

        @Override
        public void onRouteDown(String backendName) {
        }

        @Override
        public void onError(String component, Throwable error) {
            errors.add(new RuntimeException(component, error));
        }
    }
}
