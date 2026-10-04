package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.agent.AgentSecureChannel;
import dev.akashic.voice.platform.agent.AgentSocketHandshake;
import dev.akashic.voice.voice.common.SessionHandshakePackets;
import dev.akashic.voice.voice.common.crypto.SessionKeys;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

final class GatewayHandshakeNatBurstIntegrationTest {
    @Test
    void thirtyTwoClientsBehindOneIpReceiveChallengesInSingleBurst() throws Exception {
        int udpPort = freeUdpPort();
        byte[] psk = new byte[32];
        new SecureRandom().nextBytes(psk);
        List<DatagramSocket> clients = new ArrayList<>();
        List<BootstrapGrant> grants = new ArrayList<>();
        List<byte[]> nonces = new ArrayList<>();
        try (BootstrapSessionRegistry registry = new BootstrapSessionRegistry();
             GatewayRuntime runtime = new GatewayRuntime(
                 udpPort,
                 new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                 Map.of("lobby", psk), registry, new NoopListener())) {
            runtime.start();
            try (AgentSecureChannel agent = connectAgent(runtime.agentPort(), psk)) {
                await(() -> runtime.isBackendReady("lobby"), "lobby route unavailable");
                for (int i = 0; i < 32; i++) {
                    BootstrapGrant grant = runtime.grantFor(UUID.randomUUID(), "NatPlayer" + i, "lobby");
                    assertNotNull(grant);
                    DatagramSocket socket = new DatagramSocket();
                    socket.connect(InetAddress.getLoopbackAddress(), runtime.udpPort());
                    socket.setSoTimeout(2_000);
                    byte[] nonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
                    new SecureRandom().nextBytes(nonce);
                    clients.add(socket);
                    grants.add(grant);
                    nonces.add(nonce);
                }
                for (int i = 0; i < clients.size(); i++) {
                    sendHello(clients.get(i), grants.get(i), nonces.get(i));
                }
                for (int i = 0; i < clients.size(); i++) {
                    ByteBuffer packet = receive(clients.get(i));
                    SessionHandshakePackets.Challenge challenge = SessionHandshakePackets.readChallenge(packet);
                    assertNotNull(challenge, "missing challenge for NAT client " + i);
                    assertEquals(grants.get(i).sessionId(), challenge.sessionId);
                    Arrays.fill(challenge.serverNonce, (byte) 0);
                    Arrays.fill(challenge.serverProof, (byte) 0);
                }
                assertEquals(32, runtime.statusSnapshot().pendingHandshakes());
                assertEquals(0L, runtime.statusSnapshot().helloRateLimited());
                assertTrue(agent.isOpen());
            }
        } finally {
            for (DatagramSocket socket : clients) socket.close();
            for (byte[] nonce : nonces) Arrays.fill(nonce, (byte) 0);
            Arrays.fill(psk, (byte) 0);
        }
    }

    private static void sendHello(DatagramSocket socket, BootstrapGrant grant, byte[] nonce) throws Exception {
        ByteBuffer hello = ByteBuffer.allocate(SessionHandshakePackets.HELLO_BYTES);
        SessionHandshakePackets.writeHello(hello, grant.sessionId(), grant.voiceIdentity(), nonce);
        byte[] bytes = Arrays.copyOf(hello.array(), hello.position());
        socket.send(new DatagramPacket(bytes, bytes.length));
    }
    private static ByteBuffer receive(DatagramSocket socket) throws Exception {
        byte[] buffer = new byte[256];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        return ByteBuffer.wrap(Arrays.copyOf(packet.getData(), packet.getLength()));
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

    private static void await(BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10L);
        assertTrue(condition.getAsBoolean(), message);
    }

    private static final class NoopListener implements GatewayRuntime.Listener {
        @Override public void onRouteUp(String backendName) { }
        @Override public void onRouteDown(String backendName) { }
        @Override public void onError(String component, Throwable error) {
            if (error instanceof java.io.EOFException) return;
            if (error instanceof java.net.SocketException) return;
            fail("unexpected runtime error from " + component, error);
        }
    }
}
