package dev.akashic.voice.platform.velocity;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class BootstrapSessionRegistry implements AutoCloseable {
    private final SecureRandom random;
    private final Map<UUID, Session> byProxyPlayer = new HashMap<>();
    private final Map<UUID, Session> byVoiceIdentity = new HashMap<>();

    BootstrapSessionRegistry() {
        this(new SecureRandom());
    }

    BootstrapSessionRegistry(SecureRandom random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    synchronized BootstrapGrant grantFor(UUID proxyPlayerId, int udpPort) {
        validatePort(udpPort);
        Session existing = byProxyPlayer.get(proxyPlayerId);
        if (existing != null && existing.udpPort == udpPort) {
            return existing.grant();
        }
        if (existing != null) remove(existing);

        byte[] secret = new byte[LegacyForgeBootstrapCodec.MASTER_SECRET_BYTES];
        random.nextBytes(secret);
        UUID voiceIdentity = nextVoiceIdentity();
        Session session = new Session(proxyPlayerId, voiceIdentity, secret, udpPort);
        Arrays.fill(secret, (byte) 0);
        byProxyPlayer.put(proxyPlayerId, session);
        byVoiceIdentity.put(voiceIdentity, session);
        return session.grant();
    }

    synchronized SessionMaterial findByVoiceIdentity(UUID voiceIdentity) {
        Session session = byVoiceIdentity.get(voiceIdentity);
        return session == null ? null : session.material();
    }

    synchronized void invalidate(UUID proxyPlayerId) {
        Session session = byProxyPlayer.get(proxyPlayerId);
        if (session != null) remove(session);
    }

    synchronized int size() {
        return byProxyPlayer.size();
    }

    @Override
    public synchronized void close() {
        for (Session session : byProxyPlayer.values()) session.close();
        byProxyPlayer.clear();
        byVoiceIdentity.clear();
    }

    private UUID nextVoiceIdentity() {
        UUID id;
        do {
            id = new UUID(random.nextLong(), random.nextLong());
        } while (byVoiceIdentity.containsKey(id));
        return id;
    }

    private void remove(Session session) {
        byProxyPlayer.remove(session.proxyPlayerId, session);
        byVoiceIdentity.remove(session.voiceIdentity, session);
        session.close();
    }

    private static void validatePort(int port) {
        if (port < 1 || port > 65_535) throw new IllegalArgumentException("invalid UDP port");
    }

    static final class SessionMaterial {
        private final UUID proxyPlayerId;
        private final UUID voiceIdentity;
        private final byte[] secret;
        private final int udpPort;

        private SessionMaterial(UUID proxyPlayerId, UUID voiceIdentity, byte[] secret, int udpPort) {
            this.proxyPlayerId = proxyPlayerId;
            this.voiceIdentity = voiceIdentity;
            this.secret = Arrays.copyOf(secret, secret.length);
            this.udpPort = udpPort;
        }

        UUID proxyPlayerId() { return proxyPlayerId; }
        UUID voiceIdentity() { return voiceIdentity; }
        byte[] secretCopy() { return Arrays.copyOf(secret, secret.length); }
        int udpPort() { return udpPort; }
    }

    private static final class Session implements AutoCloseable {
        private final UUID proxyPlayerId;
        private final UUID voiceIdentity;
        private final byte[] secret;
        private final int udpPort;
        private boolean closed;

        private Session(UUID proxyPlayerId, UUID voiceIdentity, byte[] secret, int udpPort) {
            this.proxyPlayerId = Objects.requireNonNull(proxyPlayerId, "proxyPlayerId");
            this.voiceIdentity = Objects.requireNonNull(voiceIdentity, "voiceIdentity");
            this.secret = Arrays.copyOf(secret, secret.length);
            this.udpPort = udpPort;
        }

        private BootstrapGrant grant() {
            if (closed) throw new IllegalStateException("session closed");
            return new BootstrapGrant(voiceIdentity, secret, udpPort);
        }

        private SessionMaterial material() {
            if (closed) return null;
            return new SessionMaterial(proxyPlayerId, voiceIdentity, secret, udpPort);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            Arrays.fill(secret, (byte) 0);
        }
    }
}
