package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.bridge.BridgeDelivery;
import dev.akashic.voice.platform.bridge.PositionSnapshot;
import dev.akashic.voice.platform.bridge.SpatialKind;
import dev.akashic.voice.voice.common.AuthAckPacket;
import dev.akashic.voice.voice.common.HandshakePackets;
import dev.akashic.voice.voice.common.MicFramePacket;
import dev.akashic.voice.voice.common.SoundFramePacket;
import dev.akashic.voice.voice.common.WirePacketType;
import dev.akashic.voice.voice.common.crypto.HandshakeProof;
import dev.akashic.voice.voice.common.crypto.SessionCrypto;
import dev.akashic.voice.voice.common.crypto.SessionKeys;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
final class GatewayUdpServerIntegrationTest {

    @Test
    void secureRoundTripRejectsReplayAndPreservesOpus() throws Exception {
        int port = freeUdpPort();
        UUID proxyPlayerId = UUID.randomUUID();
        RecordingListener listener = new RecordingListener();

        try (BootstrapSessionRegistry registry = new BootstrapSessionRegistry();
             GatewayUdpServer server = new GatewayUdpServer(port, registry, listener);
             DatagramSocket client = new DatagramSocket()) {
            server.start();
            BootstrapGrant grant = registry.grantFor(proxyPlayerId, "TestPlayer", "lobby", port);
            client.connect(InetAddress.getLoopbackAddress(), port);
            client.setSoTimeout(2_000);

            byte[] secret = grant.masterSecretCopy();
            byte[] clientNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
            new SecureRandom().nextBytes(clientNonce);
            SessionCrypto clientCrypto = null;
            try {
                clientCrypto = authenticate(client, grant.voiceIdentity(), secret, clientNonce);
                assertTrue(listener.connected.await(2, TimeUnit.SECONDS));
                assertEquals(proxyPlayerId, listener.connectedProxyPlayerId);
                assertEquals("lobby", listener.connectedBackendName);
                assertEquals(grant.voiceIdentity(), listener.connectedVoiceIdentity);
                byte[] micOpus = new byte[] {11, 22, 33, 44, 55, 66};
                ByteBuffer encryptedMic = sealMic(clientCrypto, -1, micOpus);
                byte[] encryptedMicBytes = Arrays.copyOf(encryptedMic.array(), encryptedMic.position());
                send(client, encryptedMicBytes);
                send(client, encryptedMicBytes);

                MicObservation mic = listener.micFrames.poll(2, TimeUnit.SECONDS);
                assertNotNull(mic, "Gateway did not emit the MIC callback");
                assertEquals(proxyPlayerId, mic.proxyPlayerId);
                assertEquals("lobby", mic.backendName);
                assertEquals(grant.voiceIdentity(), mic.voiceIdentity);
                assertEquals(0xFFFF_FFFFL, mic.sequence);
                assertArrayEquals(micOpus, mic.opus);
                assertNull(listener.micFrames.poll(200, TimeUnit.MILLISECONDS), "Replay was accepted twice");

                UUID svcSender = UUID.randomUUID();
                byte[] downlinkOpus = new byte[] {7, 8, 9, 10};
                BridgeDelivery delivery = new BridgeDelivery(
                    proxyPlayerId, svcSender, UUID.randomUUID(), 123L,
                    SpatialKind.LOCATION, null,
                    new PositionSnapshot("minecraft:overworld", 10.5, 64.0, -7.25),
                    48F, false, null, downlinkOpus);
                assertTrue(server.sendBridgeDelivery("lobby", delivery));
                SoundFramePacket sound = receiveSound(client, clientCrypto);
                assertEquals(svcSender, sound.senderId);
                assertEquals(123, sound.sequence);
                assertTrue(sound.isSpatial());
                assertEquals(10.5F, sound.x);
                assertEquals(64F, sound.y);
                assertEquals(-7.25F, sound.z);
                assertArrayEquals(downlinkOpus,
                    Arrays.copyOfRange(sound.opusData, sound.opusOffset, sound.opusOffset + sound.opusLength));

                sendDisconnect(client, clientCrypto);
                assertTrue(listener.disconnected.await(2, TimeUnit.SECONDS));
                assertEquals(proxyPlayerId, listener.disconnectedProxyPlayerId);
                assertEquals("lobby", listener.disconnectedBackendName);
                assertEquals(grant.voiceIdentity(), listener.disconnectedVoiceIdentity);
                assertTrue(listener.errors.isEmpty(), "Gateway reported unexpected errors: " + listener.errors);
            } finally {
                if (clientCrypto != null) clientCrypto.close();
                Arrays.fill(secret, (byte) 0);
                Arrays.fill(clientNonce, (byte) 0);
            }
        }
    }

