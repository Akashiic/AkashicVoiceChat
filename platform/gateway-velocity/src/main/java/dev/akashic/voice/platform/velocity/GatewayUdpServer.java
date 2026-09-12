package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.bridge.BridgeDelivery;
import dev.akashic.voice.platform.bridge.BridgeStreamReset;
import dev.akashic.voice.platform.bridge.ManagedPlayer;
import dev.akashic.voice.platform.bridge.PositionSnapshot;
import dev.akashic.voice.platform.bridge.SpatialKind;
import dev.akashic.voice.voice.common.HandshakePackets;
import dev.akashic.voice.voice.common.KeepAlivePacket;
import dev.akashic.voice.voice.common.MicFramePacket;
import dev.akashic.voice.voice.common.SoundFramePacket;
import dev.akashic.voice.voice.common.SoundFrameV2Packet;
import dev.akashic.voice.voice.common.WirePacketType;
import dev.akashic.voice.voice.common.WireProtocol;
import dev.akashic.voice.voice.common.crypto.SessionCrypto;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class GatewayUdpServer implements AutoCloseable {
    private static final int MAX_DATAGRAM_BYTES = 1500;
    private static final int MAX_PENDING_HANDSHAKES = 1024;
    private static final long IDLE_TIMEOUT_NANOS = 10_000_000_000L;
    private static final long SWEEP_INTERVAL_MILLIS = 2_000L;

    interface Listener {
        void onConnected(UUID proxyPlayerId, String backendName, UUID voiceIdentity);
        void onDisconnected(UUID proxyPlayerId, String backendName, UUID voiceIdentity);
        void onMicFrame(UUID proxyPlayerId, String backendName, UUID voiceIdentity, long sequence, byte[] opus);
        void onError(Throwable error);
    }

    private final int port;
    private final BootstrapSessionRegistry bootstrapSessions;
    private final Listener listener;
    private final SecureRandom random = new SecureRandom();
    private final GatewayHandshakeRateLimiter handshakeRateLimiter = new GatewayHandshakeRateLimiter();
    private final Object pendingLock = new Object();
    private final Object connectionLock = new Object();
    private final Map<SocketAddress, GatewayPendingHandshake> pendingHandshakes = new HashMap<>();
    private final Map<UUID, GatewayVoiceConnection> byVoiceIdentity = new ConcurrentHashMap<>();
    private final Map<UUID, GatewayVoiceConnection> byProxyPlayer = new ConcurrentHashMap<>();
    private final Map<SocketAddress, GatewayVoiceConnection> byAddress = new ConcurrentHashMap<>();
    private final AtomicLong helloRateLimited = new AtomicLong();
    private final AtomicLong mediaRateLimited = new AtomicLong();
    private final AtomicLong encryptedRejected = new AtomicLong();
    private final AtomicLong micFramesAccepted = new AtomicLong();
    private final AtomicLong soundFramesSent = new AtomicLong();
    private final AtomicLong soundResetsSent = new AtomicLong();
    private final AtomicLong sessionsOpened = new AtomicLong();
    private final AtomicLong sessionsClosed = new AtomicLong();

    private DatagramSocket socket;
    private Thread receiveThread;
    private Thread sweeperThread;
    private volatile boolean running;

    GatewayUdpServer(int port, BootstrapSessionRegistry bootstrapSessions, Listener listener) {
        if (port < 1 || port > 65_535) throw new IllegalArgumentException("invalid UDP port");
        this.port = port;
        this.bootstrapSessions = bootstrapSessions;
        this.listener = listener;
    }

    synchronized void start() throws SocketException {
        if (running) return;
        socket = new DatagramSocket(port);
        running = true;
        receiveThread = new Thread(this::receiveLoop, "AkashicVoice-Gateway-UDP");
        receiveThread.setDaemon(true);
        receiveThread.start();
        sweeperThread = new Thread(this::sweepLoop, "AkashicVoice-Gateway-Sweeper");
        sweeperThread.setDaemon(true);
        sweeperThread.start();
    }

    int localPort() {
        DatagramSocket current = socket;
        return current != null ? current.getLocalPort() : port;
    }

    int connectionCount() {
        return byVoiceIdentity.size();
    }

    GatewayUdpSnapshot snapshot() {
        final int pending;
        synchronized (pendingLock) {
            pending = pendingHandshakes.size();
        }
        return new GatewayUdpSnapshot(
            byVoiceIdentity.size(), pending,
            helloRateLimited.get(), mediaRateLimited.get(), encryptedRejected.get(),
            micFramesAccepted.get(), soundFramesSent.get(), soundResetsSent.get(),
            sessionsOpened.get(), sessionsClosed.get());
    }

    boolean hasConnection(UUID proxyPlayerId, String backendName) {
        GatewayVoiceConnection connection = byProxyPlayer.get(proxyPlayerId);
        return connection != null && backendName != null && backendName.equals(connection.backendName);
    }

    List<ManagedPlayer> managedPlayers(String backendName) {
        ArrayList<ManagedPlayer> players = new ArrayList<>();
        for (GatewayVoiceConnection connection : byVoiceIdentity.values()) {
            if (!backendName.equals(connection.backendName)) continue;
            players.add(new ManagedPlayer(
                connection.proxyPlayerId, connection.backendName, connection.playerName));
        }
        return List.copyOf(players);
    }

    private void receiveLoop() {
        byte[] buffer = new byte[MAX_DATAGRAM_BYTES];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        while (running) {
            try {
                packet.setLength(buffer.length);
                socket.receive(packet);
                handleDatagram(packet);
            } catch (SocketException e) {
                if (running) reportError(e);
                return;
            } catch (IOException e) {
                if (running) reportError(e);
            } catch (Throwable t) {
                if (running) reportError(t);
            }
        }
    }

    private void sweepLoop() {
        while (running) {
            try {
                Thread.sleep(SWEEP_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                return;
            }
            long nowNanos = System.nanoTime();
            for (GatewayVoiceConnection connection : byVoiceIdentity.values()) {
                if (connection.isIdle(IDLE_TIMEOUT_NANOS)) removeConnection(connection);
            }
            sweepPending(System.currentTimeMillis());
        }
    }

    private void handleDatagram(DatagramPacket packet) {
        ByteBuffer datagram = ByteBuffer.wrap(packet.getData(), packet.getOffset(), packet.getLength());
        byte outerType = WireProtocol.peekHeader(datagram);
        SocketAddress remote = packet.getSocketAddress();

        if (outerType == WireProtocol.OUTER_HELLO) {
            if (datagram.remaining() != HandshakePackets.HELLO_BYTES) return;
            if (!handshakeRateLimiter.allow(remote)) {
                helloRateLimited.incrementAndGet();
                return;
            }
            HandshakePackets.Hello hello = HandshakePackets.readHello(datagram);
            if (hello != null) handleHello(hello, remote);
            return;
        }
        if (outerType == WireProtocol.OUTER_AUTH_RESPONSE) {
            HandshakePackets.AuthResponse response = HandshakePackets.readAuthResponse(datagram);
            if (response != null) handleAuthResponse(response, remote);
            return;
        }
        if (outerType != WireProtocol.OUTER_ENCRYPTED) return;

        GatewayVoiceConnection connection = byAddress.get(remote);
        if (connection == null) return;
        if (!connection.allowInboundDatagram(packet.getLength())) {
            mediaRateLimited.incrementAndGet();
            return;
        }
        ByteBuffer plain = connection.open(datagram);
        if (plain != null) {
            handleInner(plain, connection);
        } else {
            encryptedRejected.incrementAndGet();
        }
    }

    private void handleInner(ByteBuffer buf, GatewayVoiceConnection connection) {
        if (!buf.hasRemaining()) return;
        byte type = buf.get();
        switch (type) {
            case WirePacketType.KEEPALIVE -> {
                KeepAlivePacket keepAlive = KeepAlivePacket.read(buf);
                if (keepAlive != null) connection.sendKeepAlive(socket, keepAlive.timestamp);
            }
            case WirePacketType.MIC_FRAME -> {
                MicFramePacket mic = MicFramePacket.read(buf);
                if (mic == null) return;
                byte[] opus = Arrays.copyOfRange(
                    mic.opusData, mic.opusOffset, mic.opusOffset + mic.opusLength);
                notifyMicFrame(connection, Integer.toUnsignedLong(mic.sequence), opus);
            }
            case WirePacketType.DISCONNECT -> {
                if (!buf.hasRemaining()) removeConnection(connection);
            }
            default -> {
                // Unknown inner packets are ignored under the authenticated session.
            }
        }
    }

    private void handleHello(HandshakePackets.Hello hello, SocketAddress remote) {
        try (BootstrapSessionRegistry.SessionMaterial material =
                 bootstrapSessions.findByVoiceIdentity(hello.playerId)) {
            if (material == null) return;
            long nowMillis = System.currentTimeMillis();
            sweepPending(nowMillis);
            synchronized (pendingLock) {
                GatewayPendingHandshake existing = pendingHandshakes.get(remote);
                if (existing != null && !existing.isExpired(nowMillis)
                    && existing.matches(hello.playerId, hello.clientNonce)) {
                    sendChallenge(existing);
                    return;
                }
                if (existing != null) {
                    pendingHandshakes.remove(remote);
                    existing.close();
                }
                if (pendingHandshakes.size() >= MAX_PENDING_HANDSHAKES) return;
                GatewayPendingHandshake pending = GatewayPendingHandshake.create(
                    material, remote, hello.clientNonce, random);
                pendingHandshakes.put(remote, pending);
                sendChallenge(pending);
            }
        } finally {
            Arrays.fill(hello.clientNonce, (byte) 0);
        }
    }

    private void sendChallenge(GatewayPendingHandshake pending) {
        ByteBuffer buffer = ByteBuffer.allocate(HandshakePackets.CHALLENGE_BYTES);
        HandshakePackets.writeChallenge(buffer, pending.serverNonce, pending.serverProof);
        sendRaw(buffer, pending.remoteAddress);
    }

    private void handleAuthResponse(HandshakePackets.AuthResponse response, SocketAddress remote) {
        GatewayVoiceConnection connected = null;
        List<GatewayVoiceConnection> displaced = List.of();
        try {
            synchronized (pendingLock) {
                GatewayPendingHandshake pending = pendingHandshakes.get(remote);
                if (pending == null) return;
                if (pending.isExpired(System.currentTimeMillis())) {
                    pendingHandshakes.remove(remote);
                    pending.close();
                    return;
                }
                if (!pending.verifyClientProof(response.clientProof)) return;
                if (pending.isActivated()) {
                    GatewayVoiceConnection existing = byVoiceIdentity.get(pending.voiceIdentity);
                    if (existing != null && remote.equals(existing.remoteAddress)) {
                        existing.sendAuthAck(socket);
                    }
                    return;
                }
                SessionCrypto crypto = pending.activate();
                if (crypto == null) return;
                connected = new GatewayVoiceConnection(
                    pending.proxyPlayerId, pending.playerName, pending.backendName, pending.voiceIdentity, remote, crypto);
                displaced = installConnection(connected);
                connected.sendAuthAck(socket);
            }
            notifyDisplaced(displaced);
            notifyConnected(connected);
        } finally {
            Arrays.fill(response.clientProof, (byte) 0);
        }
    }

    private List<GatewayVoiceConnection> installConnection(GatewayVoiceConnection connection) {
        List<GatewayVoiceConnection> displaced = new ArrayList<>(3);
        synchronized (connectionLock) {
            addDisplaced(displaced, byVoiceIdentity.get(connection.voiceIdentity), connection);
            addDisplaced(displaced, byProxyPlayer.get(connection.proxyPlayerId), connection);
            addDisplaced(displaced, byAddress.get(connection.remoteAddress), connection);
            for (GatewayVoiceConnection old : displaced) removeIndexes(old);
            byVoiceIdentity.put(connection.voiceIdentity, connection);
            byProxyPlayer.put(connection.proxyPlayerId, connection);
            byAddress.put(connection.remoteAddress, connection);
        }
        for (GatewayVoiceConnection old : displaced) old.close();
        return displaced;
    }

    private static void addDisplaced(List<GatewayVoiceConnection> displaced,
                                     GatewayVoiceConnection candidate,
                                     GatewayVoiceConnection replacement) {
        if (candidate == null || candidate == replacement || displaced.contains(candidate)) return;
        displaced.add(candidate);
    }

    private void removeIndexes(GatewayVoiceConnection connection) {
        byVoiceIdentity.remove(connection.voiceIdentity, connection);
        byProxyPlayer.remove(connection.proxyPlayerId, connection);
        byAddress.remove(connection.remoteAddress, connection);
    }

    private void removeConnection(GatewayVoiceConnection connection) {
        boolean removed;
        synchronized (connectionLock) {
            removed = byVoiceIdentity.remove(connection.voiceIdentity, connection);
            removed |= byProxyPlayer.remove(connection.proxyPlayerId, connection);
            removed |= byAddress.remove(connection.remoteAddress, connection);
        }
        if (!removed) return;
        connection.close();
        notifyDisconnected(connection);
    }

    private void notifyDisplaced(List<GatewayVoiceConnection> displaced) {
        for (GatewayVoiceConnection connection : displaced) notifyDisconnected(connection);
    }

    private void notifyConnected(GatewayVoiceConnection connection) {
        sessionsOpened.incrementAndGet();
        safeListener(() -> listener.onConnected(
            connection.proxyPlayerId, connection.backendName, connection.voiceIdentity));
    }

    private void notifyDisconnected(GatewayVoiceConnection connection) {
        sessionsClosed.incrementAndGet();
        safeListener(() -> listener.onDisconnected(
            connection.proxyPlayerId, connection.backendName, connection.voiceIdentity));
    }

    private void notifyMicFrame(GatewayVoiceConnection connection, long sequence, byte[] opus) {
        micFramesAccepted.incrementAndGet();
        safeListener(() -> listener.onMicFrame(
            connection.proxyPlayerId, connection.backendName, connection.voiceIdentity, sequence, opus));
    }

    private void safeListener(Runnable callback) {
        try {
            callback.run();
        } catch (Throwable t) {
            rethrowFatal(t);
            reportError(t);
        }
    }

    private void reportError(Throwable error) {
        rethrowFatal(error);
        try {
            listener.onError(error);
        } catch (Throwable listenerError) {
            rethrowFatal(listenerError);
        }
    }

    @SuppressWarnings("removal")
    private static void rethrowFatal(Throwable error) {
        if (error instanceof ThreadDeath death) throw death;
        if (error instanceof VirtualMachineError vmError) throw vmError;
    }

    boolean sendBridgeDelivery(String backendName, BridgeDelivery delivery) {
        if (backendName == null || delivery == null) return false;
        GatewayVoiceConnection connection = byProxyPlayer.get(delivery.recipientId());
        if (connection == null || !backendName.equals(connection.backendName)) return false;

        boolean spatial = delivery.spatialKind() != SpatialKind.STATIC;
        float x = 0F;
        float y = 0F;
        float z = 0F;
        if (spatial) {
            PositionSnapshot position = delivery.position();
            if (position == null) return false;
            x = (float) position.x();
            y = (float) position.y();
            z = (float) position.z();
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) return false;
        }

        SoundFrameV2Packet packet = new SoundFrameV2Packet(
            delivery.senderId(),
            spatial,
            delivery.whispering(),
            x,
            y,
            z,
            spatial ? delivery.distance() : 0F,
            (int) delivery.sequence(),
            delivery.opusUnsafe());
        boolean sent = connection.sendSoundV2(socket, packet);
        if (sent) soundFramesSent.incrementAndGet();
        return sent;
    }

    boolean sendBridgeReset(String backendName, BridgeStreamReset reset) {
        if (backendName == null || reset == null) return false;
        GatewayVoiceConnection connection = byProxyPlayer.get(reset.recipientId());
        if (connection == null || !backendName.equals(connection.backendName)) return false;
        boolean sent = connection.sendSoundReset(socket, reset.senderId());
        if (sent) soundResetsSent.incrementAndGet();
        return sent;
    }

    void disconnectBackend(String backendName) {
        if (backendName == null) return;
        for (GatewayVoiceConnection connection : byVoiceIdentity.values()) {
            if (backendName.equals(connection.backendName)) removeConnection(connection);
        }
    }

    void disconnectProxyPlayer(UUID proxyPlayerId) {
        GatewayVoiceConnection connection = byProxyPlayer.get(proxyPlayerId);
        if (connection != null) removeConnection(connection);
        synchronized (pendingLock) {
            var iterator = pendingHandshakes.entrySet().iterator();
            while (iterator.hasNext()) {
                GatewayPendingHandshake pending = iterator.next().getValue();
                if (!pending.proxyPlayerId.equals(proxyPlayerId)) continue;
                pending.close();
                iterator.remove();
            }
        }
    }
    private void sweepPending(long nowMillis) {
        synchronized (pendingLock) {
            var iterator = pendingHandshakes.entrySet().iterator();
            while (iterator.hasNext()) {
                GatewayPendingHandshake pending = iterator.next().getValue();
                if (!pending.isExpired(nowMillis)) continue;
                pending.close();
                iterator.remove();
            }
        }
    }

    private void sendRaw(ByteBuffer buffer, SocketAddress target) {
        DatagramSocket current = socket;
        if (current == null || current.isClosed()) return;
        try {
            DatagramPacket packet = new DatagramPacket(
                buffer.array(), buffer.arrayOffset(), buffer.position(), target);
            current.send(packet);
        } catch (IOException error) {
            if (running) reportError(error);
        }
    }
    @Override
    public synchronized void close() {
        if (!running && socket == null) return;
        running = false;
        DatagramSocket current = socket;
        socket = null;
        if (current != null) current.close();
        if (receiveThread != null) receiveThread.interrupt();
        if (sweeperThread != null) sweeperThread.interrupt();

        List<GatewayVoiceConnection> active;
        synchronized (connectionLock) {
            active = new ArrayList<>(byVoiceIdentity.values());
            byVoiceIdentity.clear();
            byProxyPlayer.clear();
            byAddress.clear();
        }
        for (GatewayVoiceConnection connection : active) {
            connection.close();
            notifyDisconnected(connection);
        }
        synchronized (pendingLock) {
            for (GatewayPendingHandshake pending : pendingHandshakes.values()) {
                pending.close();
            }
            pendingHandshakes.clear();
        }
        receiveThread = null;
        sweeperThread = null;
    }
}
