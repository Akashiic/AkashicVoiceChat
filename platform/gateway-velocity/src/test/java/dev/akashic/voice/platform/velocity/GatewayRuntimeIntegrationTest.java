package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.agent.AgentMessage;
import dev.akashic.voice.platform.agent.AgentSecureChannel;
import dev.akashic.voice.platform.agent.AgentSocketHandshake;
import dev.akashic.voice.platform.bridge.BridgeDelivery;
import dev.akashic.voice.platform.bridge.BridgeStreamReset;
import dev.akashic.voice.platform.bridge.PositionSnapshot;
import dev.akashic.voice.platform.bridge.SpatialKind;
import dev.akashic.voice.voice.common.AuthAckPacket;
import dev.akashic.voice.voice.common.HandshakePackets;
import dev.akashic.voice.voice.common.MicFramePacket;
import dev.akashic.voice.voice.common.SoundFramePacket;
import dev.akashic.voice.voice.common.SoundFrameV2Packet;
import dev.akashic.voice.voice.common.SoundResetPacket;
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
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

final class GatewayRuntimeIntegrationTest {
    @Test
    void bridgesUdpClientAndAuthenticatedBackendAgentBothWays() throws Exception {
        int udpPort = freeUdpPort();
        byte[] psk = new byte[32];
        new SecureRandom().nextBytes(psk);
        UUID proxyPlayerId = UUID.randomUUID();
        RuntimeListener listener = new RuntimeListener();

        try (BootstrapSessionRegistry registry = new BootstrapSessionRegistry();
             GatewayRuntime runtime = new GatewayRuntime(
                 udpPort,
                 new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                 Map.of("lobby", psk),
                 registry,
                 listener)) {
            runtime.start();
            try (AgentSecureChannel agent = connectAgent(runtime.agentPort(), psk);
                 DatagramSocket client = new DatagramSocket()) {
                assertTrue(listener.routeUp.await(2, TimeUnit.SECONDS));
                assertTrue(runtime.isBackendReady("lobby"));
                BootstrapGrant grant = runtime.grantFor(proxyPlayerId, "TestPlayer", "lobby");
                assertNotNull(grant);
                client.connect(InetAddress.getLoopbackAddress(), runtime.udpPort());
                client.setSoTimeout(2_000);
                agent.socket().setSoTimeout(2_000);

                byte[] secret = grant.masterSecretCopy();
                byte[] clientNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
                new SecureRandom().nextBytes(clientNonce);
                SessionCrypto clientCrypto = null;
                try {
                    clientCrypto = authenticate(client, grant.voiceIdentity(), secret, clientNonce);

                    AgentMessage.Attach attach = assertInstanceOf(
                        AgentMessage.Attach.class, agent.receive());
                    assertEquals(proxyPlayerId, attach.player().playerId());
                    assertEquals("lobby", attach.player().backendId());
                    assertEquals("TestPlayer", attach.player().playerName());

                    byte[] micOpus = new byte[] {1, 4, 9, 16, 25};
                    sendEncryptedMic(client, clientCrypto, 77, micOpus);
                    AgentMessage.SpeakerFrame speaker = assertInstanceOf(
                        AgentMessage.SpeakerFrame.class, agent.receive());
                    assertEquals(proxyPlayerId, speaker.frame().speakerId());
                    assertEquals(77L, speaker.frame().sequence());
                    assertArrayEquals(micOpus, speaker.frame().opusUnsafe());
                    UUID svcSender = UUID.randomUUID();
                    UUID svcChannel = UUID.randomUUID();
                    byte[] downlinkOpus = new byte[] {3, 1, 4, 1, 5, 9};
                    BridgeDelivery delivery = new BridgeDelivery(
                        proxyPlayerId,
                        svcSender,
                        svcChannel,
                        9001L,
                        SpatialKind.LOCATION,
                        null,
                        new PositionSnapshot("minecraft:overworld", 12.5, 70.0, -2.25),
                        23.5F,
                        true,
                        "proximity",
                        downlinkOpus);
                    assertTrue(agent.send(new AgentMessage.SvcDelivery(delivery)));

                    SoundFrameV2Packet sound = receiveSound(client, clientCrypto);
                    assertEquals(svcSender, sound.senderId);
                    assertEquals(9001, sound.sequence);
                    assertTrue(sound.isSpatial());
                    assertEquals(12.5F, sound.x);
                    assertEquals(70F, sound.y);
                    assertEquals(-2.25F, sound.z);
                    assertEquals(23.5F, sound.maxDistance);
                    assertTrue(sound.isWhispering());
                    assertArrayEquals(
                        downlinkOpus,
                        Arrays.copyOfRange(sound.opusData, sound.opusOffset, sound.opusOffset + sound.opusLength));
                    BridgeStreamReset reset = new BridgeStreamReset(
                        proxyPlayerId, svcSender, svcChannel);
                    assertTrue(agent.send(new AgentMessage.SvcReset(reset)));
                    SoundResetPacket soundReset = receiveReset(client, clientCrypto);
                    assertEquals(svcSender, soundReset.senderId);

                    sendDisconnect(client, clientCrypto);
                    AgentMessage.Detach detach = assertInstanceOf(
                        AgentMessage.Detach.class, agent.receive());
                    assertEquals(proxyPlayerId, detach.playerId());
                    assertTrue(listener.errors.isEmpty(),
                        () -> "Runtime errors: " + listener.errors);
                } finally {
                    if (clientCrypto != null) clientCrypto.close();
                    Arrays.fill(secret, (byte) 0);
                    Arrays.fill(clientNonce, (byte) 0);
                }
            }
        } finally {
            Arrays.fill(psk, (byte) 0);
        }
    }

