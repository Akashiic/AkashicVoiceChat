package dev.akashic.voice.platform.agent;

import dev.akashic.voice.voice.common.crypto.HandshakeProof;
import dev.akashic.voice.voice.common.crypto.SessionKeys;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class AgentHandshake {
    private static final int HEADER_BYTES = 6;
    public static final int CHALLENGE_BYTES = HEADER_BYTES + SessionKeys.HANDSHAKE_NONCE_BYTES + HandshakeProof.PROOF_BYTES;
    public static final int AUTH_RESPONSE_BYTES = HEADER_BYTES + HandshakeProof.PROOF_BYTES;

    private AgentHandshake() {}

    public static void writeHello(ByteBuffer out, String backendId, byte[] clientNonce) {
        byte[] backend = encodeBackend(backendId);
        requireNonce(clientNonce);
        writeHeader(out, AgentProtocol.HELLO);
        out.put((byte) backend.length).put(backend).put(clientNonce);
    }

    public static Hello readHello(ByteBuffer in) {
        if (readHeader(in) != AgentProtocol.HELLO || in.remaining() < 1 + SessionKeys.HANDSHAKE_NONCE_BYTES) return null;
        int length = in.get() & 0xff;
        if (length == 0 || length > AgentProtocol.MAX_BACKEND_BYTES || in.remaining() != length + SessionKeys.HANDSHAKE_NONCE_BYTES) return null;
        byte[] backend = new byte[length];
        in.get(backend);
        String backendId = new String(backend, StandardCharsets.US_ASCII);
        if (!isValidBackendId(backendId)) return null;
        byte[] nonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        in.get(nonce);
        return new Hello(backendId, nonce);
    }
    public static void writeChallenge(ByteBuffer out, byte[] serverNonce, byte[] serverProof) {
        requireNonce(serverNonce);
        requireProof(serverProof);
        writeHeader(out, AgentProtocol.CHALLENGE);
        out.put(serverNonce).put(serverProof);
    }

    public static Challenge readChallenge(ByteBuffer in) {
        if (in.remaining() != CHALLENGE_BYTES || readHeader(in) != AgentProtocol.CHALLENGE) return null;
        byte[] nonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        byte[] proof = new byte[HandshakeProof.PROOF_BYTES];
        in.get(nonce).get(proof);
        return new Challenge(nonce, proof);
    }

    public static void writeAuthResponse(ByteBuffer out, byte[] clientProof) {
        requireProof(clientProof);
        writeHeader(out, AgentProtocol.AUTH_RESPONSE);
        out.put(clientProof);
    }

    public static AuthResponse readAuthResponse(ByteBuffer in) {
        if (in.remaining() != AUTH_RESPONSE_BYTES || readHeader(in) != AgentProtocol.AUTH_RESPONSE) return null;
        byte[] proof = new byte[HandshakeProof.PROOF_BYTES];
        in.get(proof);
        return new AuthResponse(proof);
    }
    public static int helloBytes(String backendId) {
        return HEADER_BYTES + 1 + encodeBackend(backendId).length + SessionKeys.HANDSHAKE_NONCE_BYTES;
    }

    public static boolean isValidBackendId(String backendId) {
        if (backendId == null || backendId.isEmpty() || backendId.length() > AgentProtocol.MAX_BACKEND_BYTES) return false;
        for (int i = 0; i < backendId.length(); i++) {
            char c = backendId.charAt(i);
            boolean ok = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                || c == '.' || c == '_' || c == '-';
            if (!ok) return false;
        }
        return true;
    }

    private static byte[] encodeBackend(String backendId) {
        if (!isValidBackendId(backendId)) throw new IllegalArgumentException("invalid backendId");
        return backendId.getBytes(StandardCharsets.US_ASCII);
    }

    private static void writeHeader(ByteBuffer out, byte type) {
        out.putInt(AgentProtocol.MAGIC).put(AgentProtocol.VERSION).put(type);
    }

    private static byte readHeader(ByteBuffer in) {
        if (in.remaining() < HEADER_BYTES || in.getInt() != AgentProtocol.MAGIC || in.get() != AgentProtocol.VERSION) return -1;
        return in.get();
    }
    private static void requireNonce(byte[] nonce) {
        if (nonce == null || nonce.length != SessionKeys.HANDSHAKE_NONCE_BYTES) {
            throw new IllegalArgumentException("invalid nonce");
        }
    }

    private static void requireProof(byte[] proof) {
        if (proof == null || proof.length != HandshakeProof.PROOF_BYTES) {
            throw new IllegalArgumentException("invalid proof");
        }
    }

    public record Hello(String backendId, byte[] clientNonce) {
        public Hello { clientNonce = Arrays.copyOf(clientNonce, clientNonce.length); }
    }

    public record Challenge(byte[] serverNonce, byte[] serverProof) {
        public Challenge {
            serverNonce = Arrays.copyOf(serverNonce, serverNonce.length);
            serverProof = Arrays.copyOf(serverProof, serverProof.length);
        }
    }

    public record AuthResponse(byte[] clientProof) {
        public AuthResponse { clientProof = Arrays.copyOf(clientProof, clientProof.length); }
    }
}
