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
import dev.akashic.voice.voice.common.HandshakePackets;
import dev.akashic.voice.voice.common.MicFramePacket;
import dev.akashic.voice.voice.common.WirePacketType;
import dev.akashic.voice.voice.common.crypto.HandshakeProof;
import dev.akashic.voice.voice.common.crypto.SessionCrypto;
import dev.akashic.voice.voice.common.crypto.SessionKeys;

/** Real VoiceServer + loopback UDP/crypto. No Minecraft client, audio device or per-frame logging. */
public final class VoiceServerMetricsIntegrationSmoke {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void waitFor(BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5L);
        check(condition.getAsBoolean(), message);
    }

    public static void main(String[] args) throws Exception {
        VoiceServer server = new VoiceServer(0);
        UUID player = new UUID(9001L, 42L);
        byte[] secret = null;
        SessionCrypto clientCrypto = null;
        try (DatagramSocket client = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            client.setSoTimeout(3000);
            server.start();
            VoiceServerMetricsSnapshot initial = server.metricsSnapshot();
            check(initial.running() && initial.udpPort() > 0 && initial.activeSessions() == 0,
                "server live snapshot incorrect");
            client.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), initial.udpPort()));

            client.send(new DatagramPacket(new byte[] {1, 2, 3}, 3));
            waitFor(() -> server.metricsSnapshot().datagramsReceived() >= 1, "malformed datagram not counted");
            check(server.metricsSnapshot().bytesReceived() >= 3, "received bytes not counted");

            secret = SecretRegistry.getOrCreate(player);
            PlayerStateRegistry.update(player, 0, 0.0, 64.0, 0.0);
            byte[] clientNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
            for (int i = 0; i < clientNonce.length; i++) clientNonce[i] = (byte) (i + 10);
            ByteBuffer hello = ByteBuffer.allocate(HandshakePackets.HELLO_BYTES);
            HandshakePackets.writeHello(hello, player, clientNonce);
            long rejectedBeforeMalformedHello = server.metricsSnapshot().handshakeRejected();
            send(client, ByteBuffer.wrap(Arrays.copyOf(hello.array(), hello.position() - 1)));
            waitFor(() -> server.metricsSnapshot().handshakeRejected() == rejectedBeforeMalformedHello + 1,
                "malformed HELLO not counted");
            send(client, hello);

            ByteBuffer challengeWire = receive(client);
            HandshakePackets.Challenge challenge = HandshakePackets.readChallenge(challengeWire);
            check(challenge != null, "server challenge missing");
            SessionKeys clientKeys = SessionKeys.derive(secret, clientNonce, challenge.serverNonce);
            check(HandshakeProof.verifyServer(clientKeys, player, clientNonce, challenge.serverNonce,
                challenge.serverProof), "server proof invalid");
            byte[] proof = HandshakeProof.computeClient(clientKeys, player, clientNonce, challenge.serverNonce);
            clientCrypto = SessionCrypto.forClient(clientKeys);
            clientKeys.close();

            ByteBuffer response = ByteBuffer.allocate(HandshakePackets.AUTH_RESPONSE_BYTES);
            HandshakePackets.writeAuthResponse(response, proof);
            long rejectedBeforeMalformedAuth = server.metricsSnapshot().handshakeRejected();
            send(client, ByteBuffer.wrap(Arrays.copyOf(response.array(), response.position() - 1)));
            waitFor(() -> server.metricsSnapshot().handshakeRejected() == rejectedBeforeMalformedAuth + 1,
                "malformed AUTH response not counted");
            send(client, response);

            ByteBuffer authWire = receive(client);
            ByteBuffer authPlain = ByteBuffer.allocate(256);
            check(clientCrypto.open(authWire, authPlain), "auth ACK encryption invalid");
            check(authPlain.get() == WirePacketType.AUTH_ACK, "wrong auth inner type");
            AuthAckPacket ack = AuthAckPacket.read(authPlain);
            check(ack != null && ack.status == AuthAckPacket.STATUS_OK, "auth not accepted");
            waitFor(() -> server.metricsSnapshot().activeSessions() == 1, "active session not reflected");
            VoiceServerMetricsSnapshot authenticated = server.metricsSnapshot();
            check(authenticated.sessionsOpened() == 1 && authenticated.datagramsSent() >= 2,
                "session or outbound handshake metrics missing");

            byte[] opus = new byte[] {10, 20, 30, 40};
            ByteBuffer micPlain = ByteBuffer.allocate(128);
            MicFramePacket.write(micPlain, 7, opus, opus.length);
            ByteBuffer micWire = ByteBuffer.allocate(512);
            check(clientCrypto.seal(micPlain, micWire), "client mic seal failed");
            byte[] sealedMic = Arrays.copyOf(micWire.array(), micWire.position());
            send(client, ByteBuffer.wrap(sealedMic));
            waitFor(() -> server.metricsSnapshot().micFramesAccepted() == 1, "valid mic frame not counted");
            check(server.metricsSnapshot().micBytesAccepted() == opus.length, "mic byte count incorrect");

            send(client, ByteBuffer.wrap(sealedMic));
            waitFor(() -> server.metricsSnapshot().encryptedRejected() >= 1, "encrypted replay not counted");
            check(server.metricsSnapshot().micFramesAccepted() == 1, "replay incremented accepted mic frames");

            long rejectedBeforeOrphanAuth = server.metricsSnapshot().handshakeRejected();
            try (DatagramSocket orphan = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
                orphan.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), initial.udpPort()));
                send(orphan, response);
            }
            waitFor(() -> server.metricsSnapshot().handshakeRejected() == rejectedBeforeOrphanAuth + 1,
                "orphan AUTH response not counted");

            UUID unknown = new UUID(1234L, 5678L);
            ByteBuffer unknownHello = ByteBuffer.allocate(HandshakePackets.HELLO_BYTES);
            HandshakePackets.writeHello(unknownHello, unknown, new byte[SessionKeys.HANDSHAKE_NONCE_BYTES]);
            send(client, unknownHello);
            waitFor(() -> server.metricsSnapshot().handshakeRejected() >= 1, "unknown bootstrap not counted");

            ByteBuffer disconnectPlain = ByteBuffer.allocate(8);
            disconnectPlain.put(WirePacketType.DISCONNECT);
            ByteBuffer disconnectWire = ByteBuffer.allocate(128);
            check(clientCrypto.seal(disconnectPlain, disconnectWire), "disconnect seal failed");
            send(client, disconnectWire);
            waitFor(() -> {
                VoiceServerMetricsSnapshot snapshot = server.metricsSnapshot();
                return snapshot.activeSessions() == 0 && snapshot.sessionsClosed() == 1;
            }, "disconnect or close metric not reflected");
            VoiceServerMetricsSnapshot disconnected = server.metricsSnapshot();
            check(disconnected.sessionsClosed() == 1 && disconnected.sessionsTimedOut() == 0,
                "explicit disconnect session metrics wrong");
            check(disconnected.datagramsReceived() >= 6 && disconnected.bytesReceived() > 0,
                "aggregate receive metrics incomplete");
            check(disconnected.datagramsSent() >= 2 && disconnected.bytesSent() > 0,
                "aggregate send metrics incomplete");

            Arrays.fill(clientNonce, (byte) 0);
            Arrays.fill(proof, (byte) 0);
            System.out.println("VOICE_SERVER_METRICS_INTEGRATION_OK rx=" + disconnected.datagramsReceived()
                + " tx=" + disconnected.datagramsSent() + " mic=" + disconnected.micFramesAccepted());
        } finally {
            if (clientCrypto != null) clientCrypto.close();
            if (secret != null) Arrays.fill(secret, (byte) 0);
            SecretRegistry.invalidate(player);
            PlayerStateRegistry.remove(player);
            server.stop();
            VoiceServerMetricsSnapshot beforeClosedSend = server.metricsSnapshot();
            java.lang.reflect.Method sendRaw = VoiceServer.class.getDeclaredMethod(
                "sendRaw", ByteBuffer.class, java.net.SocketAddress.class);
            sendRaw.setAccessible(true);
            ByteBuffer dead = ByteBuffer.allocate(8);
            dead.put((byte) 1);
            Object sent = sendRaw.invoke(server, dead, new InetSocketAddress(InetAddress.getLoopbackAddress(), 9));
            check(Boolean.FALSE.equals(sent), "closed socket reported successful send");
            byte[] syntheticSecret = new byte[SessionKeys.MASTER_SECRET_BYTES];
            byte[] syntheticNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
            SessionKeys syntheticKeys = SessionKeys.derive(syntheticSecret, syntheticNonce, syntheticNonce);
            VoiceConnection deadConnection = new VoiceConnection(new UUID(77L, 88L),
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 9), SessionCrypto.forServer(syntheticKeys));
            syntheticKeys.close();
            java.lang.reflect.Method sendEncrypted = VoiceServer.class.getDeclaredMethod(
                "sendEncrypted", VoiceConnection.class, ByteBuffer.class);
            sendEncrypted.setAccessible(true);
            ByteBuffer deadPlain = ByteBuffer.allocate(8);
            deadPlain.put(WirePacketType.KEEPALIVE);
            Object encryptedSent = sendEncrypted.invoke(server, deadConnection, deadPlain);
            check(Boolean.FALSE.equals(encryptedSent), "encrypted send hid closed socket failure");
            deadConnection.close();
            Arrays.fill(syntheticSecret, (byte) 0);
            Arrays.fill(syntheticNonce, (byte) 0);
            VoiceServerMetricsSnapshot afterClosedSend = server.metricsSnapshot();
            check(afterClosedSend.datagramsSent() == beforeClosedSend.datagramsSent()
                && afterClosedSend.bytesSent() == beforeClosedSend.bytesSent(), "failed send inflated tx metrics");
            check(!afterClosedSend.running(), "server remained running after stop");
        }
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
        return ByteBuffer.wrap(Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getOffset() + packet.getLength()));
    }
}