    private static SessionCrypto authenticate(DatagramSocket client, UUID voiceIdentity,
                                              byte[] secret, byte[] clientNonce) throws Exception {
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
            clientProof = HandshakeProof.computeClient(keys, voiceIdentity, clientNonce, challenge.serverNonce);
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
    private static ByteBuffer sealMic(SessionCrypto crypto, int sequence, byte[] opus) {
        ByteBuffer inner = ByteBuffer.allocate(1_500);
        MicFramePacket.write(inner, sequence, opus, opus.length);
        ByteBuffer encrypted = ByteBuffer.allocate(1_500);
        assertTrue(crypto.seal(inner, encrypted));
        return encrypted;
    }

    private static SoundFramePacket receiveSound(DatagramSocket client, SessionCrypto crypto) throws Exception {
        ByteBuffer plain = ByteBuffer.allocate(1_500);
        assertTrue(crypto.open(receive(client), plain), "SOUND datagram failed AEAD verification");
        assertEquals(WirePacketType.SOUND_FRAME, plain.get());
        SoundFramePacket packet = SoundFramePacket.read(plain);
        assertNotNull(packet);
        return packet;
    }

    private static void sendDisconnect(DatagramSocket client, SessionCrypto crypto) throws Exception {
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

    private record MicObservation(UUID proxyPlayerId, String backendName, UUID voiceIdentity, long sequence, byte[] opus) {}

    private static final class RecordingListener implements GatewayUdpServer.Listener {
        private final CountDownLatch connected = new CountDownLatch(1);
        private final CountDownLatch disconnected = new CountDownLatch(1);
        private final ArrayBlockingQueue<MicObservation> micFrames = new ArrayBlockingQueue<>(8);
        private final ArrayBlockingQueue<Throwable> errors = new ArrayBlockingQueue<>(8);
        private volatile UUID connectedProxyPlayerId;
        private volatile String connectedBackendName;
        private volatile UUID connectedVoiceIdentity;
        private volatile UUID disconnectedProxyPlayerId;
        private volatile String disconnectedBackendName;
        private volatile UUID disconnectedVoiceIdentity;
        @Override
        public void onConnected(UUID proxyPlayerId, String backendName, UUID voiceIdentity) {
            connectedProxyPlayerId = proxyPlayerId;
            connectedBackendName = backendName;
            connectedVoiceIdentity = voiceIdentity;
            connected.countDown();
        }

        @Override
        public void onDisconnected(UUID proxyPlayerId, String backendName, UUID voiceIdentity) {
            disconnectedProxyPlayerId = proxyPlayerId;
            disconnectedBackendName = backendName;
            disconnectedVoiceIdentity = voiceIdentity;
            disconnected.countDown();
        }

        @Override
        public void onMicFrame(UUID proxyPlayerId, String backendName, UUID voiceIdentity, long sequence, byte[] opus) {
            micFrames.offer(new MicObservation(proxyPlayerId, backendName, voiceIdentity, sequence, opus));
        }

        @Override
        public void onError(Throwable error) {
            errors.offer(error);
        }
    }
}
