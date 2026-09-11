package dev.akashic.voice.voice.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.akashic.voice.AkashicVoice;
import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.common.AuthAckPacket;
import dev.akashic.voice.voice.common.HandshakePackets;
import dev.akashic.voice.voice.common.KeepAlivePacket;
import dev.akashic.voice.voice.common.MicFramePacket;
import dev.akashic.voice.voice.common.SoundFramePacket;
import dev.akashic.voice.voice.common.WirePacketType;
import dev.akashic.voice.voice.common.WireProtocol;
import dev.akashic.voice.voice.common.crypto.SessionCrypto;
import dev.akashic.voice.voice.server.groups.GroupRegistry;
import dev.akashic.voice.voice.server.groups.GroupRegistryHolder;

public final class VoiceServer {

    private static final int MAX_DATAGRAM_BYTES = 1500;
    private static final int MAX_PENDING_HANDSHAKES = 1024;
    private static final long IDLE_TIMEOUT_MILLIS = 10_000L;
    private static final long IDLE_SWEEP_INTERVAL_MILLIS = 2_000L;

    private final int port;
    private final SecureRandom random = new SecureRandom();
    private final HandshakeRateLimiter handshakeRateLimiter = new HandshakeRateLimiter();
    private final Object pendingLock = new Object();
    private final Map<UUID, VoiceConnection> connections = new ConcurrentHashMap<>();
    private final Map<SocketAddress, UUID> connectionsByAddress = new ConcurrentHashMap<>();
    private final Map<SocketAddress, PendingHandshake> pendingHandshakes = new HashMap<>();

    private final DatagramPacket outboundPacket = new DatagramPacket(new byte[0], 0);
    private final ByteBuffer handshakeSendBuffer = ByteBuffer.allocate(128);
    private final ByteBuffer plainReceiveBuffer = ByteBuffer.allocate(MAX_DATAGRAM_BYTES);
    private final ByteBuffer spatialInnerBuffer = ByteBuffer.allocate(MAX_DATAGRAM_BYTES);
    private final ByteBuffer nonSpatialInnerBuffer = ByteBuffer.allocate(MAX_DATAGRAM_BYTES);
    private final ByteBuffer controlInnerBuffer = ByteBuffer.allocate(64);
    private final ByteBuffer encryptedSendBuffer = ByteBuffer.allocate(MAX_DATAGRAM_BYTES);

    private DatagramSocket socket;
    private Thread receiveThread;
    private Thread sweeperThread;
    private volatile boolean running;

    public VoiceServer(int port) {
        this.port = port;
    }

    public synchronized void start() throws SocketException {
        if (running) return;
        socket = new DatagramSocket(port);
        running = true;

        receiveThread = new Thread(this::receiveLoop, "AkashicVoice-Voice-Recv");
        receiveThread.setDaemon(true);
        receiveThread.start();

        sweeperThread = new Thread(this::sweepLoop, "AkashicVoice-Voice-Sweeper");
        sweeperThread.setDaemon(true);
        sweeperThread.start();

        AkashicVoice.LOG.info("Secure voice server bound to UDP port {}", port);
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (socket != null) socket.close();
        if (receiveThread != null) receiveThread.interrupt();
        if (sweeperThread != null) sweeperThread.interrupt();
        for (VoiceConnection connection : connections.values()) {
            try {
                connection.close();
            } catch (RuntimeException ignored) {
                // best-effort shutdown
            }
        }
        connections.clear();
        connectionsByAddress.clear();
        synchronized (pendingLock) {
            for (PendingHandshake pending : pendingHandshakes.values()) {
                pending.close();
            }
            pendingHandshakes.clear();
        }
        AkashicVoice.LOG.info("Secure voice server stopped");
    }

    public int connectionCount() {
        return connections.size();
    }

    public void disconnectPlayer(UUID playerId) {
        VoiceConnection connection = connections.remove(playerId);
        if (connection != null) {
            connectionsByAddress.remove(connection.remoteAddress, playerId);
            connection.close();
        }
        synchronized (pendingLock) {
            java.util.Iterator<Map.Entry<SocketAddress, PendingHandshake>> it = pendingHandshakes.entrySet()
                .iterator();
            while (it.hasNext()) {
                Map.Entry<SocketAddress, PendingHandshake> entry = it.next();
                if (!entry.getValue().playerId.equals(playerId)) continue;
                entry.getValue()
                    .close();
                it.remove();
            }
        }
    }

    private void receiveLoop() {
        byte[] buffer = new byte[MAX_DATAGRAM_BYTES];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        while (running) {
            try {
                socket.receive(packet);
                handleDatagram(packet);
            } catch (SocketException e) {
                if (running) AkashicVoice.LOG.warn("Voice socket error", e);
            } catch (Throwable t) {
                if (running) AkashicVoice.LOG.warn("Voice packet handling failed", t);
            }
        }
    }

