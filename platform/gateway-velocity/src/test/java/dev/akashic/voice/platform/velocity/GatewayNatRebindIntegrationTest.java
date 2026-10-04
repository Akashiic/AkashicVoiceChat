package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.bridge.BridgeDelivery;
import dev.akashic.voice.platform.bridge.PositionSnapshot;
import dev.akashic.voice.platform.bridge.SpatialKind;
import dev.akashic.voice.voice.common.AuthAckPacket;
import dev.akashic.voice.voice.common.HandshakePackets;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

final class GatewayNatRebindIntegrationTest {
    @Test
    void sameSessionRebindMovesAuthorityToNewUdpEndpoint() throws Exception {
        int port = freeUdpPort();
        UUID proxyId = UUID.randomUUID();
        RecordingListener listener = new RecordingListener();
        try (BootstrapSessionRegistry registry = new BootstrapSessionRegistry();
             GatewayUdpServer server = new GatewayUdpServer(port, registry, listener);
             DatagramSocket first = connected(port);
             DatagramSocket second = connected(port)) {
            server.start();
            BootstrapGrant grant = registry.grantFor(proxyId, "NatRebind", "lobby", port);
            assertNotNull(grant);
            byte[] secret = grant.masterSecretCopy();
            SessionCrypto firstCrypto = null, secondCrypto = null;
            try {
                firstCrypto = authenticate(first, grant, secret, nonce(11));
                waitFor(() -> server.snapshot().activeSessions() == 1, "first endpoint not active");
                assertEquals(1, listener.connected.get());
                sendMic(first, firstCrypto, 1, new byte[] {1,2,3});
                assertMic(listener, proxyId, 1);

                secondCrypto = authenticate(second, grant, secret, nonce(31));
                waitFor(() -> server.snapshot().sessionsOpened() == 2 && server.snapshot().sessionsClosed() == 1,
                    "rebind transport metrics did not settle");
                assertEquals(1,listener.connected.get(),"logical connect churned on rebind");
                assertEquals(0,listener.disconnected.get(),"logical disconnect churned on rebind");
                assertEquals(1, server.snapshot().activeSessions(), "rebind leaked two active sessions");
                assertEquals(1, server.snapshot().pendingHandshakes(), "old AUTH receipt survived rebind");
                assertEquals(2L, server.snapshot().sessionsOpened());
                assertEquals(1L, server.snapshot().sessionsClosed());

                sendMic(first, firstCrypto, 2, new byte[] {4,5,6});
                assertNull(listener.mic.poll(250, TimeUnit.MILLISECONDS), "old endpoint still routed media");
                sendMic(second, secondCrypto, 3, new byte[] {7,8,9});
                assertMic(listener, proxyId, 3);

                UUID sender = UUID.randomUUID();
                byte[] downlink = {9,8,7,6};
                BridgeDelivery delivery = new BridgeDelivery(proxyId, sender, UUID.randomUUID(), 44L,
                    SpatialKind.LOCATION, null, new PositionSnapshot("minecraft:overworld", 1,64,2),
                    24F, false, "proximity", downlink);
                assertTrue(server.sendBridgeDelivery("lobby", delivery));
                assertSound(second, secondCrypto, sender, 44, downlink);
                assertSilence(first);

                sendDisconnect(second, secondCrypto);
                waitFor(() -> server.snapshot().activeSessions() == 0, "rebound endpoint did not disconnect");
                assertEquals(2L, server.snapshot().sessionsOpened());
                assertEquals(2L, server.snapshot().sessionsClosed());
                assertEquals(0, server.snapshot().pendingHandshakes(), "disconnect left AUTH receipt");
                assertTrue(listener.errors.isEmpty(), "gateway errors: " + listener.errors);
            } finally {
                if (firstCrypto != null) firstCrypto.close();
                if (secondCrypto != null) secondCrypto.close();
                Arrays.fill(secret, (byte)0);
            }
        }
    }

