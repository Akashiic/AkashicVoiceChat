package dev.akashic.voice.voice.common.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;

import javax.crypto.Mac;

public final class HandshakeProof {

    public static final int PROOF_BYTES = 32;
    private static final byte[] SERVER_CONTEXT = "AkashicVoice/server-proof/v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CLIENT_CONTEXT = "AkashicVoice/client-proof/v1".getBytes(StandardCharsets.UTF_8);

    private HandshakeProof() {}

    public static byte[] computeServer(SessionKeys keys, UUID playerId, byte[] clientNonce, byte[] serverNonce) {
        return compute(keys, playerId, clientNonce, serverNonce, SERVER_CONTEXT);
    }

    public static byte[] computeClient(SessionKeys keys, UUID playerId, byte[] clientNonce, byte[] serverNonce) {
        return compute(keys, playerId, clientNonce, serverNonce, CLIENT_CONTEXT);
    }

    public static boolean verifyServer(SessionKeys keys, UUID playerId, byte[] clientNonce, byte[] serverNonce,
        byte[] proof) {
        return verify(keys, playerId, clientNonce, serverNonce, proof, SERVER_CONTEXT);
    }

    public static boolean verifyClient(SessionKeys keys, UUID playerId, byte[] clientNonce, byte[] serverNonce,
        byte[] proof) {
        return verify(keys, playerId, clientNonce, serverNonce, proof, CLIENT_CONTEXT);
    }

    private static byte[] compute(SessionKeys keys, UUID playerId, byte[] clientNonce, byte[] serverNonce,
        byte[] context) {
        if (keys == null || playerId == null) throw new IllegalArgumentException("null handshake input");
        requireNonce(clientNonce, "clientNonce");
        requireNonce(serverNonce, "serverNonce");
        Mac mac = HkdfSha256.newMac(keys.authKey());
        mac.update(context);
        ByteBuffer id = ByteBuffer.allocate(16);
        id.putLong(playerId.getMostSignificantBits())
            .putLong(playerId.getLeastSignificantBits());
        mac.update(id.array());
        mac.update(clientNonce);
        mac.update(serverNonce);
        return mac.doFinal();
    }

    private static boolean verify(SessionKeys keys, UUID playerId, byte[] clientNonce, byte[] serverNonce, byte[] proof,
        byte[] context) {
        if (proof == null || proof.length != PROOF_BYTES) return false;
        byte[] expected = compute(keys, playerId, clientNonce, serverNonce, context);
        boolean ok = MessageDigest.isEqual(expected, proof);
        Arrays.fill(expected, (byte) 0);
        return ok;
    }

    private static void requireNonce(byte[] nonce, String name) {
        if (nonce == null || nonce.length != SessionKeys.HANDSHAKE_NONCE_BYTES) {
            throw new IllegalArgumentException(name + " must be " + SessionKeys.HANDSHAKE_NONCE_BYTES + " bytes");
        }
    }
}
