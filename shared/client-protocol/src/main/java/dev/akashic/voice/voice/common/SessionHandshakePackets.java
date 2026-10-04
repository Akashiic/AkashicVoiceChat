package dev.akashic.voice.voice.common;

import java.nio.ByteBuffer;
import java.util.UUID;

import dev.akashic.voice.voice.common.crypto.HandshakeProof;
import dev.akashic.voice.voice.common.crypto.SessionKeys;

/** Additive AV UDP handshake carrying an explicit bootstrap session identity. */
public final class SessionHandshakePackets {
    public static final int HELLO_BYTES = WireProtocol.OUTER_HEADER_BYTES + WireProtocol.SESSION_ID_BYTES
        + 16 + SessionKeys.HANDSHAKE_NONCE_BYTES;
    public static final int CHALLENGE_BYTES = WireProtocol.OUTER_HEADER_BYTES + WireProtocol.SESSION_ID_BYTES
        + SessionKeys.HANDSHAKE_NONCE_BYTES + HandshakeProof.PROOF_BYTES;
    public static final int AUTH_RESPONSE_BYTES = WireProtocol.OUTER_HEADER_BYTES + WireProtocol.SESSION_ID_BYTES
        + HandshakeProof.PROOF_BYTES;

    private SessionHandshakePackets() {}

    public static void writeHello(ByteBuffer buf, UUID sessionId, UUID playerId, byte[] clientNonce) {
        requireIds(sessionId, playerId);
        requireNonce(clientNonce);
        WireProtocol.writeHeader(buf, WireProtocol.OUTER_SESSION_HELLO);
        writeUuid(buf, sessionId);
        writeUuid(buf, playerId);
        buf.put(clientNonce);
    }

    public static Hello readHello(ByteBuffer buf) {
        if (buf.remaining() != HELLO_BYTES) return null;
        if (WireProtocol.readHeader(buf) != WireProtocol.OUTER_SESSION_HELLO) return null;
        UUID sessionId = readUuid(buf);
        UUID playerId = readUuid(buf);
        byte[] clientNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        buf.get(clientNonce);
        return new Hello(sessionId, playerId, clientNonce);
    }

    public static void writeChallenge(ByteBuffer buf, UUID sessionId, byte[] serverNonce, byte[] serverProof) {
        if (sessionId == null) throw new IllegalArgumentException("sessionId");
        requireNonce(serverNonce);
        requireProof(serverProof);
        WireProtocol.writeHeader(buf, WireProtocol.OUTER_SESSION_CHALLENGE);
        writeUuid(buf, sessionId);
        buf.put(serverNonce);
        buf.put(serverProof);
    }

    public static Challenge readChallenge(ByteBuffer buf) {
        if (buf.remaining() != CHALLENGE_BYTES) return null;
        if (WireProtocol.readHeader(buf) != WireProtocol.OUTER_SESSION_CHALLENGE) return null;
        UUID sessionId = readUuid(buf);
        byte[] serverNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        byte[] serverProof = new byte[HandshakeProof.PROOF_BYTES];
        buf.get(serverNonce);
        buf.get(serverProof);
        return new Challenge(sessionId, serverNonce, serverProof);
    }

    public static void writeAuthResponse(ByteBuffer buf, UUID sessionId, byte[] clientProof) {
        if (sessionId == null) throw new IllegalArgumentException("sessionId");
        requireProof(clientProof);
        WireProtocol.writeHeader(buf, WireProtocol.OUTER_SESSION_AUTH_RESPONSE);
        writeUuid(buf, sessionId);
        buf.put(clientProof);
    }

    public static AuthResponse readAuthResponse(ByteBuffer buf) {
        if (buf.remaining() != AUTH_RESPONSE_BYTES) return null;
        if (WireProtocol.readHeader(buf) != WireProtocol.OUTER_SESSION_AUTH_RESPONSE) return null;
        UUID sessionId = readUuid(buf);
        byte[] clientProof = new byte[HandshakeProof.PROOF_BYTES];
        buf.get(clientProof);
        return new AuthResponse(sessionId, clientProof);
    }

    private static void requireIds(UUID sessionId, UUID playerId) {
        if (sessionId == null || playerId == null) throw new IllegalArgumentException("null session handshake id");
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

    private static void writeUuid(ByteBuffer buf, UUID id) {
        buf.putLong(id.getMostSignificantBits());
        buf.putLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(ByteBuffer buf) {
        return new UUID(buf.getLong(), buf.getLong());
    }

    public static final class Hello {
        public final UUID sessionId;
        public final UUID playerId;
        public final byte[] clientNonce;
        private Hello(UUID sessionId, UUID playerId, byte[] clientNonce) {
            this.sessionId = sessionId; this.playerId = playerId; this.clientNonce = clientNonce;
        }
    }

    public static final class Challenge {
        public final UUID sessionId;
        public final byte[] serverNonce;
        public final byte[] serverProof;
        private Challenge(UUID sessionId, byte[] serverNonce, byte[] serverProof) {
            this.sessionId = sessionId; this.serverNonce = serverNonce; this.serverProof = serverProof;
        }
    }

    public static final class AuthResponse {
        public final UUID sessionId;
        public final byte[] clientProof;
        private AuthResponse(UUID sessionId, byte[] clientProof) {
            this.sessionId = sessionId; this.clientProof = clientProof;
        }
    }
}
