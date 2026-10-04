package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.agent.AgentSecureChannel;
import dev.akashic.voice.platform.agent.AgentSocketHandshake;
import dev.akashic.voice.voice.common.AuthAckPacket;
import dev.akashic.voice.platform.agent.AgentMessage;
import dev.akashic.voice.voice.common.MicFramePacket;
import dev.akashic.voice.voice.common.SessionHandshakePackets;
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
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

final class GatewaySessionRestartSoakTest {
    @RepeatedTest(6)
    void restartRejectsOldSessionAndResumesSpeakingWithFreshBootstrap() throws Exception {
        int udpPort = freeUdpPort();
        byte[] psk = new byte[32];
        new SecureRandom().nextBytes(psk);
        UUID proxyPlayerId = UUID.randomUUID();
        DatagramSocket client = new DatagramSocket();
        client.connect(InetAddress.getLoopbackAddress(), udpPort);
        client.setSoTimeout(2_000);

        UUID oldVoiceIdentity;
        UUID oldSessionId;
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
                oldSessionId = grant.sessionId();
                oldSecret = grant.masterSecretCopy();
                byte[] nonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
                new SecureRandom().nextBytes(nonce);
                SessionCrypto crypto = authenticate(client, grant, oldSecret, nonce);
                assertAttach(agent, proxyPlayerId);
                sendMicAndAssert(client, crypto, agent, proxyPlayerId, 7);
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
                sendSessionHello(client, oldSessionId, oldVoiceIdentity, staleNonce);
                client.setSoTimeout(350);
                assertThrows(SocketTimeoutException.class, () -> receive(client),
                    "restarted Gateway accepted stale voice identity");
                Arrays.fill(staleNonce, (byte) 0);

                BootstrapGrant freshGrant = runtime.grantFor(proxyPlayerId, "RestartPlayer", "lobby");
                assertNotNull(freshGrant);
                assertNotEquals(oldVoiceIdentity, freshGrant.voiceIdentity());
                assertNotEquals(oldSessionId, freshGrant.sessionId());
                byte[] freshSecret = freshGrant.masterSecretCopy();
                byte[] freshNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
                new SecureRandom().nextBytes(freshNonce);
                client.setSoTimeout(2_000);
                SessionCrypto freshCrypto = authenticate(client, freshGrant, freshSecret, freshNonce);
                try {
                    assertEquals(1, runtime.statusSnapshot().activeUdpSessions());
                    assertEquals(1L, runtime.statusSnapshot().sessionsOpened());
                    assertAttach(agent, proxyPlayerId);
                    sendMicAndAssert(client, freshCrypto, agent, proxyPlayerId, 107);
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
    @RepeatedTest(6)
    void backendTransferRevokesOldUdpBeforeFreshHandshake() throws Exception {
        int udpPort = freeUdpPort();
        byte[] lobbyPsk = new byte[32], forbiddenPsk = new byte[32];
        new SecureRandom().nextBytes(lobbyPsk); new SecureRandom().nextBytes(forbiddenPsk);
        UUID playerId = UUID.randomUUID();
        try (BootstrapSessionRegistry registry = new BootstrapSessionRegistry();
             GatewayRuntime runtime = new GatewayRuntime(udpPort,
                 new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                 Map.of("lobby", lobbyPsk, "forbidden", forbiddenPsk), registry, new RuntimeListener());
             DatagramSocket client = new DatagramSocket()) {
            runtime.start();
            try (AgentSecureChannel lobby = connectAgent(runtime.agentPort(), "lobby", lobbyPsk);
                 AgentSecureChannel forbidden = connectAgent(runtime.agentPort(), "forbidden", forbiddenPsk)) {
                waitForRoute(runtime, "lobby"); waitForRoute(runtime, "forbidden");
                client.connect(InetAddress.getLoopbackAddress(), udpPort); client.setSoTimeout(2_000);
                BootstrapGrant first = runtime.grantFor(playerId, "TransferPlayer", "lobby");
                assertNotNull(first);
                byte[] firstSecret = first.masterSecretCopy(), firstNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
                new SecureRandom().nextBytes(firstNonce);
                SessionCrypto oldCrypto = authenticate(client, first, firstSecret, firstNonce);
                assertAttach(lobby, playerId, "lobby");
                sendMicAndAssert(client, oldCrypto, lobby, playerId, 1);

                lobby.socket().setSoTimeout(500);
                BootstrapGrant transfer = runtime.grantFor(playerId, "TransferPlayer", "forbidden");
                assertNotNull(transfer);
                assertNotEquals(first.voiceIdentity(), transfer.voiceIdentity());
                assertNotEquals(first.sessionId(), transfer.sessionId());
                AgentMessage.Detach detach = assertInstanceOf(AgentMessage.Detach.class, lobby.receive());
                assertEquals(playerId, detach.playerId(), "old backend was not detached at grant rotation");

                lobby.socket().setSoTimeout(350);
                sendMic(client, oldCrypto, 2, new byte[] {2, 42, 24, 7});
                assertThrows(SocketTimeoutException.class, lobby::receive,
                    "old session still routed media after backend grant rotation");

                byte[] nextSecret = transfer.masterSecretCopy();
                byte[] nextNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
                new SecureRandom().nextBytes(nextNonce); client.setSoTimeout(2_000);
                try (SessionCrypto fresh = authenticate(client, transfer, nextSecret, nextNonce)) {
                    assertAttach(forbidden, playerId, "forbidden");
                    sendMicAndAssert(client, fresh, forbidden, playerId, 3);
                    assertTrue(runtime.hasVoiceSession(playerId, "forbidden"));
                    assertFalse(runtime.hasVoiceSession(playerId, "lobby"));
                } finally {
                    oldCrypto.close(); Arrays.fill(firstSecret,(byte)0); Arrays.fill(firstNonce,(byte)0);
                    Arrays.fill(nextSecret,(byte)0); Arrays.fill(nextNonce,(byte)0);
                }
            }
        } finally { Arrays.fill(lobbyPsk,(byte)0); Arrays.fill(forbiddenPsk,(byte)0); }
    }

    @RepeatedTest(3)
    void multiHopTransferRotatesSessionAndRoutesOnlyToCurrentBackend() throws Exception {
        int udpPort = freeUdpPort();
        byte[] lobbyPsk = new byte[32], normalPsk = new byte[32], forbiddenPsk = new byte[32];
        SecureRandom random = new SecureRandom();
        random.nextBytes(lobbyPsk); random.nextBytes(normalPsk); random.nextBytes(forbiddenPsk);
        UUID playerId = UUID.randomUUID();
        String[] hops = {"lobby", "atm10-normal", "forbidden", "lobby", "atm10-normal", "forbidden",
            "lobby", "atm10-normal", "forbidden", "lobby", "atm10-normal", "forbidden"};
        try (BootstrapSessionRegistry registry = new BootstrapSessionRegistry();
             GatewayRuntime runtime = new GatewayRuntime(udpPort,
                 new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                 Map.of("lobby", lobbyPsk, "atm10-normal", normalPsk, "forbidden", forbiddenPsk),
                 registry, new RuntimeListener());
             DatagramSocket client = new DatagramSocket()) {
            runtime.start();
            try (AgentSecureChannel lobby = connectAgent(runtime.agentPort(), "lobby", lobbyPsk);
                 AgentSecureChannel normal = connectAgent(runtime.agentPort(), "atm10-normal", normalPsk);
                 AgentSecureChannel forbidden = connectAgent(runtime.agentPort(), "forbidden", forbiddenPsk)) {
                Map<String, AgentSecureChannel> agents = Map.of(
                    "lobby", lobby, "atm10-normal", normal, "forbidden", forbidden);
                waitForRoute(runtime, "lobby");
                waitForRoute(runtime, "atm10-normal");
                waitForRoute(runtime, "forbidden");
                client.connect(InetAddress.getLoopbackAddress(), udpPort);
                client.setSoTimeout(2_000);
                BootstrapGrant previousGrant = null;
                SessionCrypto previousCrypto = null;
                byte[] previousSecret = null;
                byte[] previousNonce = null;
                String previousBackend = null;
                for (int index = 0; index < hops.length; index++) {
                    String targetBackend = hops[index];
                    AgentSecureChannel target = agents.get(targetBackend);
                    BootstrapGrant grant = runtime.grantFor(playerId, "MultiHopPlayer", targetBackend);
                    assertNotNull(grant);
                    if (previousGrant != null) {
                        assertNotEquals(previousGrant.voiceIdentity(), grant.voiceIdentity());
                        assertNotEquals(previousGrant.sessionId(), grant.sessionId());
                        AgentSecureChannel oldAgent = agents.get(previousBackend);
                        oldAgent.socket().setSoTimeout(500);
                        AgentMessage.Detach detach = assertInstanceOf(AgentMessage.Detach.class, oldAgent.receive());
                        assertEquals(playerId, detach.playerId());
                        assertEquals(0, runtime.statusSnapshot().activeUdpSessions(),
                            "old UDP session remained active during backend transfer");
                        oldAgent.socket().setSoTimeout(250);
                        sendMic(client, previousCrypto, 10_000 + index, new byte[] {(byte) index, 9, 8, 7});
                        assertThrows(SocketTimeoutException.class, oldAgent::receive,
                            "stale UDP generation routed media after hop " + index);
                        previousCrypto.close();
                        Arrays.fill(previousSecret, (byte) 0);
                        Arrays.fill(previousNonce, (byte) 0);
                    }
                    byte[] secret = grant.masterSecretCopy();
                    byte[] nonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
                    random.nextBytes(nonce);
                    client.setSoTimeout(2_000);
                    SessionCrypto fresh = authenticate(client, grant, secret, nonce);
                    assertAttach(target, playerId, targetBackend);
                    sendMicAndAssert(client, fresh, target, playerId, 20_000 + index);
                    assertEquals(1, runtime.statusSnapshot().activeUdpSessions());
                    assertTrue(runtime.hasVoiceSession(playerId, targetBackend));
                    for (String backend : agents.keySet()) {
                        if (!backend.equals(targetBackend)) {
                            assertFalse(runtime.hasVoiceSession(playerId, backend),
                                "stale voice route survived hop to " + targetBackend);
                        }
                    }
                    previousGrant = grant;
                    previousCrypto = fresh;
                    previousSecret = secret;
                    previousNonce = nonce;
                    previousBackend = targetBackend;
                }
                if (previousCrypto != null) previousCrypto.close();
                if (previousSecret != null) Arrays.fill(previousSecret, (byte) 0);
                if (previousNonce != null) Arrays.fill(previousNonce, (byte) 0);
            }
        } finally {
            Arrays.fill(lobbyPsk, (byte) 0); Arrays.fill(normalPsk, (byte) 0); Arrays.fill(forbiddenPsk, (byte) 0);
        }
    }

    @RepeatedTest(3)
    void natRebindOfSameGrantDoesNotChurnBackendLogicalRoute() throws Exception {
        int udpPort=freeUdpPort(); byte[] psk=new byte[32]; new SecureRandom().nextBytes(psk);
        UUID playerId=UUID.randomUUID();
        try (BootstrapSessionRegistry registry=new BootstrapSessionRegistry();
             GatewayRuntime runtime=new GatewayRuntime(udpPort,new InetSocketAddress(InetAddress.getLoopbackAddress(),0),
                 Map.of("lobby",psk),registry,new RuntimeListener());
             DatagramSocket first=new DatagramSocket(); DatagramSocket rebound=new DatagramSocket()) {
            runtime.start();
            try (AgentSecureChannel agent=connectAgent(runtime.agentPort(),psk)) {
                waitForRoute(runtime); first.connect(InetAddress.getLoopbackAddress(),udpPort); rebound.connect(InetAddress.getLoopbackAddress(),udpPort);
                first.setSoTimeout(2_000); rebound.setSoTimeout(2_000);
                BootstrapGrant grant=runtime.grantFor(playerId,"RebindRoute","lobby"); assertNotNull(grant);
                byte[] secret=grant.masterSecretCopy(), nonce1=new byte[SessionKeys.HANDSHAKE_NONCE_BYTES], nonce2=new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
                new SecureRandom().nextBytes(nonce1); new SecureRandom().nextBytes(nonce2);
                try (SessionCrypto one=authenticate(first,grant,secret,nonce1);
                     SessionCrypto two=authenticate(rebound,grant,secret,nonce2)) {
                    assertAttach(agent,playerId); agent.socket().setSoTimeout(300);
                    assertThrows(SocketTimeoutException.class,agent::receive,"NAT rebind churned Detach/Attach on backend route");
                    assertEquals(2L,runtime.statusSnapshot().sessionsOpened(),"transport open metric lost rebind");
                    assertEquals(1L,runtime.statusSnapshot().sessionsClosed(),"transport close metric lost rebind");
                    sendMicAndAssert(rebound,two,agent,playerId,501);
                    agent.socket().setSoTimeout(250); sendMic(first,one,502,new byte[]{5,0,2});
                    assertThrows(SocketTimeoutException.class,agent::receive,"stale pre-rebind endpoint still routed media");
                    assertEquals(1,runtime.statusSnapshot().activeUdpSessions()); assertEquals(1,runtime.statusSnapshot().pendingHandshakes());
                } finally { Arrays.fill(secret,(byte)0); Arrays.fill(nonce1,(byte)0); Arrays.fill(nonce2,(byte)0); }
            }
        } finally { Arrays.fill(psk,(byte)0); }
    }

    private static AgentSecureChannel connectAgent(int port, byte[] psk) throws Exception {
        return connectAgent(port, "lobby", psk);
    }

    private static AgentSecureChannel connectAgent(int port, String backend, byte[] psk) throws Exception {
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
        socket.setSoTimeout(2_000);
        return AgentSocketHandshake.connect(socket, backend, psk, new SecureRandom());
    }

    private static void waitForRoute(GatewayRuntime runtime) throws Exception { waitForRoute(runtime, "lobby"); }

    private static void waitForRoute(GatewayRuntime runtime, String backend) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!runtime.isBackendReady(backend) && System.nanoTime() < deadline) Thread.sleep(10L);
        assertTrue(runtime.isBackendReady(backend), "agent route did not become ready: " + backend);
    }

