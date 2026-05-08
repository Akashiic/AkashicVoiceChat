package dev.jm.proxychat.voice.server;

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

import dev.jm.proxychat.ProxyChat;
import dev.jm.proxychat.config.ProxyChatConfig;
import dev.jm.proxychat.voice.common.AuthAckPacket;
import dev.jm.proxychat.voice.common.AuthenticatePacket;
import dev.jm.proxychat.voice.common.KeepAlivePacket;
import dev.jm.proxychat.voice.common.MicFramePacket;
import dev.jm.proxychat.voice.common.SoundFramePacket;
import dev.jm.proxychat.voice.common.WirePacketType;

public final class VoiceServer {

    private static final int MAX_DATAGRAM_BYTES = 1500;
    private static final long IDLE_TIMEOUT_MILLIS = 10_000L;
    private static final long IDLE_SWEEP_INTERVAL_MILLIS = 2_000L;

    private final int port;
    private final Map<UUID, VoiceConnection> connections = new ConcurrentHashMap<>();
    private final Map<SocketAddress, UUID> connectionsByAddress = new ConcurrentHashMap<>();

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

        receiveThread = new Thread(this::receiveLoop, "ProxyChat-Voice-Recv");
        receiveThread.setDaemon(true);
        receiveThread.start();

        sweeperThread = new Thread(this::sweepLoop, "ProxyChat-Voice-Sweeper");
        sweeperThread.setDaemon(true);
        sweeperThread.start();

        ProxyChat.LOG.info("Voice server bound to UDP port {}", port);
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (socket != null) socket.close();
        if (receiveThread != null) receiveThread.interrupt();
        if (sweeperThread != null) sweeperThread.interrupt();
        connections.clear();
        connectionsByAddress.clear();
        ProxyChat.LOG.info("Voice server stopped");
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
                if (running) ProxyChat.LOG.warn("Voice socket error", e);
            } catch (Throwable t) {
                ProxyChat.LOG.warn("Voice packet handling failed", t);
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
                    ProxyChat.LOG.info("Voice connection for {} timed out", e.getKey());
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
            case WirePacketType.AUTHENTICATE:
                handleAuthenticate(AuthenticatePacket.read(buf), remote);
                break;
            case WirePacketType.KEEPALIVE:
                handleKeepAlive(KeepAlivePacket.read(buf), remote);
                break;
            case WirePacketType.MIC_FRAME:
                handleMicFrame(MicFramePacket.read(buf), remote);
                break;
            case WirePacketType.DISCONNECT:
                handleDisconnect(remote);
                break;
            default:
                ProxyChat.LOG.debug("Unknown UDP packet type {} from {}", type, remote);
        }
    }

    private void handleAuthenticate(AuthenticatePacket pkt, SocketAddress remote) {
        UUID expected = SecretRegistry.get(pkt.playerId);
        if (expected == null) {
            sendAuthAck(remote, AuthAckPacket.STATUS_UNKNOWN_PLAYER);
            ProxyChat.LOG.info("Voice auth rejected (unknown player {}) from {}", pkt.playerId, remote);
            return;
        }
        if (!expected.equals(pkt.secret)) {
            sendAuthAck(remote, AuthAckPacket.STATUS_INVALID_SECRET);
            ProxyChat.LOG.info("Voice auth rejected (bad secret for {}) from {}", pkt.playerId, remote);
            return;
        }

        VoiceConnection prev = connections.get(pkt.playerId);
        if (prev != null) connectionsByAddress.remove(prev.remoteAddress);

        VoiceConnection conn = new VoiceConnection(pkt.playerId, remote);
        connections.put(pkt.playerId, conn);
        connectionsByAddress.put(remote, pkt.playerId);

        sendAuthAck(remote, AuthAckPacket.STATUS_OK);
        ProxyChat.LOG
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

    private void handleMicFrame(MicFramePacket pkt, SocketAddress remote) {
        UUID senderId = connectionsByAddress.get(remote);
        if (senderId == null) return;
        VoiceConnection senderConn = connections.get(senderId);
        if (senderConn == null) return;
        senderConn.touch();

        PlayerStateRegistry.PlayerState senderState = PlayerStateRegistry.get(senderId);
        // Prefer authoritative server-tracked position; fall back to client-reported
        // if registry has no entry yet (e.g., during the first tick after login).
        float sx;
        float sy;
        float sz;
        int senderDim;
        if (senderState != null) {
            sx = (float) senderState.x;
            sy = (float) senderState.y;
            sz = (float) senderState.z;
            senderDim = senderState.dimension;
        } else {
            sx = pkt.x;
            sy = pkt.y;
            sz = pkt.z;
            senderDim = Integer.MIN_VALUE; // unknown — only sender hears themselves below
        }

        int radius = ProxyChatConfig.voiceRadius();
        long radiusSq = (long) radius * radius;

        SoundFramePacket out = new SoundFramePacket(senderId, sx, sy, sz, pkt.sequence, pkt.opusPayload);

        boolean selfEcho = ProxyChatConfig.debugSelfEcho();
        for (Map.Entry<UUID, VoiceConnection> entry : connections.entrySet()) {
            UUID receiverId = entry.getKey();
            if (receiverId.equals(senderId) && !selfEcho) continue;

            if (receiverId.equals(senderId)) {
                // self-echo path: skip distance/dimension checks
                sendSoundFrame(entry.getValue().remoteAddress, out);
                continue;
            }

            PlayerStateRegistry.PlayerState recv = PlayerStateRegistry.get(receiverId);
            if (recv == null) continue;
            if (senderState == null || recv.dimension != senderDim) continue;

            double dx = recv.x - sx;
            double dy = recv.y - sy;
            double dz = recv.z - sz;
            if (dx * dx + dy * dy + dz * dz > radiusSq) continue;

            sendSoundFrame(entry.getValue().remoteAddress, out);
        }
    }

    private void handleDisconnect(SocketAddress remote) {
        UUID playerId = connectionsByAddress.remove(remote);
        if (playerId != null) {
            connections.remove(playerId);
            ProxyChat.LOG.info("Voice client {} disconnected", playerId);
        }
    }

    private void sendAuthAck(SocketAddress remote, byte status) {
        ByteBuffer buf = ByteBuffer.allocate(AuthAckPacket.byteSize());
        new AuthAckPacket(status).write(buf);
        send(buf, remote);
    }

    private void sendKeepAlive(SocketAddress remote, long timestamp) {
        ByteBuffer buf = ByteBuffer.allocate(KeepAlivePacket.byteSize());
        new KeepAlivePacket(timestamp).write(buf);
        send(buf, remote);
    }

    private void sendSoundFrame(SocketAddress remote, SoundFramePacket pkt) {
        ByteBuffer buf = ByteBuffer.allocate(pkt.byteSize());
        pkt.write(buf);
        send(buf, remote);
    }

    private void send(ByteBuffer buf, SocketAddress remote) {
        try {
            DatagramPacket out = new DatagramPacket(buf.array(), 0, buf.position(), remote);
            socket.send(out);
        } catch (IOException e) {
            ProxyChat.LOG.debug("UDP send to {} failed", remote, e);
        }
    }
}
