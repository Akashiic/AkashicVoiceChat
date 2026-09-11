package dev.akashic.voice.voice.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.akashic.voice.AkashicVoice;
import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.common.AuthAckPacket;
import dev.akashic.voice.voice.common.AuthenticatePacket;
import dev.akashic.voice.voice.common.KeepAlivePacket;
import dev.akashic.voice.voice.common.MicFramePacket;
import dev.akashic.voice.voice.common.SoundFramePacket;
import dev.akashic.voice.voice.common.WirePacketType;
import dev.akashic.voice.voice.server.groups.GroupRegistry;
import dev.akashic.voice.voice.server.groups.GroupRegistryHolder;

public final class VoiceServer {

    private static final int MAX_DATAGRAM_BYTES = 1500;
    private static final long IDLE_TIMEOUT_MILLIS = 10_000L;
    private static final long IDLE_SWEEP_INTERVAL_MILLIS = 2_000L;

    private final int port;
    private final Map<UUID, VoiceConnection> connections = new ConcurrentHashMap<>();
    private final Map<SocketAddress, UUID> connectionsByAddress = new ConcurrentHashMap<>();

    private DatagramSocket socket;
    // All send scratch state is confined to the receive thread.
    private final DatagramPacket outboundPacket = new DatagramPacket(new byte[0], 0);
    private final ByteBuffer spatialSendBuffer = ByteBuffer.allocate(MAX_DATAGRAM_BYTES);
    private final ByteBuffer nonSpatialSendBuffer = ByteBuffer.allocate(MAX_DATAGRAM_BYTES);
    private final ByteBuffer controlSendBuffer = ByteBuffer.allocate(64);
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