    private static void sendSessionHello(DatagramSocket client, UUID sessionId,
                                         UUID voiceIdentity, byte[] clientNonce) throws Exception {
        ByteBuffer hello = ByteBuffer.allocate(SessionHandshakePackets.HELLO_BYTES);
        SessionHandshakePackets.writeHello(hello, sessionId, voiceIdentity, clientNonce);
        send(client, Arrays.copyOf(hello.array(), hello.position()));
    }

    private static SessionCrypto authenticate(DatagramSocket client, BootstrapGrant grant,
                                              byte[] secret, byte[] clientNonce) throws Exception {
        sendSessionHello(client, grant.sessionId(), grant.voiceIdentity(), clientNonce);
        SessionHandshakePackets.Challenge challenge = SessionHandshakePackets.readChallenge(receive(client));
        assertNotNull(challenge, "Gateway session challenge missing");
        assertEquals(grant.sessionId(), challenge.sessionId);
        SessionKeys keys = SessionKeys.derive(secret, clientNonce, challenge.serverNonce, grant.sessionId());
        byte[] clientProof = null;
        SessionCrypto crypto = null;
        try {
            assertTrue(HandshakeProof.verifyServer(keys, grant.voiceIdentity(), clientNonce,
                challenge.serverNonce, challenge.serverProof));
            clientProof = HandshakeProof.computeClient(keys, grant.voiceIdentity(), clientNonce, challenge.serverNonce);
            crypto = SessionCrypto.forClient(keys, grant.sessionId());
        } finally {
            keys.close(); Arrays.fill(challenge.serverNonce,(byte)0); Arrays.fill(challenge.serverProof,(byte)0);
        }
        ByteBuffer response = ByteBuffer.allocate(SessionHandshakePackets.AUTH_RESPONSE_BYTES);
        SessionHandshakePackets.writeAuthResponse(response, grant.sessionId(), clientProof);
        send(client, Arrays.copyOf(response.array(), response.position())); Arrays.fill(clientProof,(byte)0);
        ByteBuffer plain = ByteBuffer.allocate(256); assertTrue(crypto.open(receive(client), plain));
        assertEquals(WirePacketType.AUTH_ACK, plain.get()); assertNotNull(AuthAckPacket.read(plain));
        return crypto;
    }

