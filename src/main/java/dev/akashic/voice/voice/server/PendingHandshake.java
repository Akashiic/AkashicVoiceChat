package dev.akashic.voice.voice.server;

import java.net.SocketAddress;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;

import dev.akashic.voice.voice.common.crypto.HandshakeProof;
import dev.akashic.voice.voice.common.crypto.SessionCrypto;
import dev.akashic.voice.voice.common.crypto.SessionKeys;

final class PendingHandshake implements AutoCloseable {

    static final long TIMEOUT_MILLIS = 10_000L;

    final UUID playerId;
    final UUID sessionId;
    final SocketAddress remoteAddress;
    final byte[] clientNonce;
    final byte[] serverNonce;
    final byte[] serverProof;
    private final byte[] expectedClientProof;
    private final long createdMillis;
    private SessionCrypto crypto;
    private boolean activated;

    private PendingHandshake(UUID playerId, UUID sessionId, SocketAddress remoteAddress, byte[] clientNonce,
        byte[] serverNonce, byte[] serverProof, byte[] expectedClientProof, SessionCrypto crypto) {
        this.playerId = playerId;
        this.sessionId = sessionId;
        this.remoteAddress = remoteAddress;
        this.clientNonce = Arrays.copyOf(clientNonce, clientNonce.length);
        this.serverNonce = Arrays.copyOf(serverNonce, serverNonce.length);
        this.serverProof = Arrays.copyOf(serverProof, serverProof.length);
        this.expectedClientProof = Arrays.copyOf(expectedClientProof, expectedClientProof.length);
        this.crypto = crypto;
        this.createdMillis = System.currentTimeMillis();
    }

    static PendingHandshake create(UUID playerId, SocketAddress remoteAddress, byte[] masterSecret, byte[] clientNonce,
        SecureRandom random) {
        byte[] serverNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        random.nextBytes(serverNonce);
        SessionKeys keys = SessionKeys.derive(masterSecret, clientNonce, serverNonce);
        try {
            byte[] serverProof = HandshakeProof.computeServer(keys, playerId, clientNonce, serverNonce);
            byte[] clientProof = HandshakeProof.computeClient(keys, playerId, clientNonce, serverNonce);
            SessionCrypto crypto = SessionCrypto.forServer(keys);
            return new PendingHandshake(
                playerId,
                null,
                remoteAddress,
                clientNonce,
                serverNonce,
                serverProof,
                clientProof,
                crypto);
        } finally {
            keys.close();
        }
    }

    static PendingHandshake create(UUID playerId, UUID sessionId, SocketAddress remoteAddress, byte[] masterSecret,
        byte[] clientNonce, SecureRandom random) {
        if (sessionId == null) throw new IllegalArgumentException("sessionId");
        byte[] serverNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        random.nextBytes(serverNonce);
        SessionKeys keys = SessionKeys.derive(masterSecret, clientNonce, serverNonce, sessionId);
        try {
            byte[] serverProof = HandshakeProof.computeServer(keys, playerId, clientNonce, serverNonce);
            byte[] clientProof = HandshakeProof.computeClient(keys, playerId, clientNonce, serverNonce);
            SessionCrypto crypto = SessionCrypto.forServer(keys, sessionId);
            return new PendingHandshake(
                playerId,
                sessionId,
                remoteAddress,
                clientNonce,
                serverNonce,
                serverProof,
                clientProof,
                crypto);
        } finally {
            keys.close();
        }
    }

    boolean matches(UUID playerId, byte[] clientNonce) {
        return sessionId == null && this.playerId.equals(playerId) && Arrays.equals(this.clientNonce, clientNonce);
    }

    boolean matches(UUID playerId, UUID sessionId, byte[] clientNonce) {
        return this.sessionId != null && this.sessionId.equals(sessionId)
            && this.playerId.equals(playerId)
            && Arrays.equals(this.clientNonce, clientNonce);
    }

    boolean verifyClientProof(byte[] proof) {
        return proof != null && proof.length == expectedClientProof.length
            && MessageDigest.isEqual(expectedClientProof, proof);
    }

    boolean isExpired(long nowMillis) {
        return nowMillis - createdMillis > TIMEOUT_MILLIS;
    }

    boolean isActivated() {
        return activated;
    }

    SessionCrypto activate() {
        if (activated || crypto == null) return null;
        activated = true;
        SessionCrypto value = crypto;
        crypto = null;
        return value;
    }

    @Override
    public void close() {
        Arrays.fill(clientNonce, (byte) 0);
        Arrays.fill(serverNonce, (byte) 0);
        Arrays.fill(serverProof, (byte) 0);
        Arrays.fill(expectedClientProof, (byte) 0);
        if (crypto != null) {
            crypto.close();
            crypto = null;
        }
    }
}