        AkashicVoice.LOG.info("Voice server bound to UDP port {}", port);
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (socket != null) socket.close();
        if (receiveThread != null) receiveThread.interrupt();
        if (sweeperThread != null) sweeperThread.interrupt();
        connections.clear();
        connectionsByAddress.clear();
        AkashicVoice.LOG.info("Voice server stopped");
    }

    public int connectionCount() {
        return connections.size();
    }

    public void disconnectPlayer(UUID playerId) {
        VoiceConnection conn = connections.remove(playerId);
        if (conn != null) {
            connectionsByAddress.remove(conn.remoteAddress);
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
                AkashicVoice.LOG.warn("Voice packet handling failed", t);
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
            Iterator<Map.Entry<UUID, VoiceConnection>> it = connections.entrySet()
                .iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, VoiceConnection> e = it.next();
                if (e.getValue()
                    .isIdle(IDLE_TIMEOUT_MILLIS)) {
                    connectionsByAddress.remove(e.getValue().remoteAddress);
                    it.remove();
                    AkashicVoice.LOG.info("Voice connection for {} timed out", e.getKey());
                }
            }
        }
    }

    private void handleDatagram(DatagramPacket packet) {
        ByteBuffer buf = ByteBuffer.wrap(packet.getData(), packet.getOffset(), packet.getLength());
        if (!buf.hasRemaining()) return;

        byte type = buf.get();
        SocketAddress remote = packet.getSocketAddress();

        switch (type) {
            case WirePacketType.AUTHENTICATE: {
                AuthenticatePacket auth = AuthenticatePacket.read(buf);
                if (auth != null) handleAuthenticate(auth, remote);
                break;
            }
            case WirePacketType.KEEPALIVE: {
                if (!connectionsByAddress.containsKey(remote)) break;
                KeepAlivePacket keepAlive = KeepAlivePacket.read(buf);
                if (keepAlive != null) handleKeepAlive(keepAlive, remote);
                break;
            }
            case WirePacketType.MIC_FRAME: {
                UUID senderId = connectionsByAddress.get(remote);
                if (senderId == null) break;
                MicFramePacket mic = MicFramePacket.read(buf);
                if (mic != null) handleMicFrame(mic, remote, senderId);
                break;
            }
            case WirePacketType.DISCONNECT:
                handleDisconnect(remote);
                break;
            default:
                break;
        }
    }

    private void handleAuthenticate(AuthenticatePacket pkt, SocketAddress remote) {
        UUID expected = SecretRegistry.get(pkt.playerId);
        if (expected == null) {
            sendAuthAck(remote, AuthAckPacket.STATUS_UNKNOWN_PLAYER);
            AkashicVoice.LOG.debug("Voice auth rejected for unknown player {}", pkt.playerId);
            return;
        }
        if (!expected.equals(pkt.secret)) {
            sendAuthAck(remote, AuthAckPacket.STATUS_INVALID_SECRET);
            AkashicVoice.LOG.debug("Voice auth rejected for {}", pkt.playerId);
            return;
        }

        VoiceConnection prev = connections.get(pkt.playerId);
        if (prev != null && remote.equals(prev.remoteAddress)) {
            prev.touch();
            sendAuthAck(remote, AuthAckPacket.STATUS_OK);
            return;
        }
        if (prev != null) connectionsByAddress.remove(prev.remoteAddress);

        VoiceConnection conn = new VoiceConnection(pkt.playerId, remote);
        connections.put(pkt.playerId, conn);
        connectionsByAddress.put(remote, pkt.playerId);

        sendAuthAck(remote, AuthAckPacket.STATUS_OK);
        AkashicVoice.LOG
            .info("Voice auth ok for {} ({}); active connections: {}", pkt.playerId, remote, connections.size());
    }

    private void handleKeepAlive(KeepAlivePacket pkt, SocketAddress remote) {
        UUID playerId = connectionsByAddress.get(remote);
        if (playerId == null) return;
        VoiceConnection conn = connections.get(playerId);
        if (conn == null) return;
        conn.touch();
        sendKeepAlive(remote, pkt.timestamp);
    }

    private void handleMicFrame(MicFramePacket pkt, SocketAddress remote, UUID senderId) {
        VoiceConnection senderConn = connections.get(senderId);
        if (senderConn == null) return;
        senderConn.touch();

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
        ByteBuffer spatialWire = null;
        ByteBuffer nonSpatialWire = null;

        boolean selfEcho = AkashicVoiceConfig.debugSelfEcho();
        GroupRegistry groups = GroupRegistryHolder.get();
        for (Map.Entry<UUID, VoiceConnection> entry : connections.entrySet()) {
            UUID receiverId = entry.getKey();
            if (receiverId.equals(senderId) && !selfEcho) continue;

            if (receiverId.equals(senderId)) {
                // self-echo path: skip distance/dimension/group checks
                if (nonSpatialWire == null) nonSpatialWire = serialize(nonSpatialOut, nonSpatialSendBuffer);
                send(nonSpatialWire, entry.getValue().remoteAddress);
                continue;
            }

            // Group bypass: same group → always send, no distance/dimension check.
            if (groups != null && groups.haveCommonGroup(senderId, receiverId)) {
                if (nonSpatialWire == null) nonSpatialWire = serialize(nonSpatialOut, nonSpatialSendBuffer);
                send(nonSpatialWire, entry.getValue().remoteAddress);
                continue;
            }

            PlayerStateRegistry.PlayerState recv = PlayerStateRegistry.get(receiverId);
            if (recv == null) continue;
            if (recv.dimension != senderDim) continue;

            double dx = recv.x - sx;
            double dy = recv.y - sy;
            double dz = recv.z - sz;
            if (dx * dx + dy * dy + dz * dz > radiusSq) continue;

            if (spatialWire == null) spatialWire = serialize(spatialOut, spatialSendBuffer);
            send(spatialWire, entry.getValue().remoteAddress);
        }
    }

    private void handleDisconnect(SocketAddress remote) {
        UUID playerId = connectionsByAddress.remove(remote);
        if (playerId != null) {
            connections.remove(playerId);
            AkashicVoice.LOG.info("Voice client {} disconnected", playerId);
        }
    }

    private void sendAuthAck(SocketAddress remote, byte status) {
        controlSendBuffer.clear();
        new AuthAckPacket(status).write(controlSendBuffer);
        send(controlSendBuffer, remote);
    }

    private void sendKeepAlive(SocketAddress remote, long timestamp) {
        controlSendBuffer.clear();
        new KeepAlivePacket(timestamp).write(controlSendBuffer);
        send(controlSendBuffer, remote);
    }

    private static ByteBuffer serialize(SoundFramePacket pkt, ByteBuffer target) {
        target.clear();
        pkt.write(target);
        return target;
    }

    private void send(ByteBuffer buf, SocketAddress remote) {
        try {
            outboundPacket.setData(buf.array(), 0, buf.position());
            outboundPacket.setSocketAddress(remote);
            socket.send(outboundPacket);
        } catch (IOException e) {
            AkashicVoice.LOG.debug("UDP send to {} failed", remote, e);
        }
    }
}
