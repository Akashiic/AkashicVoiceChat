package dev.akashic.voice.platform.agent;

import dev.akashic.voice.voice.common.crypto.HkdfSha256;
import dev.akashic.voice.voice.common.crypto.SessionKeys;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

public final class AgentKeyDerivation {
    public static final int PSK_BYTES = 32;
    private static final byte[] DOMAIN = "AkashicVoice/agent-domain/v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] INFO = "AkashicVoice/agent-master/v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ROUTE = "AkashicVoice/agent-route/v1".getBytes(StandardCharsets.UTF_8);

    private AgentKeyDerivation() {}

    public static SessionKeys derive(byte[] psk, String backendId, byte[] clientNonce, byte[] serverNonce) {
        requirePsk(psk);
        byte[] backend = backendBytes(backendId);
        byte[] salt = sha256(DOMAIN);
        byte[] prk = HkdfSha256.extract(salt, psk);
        byte[] info = ByteBuffer.allocate(INFO.length + 1 + backend.length + clientNonce.length + serverNonce.length)
            .put(INFO).put((byte) 0).put(backend).put(clientNonce).put(serverNonce).array();
        byte[] master = HkdfSha256.expand(prk, info, SessionKeys.MASTER_SECRET_BYTES);
        try {
            return SessionKeys.derive(master, clientNonce, serverNonce);
        } finally {
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(prk, (byte) 0);
            Arrays.fill(info, (byte) 0);
            Arrays.fill(master, (byte) 0);
        }
    }
    public static UUID routeIdentity(String backendId) {
        byte[] backend = backendBytes(backendId);
        byte[] input = ByteBuffer.allocate(ROUTE.length + 1 + backend.length)
            .put(ROUTE).put((byte) 0).put(backend).array();
        byte[] digest = sha256(input);
        try {
            long most = ByteBuffer.wrap(digest, 0, 8).getLong();
            long least = ByteBuffer.wrap(digest, 8, 8).getLong();
            most = (most & 0xffffffffffff0fffL) | 0x0000000000004000L;
            least = (least & 0x3fffffffffffffffL) | 0x8000000000000000L;
            return new UUID(most, least);
        } finally {
            Arrays.fill(input, (byte) 0);
            Arrays.fill(digest, (byte) 0);
        }
    }

    private static byte[] backendBytes(String backendId) {
        if (backendId == null || backendId.isBlank()) throw new IllegalArgumentException("backendId");
        byte[] bytes = backendId.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > AgentProtocol.MAX_BACKEND_BYTES) {
            throw new IllegalArgumentException("backendId length");
        }
        return bytes;
    }

    private static void requirePsk(byte[] psk) {
        if (psk == null || psk.length != PSK_BYTES) {
            throw new IllegalArgumentException("agent PSK must be 32 bytes");
        }
    }
    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