    @Test
    void repeatedSameSessionRebindsNeverCreateDualAuthority() throws Exception {
        int port=freeUdpPort(); UUID proxyId=UUID.randomUUID(); RecordingListener listener=new RecordingListener();
        try (BootstrapSessionRegistry registry=new BootstrapSessionRegistry();
             GatewayUdpServer server=new GatewayUdpServer(port,registry,listener)) {
            server.start();
            BootstrapGrant grant=registry.grantFor(proxyId,"NatLoop","lobby",port); assertNotNull(grant);
            byte[] secret=grant.masterSecretCopy();
            List<DatagramSocket> sockets=new ArrayList<>(); List<SessionCrypto> cryptos=new ArrayList<>();
            try {
                final int rebinds=32;
                for(int i=0;i<rebinds;i++) {
                    DatagramSocket socket=connected(port); sockets.add(socket);
                    SessionCrypto crypto=authenticate(socket,grant,secret,nonce(40+i*3)); cryptos.add(crypto);
                    final int opened=i+1;
                    waitFor(()->server.snapshot().activeSessions()==1 && server.snapshot().sessionsOpened()==opened,
                        "rebind did not settle");
                    assertEquals(opened,server.snapshot().sessionsOpened()); assertEquals(i,server.snapshot().sessionsClosed());
                    assertEquals(1,listener.connected.get(),"rebind repeated logical connect");
                    assertEquals(0,listener.disconnected.get(),"rebind repeated logical disconnect");
                    assertEquals(1,server.snapshot().pendingHandshakes(),"activated rebind receipts accumulated");
                    sendMic(socket,crypto,100+i,new byte[]{(byte)i}); assertMic(listener,proxyId,100+i);
                    if(i>0) {
                        sendMic(sockets.get(i-1),cryptos.get(i-1),900+i,new byte[]{99});
                        assertNull(listener.mic.poll(120,TimeUnit.MILLISECONDS),"previous endpoint regained authority");
                    }
                }
                assertEquals(0L,server.snapshot().helloRateLimited());
                int last=rebinds-1;
                sendDisconnect(sockets.get(last),cryptos.get(last));
                waitFor(()->server.snapshot().activeSessions()==0,"final session leaked");
                assertEquals(rebinds,server.snapshot().sessionsOpened()); assertEquals(rebinds,server.snapshot().sessionsClosed());
                assertEquals(0,server.snapshot().pendingHandshakes(),"final AUTH receipt leaked");
                assertTrue(listener.errors.isEmpty(),"gateway errors: "+listener.errors);
            } finally {
                Arrays.fill(secret,(byte)0);
                for(SessionCrypto crypto:cryptos) crypto.close();
                for(DatagramSocket socket:sockets) socket.close();
            }
        }
    }

