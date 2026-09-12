package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.voice.common.crypto.HandshakeProof;
import dev.akashic.voice.voice.common.crypto.SessionCrypto;
import dev.akashic.voice.voice.common.crypto.SessionKeys;

import java.net.SocketAddress;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;

final class GatewayPendingHandshake implements AutoCloseable {
    static final long TIMEOUT_MILLIS = 10_000L;

    final UUID proxyPlayerId;
    final String playerName;
    final String backendName;
    final UUID voiceIdentity;
    final SocketAddress remoteAddress;
    final byte[] clientNonce;
    final byte[] serverNonce;
    final byte[] serverProof;
    private final byte[] expectedClientProof;
    private final long createdMillis;
    private SessionCrypto crypto;
    private boolean activated;

    private GatewayPendingHandshake(UUID proxyPlayerId, String playerName, String backendName, UUID voiceIdentity, SocketAddress remoteAddress,
                                    byte[] clientNonce, byte[] serverNonce, byte[] serverProof,
                                    byte[] expectedClientProof, SessionCrypto crypto) {
        this.proxyPlayerId = proxyPlayerId;
        this.playerName = playerName;
        this.backendName = backendName;
        this.voiceIdentity = voiceIdentity;
        this.remoteAddress = remoteAddress;
        this.clientNonce = Arrays.copyOf(clientNonce, clientNonce.length);
        this.serverNonce = Arrays.copyOf(serverNonce, serverNonce.length);
        this.serverProof = Arrays.copyOf(serverProof, serverProof.length);
        this.expectedClientProof = Arrays.copyOf(expectedClientProof, expectedClientProof.length);
        this.crypto = crypto;
        this.createdMillis = System.currentTimeMillis();
    }

    static GatewayPendingHandshake create(BootstrapSessionRegistry.SessionMaterial material,
                                          SocketAddress remoteAddress,
                                          byte[] clientNonce,
                                          SecureRandom random) {
        byte[] secret = material.secretCopy();
        byte[] serverNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        random.nextBytes(serverNonce);
        SessionKeys keys = SessionKeys.derive(secret, clientNonce, serverNonce);
        byte[] serverProof = null;
        byte[] clientProof = null;
        try {
            serverProof = HandshakeProof.computeServer(
                keys, material.voiceIdentity(), clientNonce, serverNonce);
            clientProof = HandshakeProof.computeClient(
                keys, material.voiceIdentity(), clientNonce, serverNonce);
            SessionCrypto crypto = SessionCrypto.forServer(keys);
            return new GatewayPendingHandshake(
                material.proxyPlayerId(), material.playerName(), material.backendName(), material.voiceIdentity(), remoteAddress,
                clientNonce, serverNonce, serverProof, clientProof, crypto);
        } finally {
            keys.close();
            Arrays.fill(secret, (byte) 0);
            Arrays.fill(serverNonce, (byte) 0);
            if (serverProof != null) Arrays.fill(serverProof, (byte) 0);
            if (clientProof != null) Arrays.fill(clientProof, (byte) 0);
        }
    }

    boolean matches(UUID voiceIdentity, byte[] clientNonce) {
        return this.voiceIdentity.equals(voiceIdentity) && Arrays.equals(this.clientNonce, clientNonce);
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