    private void sweepLoop() {
        while (running) {
            try {
                Thread.sleep(IDLE_SWEEP_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                return;
            }
            for (Map.Entry<UUID, VoiceConnection> entry : connections.entrySet()) {
                UUID playerId = entry.getKey();
                VoiceConnection connection = entry.getValue();
                if (!connection.isIdle(IDLE_TIMEOUT_MILLIS)) continue;
                if (!connections.remove(playerId, connection)) continue;
                connectionsByAddress.remove(connection.remoteAddress, playerId);
                connection.close();
                AkashicVoice.LOG.info("Voice connection for {} timed out", playerId);
            }
            sweepPendingHandshakes(System.currentTimeMillis());
        }
    }

    private void sweepPendingHandshakes(long nowMillis) {
        synchronized (pendingLock) {
            java.util.Iterator<Map.Entry<SocketAddress, PendingHandshake>> it = pendingHandshakes.entrySet()
                .iterator();
            while (it.hasNext()) {
                Map.Entry<SocketAddress, PendingHandshake> entry = it.next();
                if (!entry.getValue()
                    .isExpired(nowMillis)) continue;
                entry.getValue()
                    .close();
                it.remove();
            }
        }
    }

    private void handleDatagram(DatagramPacket packet) {
        ByteBuffer datagram = ByteBuffer.wrap(packet.getData(), packet.getOffset(), packet.getLength());
        byte outerType = WireProtocol.peekHeader(datagram);
        SocketAddress remote = packet.getSocketAddress();

        if (outerType == WireProtocol.OUTER_HELLO) {
            if (datagram.remaining() != HandshakePackets.HELLO_BYTES) return;
            if (!handshakeRateLimiter.allow(remote)) return;
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

        UUID playerId = connectionsByAddress.get(remote);
        if (playerId == null) return;
        VoiceConnection connection = connections.get(playerId);
        if (connection == null || !remote.equals(connection.remoteAddress)) return;
        if (!connection.allowInboundDatagram(packet.getLength())) return;
        boolean opened;
        try {
            opened = connection.crypto.open(datagram, plainReceiveBuffer);
        } catch (IllegalStateException ignored) {
            return;
        }
        if (!opened) return;
        connection.touch();
        handleInner(plainReceiveBuffer, connection);
    }

    private void handleInner(ByteBuffer buf, VoiceConnection connection) {
        if (!buf.hasRemaining()) return;
        byte type = buf.get();
        switch (type) {
            case WirePacketType.KEEPALIVE: {
                KeepAlivePacket keepAlive = KeepAlivePacket.read(buf);
                if (keepAlive != null) sendKeepAlive(connection, keepAlive.timestamp);
                break;
            }
            case WirePacketType.MIC_FRAME: {
                MicFramePacket mic = MicFramePacket.read(buf);
                if (mic != null) handleMicFrame(mic, connection);
                break;
            }
            case WirePacketType.DISCONNECT:
                if (!buf.hasRemaining()) handleDisconnect(connection);
                break;
            default:
                break;
        }
    }

    private void handleHello(HandshakePackets.Hello hello, SocketAddress remote) {
        byte[] masterSecret = SecretRegistry.get(hello.playerId);
        if (masterSecret == null) {
            Arrays.fill(hello.clientNonce, (byte) 0);
            return;
        }
        try {
            long nowMillis = System.currentTimeMillis();
            sweepPendingHandshakes(nowMillis);
            synchronized (pendingLock) {
                PendingHandshake existing = pendingHandshakes.get(remote);
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

                PendingHandshake pending = PendingHandshake
                    .create(hello.playerId, remote, masterSecret, hello.clientNonce, random);
                pendingHandshakes.put(remote, pending);
                sendChallenge(pending);
            }
        } finally {
            Arrays.fill(masterSecret, (byte) 0);
            Arrays.fill(hello.clientNonce, (byte) 0);
        }
    }

    private void sendChallenge(PendingHandshake pending) {
        handshakeSendBuffer.clear();
        HandshakePackets.writeChallenge(handshakeSendBuffer, pending.serverNonce, pending.serverProof);
        sendRaw(handshakeSendBuffer, pending.remoteAddress);
    }

    private void handleAuthResponse(HandshakePackets.AuthResponse response, SocketAddress remote) {
        try {
            synchronized (pendingLock) {
                PendingHandshake pending = pendingHandshakes.get(remote);
                if (pending == null) return;
                if (pending.isExpired(System.currentTimeMillis())) {
                    pendingHandshakes.remove(remote);
                    pending.close();
                    return;
                }
                if (!pending.verifyClientProof(response.clientProof)) return;
                if (pending.isActivated()) {
                    UUID playerId = connectionsByAddress.get(remote);
                    VoiceConnection connection = playerId != null ? connections.get(playerId) : null;
                    if (connection != null && pending.playerId.equals(connection.playerId)) {
                        sendAuthAck(connection);
                    }
                    return;
                }

                SessionCrypto sessionCrypto = pending.activate();
                if (sessionCrypto == null) return;
                VoiceConnection connection = new VoiceConnection(pending.playerId, remote, sessionCrypto);
                installConnection(connection);
                sendAuthAck(connection);
                AkashicVoice.LOG
                    .info("Secure voice auth ok for {}; active connections: {}", pending.playerId, connections.size());
            }
        } finally {
            Arrays.fill(response.clientProof, (byte) 0);
        }
    }

    private void installConnection(VoiceConnection connection) {
        UUID displacedId = connectionsByAddress.get(connection.remoteAddress);
        if (displacedId != null && !displacedId.equals(connection.playerId)) {
            connectionsByAddress.remove(connection.remoteAddress, displacedId);
            VoiceConnection displaced = connections.remove(displacedId);
            if (displaced != null) displaced.close();
        }

        VoiceConnection previous = connections.put(connection.playerId, connection);
        if (previous != null && previous != connection) {
            connectionsByAddress.remove(previous.remoteAddress, connection.playerId);
            previous.close();
        }
        connectionsByAddress.put(connection.remoteAddress, connection.playerId);
    }

    private void handleMicFrame(MicFramePacket pkt, VoiceConnection senderConnection) {
        UUID senderId = senderConnection.playerId;
        PlayerStateRegistry.PlayerState senderState = PlayerStateRegistry.get(senderId);
        if (senderState == null) return;
        float sx = (float) senderState.x;
        float sy = (float) senderState.y;
        float sz = (float) senderState.z;
        int senderDim = senderState.dimension;

        int radius = AkashicVoiceConfig.voiceRadius();
        long radiusSq = (long) radius * radius;

        SoundFramePacket spatialOut = new SoundFramePacket(
            senderId,
            true,
            sx,
            sy,
            sz,
            pkt.sequence,
            pkt.opusData,
            pkt.opusOffset,
            pkt.opusLength);
        SoundFramePacket nonSpatialOut = new SoundFramePacket(
            senderId,
            false,
            sx,
            sy,
            sz,
            pkt.sequence,
            pkt.opusData,
            pkt.opusOffset,
            pkt.opusLength);
        boolean spatialReady = false;
        boolean nonSpatialReady = false;

        boolean selfEcho = AkashicVoiceConfig.debugSelfEcho();
        GroupRegistry groups = GroupRegistryHolder.get();
        for (Map.Entry<UUID, VoiceConnection> entry : connections.entrySet()) {
            UUID receiverId = entry.getKey();
            VoiceConnection receiverConnection = entry.getValue();
            if (receiverId.equals(senderId) && !selfEcho) continue;

            if (receiverId.equals(senderId)) {
                if (!nonSpatialReady) {
                    serialize(nonSpatialOut, nonSpatialInnerBuffer);
                    nonSpatialReady = true;
                }
                sendEncrypted(receiverConnection, nonSpatialInnerBuffer);
                continue;
            }

            if (groups != null && groups.haveCommonGroup(senderId, receiverId)) {
                if (!nonSpatialReady) {
                    serialize(nonSpatialOut, nonSpatialInnerBuffer);
                    nonSpatialReady = true;
                }
                sendEncrypted(receiverConnection, nonSpatialInnerBuffer);
                continue;
            }

            PlayerStateRegistry.PlayerState recv = PlayerStateRegistry.get(receiverId);
            if (recv == null || recv.dimension != senderDim) continue;
            double dx = recv.x - sx;
            double dy = recv.y - sy;
            double dz = recv.z - sz;
            if (dx * dx + dy * dy + dz * dz > radiusSq) continue;

            if (!spatialReady) {
                serialize(spatialOut, spatialInnerBuffer);
                spatialReady = true;
            }
            sendEncrypted(receiverConnection, spatialInnerBuffer);
        }
    }

    private void handleDisconnect(VoiceConnection connection) {
        if (!connections.remove(connection.playerId, connection)) return;
        connectionsByAddress.remove(connection.remoteAddress, connection.playerId);
        connection.close();
        AkashicVoice.LOG.info("Voice client {} disconnected", connection.playerId);
    }

    private void sendAuthAck(VoiceConnection connection) {
        controlInnerBuffer.clear();
        new AuthAckPacket(AuthAckPacket.STATUS_OK).write(controlInnerBuffer);
        sendEncrypted(connection, controlInnerBuffer);
    }

    private void sendKeepAlive(VoiceConnection connection, long timestamp) {
        controlInnerBuffer.clear();
        new KeepAlivePacket(timestamp).write(controlInnerBuffer);
        sendEncrypted(connection, controlInnerBuffer);
    }

    private static void serialize(SoundFramePacket packet, ByteBuffer target) {
        target.clear();
        packet.write(target);
    }

    private boolean sendEncrypted(VoiceConnection connection, ByteBuffer plaintext) {
        try {
            if (!connection.crypto.seal(plaintext, encryptedSendBuffer)) return false;
            sendRaw(encryptedSendBuffer, connection.remoteAddress);
            return true;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    private void sendRaw(ByteBuffer buf, SocketAddress remote) {
        DatagramSocket currentSocket = socket;
        if (currentSocket == null || currentSocket.isClosed()) return;
        try {
            outboundPacket.setData(buf.array(), 0, buf.position());
            outboundPacket.setSocketAddress(remote);
            currentSocket.send(outboundPacket);
        } catch (IOException e) {
            if (running) AkashicVoice.LOG.debug("UDP send to {} failed", remote, e);
        }
    }
}
