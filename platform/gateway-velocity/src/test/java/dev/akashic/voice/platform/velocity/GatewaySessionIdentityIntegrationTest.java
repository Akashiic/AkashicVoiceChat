package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.bridge.BridgeDelivery;
import dev.akashic.voice.platform.bridge.PositionSnapshot;
import dev.akashic.voice.platform.bridge.SpatialKind;
import dev.akashic.voice.voice.common.AuthAckPacket;
import dev.akashic.voice.voice.common.MicFramePacket;
import dev.akashic.voice.voice.common.SessionHandshakePackets;
import dev.akashic.voice.voice.common.SoundFrameV2Packet;
import dev.akashic.voice.voice.common.WirePacketType;
import dev.akashic.voice.voice.common.crypto.HandshakeProof;
import dev.akashic.voice.voice.common.crypto.SessionCrypto;
import dev.akashic.voice.voice.common.crypto.SessionKeys;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
final class GatewaySessionIdentityIntegrationTest {

    @Test
    void sessionBoundRoundTripRejectsWrongGenerationBeforeReplay() throws Exception {
        int port = freeUdpPort();
        UUID proxyPlayerId = UUID.randomUUID();
        RecordingListener listener = new RecordingListener();
        try (BootstrapSessionRegistry registry = new BootstrapSessionRegistry();
             GatewayUdpServer server = new GatewayUdpServer(port, registry, listener);
             DatagramSocket client = new DatagramSocket()) {
            server.start();
            BootstrapGrant grant = registry.grantFor(proxyPlayerId, "SessionPlayer", "lobby", port);
            client.connect(InetAddress.getLoopbackAddress(), port);
            client.setSoTimeout(300);

            byte[] secret = grant.masterSecretCopy();
            byte[] nonce = nonce(17);
            SessionCrypto crypto = null;
            try {
                UUID wrong = new UUID(grant.sessionId().getMostSignificantBits(),
                    grant.sessionId().getLeastSignificantBits() ^ 1L);
                sendSessionHello(client, wrong, grant.voiceIdentity(), nonce);
                assertThrows(SocketTimeoutException.class, () -> receive(client));
                assertEquals(0, server.snapshot().pendingHandshakes());

                client.setSoTimeout(2_000);
                crypto = authenticate(client, grant, secret, nonce);
                assertTrue(listener.connected.await(2, TimeUnit.SECONDS));
                assertEquals(1, server.snapshot().activeSessions());
                byte[] opus = new byte[] {2, 7, 1, 8, 2, 8};
                byte[] micWire = sealedMic(crypto, 9, opus);
                byte[] wrongSessionWire = Arrays.copyOf(micWire, micWire.length);
                wrongSessionWire[4] ^= 1;
                long rejectedBefore = server.snapshot().encryptedRejected();
                send(client, wrongSessionWire);
                waitFor(() -> server.snapshot().encryptedRejected() == rejectedBefore + 1,
                    "wrong session envelope was not rejected");
                assertNull(listener.micFrames.poll(100, TimeUnit.MILLISECONDS));

                send(client, micWire);
                MicObservation mic = listener.micFrames.poll(2, TimeUnit.SECONDS);
                assertNotNull(mic, "valid packet after session mismatch was lost");
                assertEquals(proxyPlayerId, mic.proxyPlayerId());
                assertEquals(9L, mic.sequence());
                assertArrayEquals(opus, mic.opus());

                send(client, micWire);
                waitFor(() -> server.snapshot().encryptedRejected() >= rejectedBefore + 2,
                    "replay was not rejected");
                assertNull(listener.micFrames.poll(150, TimeUnit.MILLISECONDS));

                UUID sender = UUID.randomUUID();
                byte[] downlink = new byte[] {4, 2, 4, 2};
                BridgeDelivery delivery = new BridgeDelivery(
                    proxyPlayerId, sender, UUID.randomUUID(), 77L, SpatialKind.LOCATION, null,
                    new PositionSnapshot("minecraft:overworld", 1.0, 65.0, 2.0), 32F, false, null, downlink);
                assertTrue(server.sendBridgeDelivery("lobby", delivery));
                ByteBuffer soundPlain = ByteBuffer.allocate(1500);
                assertTrue(crypto.open(receive(client), soundPlain), "session downlink did not authenticate");
                assertEquals(WirePacketType.SOUND_FRAME_V2, soundPlain.get());
                SoundFrameV2Packet sound = SoundFrameV2Packet.read(soundPlain);
                assertNotNull(sound);
                assertEquals(sender, sound.senderId);
                assertEquals(77, sound.sequence);
                assertArrayEquals(downlink,
                    Arrays.copyOfRange(sound.opusData, sound.opusOffset, sound.opusOffset + sound.opusLength));

                sendDisconnect(client, crypto);
                assertTrue(listener.disconnected.await(2, TimeUnit.SECONDS));
                waitFor(() -> server.snapshot().activeSessions() == 0, "session disconnect did not clear gateway");
                assertTrue(listener.errors.isEmpty(), "unexpected gateway errors: " + listener.errors);
            } finally {
                if (crypto != null) crypto.close();
                Arrays.fill(secret, (byte) 0);
                Arrays.fill(nonce, (byte) 0);
            }
        }
    }

