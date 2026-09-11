package dev.akashic.voice.voice.server;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;

import dev.akashic.voice.voice.common.AuthAckPacket;
import dev.akashic.voice.voice.common.MicFramePacket;
import dev.akashic.voice.voice.common.WirePacketType;
import dev.akashic.voice.voice.common.crypto.HandshakeProof;
import dev.akashic.voice.voice.common.crypto.SessionCrypto;
import dev.akashic.voice.voice.common.crypto.SessionKeys;

public final class SecureHandshakeFlowSmoke {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        UUID playerId = new UUID(123L, 456L);
        byte[] master = new byte[SessionKeys.MASTER_SECRET_BYTES];
        byte[] clientNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        for (int i = 0; i < master.length; i++) master[i] = (byte) (i + 11);
        for (int i = 0; i < clientNonce.length; i++) clientNonce[i] = (byte) (i + 31);

        PendingHandshake pending = PendingHandshake.create(
            playerId,
            new InetSocketAddress("127.0.0.1", 24454),
            master,
            clientNonce,
            new SecureRandom());

        SessionKeys clientKeys = SessionKeys.derive(master, clientNonce, pending.serverNonce);
        require(
            HandshakeProof.verifyServer(clientKeys, playerId, clientNonce, pending.serverNonce, pending.serverProof),
            "server proof failed");
        byte[] clientProof = HandshakeProof.computeClient(clientKeys, playerId, clientNonce, pending.serverNonce);
        require(pending.verifyClientProof(clientProof), "client proof failed");
        clientProof[0] ^= 1;
        require(!pending.verifyClientProof(clientProof), "tampered client proof accepted");
        clientProof[0] ^= 1;

        SessionCrypto clientCrypto = SessionCrypto.forClient(clientKeys);
        clientKeys.close();
        SessionCrypto serverCrypto = pending.activate();
        require(serverCrypto != null, "server crypto activation failed");

        ByteBuffer plain = ByteBuffer.allocate(1500);
        ByteBuffer wire = ByteBuffer.allocate(1500);
        ByteBuffer opened = ByteBuffer.allocate(1500);

        new AuthAckPacket(AuthAckPacket.STATUS_OK).write(plain);
        require(serverCrypto.seal(plain, wire), "server auth ACK seal failed");
        byte[] ackWire = Arrays.copyOf(wire.array(), wire.position());
        require(clientCrypto.open(ByteBuffer.wrap(ackWire), opened), "client failed to open auth ACK");
        require(opened.get() == WirePacketType.AUTH_ACK, "wrong inner auth type");
        AuthAckPacket ack = AuthAckPacket.read(opened);
        require(ack != null && ack.status == AuthAckPacket.STATUS_OK, "bad auth ACK");

        byte[] opus = new byte[] {10, 20, 30, 40};
        plain.clear();
        wire.clear();
        opened.clear();
        MicFramePacket.write(plain, 77, opus, opus.length);
        require(clientCrypto.seal(plain, wire), "client mic seal failed");
        byte[] micWire = Arrays.copyOf(wire.array(), wire.position());
        require(serverCrypto.open(ByteBuffer.wrap(micWire), opened), "server failed to open mic frame");
        require(opened.get() == WirePacketType.MIC_FRAME, "wrong inner mic type");
        MicFramePacket mic = MicFramePacket.read(opened);
        require(mic != null && mic.sequence == 77 && mic.opusLength == opus.length, "bad mic payload");
        require(!serverCrypto.open(ByteBuffer.wrap(micWire), ByteBuffer.allocate(1500)), "mic replay accepted");

        clientCrypto.close();
        serverCrypto.close();
        pending.close();
        Arrays.fill(master, (byte) 0);
        Arrays.fill(clientNonce, (byte) 0);
        Arrays.fill(clientProof, (byte) 0);
        System.out.println("secure-handshake-flow-smoke-ok");
    }
}
