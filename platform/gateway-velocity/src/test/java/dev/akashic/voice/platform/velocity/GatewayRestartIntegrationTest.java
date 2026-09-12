package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.agent.AgentSecureChannel;
import dev.akashic.voice.platform.agent.AgentSocketHandshake;
import dev.akashic.voice.voice.common.AuthAckPacket;
import dev.akashic.voice.voice.common.HandshakePackets;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

final class GatewayRestartIntegrationTest {
    @Test
    void restartRejectsOldGrantAndAcceptsFreshBootstrap() throws Exception {
        int udpPort = freeUdpPort();
        byte[] psk = new byte[32];
        new SecureRandom().nextBytes(psk);
        UUID proxyPlayerId = UUID.randomUUID();
        DatagramSocket client = new DatagramSocket();
        client.connect(InetAddress.getLoopbackAddress(), udpPort);
        client.setSoTimeout(2_000);

        UUID oldVoiceIdentity;
        byte[] oldSecret;
        try (BootstrapSessionRegistry registry = new BootstrapSessionRegistry();
             GatewayRuntime runtime = new GatewayRuntime(
                 udpPort,
                 new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                 Map.of("lobby", psk), registry, new RuntimeListener())) {
            runtime.start();
            try (AgentSecureChannel agent = connectAgent(runtime.agentPort(), psk)) {
                assertTrue(agent.isOpen());
                waitForRoute(runtime);
                BootstrapGrant grant = runtime.grantFor(proxyPlayerId, "RestartPlayer", "lobby");
                assertNotNull(grant);
                oldVoiceIdentity = grant.voiceIdentity();
                oldSecret = grant.masterSecretCopy();
                byte[] nonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
                new SecureRandom().nextBytes(nonce);
                SessionCrypto crypto = authenticate(client, oldVoiceIdentity, oldSecret, nonce);
                crypto.close();
                Arrays.fill(nonce, (byte) 0);
                assertEquals(1, runtime.statusSnapshot().activeUdpSessions());
            }
        }
        try (BootstrapSessionRegistry registry = new BootstrapSessionRegistry();
             GatewayRuntime runtime = new GatewayRuntime(
                 udpPort,
                 new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                 Map.of("lobby", psk), registry, new RuntimeListener())) {
            runtime.start();
            try (AgentSecureChannel agent = connectAgent(runtime.agentPort(), psk)) {
                assertTrue(agent.isOpen());
                waitForRoute(runtime);

                byte[] staleNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
                new SecureRandom().nextBytes(staleNonce);
                sendHello(client, oldVoiceIdentity, staleNonce);
                client.setSoTimeout(350);
                assertThrows(SocketTimeoutException.class, () -> receive(client),
                    "restarted Gateway accepted stale voice identity");
                Arrays.fill(staleNonce, (byte) 0);

                BootstrapGrant freshGrant = runtime.grantFor(proxyPlayerId, "RestartPlayer", "lobby");
                assertNotNull(freshGrant);
                assertNotEquals(oldVoiceIdentity, freshGrant.voiceIdentity());
                byte[] freshSecret = freshGrant.masterSecretCopy();
                byte[] freshNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
                new SecureRandom().nextBytes(freshNonce);
                client.setSoTimeout(2_000);
                SessionCrypto freshCrypto = authenticate(
                    client, freshGrant.voiceIdentity(), freshSecret, freshNonce);
                try {
                    assertEquals(1, runtime.statusSnapshot().activeUdpSessions());
                    assertEquals(1L, runtime.statusSnapshot().sessionsOpened());
                } finally {
                    freshCrypto.close();
                    Arrays.fill(freshSecret, (byte) 0);
                    Arrays.fill(freshNonce, (byte) 0);
                }
            }
        } finally {
            client.close();
            Arrays.fill(oldSecret, (byte) 0);
            Arrays.fill(psk, (byte) 0);
        }
    }
    private static AgentSecureChannel connectAgent(int port, byte[] psk) throws Exception {
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
        socket.setSoTimeout(2_000);
        return AgentSocketHandshake.connect(socket, "lobby", psk, new SecureRandom());
    }

    private static void waitForRoute(GatewayRuntime runtime) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!runtime.isBackendReady("lobby") && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertTrue(runtime.isBackendReady("lobby"), "agent route did not become ready");
    }

    private static void sendHello(DatagramSocket client, UUID voiceIdentity, byte[] clientNonce) throws Exception {
        ByteBuffer hello = ByteBuffer.allocate(HandshakePackets.HELLO_BYTES);
        HandshakePackets.writeHello(hello, voiceIdentity, clientNonce);
        send(client, Arrays.copyOf(hello.array(), hello.position()));
    }

    private static SessionCrypto authenticate(DatagramSocket client, UUID voiceIdentity,
                                              byte[] secret, byte[] clientNonce) throws Exception {
        sendHello(client, voiceIdentity, clientNonce);
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

        ByteBuffer plain = ByteBuffer.allocate(256);
        assertTrue(crypto.open(receive(client), plain), "encrypted AUTH_ACK failed");
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

    private static int freeUdpPort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        }
    }
    private static final class RuntimeListener implements GatewayRuntime.Listener {
        private final CountDownLatch routeUp = new CountDownLatch(1);

        @Override
        public void onRouteUp(String backendName) {
            if ("lobby".equals(backendName)) routeUp.countDown();
        }

        @Override
        public void onRouteDown(String backendName) {
            // expected during runtime shutdown
        }

        @Override
        public void onError(String component, Throwable error) {
            if (error instanceof java.io.EOFException) return;
            if (error instanceof java.net.SocketException) return;
            fail("unexpected runtime error from " + component, error);
        }
    }
}