    @Test
    void sustainedMediaAcrossRebindsKeepsOneAuthorityAndOneReceipt() throws Exception {
        int port=freeUdpPort(); UUID proxyId=UUID.randomUUID(); RecordingListener listener=new RecordingListener();
        try (BootstrapSessionRegistry registry=new BootstrapSessionRegistry();
             GatewayUdpServer server=new GatewayUdpServer(port,registry,listener)) {
            server.start(); BootstrapGrant grant=registry.grantFor(proxyId,"NatLoad","lobby",port); assertNotNull(grant);
            byte[] secret=grant.masterSecretCopy(); List<DatagramSocket> sockets=new ArrayList<>();
            List<SessionCrypto> cryptos=new ArrayList<>(); final int rebinds=12, framesPerEndpoint=50;
            UUID downlinkSender=UUID.randomUUID();
            try {
                for(int hop=0;hop<rebinds;hop++) {
                    DatagramSocket socket=connected(port); sockets.add(socket);
                    SessionCrypto crypto=authenticate(socket,grant,secret,nonce(140+hop*3)); cryptos.add(crypto);
                    final int opened=hop+1; waitFor(()->server.snapshot().activeSessions()==1 && server.snapshot().sessionsOpened()==opened,
                        "load rebind did not settle");
                    assertEquals(1,listener.connected.get(),"load rebind churned logical connect");
                    assertEquals(0,listener.disconnected.get(),"load rebind churned logical disconnect");
                    assertEquals(1,server.snapshot().pendingHandshakes(),"load rebind accumulated receipt");
                    if(hop>0) { sendMic(sockets.get(hop-1),cryptos.get(hop-1),9000+hop,new byte[]{99});
                        assertNull(listener.mic.poll(120,TimeUnit.MILLISECONDS),"old endpoint emitted under load"); }
                    for(int f=0;f<framesPerEndpoint;f++) { int seq=hop*framesPerEndpoint+f;
                        sendMic(socket,crypto,seq,new byte[]{(byte)hop,(byte)f}); assertMic(listener,proxyId,seq); Thread.sleep(20L); }
                    byte[] downlink={(byte)hop,7,3};
                    BridgeDelivery delivery=new BridgeDelivery(proxyId,downlinkSender,UUID.randomUUID(),1000L+hop,
                        SpatialKind.LOCATION,null,new PositionSnapshot("minecraft:overworld",hop,64,hop),24F,false,"proximity",downlink);
                    assertTrue(server.sendBridgeDelivery("lobby",delivery)); assertSound(socket,crypto,downlinkSender,1000+hop,downlink);
                    if(hop>0) assertSilence(sockets.get(hop-1));
                }
                GatewayUdpSnapshot snap=server.snapshot();
                assertEquals((long)rebinds*framesPerEndpoint,snap.micFramesAccepted());
                assertEquals(rebinds,snap.soundFramesSent()); assertEquals(0L,snap.mediaRateLimited());
                assertEquals(0L,snap.encryptedRejected()); assertEquals(1,snap.pendingHandshakes());
                int last=rebinds-1; sendDisconnect(sockets.get(last),cryptos.get(last));
                waitFor(()->server.snapshot().activeSessions()==0,"load final session leaked");
                assertEquals(0,server.snapshot().pendingHandshakes(),"load final receipt leaked");
                assertTrue(listener.errors.isEmpty(),"gateway errors: "+listener.errors);
            } finally {
                Arrays.fill(secret,(byte)0); for(SessionCrypto crypto:cryptos) crypto.close();
                for(DatagramSocket socket:sockets) socket.close();
            }
        }
    }

    @Test
    void legacyHandshakeRebindAlsoBoundsActivatedReceipt() throws Exception {
        int port=freeUdpPort(); UUID proxyId=UUID.randomUUID(); RecordingListener listener=new RecordingListener();
        try (BootstrapSessionRegistry registry=new BootstrapSessionRegistry();
             GatewayUdpServer server=new GatewayUdpServer(port,registry,listener);
             DatagramSocket first=connected(port); DatagramSocket second=connected(port)) {
            server.start(); BootstrapGrant grant=registry.grantFor(proxyId,"LegacyNat","lobby",port);
            assertNotNull(grant); byte[] secret=grant.masterSecretCopy();
            try (SessionCrypto one=authenticateLegacy(first,grant,secret,nonce(7));
                 SessionCrypto two=authenticateLegacy(second,grant,secret,nonce(23))) {
                waitFor(()->server.snapshot().sessionsOpened()==2 && server.snapshot().sessionsClosed()==1,"legacy rebind did not settle");
                assertEquals(1,listener.connected.get(),"legacy rebind churned logical connect");
                assertEquals(0,listener.disconnected.get(),"legacy rebind churned logical disconnect");
                assertEquals(1,server.snapshot().activeSessions());
                assertEquals(1,server.snapshot().pendingHandshakes(),"legacy receipt accumulated");
                sendMic(first,one,70,new byte[]{7}); assertNull(listener.mic.poll(150,TimeUnit.MILLISECONDS));
                sendMic(second,two,71,new byte[]{8}); assertMic(listener,proxyId,71);
                sendDisconnect(second,two); waitFor(()->server.snapshot().activeSessions()==0,"legacy disconnect leaked");
                assertEquals(0,server.snapshot().pendingHandshakes(),"legacy disconnect left receipt");
            } finally { Arrays.fill(secret,(byte)0); }
        }
    }