    private static void assertAttach(AgentSecureChannel agent, UUID playerId) throws Exception {
        assertAttach(agent, playerId, "lobby");
    }

    private static void assertAttach(AgentSecureChannel agent, UUID playerId, String backend) throws Exception {
        AgentMessage.Attach attach = assertInstanceOf(AgentMessage.Attach.class, agent.receive());
        assertEquals(playerId, attach.player().playerId());
        assertEquals(backend, attach.player().backendId());
    }

    private static void sendMic(DatagramSocket client, SessionCrypto crypto,
                                int sequence, byte[] opus) throws Exception {
        ByteBuffer inner = ByteBuffer.allocate(128);
        MicFramePacket.write(inner, sequence, opus, opus.length);
        ByteBuffer encrypted = ByteBuffer.allocate(256);
        assertTrue(crypto.seal(inner, encrypted));
        send(client, Arrays.copyOf(encrypted.array(), encrypted.position()));
    }

    private static void sendMicAndAssert(DatagramSocket client, SessionCrypto crypto,
                                         AgentSecureChannel agent, UUID playerId, int sequence) throws Exception {
        byte[] opus = {(byte) sequence, 42, 24, 7};
        ByteBuffer inner = ByteBuffer.allocate(128);
        MicFramePacket.write(inner, sequence, opus, opus.length);
        ByteBuffer encrypted = ByteBuffer.allocate(256);
        assertTrue(crypto.seal(inner, encrypted));
        send(client, Arrays.copyOf(encrypted.array(), encrypted.position()));
        AgentMessage.SpeakerFrame message = assertInstanceOf(AgentMessage.SpeakerFrame.class, agent.receive());
        assertEquals(playerId, message.frame().speakerId());
        assertEquals(Integer.toUnsignedLong(sequence), message.frame().sequence());
        assertArrayEquals(opus, message.frame().opusUnsafe());
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
