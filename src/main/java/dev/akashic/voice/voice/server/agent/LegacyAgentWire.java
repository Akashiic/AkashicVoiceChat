package dev.akashic.voice.voice.server.agent;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

import dev.akashic.voice.voice.common.crypto.HandshakeProof;
import dev.akashic.voice.voice.common.crypto.HkdfSha256;
import dev.akashic.voice.voice.common.crypto.SessionKeys;

/** Java 8 codec for the exact Agent v1 wire spoken by the Velocity Gateway. */
final class LegacyAgentWire {

    static final int MAGIC = 0x41564147;
    static final byte VERSION = 1;
    static final byte HELLO = 1, CHALLENGE = 2, AUTH_RESPONSE = 3;
    static final byte READY = 16, ATTACH = 17, DETACH = 18, SPEAKER_FRAME = 19, SPEAKER_RESET = 20;
    static final byte SVC_DELIVERY = 21, SVC_RESET = 22, PING = 23, PONG = 24;
    static final int MAX_BACKEND_BYTES = 64, MAX_STRING_BYTES = 256;
    static final int MAX_PLAINTEXT_BYTES = 4096, MAX_ENCRYPTED_BYTES = 8192;
    static final int PSK_BYTES = 32, HEADER_BYTES = 6;
    static final int CHALLENGE_BYTES = HEADER_BYTES + SessionKeys.HANDSHAKE_NONCE_BYTES + HandshakeProof.PROOF_BYTES;
    static final int AUTH_BYTES = HEADER_BYTES + HandshakeProof.PROOF_BYTES;
    private static final byte[] DOMAIN = "AkashicVoice/agent-domain/v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] INFO = "AkashicVoice/agent-master/v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ROUTE = "AkashicVoice/agent-route/v1".getBytes(StandardCharsets.UTF_8);

    private LegacyAgentWire() {}

