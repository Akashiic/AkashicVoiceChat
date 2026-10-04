package dev.akashic.voice.voice.common.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

public final class SessionKeys implements AutoCloseable {

    public static final int MASTER_SECRET_BYTES = 32;
    public static final int HANDSHAKE_NONCE_BYTES = 16;
    static final int AUTH_KEY_BYTES = 32;
    static final int AES_KEY_BYTES = 16;
    static final int NONCE_PREFIX_BYTES = 4;

    private static final byte[] INFO = "AkashicVoice/session/v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SESSION_INFO = "AkashicVoice/session/v2".getBytes(StandardCharsets.UTF_8);

    private final byte[] authKey = new byte[AUTH_KEY_BYTES];
    private final byte[] c2sKey = new byte[AES_KEY_BYTES];
    private final byte[] s2cKey = new byte[AES_KEY_BYTES];
    private final byte[] c2sNoncePrefix = new byte[NONCE_PREFIX_BYTES];
    private final byte[] s2cNoncePrefix = new byte[NONCE_PREFIX_BYTES];
    private boolean destroyed;

    private SessionKeys() {}

    public static SessionKeys derive(byte[] masterSecret, byte[] clientNonce, byte[] serverNonce) {
        requireLength(masterSecret, MASTER_SECRET_BYTES, "masterSecret");
        requireLength(clientNonce, HANDSHAKE_NONCE_BYTES, "clientNonce");
        requireLength(serverNonce, HANDSHAKE_NONCE_BYTES, "serverNonce");
        byte[] salt = new byte[HANDSHAKE_NONCE_BYTES * 2];
        System.arraycopy(clientNonce, 0, salt, 0, HANDSHAKE_NONCE_BYTES);
        System.arraycopy(serverNonce, 0, salt, HANDSHAKE_NONCE_BYTES, HANDSHAKE_NONCE_BYTES);
        byte[] prk = HkdfSha256.extract(salt, masterSecret);
        byte[] okm = HkdfSha256.expand(prk, INFO, 72);

        SessionKeys keys = new SessionKeys();
        int offset = 0;
        System.arraycopy(okm, offset, keys.authKey, 0, AUTH_KEY_BYTES);
        offset += AUTH_KEY_BYTES;
        System.arraycopy(okm, offset, keys.c2sKey, 0, AES_KEY_BYTES);
        offset += AES_KEY_BYTES;
        System.arraycopy(okm, offset, keys.s2cKey, 0, AES_KEY_BYTES);
        offset += AES_KEY_BYTES;
        System.arraycopy(okm, offset, keys.c2sNoncePrefix, 0, NONCE_PREFIX_BYTES);
        offset += NONCE_PREFIX_BYTES;
        System.arraycopy(okm, offset, keys.s2cNoncePrefix, 0, NONCE_PREFIX_BYTES);

        Arrays.fill(salt, (byte) 0);
        Arrays.fill(prk, (byte) 0);
        Arrays.fill(okm, (byte) 0);
        return keys;
    }

    public static SessionKeys derive(byte[] masterSecret, byte[] clientNonce, byte[] serverNonce, UUID sessionId) {
        requireLength(masterSecret, MASTER_SECRET_BYTES, "masterSecret");
        requireLength(clientNonce, HANDSHAKE_NONCE_BYTES, "clientNonce");
        requireLength(serverNonce, HANDSHAKE_NONCE_BYTES, "serverNonce");
        if (sessionId == null) throw new IllegalArgumentException("sessionId");
        byte[] salt = new byte[HANDSHAKE_NONCE_BYTES * 2 + 16];
        System.arraycopy(clientNonce, 0, salt, 0, HANDSHAKE_NONCE_BYTES);
        System.arraycopy(serverNonce, 0, salt, HANDSHAKE_NONCE_BYTES, HANDSHAKE_NONCE_BYTES);
        ByteBuffer.wrap(salt, HANDSHAKE_NONCE_BYTES * 2, 16)
            .putLong(sessionId.getMostSignificantBits()).putLong(sessionId.getLeastSignificantBits());
        try {
            return deriveMaterial(masterSecret, salt, SESSION_INFO);
        } finally {
            Arrays.fill(salt, (byte) 0);
        }
    }

    private static SessionKeys deriveMaterial(byte[] masterSecret, byte[] salt, byte[] info) {
        byte[] prk = HkdfSha256.extract(salt, masterSecret);
        byte[] okm = HkdfSha256.expand(prk, info, 72);
        try {
            SessionKeys keys = new SessionKeys();
            int offset = 0;
            System.arraycopy(okm, offset, keys.authKey, 0, AUTH_KEY_BYTES); offset += AUTH_KEY_BYTES;
            System.arraycopy(okm, offset, keys.c2sKey, 0, AES_KEY_BYTES); offset += AES_KEY_BYTES;
            System.arraycopy(okm, offset, keys.s2cKey, 0, AES_KEY_BYTES); offset += AES_KEY_BYTES;
            System.arraycopy(okm, offset, keys.c2sNoncePrefix, 0, NONCE_PREFIX_BYTES); offset += NONCE_PREFIX_BYTES;
            System.arraycopy(okm, offset, keys.s2cNoncePrefix, 0, NONCE_PREFIX_BYTES);
            return keys;
        } finally {
            Arrays.fill(prk, (byte) 0);
            Arrays.fill(okm, (byte) 0);
        }
    }

    byte[] authKey() {
        return requireLive(authKey);
    }

    byte[] c2sKey() {
        return requireLive(c2sKey);
    }

    byte[] s2cKey() {
        return requireLive(s2cKey);
    }

    byte[] c2sNoncePrefix() {
        return requireLive(c2sNoncePrefix);
    }

    byte[] s2cNoncePrefix() {
        return requireLive(s2cNoncePrefix);
    }

    @Override
    public void close() {
        if (destroyed) return;
        destroyed = true;
        Arrays.fill(authKey, (byte) 0);
        Arrays.fill(c2sKey, (byte) 0);
        Arrays.fill(s2cKey, (byte) 0);
        Arrays.fill(c2sNoncePrefix, (byte) 0);
        Arrays.fill(s2cNoncePrefix, (byte) 0);
    }

    private byte[] requireLive(byte[] value) {
        if (destroyed) throw new IllegalStateException("Session keys destroyed");
        return value;
    }

    private static void requireLength(byte[] value, int expected, String name) {
        if (value == null || value.length != expected) {
            throw new IllegalArgumentException(name + " must be " + expected + " bytes");
        }
    }
}