    private static AgentSecureChannel connectAgent(int port, byte[] psk) throws Exception {
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
        return AgentSocketHandshake.connect(socket, "lobby", psk, new SecureRandom());
    }
    private static SessionCrypto authenticate(
        DatagramSocket client,
        UUID voiceIdentity,
        byte[] secret,
        byte[] clientNonce
    ) throws Exception {
        ByteBuffer hello = ByteBuffer.allocate(HandshakePackets.HELLO_BYTES);
        HandshakePackets.writeHello(hello, voiceIdentity, clientNonce);
        send(client, Arrays.copyOf(hello.array(), hello.position()));

        HandshakePackets.Challenge challenge = HandshakePackets.readChallenge(receive(client));
        assertNotNull(challenge, "Gateway challenge missing");
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

        ByteBuffer plain = ByteBuffer.allocate(1_500);
        assertTrue(crypto.open(receive(client), plain), "Encrypted AUTH_ACK did not authenticate");
        assertEquals(WirePacketType.AUTH_ACK, plain.get());
        AuthAckPacket ack = AuthAckPacket.read(plain);
        assertNotNull(ack);
        assertEquals(AuthAckPacket.STATUS_OK, ack.status);
        return crypto;
    }

    private static void sendEncryptedMic(
        DatagramSocket client,
        SessionCrypto crypto,
        int sequence,
        byte[] opus
    ) throws Exception {
        ByteBuffer inner = ByteBuffer.allocate(1_500);
        MicFramePacket.write(inner, sequence, opus, opus.length);
        ByteBuffer encrypted = ByteBuffer.allocate(1_500);
        assertTrue(crypto.seal(inner, encrypted));
        send(client, Arrays.copyOf(encrypted.array(), encrypted.position()));
    }
    private static SoundFrameV2Packet receiveSound(
        DatagramSocket client,
        SessionCrypto crypto
    ) throws Exception {
        ByteBuffer plain = ByteBuffer.allocate(1_500);
        assertTrue(crypto.open(receive(client), plain), "SOUND datagram failed AEAD verification");
        assertEquals(WirePacketType.SOUND_FRAME_V2, plain.get());
        SoundFrameV2Packet packet = SoundFrameV2Packet.read(plain);
        assertNotNull(packet);
        return packet;
    }

    private static SoundResetPacket receiveReset(
        DatagramSocket client,
        SessionCrypto crypto
    ) throws Exception {
        ByteBuffer plain = ByteBuffer.allocate(256);
        assertTrue(crypto.open(receive(client), plain), "RESET datagram failed AEAD verification");
        assertEquals(WirePacketType.SOUND_RESET, plain.get());
        SoundResetPacket packet = SoundResetPacket.read(plain);
        assertNotNull(packet);
        return packet;
    }
    private static void sendDisconnect(
        DatagramSocket client,
        SessionCrypto crypto
    ) throws Exception {
        ByteBuffer inner = ByteBuffer.allocate(16);
        inner.put(WirePacketType.DISCONNECT);
        ByteBuffer encrypted = ByteBuffer.allocate(128);
        assertTrue(crypto.seal(inner, encrypted));
        send(client, Arrays.copyOf(encrypted.array(), encrypted.position()));
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

    private static int freeUdpPort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }
    private static final class RuntimeListener implements GatewayRuntime.Listener {
        private final CountDownLatch routeUp = new CountDownLatch(1);
        private final java.util.concurrent.ArrayBlockingQueue<Throwable> errors =
            new java.util.concurrent.ArrayBlockingQueue<>(8);

        @Override
        public void onRouteUp(String backendName) {
            if ("lobby".equals(backendName)) routeUp.countDown();
        }

        @Override
        public void onRouteDown(String backendName) {
            // Route-down is valid only during teardown in this test.
        }

        @Override
        public void onError(String component, Throwable error) {
            errors.offer(new RuntimeException(component, error));
        }
    }
}