    private static SessionCrypto authenticate(DatagramSocket client, BootstrapGrant grant,
        byte[] secret, byte[] clientNonce) throws Exception {
        sendSessionHello(client, grant.sessionId(), grant.voiceIdentity(), clientNonce);
        SessionHandshakePackets.Challenge challenge = SessionHandshakePackets.readChallenge(receive(client));
        assertNotNull(challenge, "session challenge missing");
        assertEquals(grant.sessionId(), challenge.sessionId);
        SessionKeys keys = SessionKeys.derive(secret, clientNonce, challenge.serverNonce, grant.sessionId());
        byte[] proof = null;
        SessionCrypto crypto = null;
        try {
            assertTrue(HandshakeProof.verifyServer(
                keys, grant.voiceIdentity(), clientNonce, challenge.serverNonce, challenge.serverProof));
            proof = HandshakeProof.computeClient(
                keys, grant.voiceIdentity(), clientNonce, challenge.serverNonce);
            crypto = SessionCrypto.forClient(keys, grant.sessionId());
        } finally {
            keys.close();
            Arrays.fill(challenge.serverNonce, (byte) 0);
            Arrays.fill(challenge.serverProof, (byte) 0);
        }

        ByteBuffer response = ByteBuffer.allocate(SessionHandshakePackets.AUTH_RESPONSE_BYTES);
        SessionHandshakePackets.writeAuthResponse(response, grant.sessionId(), proof);
        send(client, Arrays.copyOf(response.array(), response.position()));
        Arrays.fill(proof, (byte) 0);

        ByteBuffer plain = ByteBuffer.allocate(256);
        assertTrue(crypto.open(receive(client), plain), "session AUTH_ACK failed");
        assertEquals(WirePacketType.AUTH_ACK, plain.get());
        AuthAckPacket ack = AuthAckPacket.read(plain);
        assertNotNull(ack);
        assertEquals(AuthAckPacket.STATUS_OK, ack.status);
        return crypto;
    }
    private static void sendSessionHello(DatagramSocket socket, UUID sessionId, UUID voiceIdentity,
        byte[] nonce) throws Exception {
        ByteBuffer hello = ByteBuffer.allocate(SessionHandshakePackets.HELLO_BYTES);
        SessionHandshakePackets.writeHello(hello, sessionId, voiceIdentity, nonce);
        send(socket, Arrays.copyOf(hello.array(), hello.position()));
    }

    private static byte[] sealedMic(SessionCrypto crypto, int sequence, byte[] opus) {
        ByteBuffer inner = ByteBuffer.allocate(256);
        MicFramePacket.write(inner, sequence, opus, opus.length);
        ByteBuffer encrypted = ByteBuffer.allocate(512);
        assertTrue(crypto.seal(inner, encrypted));
        return Arrays.copyOf(encrypted.array(), encrypted.position());
    }

    private static void sendDisconnect(DatagramSocket client, SessionCrypto crypto) throws Exception {
        ByteBuffer inner = ByteBuffer.allocate(16);
        inner.put(WirePacketType.DISCONNECT);
        ByteBuffer encrypted = ByteBuffer.allocate(128);
        assertTrue(crypto.seal(inner, encrypted));
        send(client, Arrays.copyOf(encrypted.array(), encrypted.position()));
    }

    private static byte[] nonce(int seed) {
        byte[] value = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        for (int i = 0; i < value.length; i++) value[i] = (byte) (seed + i);
        return value;
    }
    private static void send(DatagramSocket socket, byte[] bytes) throws Exception {
        socket.send(new DatagramPacket(bytes, bytes.length));
    }

    private static ByteBuffer receive(DatagramSocket socket) throws Exception {
        byte[] buffer = new byte[1500];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        return ByteBuffer.wrap(Arrays.copyOf(packet.getData(), packet.getLength()));
    }

    private static int freeUdpPort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void waitFor(BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5L);
        assertTrue(condition.getAsBoolean(), message);
    }

    private record MicObservation(UUID proxyPlayerId, long sequence, byte[] opus) {}

    private static final class RecordingListener implements GatewayUdpServer.Listener {
        final CountDownLatch connected = new CountDownLatch(1);
        final CountDownLatch disconnected = new CountDownLatch(1);
        final ArrayBlockingQueue<MicObservation> micFrames = new ArrayBlockingQueue<>(8);
        final ArrayBlockingQueue<Throwable> errors = new ArrayBlockingQueue<>(8);

        @Override
        public void onConnected(UUID proxyPlayerId, String backendName, UUID voiceIdentity) {
            connected.countDown();
        }

        @Override
        public void onDisconnected(UUID proxyPlayerId, String backendName, UUID voiceIdentity) {
            disconnected.countDown();
        }

        @Override
        public void onMicFrame(UUID proxyPlayerId, String backendName, UUID voiceIdentity,
            long sequence, byte[] opus) {
            micFrames.offer(new MicObservation(proxyPlayerId, sequence, opus));
        }

        @Override
        public void onError(Throwable error) {
            errors.offer(error);
        }
    }
}