    private static SessionCrypto authenticate(DatagramSocket client, BootstrapGrant grant,
        byte[] secret, byte[] clientNonce) throws Exception {
        ByteBuffer hello = ByteBuffer.allocate(SessionHandshakePackets.HELLO_BYTES);
        SessionHandshakePackets.writeHello(hello, grant.sessionId(), grant.voiceIdentity(), clientNonce);
        send(client, Arrays.copyOf(hello.array(), hello.position()));
        SessionHandshakePackets.Challenge challenge = SessionHandshakePackets.readChallenge(receive(client));
        assertNotNull(challenge); assertEquals(grant.sessionId(), challenge.sessionId);
        SessionKeys keys = SessionKeys.derive(secret, clientNonce, challenge.serverNonce, grant.sessionId());
        byte[] proof = null; SessionCrypto crypto = null;
        try {
            assertTrue(HandshakeProof.verifyServer(keys, grant.voiceIdentity(), clientNonce,
                challenge.serverNonce, challenge.serverProof));
            proof = HandshakeProof.computeClient(keys, grant.voiceIdentity(), clientNonce, challenge.serverNonce);
            crypto = SessionCrypto.forClient(keys, grant.sessionId());
        } finally {
            keys.close(); Arrays.fill(challenge.serverNonce,(byte)0); Arrays.fill(challenge.serverProof,(byte)0);
        }
        ByteBuffer response = ByteBuffer.allocate(SessionHandshakePackets.AUTH_RESPONSE_BYTES);
        SessionHandshakePackets.writeAuthResponse(response, grant.sessionId(), proof);
        byte[] responseBytes=Arrays.copyOf(response.array(), response.position());
        send(client,responseBytes);
        assertAuthAck(client,crypto);
        send(client,responseBytes);
        assertAuthAck(client,crypto);
        Arrays.fill(responseBytes,(byte)0); Arrays.fill(proof,(byte)0);
        return crypto;
    }
    private static SessionCrypto authenticateLegacy(DatagramSocket client, BootstrapGrant grant,
        byte[] secret, byte[] clientNonce) throws Exception {
        ByteBuffer hello=ByteBuffer.allocate(HandshakePackets.HELLO_BYTES);
        HandshakePackets.writeHello(hello,grant.voiceIdentity(),clientNonce);
        send(client,Arrays.copyOf(hello.array(),hello.position()));
        HandshakePackets.Challenge challenge=HandshakePackets.readChallenge(receive(client)); assertNotNull(challenge);
        SessionKeys keys=SessionKeys.derive(secret,clientNonce,challenge.serverNonce); byte[] proof=null; SessionCrypto crypto=null;
        try { assertTrue(HandshakeProof.verifyServer(keys,grant.voiceIdentity(),clientNonce,challenge.serverNonce,challenge.serverProof));
            proof=HandshakeProof.computeClient(keys,grant.voiceIdentity(),clientNonce,challenge.serverNonce); crypto=SessionCrypto.forClient(keys);
        } finally { keys.close(); Arrays.fill(challenge.serverNonce,(byte)0); Arrays.fill(challenge.serverProof,(byte)0); }
        ByteBuffer response=ByteBuffer.allocate(HandshakePackets.AUTH_RESPONSE_BYTES); HandshakePackets.writeAuthResponse(response,proof);
        byte[] bytes=Arrays.copyOf(response.array(),response.position()); send(client,bytes); assertAuthAck(client,crypto);
        send(client,bytes); assertAuthAck(client,crypto); Arrays.fill(bytes,(byte)0); Arrays.fill(proof,(byte)0); return crypto;
    }

    private static void assertAuthAck(DatagramSocket client, SessionCrypto crypto) throws Exception {
        ByteBuffer plain=ByteBuffer.allocate(256); assertTrue(crypto.open(receive(client),plain));
        assertEquals(WirePacketType.AUTH_ACK,plain.get()); AuthAckPacket ack=AuthAckPacket.read(plain);
        assertNotNull(ack); assertEquals(AuthAckPacket.STATUS_OK,ack.status);
    }

