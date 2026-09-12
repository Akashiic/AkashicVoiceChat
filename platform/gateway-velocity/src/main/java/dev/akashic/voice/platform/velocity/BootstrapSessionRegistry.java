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

    synchronized BootstrapGrant grantFor(UUID proxyPlayerId, String playerName, String backendName, int udpPort) {
        validatePort(udpPort);
        validateBackendName(backendName);
        validatePlayerName(playerName);
        Session existing = byProxyPlayer.get(proxyPlayerId);
        if (existing != null && existing.udpPort == udpPort
            && existing.backendName.equals(backendName) && existing.playerName.equals(playerName)) {
            return existing.grant();
        }
        if (existing != null) remove(existing);

        byte[] secret = new byte[LegacyForgeBootstrapCodec.MASTER_SECRET_BYTES];
        random.nextBytes(secret);
        UUID voiceIdentity = nextVoiceIdentity();
        Session session = new Session(proxyPlayerId, playerName, backendName, voiceIdentity, secret, udpPort);
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

    private static void validatePlayerName(String playerName) {
        if (playerName == null || !playerName.matches("[A-Za-z0-9_]{1,16}")) {
            throw new IllegalArgumentException("invalid player name");
        }
    }

    private static void validateBackendName(String backendName) {
        if (backendName == null || backendName.isBlank() || backendName.length() > 64) {
            throw new IllegalArgumentException("invalid backend name");
        }
    }

    static final class SessionMaterial implements AutoCloseable {
        private final UUID proxyPlayerId;
        private final String playerName;
        private final String backendName;
        private final UUID voiceIdentity;
        private final byte[] secret;
        private final int udpPort;

        private SessionMaterial(UUID proxyPlayerId, String playerName, String backendName, UUID voiceIdentity, byte[] secret, int udpPort) {
            this.proxyPlayerId = proxyPlayerId;
            this.playerName = playerName;
            this.backendName = backendName;
            this.voiceIdentity = voiceIdentity;
            this.secret = Arrays.copyOf(secret, secret.length);
            this.udpPort = udpPort;
        }

        UUID proxyPlayerId() { return proxyPlayerId; }
        String playerName() { return playerName; }
        String backendName() { return backendName; }
        UUID voiceIdentity() { return voiceIdentity; }
        byte[] secretCopy() { return Arrays.copyOf(secret, secret.length); }
        int udpPort() { return udpPort; }

        @Override
        public void close() {
            Arrays.fill(secret, (byte) 0);
        }
    }

    private static final class Session implements AutoCloseable {
        private final UUID proxyPlayerId;
        private final String playerName;
        private final String backendName;
        private final UUID voiceIdentity;
        private final byte[] secret;
        private final int udpPort;
        private boolean closed;

        private Session(UUID proxyPlayerId, String playerName, String backendName, UUID voiceIdentity, byte[] secret, int udpPort) {
            this.proxyPlayerId = Objects.requireNonNull(proxyPlayerId, "proxyPlayerId");
            this.playerName = Objects.requireNonNull(playerName, "playerName");
            this.backendName = Objects.requireNonNull(backendName, "backendName");
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
            return new SessionMaterial(proxyPlayerId, playerName, backendName, voiceIdentity, secret, udpPort);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            Arrays.fill(secret, (byte) 0);
        }
    }
}
