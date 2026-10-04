package dev.akashic.voice.voice.server.agent;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import dev.akashic.voice.AkashicVoice;
import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.server.PlayerStateRegistry;

/** Outbound-only Java 8 backend Agent for Crucible/Forge 1.7.10. */
final class LegacyGatewayAgent implements AutoCloseable {

    private final String backendId;
    private final String host;
    private final int port;
    private final byte[] psk;
    private final UUID channelId;
    private final SecureRandom random = new SecureRandom();
    private final AtomicBoolean running = new AtomicBoolean();
    private final Map<UUID, ManagedPlayer> managed = new ConcurrentHashMap<UUID, ManagedPlayer>();
    private final Map<String, UUID> localByName = new ConcurrentHashMap<String, UUID>();
    private final AtomicLong reconnects = new AtomicLong();
    private final AtomicLong deliveries = new AtomicLong();
    private final AtomicLong resets = new AtomicLong();
    private final AtomicLong droppedMedia = new AtomicLong();
    private volatile LegacyAgentChannel channel;
    private volatile Thread worker;
    private volatile long lastConnectFailureLog;

    LegacyGatewayAgent(String backendId, String host, int port, byte[] psk) {
        if (!LegacyAgentWire.validBackendId(backendId)) throw new IllegalArgumentException("invalid backendId");
        if (host == null || host.trim()
            .isEmpty()) throw new IllegalArgumentException("gatewayHost");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("gatewayPort");
        if (psk == null || psk.length != LegacyAgentWire.PSK_BYTES) throw new IllegalArgumentException("psk");
        this.backendId = backendId;
        this.host = host.trim();
        this.port = port;
        this.psk = Arrays.copyOf(psk, psk.length);
        this.channelId = LegacyAgentWire.routeIdentity(backendId);
    }

