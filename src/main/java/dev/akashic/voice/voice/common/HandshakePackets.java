package dev.akashic.voice.voice.common;

import java.nio.ByteBuffer;
import java.util.UUID;

import dev.akashic.voice.voice.common.crypto.HandshakeProof;
import dev.akashic.voice.voice.common.crypto.SessionKeys;

public final class HandshakePackets {

    public static final int HELLO_BYTES = WireProtocol.OUTER_HEADER_BYTES + 16 + SessionKeys.HANDSHAKE_NONCE_BYTES;
    public static final int CHALLENGE_BYTES = WireProtocol.OUTER_HEADER_BYTES + SessionKeys.HANDSHAKE_NONCE_BYTES
        + HandshakeProof.PROOF_BYTES;
    public static final int AUTH_RESPONSE_BYTES = WireProtocol.OUTER_HEADER_BYTES + HandshakeProof.PROOF_BYTES;

    private HandshakePackets() {}

    public static void writeHello(ByteBuffer buf, UUID playerId, byte[] clientNonce) {
        requireNonce(clientNonce);
        WireProtocol.writeHeader(buf, WireProtocol.OUTER_HELLO);
        buf.putLong(playerId.getMostSignificantBits());
        buf.putLong(playerId.getLeastSignificantBits());
        buf.put(clientNonce);
    }

    public static Hello readHello(ByteBuffer buf) {
        if (buf.remaining() != HELLO_BYTES) return null;
        if (WireProtocol.readHeader(buf) != WireProtocol.OUTER_HELLO) return null;
        UUID playerId = new UUID(buf.getLong(), buf.getLong());
        byte[] clientNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        buf.get(clientNonce);
        return new Hello(playerId, clientNonce);
    }

    public static void writeChallenge(ByteBuffer buf, byte[] serverNonce, byte[] serverProof) {
        requireNonce(serverNonce);
        requireProof(serverProof);
        WireProtocol.writeHeader(buf, WireProtocol.OUTER_CHALLENGE);
        buf.put(serverNonce);
        buf.put(serverProof);
    }

    public static Challenge readChallenge(ByteBuffer buf) {
        if (buf.remaining() != CHALLENGE_BYTES) return null;
        if (WireProtocol.readHeader(buf) != WireProtocol.OUTER_CHALLENGE) return null;
        byte[] serverNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        byte[] serverProof = new byte[HandshakeProof.PROOF_BYTES];
        buf.get(serverNonce);
        buf.get(serverProof);
        return new Challenge(serverNonce, serverProof);
    }

    public static void writeAuthResponse(ByteBuffer buf, byte[] clientProof) {
        requireProof(clientProof);
        WireProtocol.writeHeader(buf, WireProtocol.OUTER_AUTH_RESPONSE);
        buf.put(clientProof);
    }

    public static AuthResponse readAuthResponse(ByteBuffer buf) {
        if (buf.remaining() != AUTH_RESPONSE_BYTES) return null;
        if (WireProtocol.readHeader(buf) != WireProtocol.OUTER_AUTH_RESPONSE) return null;
        byte[] clientProof = new byte[HandshakeProof.PROOF_BYTES];
        buf.get(clientProof);
        return new AuthResponse(clientProof);
    }

    private static void requireNonce(byte[] nonce) {
        if (nonce == null || nonce.length != SessionKeys.HANDSHAKE_NONCE_BYTES) {
            throw new IllegalArgumentException("invalid handshake nonce");
        }
    }

    private static void requireProof(byte[] proof) {
        if (proof == null || proof.length != HandshakeProof.PROOF_BYTES) {
            throw new IllegalArgumentException("invalid handshake proof");
        }
    }

    public static final class Hello {

        public final UUID playerId;
        public final byte[] clientNonce;

        private Hello(UUID playerId, byte[] clientNonce) {
            this.playerId = playerId;
            this.clientNonce = clientNonce;
        }
    }

    public static final class Challenge {

        public final byte[] serverNonce;
        public final byte[] serverProof;

        private Challenge(byte[] serverNonce, byte[] serverProof) {
            this.serverNonce = serverNonce;
            this.serverProof = serverProof;
        }
    }

    public static final class AuthResponse {

        public final byte[] clientProof;

        private AuthResponse(byte[] clientProof) {
            this.clientProof = clientProof;
        }
    }
}
