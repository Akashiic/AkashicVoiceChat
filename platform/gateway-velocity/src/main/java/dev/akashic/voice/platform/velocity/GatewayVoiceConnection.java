package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.voice.common.AuthAckPacket;
import dev.akashic.voice.voice.common.KeepAlivePacket;
import dev.akashic.voice.voice.common.SoundFramePacket;
import dev.akashic.voice.voice.common.SoundResetPacket;
import dev.akashic.voice.voice.common.crypto.SessionCrypto;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.UUID;

final class GatewayVoiceConnection implements AutoCloseable {
    private static final int MAX_DATAGRAM_BYTES = 1500;

    final UUID proxyPlayerId;
    final String playerName;
    final String backendName;
    final UUID voiceIdentity;
    final SocketAddress remoteAddress;

    private final SessionCrypto crypto;
    private final GatewayMediaRateLimiter mediaRateLimiter = new GatewayMediaRateLimiter();
    private final ByteBuffer decryptBuffer = ByteBuffer.allocate(MAX_DATAGRAM_BYTES);
    private final ByteBuffer innerSendBuffer = ByteBuffer.allocate(MAX_DATAGRAM_BYTES);
    private final ByteBuffer encryptedSendBuffer = ByteBuffer.allocate(MAX_DATAGRAM_BYTES);
    private final DatagramPacket outboundPacket = new DatagramPacket(new byte[0], 0);
    private volatile long lastSeenNanos = System.nanoTime();
    private volatile boolean closed;

    GatewayVoiceConnection(UUID proxyPlayerId, String playerName, String backendName, UUID voiceIdentity,
                           SocketAddress remoteAddress, SessionCrypto crypto) {
        this.proxyPlayerId = proxyPlayerId;
        this.playerName = playerName;
        this.backendName = backendName;
        this.voiceIdentity = voiceIdentity;
        this.remoteAddress = remoteAddress;
        this.crypto = crypto;
        outboundPacket.setSocketAddress(remoteAddress);
    }

    boolean allowInboundDatagram(int bytes) {
        return !closed && mediaRateLimiter.allow(bytes);
    }

    synchronized ByteBuffer open(ByteBuffer datagram) {
        if (closed) return null;
        try {
            if (!crypto.open(datagram, decryptBuffer)) return null;
            lastSeenNanos = System.nanoTime();
            return decryptBuffer;
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    void touch() {
        lastSeenNanos = System.nanoTime();
    }

    boolean isIdle(long timeoutNanos) {
        return System.nanoTime() - lastSeenNanos >= timeoutNanos;
    }

    synchronized boolean sendAuthAck(DatagramSocket socket) {
        if (closed) return false;
        innerSendBuffer.clear();
        new AuthAckPacket(AuthAckPacket.STATUS_OK).write(innerSendBuffer);
        return sealAndSend(socket);
    }

    synchronized boolean sendKeepAlive(DatagramSocket socket, long timestamp) {
        if (closed) return false;
        innerSendBuffer.clear();
        new KeepAlivePacket(timestamp).write(innerSendBuffer);
        return sealAndSend(socket);
    }

    synchronized boolean sendSound(DatagramSocket socket, SoundFramePacket packet) {
        if (closed) return false;
        innerSendBuffer.clear();
        packet.write(innerSendBuffer);
        return sealAndSend(socket);
    }

    synchronized boolean sendSoundReset(DatagramSocket socket, UUID senderId) {
        if (closed) return false;
        innerSendBuffer.clear();
        new SoundResetPacket(senderId).write(innerSendBuffer);
        return sealAndSend(socket);
    }

    private boolean sealAndSend(DatagramSocket socket) {
        if (socket == null || socket.isClosed()) return false;
        if (!crypto.seal(innerSendBuffer, encryptedSendBuffer)) return false;
        try {
            outboundPacket.setData(encryptedSendBuffer.array(), 0, encryptedSendBuffer.position());
            outboundPacket.setSocketAddress(remoteAddress);
            socket.send(outboundPacket);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        crypto.close();
        decryptBuffer.clear();
        innerSendBuffer.clear();
        encryptedSendBuffer.clear();
    }
}
