package dev.akashic.voice.voice.server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import dev.akashic.voice.voice.common.AuthAckPacket;
import dev.akashic.voice.voice.common.MicFramePacket;
import dev.akashic.voice.voice.common.SessionHandshakePackets;
import dev.akashic.voice.voice.common.WirePacketType;
import dev.akashic.voice.voice.common.crypto.HandshakeProof;
import dev.akashic.voice.voice.common.crypto.SessionCrypto;
import dev.akashic.voice.voice.common.crypto.SessionKeys;

public final class SessionVoiceServerIntegrationSmoke {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static void waitFor(BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5L);
        check(condition.getAsBoolean(), message);
    }

    public static void main(String[] args) throws Exception {
        VoiceServer server = new VoiceServer(0);
        UUID player = new UUID(0x2200L, 0x2201L);
        byte[] secret = null;
        SessionCrypto clientCrypto = null;
        try (DatagramSocket client = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            client.setSoTimeout(3000);
            server.start();
            VoiceServerMetricsSnapshot initial = server.metricsSnapshot();
            check(initial.running() && initial.udpPort() > 0, "server did not bind");
            client.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), initial.udpPort()));

            secret = SecretRegistry.getOrCreate(player);
            UUID sessionId = SecretRegistry.getOrCreateSessionId(player);
            UUID staleSession = new UUID(sessionId.getMostSignificantBits(), sessionId.getLeastSignificantBits() ^ 1L);
            byte[] clientNonce = nonce(11);

            long rejected = server.metricsSnapshot().handshakeRejected();
            sendSessionHello(client, staleSession, player, clientNonce);
            waitFor(() -> server.metricsSnapshot().handshakeRejected() == rejected + 1,
                "stale session HELLO was not rejected");
            check(server.metricsSnapshot().pendingHandshakes() == 0,
                "stale session created pending handshake");

            sendSessionHello(client, sessionId, player, clientNonce);
            SessionHandshakePackets.Challenge challenge = SessionHandshakePackets.readChallenge(receive(client));
            check(challenge != null, "session challenge missing");
            check(sessionId.equals(challenge.sessionId), "challenge session id mismatch");
            SessionKeys keys = SessionKeys.derive(secret, clientNonce, challenge.serverNonce, sessionId);
            byte[] proof;
            try {
                check(HandshakeProof.verifyServer(keys, player, clientNonce, challenge.serverNonce,
                    challenge.serverProof), "session server proof invalid");
                proof = HandshakeProof.computeClient(keys, player, clientNonce, challenge.serverNonce);
                clientCrypto = SessionCrypto.forClient(keys, sessionId);
            } finally {
                keys.close();
            }

            ByteBuffer response = ByteBuffer.allocate(SessionHandshakePackets.AUTH_RESPONSE_BYTES);
            SessionHandshakePackets.writeAuthResponse(response, sessionId, proof);
            send(client, response);

            ByteBuffer authPlain = ByteBuffer.allocate(256);
            check(clientCrypto.open(receive(client), authPlain), "session AUTH_ACK did not decrypt");
            check(authPlain.get() == WirePacketType.AUTH_ACK, "wrong session auth inner type");
            AuthAckPacket ack = AuthAckPacket.read(authPlain);
            check(ack != null && ack.status == AuthAckPacket.STATUS_OK, "session auth rejected");
            waitFor(() -> server.metricsSnapshot().activeSessions() == 1, "session was not installed");

            byte[] opus = new byte[] {3, 1, 4, 1, 5};
            byte[] firstMic = sealedMic(clientCrypto, 1, opus);
            send(client, ByteBuffer.wrap(firstMic));
            waitFor(() -> server.metricsSnapshot().micFramesAccepted() == 1, "session mic not accepted");

            send(client, ByteBuffer.wrap(firstMic));
            waitFor(() -> server.metricsSnapshot().encryptedRejected() >= 1, "session replay not rejected");
            check(server.metricsSnapshot().micFramesAccepted() == 1, "replay accepted as mic");
            byte[] secondMic = sealedMic(clientCrypto, 2, opus);
            byte[] wrongSessionWire = Arrays.copyOf(secondMic, secondMic.length);
            wrongSessionWire[19] ^= 1;
            long rejectedBeforeSessionTamper = server.metricsSnapshot().encryptedRejected();
            send(client, ByteBuffer.wrap(wrongSessionWire));
            waitFor(() -> server.metricsSnapshot().encryptedRejected() == rejectedBeforeSessionTamper + 1,
                "wrong session encrypted packet not rejected");
            send(client, ByteBuffer.wrap(secondMic));
            waitFor(() -> server.metricsSnapshot().micFramesAccepted() == 2,
                "session-id rejection poisoned replay counter");

            ByteBuffer disconnectPlain = ByteBuffer.allocate(8);
            disconnectPlain.put(WirePacketType.DISCONNECT);
            ByteBuffer disconnectWire = ByteBuffer.allocate(128);
            check(clientCrypto.seal(disconnectPlain, disconnectWire), "session disconnect seal failed");
            send(client, disconnectWire);
            waitFor(() -> server.metricsSnapshot().activeSessions() == 0, "session disconnect not applied");

            SecretRegistry.invalidate(player);
            byte[] rotatedSecret = SecretRegistry.getOrCreate(player);
            UUID rotatedSession = SecretRegistry.getOrCreateSessionId(player);
            check(!sessionId.equals(rotatedSession), "bootstrap session did not rotate");
            long rejectedBeforeRotation = server.metricsSnapshot().handshakeRejected();
            sendSessionHello(client, sessionId, player, nonce(33));
            waitFor(() -> server.metricsSnapshot().handshakeRejected() == rejectedBeforeRotation + 1,
                "pre-rotation session still accepted");
            Arrays.fill(rotatedSecret, (byte) 0);

            Arrays.fill(clientNonce, (byte) 0);
            Arrays.fill(challenge.serverNonce, (byte) 0);
            Arrays.fill(challenge.serverProof, (byte) 0);
            Arrays.fill(proof, (byte) 0);
            System.out.println("SESSION_VOICE_SERVER_OK mic=2 stale=2 replay=1");
        } finally {
            if (clientCrypto != null) clientCrypto.close();
            if (secret != null) Arrays.fill(secret, (byte) 0);
            SecretRegistry.invalidate(player);
            PlayerStateRegistry.remove(player);
            server.stop();
        }
    }

    private static byte[] nonce(int seed) {
        byte[] out = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        for (int i = 0; i < out.length; i++) out[i] = (byte) (seed + i);
        return out;
    }

    private static void sendSessionHello(DatagramSocket socket, UUID sessionId, UUID playerId,
        byte[] clientNonce) throws Exception {
        ByteBuffer hello = ByteBuffer.allocate(SessionHandshakePackets.HELLO_BYTES);
        SessionHandshakePackets.writeHello(hello, sessionId, playerId, clientNonce);
        send(socket, hello);
    }

    private static byte[] sealedMic(SessionCrypto crypto, int sequence, byte[] opus) {
        ByteBuffer plain = ByteBuffer.allocate(128);
        MicFramePacket.write(plain, sequence, opus, opus.length);
        ByteBuffer wire = ByteBuffer.allocate(256);
        check(crypto.seal(plain, wire), "session mic seal failed");
        return Arrays.copyOf(wire.array(), wire.position());
    }
    private static void send(DatagramSocket socket, ByteBuffer buffer) throws Exception {
        int length = buffer.position();
        if (length == 0) length = buffer.remaining();
        byte[] bytes;
        if (buffer.hasArray() && buffer.position() > 0) {
            bytes = Arrays.copyOfRange(buffer.array(), buffer.arrayOffset(), buffer.arrayOffset() + length);
        } else {
            ByteBuffer copy = buffer.duplicate();
            bytes = new byte[copy.remaining()];
            copy.get(bytes);
        }
        socket.send(new DatagramPacket(bytes, bytes.length));
    }

    private static ByteBuffer receive(DatagramSocket socket) throws Exception {
        DatagramPacket packet = new DatagramPacket(new byte[1500], 1500);
        socket.receive(packet);
        return ByteBuffer.wrap(Arrays.copyOfRange(
            packet.getData(), packet.getOffset(), packet.getOffset() + packet.getLength()));
    }
}