    static boolean validBackendId(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_BACKEND_BYTES) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'
                || c >= '0' && c <= '9'
                || c == '.'
                || c == '_'
                || c == '-';
            if (!ok) return false;
        }
        return true;
    }

    static int helloBytes(String backendId) {
        return HEADER_BYTES + 1 + backendBytes(backendId).length + SessionKeys.HANDSHAKE_NONCE_BYTES;
    }

    static void writeHello(ByteBuffer out, String backendId, byte[] clientNonce) {
        byte[] backend = backendBytes(backendId);
        requireNonce(clientNonce);
        writeHeader(out, HELLO);
        out.put((byte) backend.length)
            .put(backend)
            .put(clientNonce);
    }

    static Challenge readChallenge(ByteBuffer in) {
        if (in.remaining() != CHALLENGE_BYTES || readHeader(in) != CHALLENGE) return null;
        byte[] nonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        byte[] proof = new byte[HandshakeProof.PROOF_BYTES];
        in.get(nonce)
            .get(proof);
        return new Challenge(nonce, proof);
    }

    static void writeAuthResponse(ByteBuffer out, byte[] clientProof) {
        if (clientProof == null || clientProof.length != HandshakeProof.PROOF_BYTES) {
            throw new IllegalArgumentException("invalid client proof");
        }
        writeHeader(out, AUTH_RESPONSE);
        out.put(clientProof);
    }

    static SessionKeys derive(byte[] psk, String backendId, byte[] clientNonce, byte[] serverNonce) {
        if (psk == null || psk.length != PSK_BYTES) throw new IllegalArgumentException("agent PSK must be 32 bytes");
        requireNonce(clientNonce);
        requireNonce(serverNonce);
        byte[] backend = backendBytes(backendId);
        byte[] salt = sha256(DOMAIN);
        byte[] prk = HkdfSha256.extract(salt, psk);
        byte[] info = ByteBuffer.allocate(INFO.length + 1 + backend.length + clientNonce.length + serverNonce.length)
            .put(INFO)
            .put((byte) 0)
            .put(backend)
            .put(clientNonce)
            .put(serverNonce)
            .array();
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

    static UUID routeIdentity(String backendId) {
        byte[] backend = backendBytes(backendId);
        byte[] input = ByteBuffer.allocate(ROUTE.length + 1 + backend.length)
            .put(ROUTE)
            .put((byte) 0)
            .put(backend)
            .array();
        byte[] digest = sha256(input);
        try {
            long most = ByteBuffer.wrap(digest, 0, 8)
                .getLong();
            long least = ByteBuffer.wrap(digest, 8, 8)
                .getLong();
            most = (most & 0xffffffffffff0fffL) | 0x0000000000004000L;
            least = (least & 0x3fffffffffffffffL) | 0x8000000000000000L;
            return new UUID(most, least);
        } finally {
            Arrays.fill(input, (byte) 0);
            Arrays.fill(digest, (byte) 0);
        }
    }

    static Inbound readInbound(ByteBuffer in) {
        if (in == null || in.remaining() < 1 || in.remaining() > MAX_PLAINTEXT_BYTES) return null;
        byte type = in.get();
        Inbound message;
        try {
            if (type == READY) message = Inbound.ready();
            else if (type == ATTACH)
                message = Inbound.attach(readUuid(in), readString(in, false), readString(in, false));
            else if (type == DETACH) message = Inbound.detach(readUuid(in));
            else if (type == SPEAKER_FRAME) {
                UUID speaker = readUuid(in);
                long sequence = in.getLong();
                if (sequence < 0L) return null;
                byte flags = in.get();
                if ((flags & ~1) != 0) return null;
                message = Inbound.frame(speaker, sequence, (flags & 1) != 0, readOpus(in));
            } else if (type == SPEAKER_RESET) message = Inbound.reset(readUuid(in));
            else if (type == PING || type == PONG) message = Inbound.heartbeat(type, in.getLong());
            else return null;
        } catch (RuntimeException malformed) {
            return null;
        }
        return !in.hasRemaining() ? message : null;
    }

    interface Outbound {

        void write(ByteBuffer out);

        boolean media();
    }

    static Outbound delivery(UUID recipient, UUID sender, UUID channel, long sequence, boolean whispering,
        float distance, String worldKey, double x, double y, double z, byte[] opus) {
        return new Delivery(recipient, sender, channel, sequence, whispering, distance, worldKey, x, y, z, opus);
    }

    static Outbound reset(UUID recipient, UUID sender, UUID channel) {
        return new Reset(recipient, sender, channel);
    }

    static Outbound ping(long timestamp) {
        return new Heartbeat(PING, timestamp);
    }

    static Outbound pong(long timestamp) {
        return new Heartbeat(PONG, timestamp);
    }

    private static final class Delivery implements Outbound {

        private final UUID recipient, sender, channel;
        private final long sequence;
        private final boolean whispering;
        private final float distance;
        private final String worldKey;
        private final double x, y, z;
        private final byte[] opus;

        Delivery(UUID recipient, UUID sender, UUID channel, long sequence, boolean whispering, float distance,
            String worldKey, double x, double y, double z, byte[] opus) {
            if (recipient == null || sender == null || channel == null || worldKey == null)
                throw new IllegalArgumentException("delivery");
            if (sequence < 0L || !Float.isFinite(distance) || distance < 0F)
                throw new IllegalArgumentException("delivery values");
            requireOpus(opus);
            this.recipient = recipient;
            this.sender = sender;
            this.channel = channel;
            this.sequence = sequence;
            this.whispering = whispering;
            this.distance = distance;
            this.worldKey = worldKey;
            this.x = x;
            this.y = y;
            this.z = z;
            this.opus = Arrays.copyOf(opus, opus.length);
        }

        @Override
        public void write(ByteBuffer out) {
            out.put(SVC_DELIVERY);
            writeUuid(out, recipient);
            writeUuid(out, sender);
            writeUuid(out, channel);
            out.putLong(sequence);
            out.put((byte) 1); // SpatialKind.LOCATION
            out.put((byte) (whispering ? 1 : 0));
            out.putFloat(distance);
            writeString(out, null, true); // category
            out.put((byte) 1); // PositionSnapshot present
            writeString(out, worldKey, false);
            out.putDouble(x)
                .putDouble(y)
                .putDouble(z);
            writeOpus(out, opus);
        }

        @Override
        public boolean media() {
            return true;
        }
    }

    private static final class Reset implements Outbound {

        private final UUID recipient, sender, channel;

        Reset(UUID recipient, UUID sender, UUID channel) {
            if (recipient == null || sender == null || channel == null) throw new IllegalArgumentException("reset");
            this.recipient = recipient;
            this.sender = sender;
            this.channel = channel;
        }

        @Override
        public void write(ByteBuffer out) {
            out.put(SVC_RESET);
            writeUuid(out, recipient);
            writeUuid(out, sender);
            writeUuid(out, channel);
        }

        @Override
        public boolean media() {
            return true;
        }
    }

    private static final class Heartbeat implements Outbound {

        private final byte type;
        private final long timestamp;

        Heartbeat(byte type, long timestamp) {
            this.type = type;
            this.timestamp = timestamp;
        }

        @Override
        public void write(ByteBuffer out) {
            out.put(type)
                .putLong(timestamp);
        }

        @Override
        public boolean media() {
            return false;
        }
    }

    static final class Challenge {

        final byte[] serverNonce;
        final byte[] serverProof;

        Challenge(byte[] nonce, byte[] proof) {
            this.serverNonce = nonce;
            this.serverProof = proof;
        }
    }

    static final class Inbound {

        final byte type;
        final UUID playerId;
        final String backendId;
        final String playerName;
        final long sequence;
        final boolean whispering;
        final byte[] opus;
        final long timestamp;

        private Inbound(byte type, UUID playerId, String backendId, String playerName, long sequence,
            boolean whispering, byte[] opus, long timestamp) {
            this.type = type;
            this.playerId = playerId;
            this.backendId = backendId;
            this.playerName = playerName;
            this.sequence = sequence;
            this.whispering = whispering;
            this.opus = opus;
            this.timestamp = timestamp;
        }

        static Inbound ready() {
            return new Inbound(READY, null, null, null, 0L, false, null, 0L);
        }

        static Inbound attach(UUID id, String backend, String name) {
            return new Inbound(ATTACH, id, backend, name, 0L, false, null, 0L);
        }

        static Inbound detach(UUID id) {
            return new Inbound(DETACH, id, null, null, 0L, false, null, 0L);
        }

        static Inbound frame(UUID id, long seq, boolean whispering, byte[] payload) {
            return new Inbound(SPEAKER_FRAME, id, null, null, seq, whispering, payload, 0L);
        }

        static Inbound reset(UUID id) {
            return new Inbound(SPEAKER_RESET, id, null, null, 0L, false, null, 0L);
        }

        static Inbound heartbeat(byte type, long timestamp) {
            return new Inbound(type, null, null, null, 0L, false, null, timestamp);
        }
    }

    private static void writeHeader(ByteBuffer out, byte type) {
        out.putInt(MAGIC)
            .put(VERSION)
            .put(type);
    }

    private static byte readHeader(ByteBuffer in) {
        if (in.remaining() < HEADER_BYTES || in.getInt() != MAGIC || in.get() != VERSION) return -1;
        return in.get();
    }

    private static byte[] backendBytes(String backendId) {
        if (!validBackendId(backendId)) throw new IllegalArgumentException("invalid backendId");
        return backendId.getBytes(StandardCharsets.US_ASCII);
    }

    private static void requireNonce(byte[] nonce) {
        if (nonce == null || nonce.length != SessionKeys.HANDSHAKE_NONCE_BYTES) {
            throw new IllegalArgumentException("invalid nonce");
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256")
                .digest(input);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void writeUuid(ByteBuffer out, UUID id) {
        out.putLong(id.getMostSignificantBits())
            .putLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(ByteBuffer in) {
        if (in.remaining() < 16) throw new IllegalArgumentException("uuid");
        return new UUID(in.getLong(), in.getLong());
    }

    private static void writeOpus(ByteBuffer out, byte[] opus) {
        requireOpus(opus);
        out.putShort((short) opus.length)
            .put(opus);
    }

    private static byte[] readOpus(ByteBuffer in) {
        if (in.remaining() < 2) throw new IllegalArgumentException("opus length");
        int length = in.getShort() & 0xffff;
        if (length <= 0 || length > 1275 || in.remaining() < length) throw new IllegalArgumentException("opus");
        byte[] opus = new byte[length];
        in.get(opus);
        return opus;
    }

    private static void requireOpus(byte[] opus) {
        if (opus == null || opus.length == 0 || opus.length > 1275) throw new IllegalArgumentException("opus");
    }

    private static void writeString(ByteBuffer out, String value, boolean nullable) {
        if (value == null) {
            if (!nullable) throw new IllegalArgumentException("string");
            out.putShort((short) 0xffff);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) throw new IllegalArgumentException("string too long");
        out.putShort((short) bytes.length)
            .put(bytes);
    }

    private static String readString(ByteBuffer in, boolean nullable) {
        if (in.remaining() < 2) throw new IllegalArgumentException("string length");
        int length = in.getShort() & 0xffff;
        if (length == 0xffff) {
            if (!nullable) throw new IllegalArgumentException("null string");
            return null;
        }
        if (length > MAX_STRING_BYTES || in.remaining() < length) throw new IllegalArgumentException("string length");
        byte[] bytes = new byte[length];
        in.get(bytes);
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (!Arrays.equals(bytes, value.getBytes(StandardCharsets.UTF_8))) throw new IllegalArgumentException("UTF-8");
        return value;
    }
}