    private static void sendMic(DatagramSocket socket, SessionCrypto crypto, int sequence, byte[] opus) throws Exception {
        ByteBuffer inner = ByteBuffer.allocate(256); MicFramePacket.write(inner, sequence, opus, opus.length);
        ByteBuffer wire = ByteBuffer.allocate(512); assertTrue(crypto.seal(inner, wire));
        send(socket, Arrays.copyOf(wire.array(), wire.position()));
    }

    private static void assertMic(RecordingListener listener, UUID player, long sequence) throws Exception {
        MicObservation value = listener.mic.poll(2, TimeUnit.SECONDS);
        assertNotNull(value, "MIC frame missing"); assertEquals(player, value.player()); assertEquals(sequence, value.sequence());
    }

    private static void assertSound(DatagramSocket socket, SessionCrypto crypto,
        UUID sender, int sequence, byte[] opus) throws Exception {
        ByteBuffer plain = ByteBuffer.allocate(1500); assertTrue(crypto.open(receive(socket), plain));
        assertEquals(WirePacketType.SOUND_FRAME_V2, plain.get());
        SoundFrameV2Packet sound = SoundFrameV2Packet.read(plain); assertNotNull(sound);
        assertEquals(sender, sound.senderId); assertEquals(sequence, sound.sequence);
        assertArrayEquals(opus, Arrays.copyOfRange(sound.opusData, sound.opusOffset, sound.opusOffset + sound.opusLength));
    }

    private static void assertSilence(DatagramSocket socket) throws Exception {
        int previous = socket.getSoTimeout(); socket.setSoTimeout(300);
        try { assertThrows(SocketTimeoutException.class, () -> receive(socket)); }
        finally { socket.setSoTimeout(previous); }
    }

    private static void sendDisconnect(DatagramSocket socket, SessionCrypto crypto) throws Exception {
        ByteBuffer inner = ByteBuffer.allocate(16); inner.put(WirePacketType.DISCONNECT);
        ByteBuffer wire = ByteBuffer.allocate(128); assertTrue(crypto.seal(inner, wire));
        send(socket, Arrays.copyOf(wire.array(), wire.position()));
    }

    private static DatagramSocket connected(int port) throws Exception {
        DatagramSocket socket = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        socket.connect(InetAddress.getLoopbackAddress(), port); socket.setSoTimeout(2_000); return socket;
    }

    private static byte[] nonce(int seed) {
        byte[] value = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        for (int i=0;i<value.length;i++) value[i]=(byte)(seed+i); return value;
    }

    private static void send(DatagramSocket socket, byte[] bytes) throws Exception {
        socket.send(new DatagramPacket(bytes, bytes.length));
    }

    private static ByteBuffer receive(DatagramSocket socket) throws Exception {
        DatagramPacket packet = new DatagramPacket(new byte[1500],1500); socket.receive(packet);
        return ByteBuffer.wrap(Arrays.copyOf(packet.getData(), packet.getLength()));
    }

    private static int freeUdpPort() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(0)) { return socket.getLocalPort(); }
    }

    private static void waitFor(BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime()<deadline) Thread.sleep(5L);
        assertTrue(condition.getAsBoolean(), message);
    }

    private record MicObservation(UUID player, long sequence) {}

    private static final class RecordingListener implements GatewayUdpServer.Listener {
        final AtomicInteger connected = new AtomicInteger(), disconnected = new AtomicInteger();
        final ArrayBlockingQueue<MicObservation> mic = new ArrayBlockingQueue<>(8);
        final ArrayBlockingQueue<Throwable> errors = new ArrayBlockingQueue<>(8);
        @Override public void onConnected(UUID player, String backend, UUID voice) { connected.incrementAndGet(); }
        @Override public void onDisconnected(UUID player, String backend, UUID voice) { disconnected.incrementAndGet(); }
        @Override public void onMicFrame(UUID player, String backend, UUID voice, long sequence, byte[] opus) {
            mic.offer(new MicObservation(player, sequence));
        }
        @Override public void onError(Throwable error) { errors.offer(error); }
    }
}