    void start() {
        if (!running.compareAndSet(false, true)) return;
        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                runLoop();
            }
        }, "AkashicVoice-LegacyAgent");
        thread.setDaemon(true);
        worker = thread;
        thread.start();
    }

    boolean isConnected() {
        LegacyAgentChannel current = channel;
        return running.get() && current != null && current.isOpen();
    }

    int managedPlayers() {
        return managed.size();
    }

    long reconnectCount() {
        return reconnects.get();
    }

    long deliveryCount() {
        return deliveries.get();
    }

    long resetCount() {
        return resets.get();
    }

    long droppedMediaFrames() {
        return droppedMedia.get();
    }

    int queuedMediaMessages() {
        LegacyAgentChannel current = channel;
        return current != null ? current.queuedMediaMessages() : 0;
    }

    void updateLocalIdentity(String playerName, UUID localId) {
        if (playerName == null || localId == null) return;
        localByName.put(playerName.toLowerCase(Locale.ROOT), localId);
    }

    void removeLocalIdentity(String playerName, UUID localId) {
        if (playerName == null || localId == null) return;
        localByName.remove(playerName.toLowerCase(Locale.ROOT), localId);
    }

    private void runLoop() {
        long retryMillis = 1_000L;
        while (running.get()) {
            LegacyAgentChannel current = null;
            try {
                current = LegacyAgentChannel.connect(host, port, backendId, psk, random, droppedMedia);
                channel = current;
                managed.clear();
                reconnects.incrementAndGet();
                retryMillis = 1_000L;
                AkashicVoice.LOG.info("Legacy voice Agent connected: backend={} gateway={}:{}", backendId, host, port);
                readLoop(current);
            } catch (IOException | RuntimeException failure) {
                logConnectFailure(failure);
            } finally {
                if (channel == current) channel = null;
                managed.clear();
                if (current != null) current.close();
            }
            if (!running.get()) break;
            sleepRetry(retryMillis);
            retryMillis = Math.min(10_000L, retryMillis * 2L);
        }
    }

    private void readLoop(LegacyAgentChannel current) throws IOException {
        while (running.get() && current.isOpen()) {
            LegacyAgentWire.Inbound message;
            try {
                message = current.receive();
            } catch (SocketTimeoutException timeout) {
                if (!current.send(LegacyAgentWire.ping(System.currentTimeMillis()))) return;
                continue;
            }
            if (message.type == LegacyAgentWire.ATTACH) {
                onAttach(message);
            } else if (message.type == LegacyAgentWire.DETACH) {
                onDetach(message.playerId);
            } else if (message.type == LegacyAgentWire.SPEAKER_FRAME) {
                onSpeakerFrame(current, message);
            } else if (message.type == LegacyAgentWire.SPEAKER_RESET) {
                sendResetToPeers(current, message.playerId);
            } else if (message.type == LegacyAgentWire.PING) {
                if (!current.send(LegacyAgentWire.pong(message.timestamp))) return;
            } else if (message.type != LegacyAgentWire.PONG) {
                throw new IOException("unexpected Gateway Agent message type " + message.type);
            }
        }
    }

    private void onAttach(LegacyAgentWire.Inbound message) {
        if (!backendId.equals(message.backendId) || !validPlayerName(message.playerName)) return;
        managed.put(message.playerId, new ManagedPlayer(message.playerId, message.playerName));
    }

    private void onDetach(UUID proxyId) {
        if (proxyId == null || managed.remove(proxyId) == null) return;
        LegacyAgentChannel current = channel;
        if (current != null) sendResetToPeers(current, proxyId);
    }

    private void onSpeakerFrame(LegacyAgentChannel current, LegacyAgentWire.Inbound frame) {
        ManagedPlayer sender = managed.get(frame.playerId);
        if (sender == null || frame.opus == null) return;
        PlayerStateRegistry.PlayerState source = stateFor(sender);
        if (source == null) return;

        int radius = AkashicVoiceConfig.voiceRadius();
        long radiusSq = (long) radius * (long) radius;
        String worldKey = "legacy-dim:" + source.dimension;
        for (ManagedPlayer recipient : managed.values()) {
            if (recipient.proxyId.equals(sender.proxyId)) continue;
            PlayerStateRegistry.PlayerState target = stateFor(recipient);
            if (target == null || target.dimension != source.dimension) continue;
            double dx = target.x - source.x;
            double dy = target.y - source.y;
            double dz = target.z - source.z;
            if (dx * dx + dy * dy + dz * dz > radiusSq) continue;
            if (current.send(
                LegacyAgentWire.delivery(
                    recipient.proxyId,
                    sender.proxyId,
                    channelId,
                    frame.sequence,
                    frame.whispering,
                    radius,
                    worldKey,
                    source.x,
                    source.y,
                    source.z,
                    frame.opus))) {
                deliveries.incrementAndGet();
            }
        }
    }

    private PlayerStateRegistry.PlayerState stateFor(ManagedPlayer player) {
        UUID localId = localByName.get(player.playerName.toLowerCase(Locale.ROOT));
        PlayerStateRegistry.PlayerState state = localId != null ? PlayerStateRegistry.get(localId) : null;
        return state != null ? state : PlayerStateRegistry.get(player.proxyId);
    }

    private void sendResetToPeers(LegacyAgentChannel current, UUID speakerId) {
        if (speakerId == null) return;
        for (ManagedPlayer recipient : managed.values()) {
            if (recipient.proxyId.equals(speakerId)) continue;
            if (current.send(LegacyAgentWire.reset(recipient.proxyId, speakerId, channelId))) {
                resets.incrementAndGet();
            }
        }
    }

    private void logConnectFailure(Throwable failure) {
        long now = System.currentTimeMillis();
        if (now - lastConnectFailureLog < 30_000L) return;
        lastConnectFailureLog = now;
        AkashicVoice.LOG.warn(
            "Legacy voice Agent unavailable for backend {} at {}:{}; voice remains proxy-only and will retry",
            backendId,
            host,
            port,
            failure);
    }

    private void sleepRetry(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread()
                .interrupt();
        }
    }

    private static boolean validPlayerName(String value) {
        return value != null && value.matches("[A-Za-z0-9_]{1,16}");
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) return;
        LegacyAgentChannel current = channel;
        channel = null;
        if (current != null) current.close();
        Thread currentWorker = worker;
        if (currentWorker != null && currentWorker != Thread.currentThread()) currentWorker.interrupt();
        managed.clear();
        localByName.clear();
        Arrays.fill(psk, (byte) 0);
    }

    private static final class ManagedPlayer {

        final UUID proxyId;
        final String playerName;

        ManagedPlayer(UUID proxyId, String playerName) {
            this.proxyId = proxyId;
            this.playerName = playerName;
        }
    }
}
