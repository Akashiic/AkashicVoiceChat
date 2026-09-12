package dev.akashic.voice.voice.client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import dev.akashic.voice.AkashicVoice;
import dev.akashic.voice.voice.client.audio.capture.AudioCaptureException;
import dev.akashic.voice.voice.client.jitter.DecoderStream;
import dev.akashic.voice.voice.client.playback.AudioPlayback;
import dev.akashic.voice.voice.client.talk.TalkCache;
import dev.akashic.voice.voice.common.AuthAckPacket;
import dev.akashic.voice.voice.common.HandshakePackets;
import dev.akashic.voice.voice.common.KeepAlivePacket;
import dev.akashic.voice.voice.common.MicFramePacket;
import dev.akashic.voice.voice.common.SoundFramePacket;
import dev.akashic.voice.voice.common.SoundFrameV2Packet;
import dev.akashic.voice.voice.common.SoundResetPacket;
import dev.akashic.voice.voice.common.WirePacketType;
import dev.akashic.voice.voice.common.WireProtocol;
import dev.akashic.voice.voice.common.crypto.HandshakeProof;
import dev.akashic.voice.voice.common.crypto.SessionCrypto;
import dev.akashic.voice.voice.common.crypto.SessionKeys;

public final class VoiceClient {

    private enum HandshakeStage {
        HELLO,
        AUTH_RESPONSE,
        AUTHENTICATED
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_DATAGRAM_BYTES = 1500;
    private static final long HANDSHAKE_RETRY_NANOS = 1_000_000_000L;
    private static final long HANDSHAKE_TIMEOUT_NANOS = 12_000_000_000L;
    private static final long RECOVERY_HANDSHAKE_TIMEOUT_NANOS = 6_000_000_000L;
    private static final long KEEPALIVE_INTERVAL_NANOS = 1_000_000_000L;
    private static final long SERVER_TIMEOUT_NANOS = 8_000_000_000L;
    private static final long MAINTENANCE_SLEEP_MILLIS = 20L;
    private static final long STREAM_IDLE_NANOS = 30_000_000_000L;
    private static final long MIC_RETRY_NANOS = 3_000_000_000L;

    private final UUID playerId;
    private final byte[] masterSecret;
    private final InetAddress serverHost;
    private final int serverPort;
    private final Object sendLock = new Object();
    private final Object handshakeLock = new Object();
    private final ByteBuffer innerSendBuffer = ByteBuffer.allocate(MAX_DATAGRAM_BYTES);
    private final ByteBuffer encryptedSendBuffer = ByteBuffer.allocate(MAX_DATAGRAM_BYTES);
    private final ByteBuffer handshakeSendBuffer = ByteBuffer.allocate(128);
    private final ByteBuffer decryptBuffer = ByteBuffer.allocate(MAX_DATAGRAM_BYTES);
    private final DatagramPacket outboundPacket = new DatagramPacket(new byte[0], 0);
    private final Map<UUID, DecoderStream> decoderStreams = new ConcurrentHashMap<>();
    private final AtomicLong receivedFrames = new AtomicLong();

    private DatagramSocket socket;
    private SocketAddress serverAddress;
    private volatile MicCapture micCapture;
    private AudioPlayback playback;
    private Thread receiveThread;
    private Thread maintenanceThread;

    private volatile boolean running;
    private volatile boolean authenticated;
    private volatile boolean freshBootstrapRequired;
    private volatile boolean recoveryHandshake;
    private volatile boolean closed;
    private volatile HandshakeStage handshakeStage = HandshakeStage.HELLO;
    private volatile byte[] clientNonce;
    private volatile byte[] serverNonce;
    private volatile byte[] clientProof;
    private volatile SessionCrypto crypto;
    private volatile long stageSinceNanos;
    private volatile long lastHandshakeAttemptNanos;
    private volatile long lastKeepAliveNanos;
    private volatile long lastServerPacketNanos;
    private volatile long lastMicAttemptNanos;
    private volatile boolean micFailureLogged;

    public VoiceClient(UUID playerId, byte[] masterSecret, InetAddress serverHost, int serverPort) {
        if (playerId == null || serverHost == null) throw new IllegalArgumentException("null voice client input");
        if (masterSecret == null || masterSecret.length != SessionKeys.MASTER_SECRET_BYTES) {
            throw new IllegalArgumentException("invalid master secret");
        }
        this.playerId = playerId;
        this.masterSecret = Arrays.copyOf(masterSecret, masterSecret.length);
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }

    public synchronized void start() throws IOException {
        if (running || closed) return;
        socket = new DatagramSocket();
        serverAddress = new java.net.InetSocketAddress(serverHost, serverPort);
        socket.connect(serverAddress);
        outboundPacket.setSocketAddress(serverAddress);
        clientNonce = newNonce();
        handshakeStage = HandshakeStage.HELLO;
        stageSinceNanos = System.nanoTime();
        running = true;
        playback = AudioPlayback.start();

        receiveThread = new Thread(this::receiveLoop, "AkashicVoice-Voice-Client-Recv");
        receiveThread.setDaemon(true);
        receiveThread.start();

        maintenanceThread = new Thread(this::maintenanceLoop, "AkashicVoice-Voice-Client-Maintenance");
        maintenanceThread.setDaemon(true);
        maintenanceThread.start();

        sendHello();
        AkashicVoice.LOG.info("Voice client started, server={}:{}", serverHost.getHostAddress(), serverPort);
    }

    public synchronized void stop() {
        if (closed) return;
        closed = true;
        if (running && authenticated) sendDisconnect();
        running = false;
        authenticated = false;
        stopMicCapture();
        if (socket != null) socket.close();
        if (receiveThread != null) receiveThread.interrupt();
        if (maintenanceThread != null) maintenanceThread.interrupt();
        for (DecoderStream stream : decoderStreams.values()) {
            try {
                stream.close();
            } catch (RuntimeException ignored) {
                // best-effort shutdown
            }
        }
        decoderStreams.clear();
        SessionCrypto currentCrypto = crypto;
        crypto = null;
        if (currentCrypto != null) currentCrypto.close();
        clearArray(clientNonce);
        clearArray(serverNonce);
        clearArray(clientProof);
        clientNonce = null;
        serverNonce = null;
        clientProof = null;
        Arrays.fill(masterSecret, (byte) 0);
        AudioPlayback.stop();
        playback = null;
        AkashicVoice.LOG.info("Voice client stopped");
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public boolean needsFreshBootstrap() {
        return freshBootstrapRequired;
    }

    public synchronized void restartMicCapture() {
        if (!running || !authenticated) return;
        stopMicCapture();
        lastMicAttemptNanos = 0L;
        micFailureLogged = false;
        startMicCapture();
    }

    public long receivedFrameCount() {
        return receivedFrames.get();
    }

    private void receiveLoop() {
        byte[] buffer = new byte[MAX_DATAGRAM_BYTES];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        while (running) {
            try {
                socket.receive(packet);
                handleDatagram(packet);
            } catch (SocketException e) {
                if (running) AkashicVoice.LOG.warn("Voice client socket error", e);
            } catch (Throwable t) {
                if (running) AkashicVoice.LOG.warn("Voice client packet handling failed", t);
            }
        }
    }

    private void maintenanceLoop() {
        while (running) {
            try {
                Thread.sleep(MAINTENANCE_SLEEP_MILLIS);
            } catch (InterruptedException e) {
                return;
            }
            long now = System.nanoTime();
            if (freshBootstrapRequired) continue;
            HandshakeStage stage = handshakeStage;
            if (stage == HandshakeStage.HELLO) {
                long timeout = recoveryHandshake ? RECOVERY_HANDSHAKE_TIMEOUT_NANOS : HANDSHAKE_TIMEOUT_NANOS;
                if (now - stageSinceNanos >= timeout) {
                    if (recoveryHandshake) {
                        requireFreshBootstrap();
                        continue;
                    }
                    resetHandshake(now, false);
                }
                if (now - lastHandshakeAttemptNanos >= HANDSHAKE_RETRY_NANOS) sendHello();
                continue;
            }
            if (stage == HandshakeStage.AUTH_RESPONSE) {
                long timeout = recoveryHandshake ? RECOVERY_HANDSHAKE_TIMEOUT_NANOS : HANDSHAKE_TIMEOUT_NANOS;
                if (now - stageSinceNanos >= timeout) {
                    if (recoveryHandshake) {
                        requireFreshBootstrap();
                        continue;
                    }
                    resetHandshake(now, false);
                    continue;
                }
                if (now - lastHandshakeAttemptNanos >= HANDSHAKE_RETRY_NANOS) sendAuthResponse();
                continue;
            }

            drainDecoderStreams(now);
            reconcileMicCapture(now);
            if (micCapture == null && now - lastMicAttemptNanos >= MIC_RETRY_NANOS) {
                startMicCapture();
            }
            if (now - lastServerPacketNanos >= SERVER_TIMEOUT_NANOS) {
                AkashicVoice.LOG.warn("Voice server timed out; restarting secure handshake");
                resetHandshake(now, true);
                continue;
            }
            if (now - lastKeepAliveNanos >= KEEPALIVE_INTERVAL_NANOS) {
                lastKeepAliveNanos = now;
                sendKeepAlive();
            }
        }
    }

    private void handleDatagram(DatagramPacket packet) {
        if (!serverAddress.equals(packet.getSocketAddress())) return;
        ByteBuffer datagram = ByteBuffer.wrap(packet.getData(), packet.getOffset(), packet.getLength());
        byte outerType = WireProtocol.peekHeader(datagram);
        if (outerType == WireProtocol.OUTER_CHALLENGE) {
            HandshakePackets.Challenge challenge = HandshakePackets.readChallenge(datagram);
            if (challenge != null) handleChallenge(challenge);
            return;
        }
        if (outerType != WireProtocol.OUTER_ENCRYPTED) return;

        SessionCrypto currentCrypto = crypto;
        if (currentCrypto == null) return;
        boolean opened;
        try {
            opened = currentCrypto.open(datagram, decryptBuffer);
        } catch (IllegalStateException ignored) {
            return;
        }
        if (!opened) return;
        lastServerPacketNanos = System.nanoTime();
        handleInner(decryptBuffer);
    }

    private void handleInner(ByteBuffer buf) {
        if (!buf.hasRemaining()) return;
        byte type = buf.get();
        if (!authenticated && type != WirePacketType.AUTH_ACK) return;
        switch (type) {
            case WirePacketType.AUTH_ACK: {
                AuthAckPacket ack = AuthAckPacket.read(buf);
                if (ack != null) handleAuthAck(ack);
                break;
            }
            case WirePacketType.KEEPALIVE:
                KeepAlivePacket.read(buf);
                break;
            case WirePacketType.SOUND_FRAME: {
                SoundFramePacket sound = SoundFramePacket.read(buf);
                if (sound != null) handleSoundFrame(sound);
                break;
            }
            case WirePacketType.SOUND_FRAME_V2: {
                SoundFrameV2Packet sound = SoundFrameV2Packet.read(buf);
                if (sound != null) handleSoundFrame(sound);
                break;
            }
            case WirePacketType.SOUND_RESET: {
                SoundResetPacket reset = SoundResetPacket.read(buf);
                if (reset != null) handleSoundReset(reset.senderId);
                break;
            }
            default:
                break;
        }
    }

    private void handleChallenge(HandshakePackets.Challenge challenge) {
        boolean sendResponse = false;
        try {
            synchronized (handshakeLock) {
                if (handshakeStage == HandshakeStage.AUTHENTICATED) return;
                if (handshakeStage == HandshakeStage.AUTH_RESPONSE) {
                    sendResponse = serverNonce != null && Arrays.equals(serverNonce, challenge.serverNonce);
                } else {
                    byte[] nonceSnapshot = clientNonce;
                    if (nonceSnapshot == null) return;
                    SessionKeys keys = SessionKeys.derive(masterSecret, nonceSnapshot, challenge.serverNonce);
                    try {
                        if (!HandshakeProof.verifyServer(
                            keys,
                            playerId,
                            nonceSnapshot,
                            challenge.serverNonce,
                            challenge.serverProof)) {
                            return;
                        }
                        byte[] proof = HandshakeProof
                            .computeClient(keys, playerId, nonceSnapshot, challenge.serverNonce);
                        SessionCrypto nextCrypto = SessionCrypto.forClient(keys);
                        SessionCrypto previousCrypto = crypto;
                        crypto = nextCrypto;
                        if (previousCrypto != null) previousCrypto.close();
                        clearArray(serverNonce);
                        clearArray(clientProof);
                        serverNonce = Arrays.copyOf(challenge.serverNonce, challenge.serverNonce.length);
                        clientProof = proof;
                        handshakeStage = HandshakeStage.AUTH_RESPONSE;
                        stageSinceNanos = System.nanoTime();
                        sendResponse = true;
                    } finally {
                        keys.close();
                    }
                }
            }
        } finally {
            clearArray(challenge.serverNonce);
            clearArray(challenge.serverProof);
        }
        if (sendResponse) sendAuthResponse();
    }

    private void handleAuthAck(AuthAckPacket ack) {
        if (ack.status != AuthAckPacket.STATUS_OK) {
            AkashicVoice.LOG.warn("Secure voice authentication rejected (status {})", ack.status);
            resetHandshake(System.nanoTime(), false);
            return;
        }
        boolean startMic = false;
        synchronized (handshakeLock) {
            if (handshakeStage != HandshakeStage.AUTH_RESPONSE) return;
            authenticated = true;
            freshBootstrapRequired = false;
            recoveryHandshake = false;
            handshakeStage = HandshakeStage.AUTHENTICATED;
            stageSinceNanos = System.nanoTime();
            lastKeepAliveNanos = stageSinceNanos;
            lastServerPacketNanos = stageSinceNanos;
            clearArray(serverNonce);
            clearArray(clientProof);
            serverNonce = null;
            clientProof = null;
            startMic = micCapture == null;
        }
        AkashicVoice.LOG.info("Secure voice session authenticated");
        if (startMic) startMicCapture();
    }

    private synchronized void reconcileMicCapture(long nowNanos) {
        MicCapture current = micCapture;
        if (current == null || current.isRunning()) return;
        current.stop();
        if (micCapture == current) micCapture = null;
        lastMicAttemptNanos = nowNanos;
    }

    private synchronized void startMicCapture() {
        if (!running || !authenticated || micCapture != null) return;
        lastMicAttemptNanos = System.nanoTime();
        try {
            MicCapture created = new MicCapture(
                this::onMicFrame,
                dev.akashic.voice.config.AkashicVoiceConfig.selectedMicDevice());
            created.start();
            micCapture = created;
            micFailureLogged = false;
        } catch (AudioCaptureException e) {
            micCapture = null;
            if (!micFailureLogged) {
                micFailureLogged = true;
                AkashicVoice.LOG.warn("Microphone unavailable: {}", e.getMessage());
            } else {
                AkashicVoice.LOG.debug("Microphone still unavailable: {}", e.getMessage());
            }
        }
    }

    private synchronized void stopMicCapture() {
        MicCapture capture = micCapture;
        micCapture = null;
        if (capture != null) capture.stop();
    }

    private void onMicFrame(int sequence, byte[] opusPayload, int payloadLength) {
        if (!authenticated) return;
        synchronized (sendLock) {
            SessionCrypto currentCrypto = crypto;
            if (currentCrypto == null || !authenticated) return;
            innerSendBuffer.clear();
            MicFramePacket.write(innerSendBuffer, sequence, opusPayload, payloadLength);
            sealAndSendLocked(currentCrypto);
        }
    }

    private void handleSoundReset(UUID senderId) {
        DecoderStream stream = decoderStreams.remove(senderId);
        if (stream != null) stream.close();
        AudioPlayback currentPlayback = playback;
        if (currentPlayback != null) currentPlayback.dropChannel(senderId);
    }

    private void handleSoundFrame(SoundFramePacket pkt) {
        long count = receivedFrames.incrementAndGet();
        DecoderStream stream = streamFor(pkt.senderId);
        stream.offer(pkt, System.nanoTime(), playback);
        TalkCache.markSpeaking(pkt.senderId);
        if (count == 1 || count % 250 == 0) {
            AkashicVoice.LOG.info("Received {} sound frames so far (last seq {})", count, pkt.sequence);
        }
    }

    private void handleSoundFrame(SoundFrameV2Packet pkt) {
        long count = receivedFrames.incrementAndGet();
        DecoderStream stream = streamFor(pkt.senderId);
        stream.offer(pkt, System.nanoTime(), playback);
        TalkCache.markSpeaking(pkt.senderId);
        if (count == 1 || count % 250 == 0) {
            AkashicVoice.LOG.info("Received {} sound frames so far (last seq {}, bridge-v2)", count, pkt.sequence);
        }
    }

    private DecoderStream streamFor(UUID senderId) {
        DecoderStream existing = decoderStreams.get(senderId);
        if (existing != null) return existing;
        DecoderStream created = new DecoderStream(senderId);
        DecoderStream raced = decoderStreams.putIfAbsent(senderId, created);
        if (raced != null) {
            created.close();
            return raced;
        }
        return created;
    }

    private void drainDecoderStreams(long nowNanos) {
        for (Map.Entry<UUID, DecoderStream> entry : decoderStreams.entrySet()) {
            UUID senderId = entry.getKey();
            DecoderStream stream = entry.getValue();
            stream.drain(nowNanos, playback);
            if (!stream.isIdle(nowNanos, STREAM_IDLE_NANOS)) continue;
            if (!decoderStreams.remove(senderId, stream)) continue;
            try {
                stream.close();
            } catch (RuntimeException ignored) {
                // best-effort cleanup
            }
            if (playback != null) playback.dropChannel(senderId);
        }
    }

    private void sendHello() {
        byte[] nonce = clientNonce;
        if (nonce == null || handshakeStage != HandshakeStage.HELLO) return;
        lastHandshakeAttemptNanos = System.nanoTime();
        synchronized (sendLock) {
            handshakeSendBuffer.clear();
            HandshakePackets.writeHello(handshakeSendBuffer, playerId, nonce);
            sendRawLocked(handshakeSendBuffer);
        }
    }

    private void sendAuthResponse() {
        byte[] proof = clientProof;
        if (proof == null || handshakeStage != HandshakeStage.AUTH_RESPONSE) return;
        lastHandshakeAttemptNanos = System.nanoTime();
        synchronized (sendLock) {
            handshakeSendBuffer.clear();
            HandshakePackets.writeAuthResponse(handshakeSendBuffer, proof);
            sendRawLocked(handshakeSendBuffer);
        }
    }

    private void sendKeepAlive() {
        synchronized (sendLock) {
            SessionCrypto currentCrypto = crypto;
            if (currentCrypto == null || !authenticated) return;
            innerSendBuffer.clear();
            new KeepAlivePacket(System.currentTimeMillis()).write(innerSendBuffer);
            sealAndSendLocked(currentCrypto);
        }
    }

    private void sendDisconnect() {
        synchronized (sendLock) {
            SessionCrypto currentCrypto = crypto;
            if (currentCrypto == null || !authenticated) return;
            innerSendBuffer.clear();
            innerSendBuffer.put(WirePacketType.DISCONNECT);
            sealAndSendLocked(currentCrypto);
        }
    }

    private void sealAndSendLocked(SessionCrypto currentCrypto) {
        if (currentCrypto.seal(innerSendBuffer, encryptedSendBuffer)) {
            sendRawLocked(encryptedSendBuffer);
        }
    }

    private void sendRawLocked(ByteBuffer buf) {
        DatagramSocket currentSocket = socket;
        if (currentSocket == null || currentSocket.isClosed()) return;
        try {
            outboundPacket.setData(buf.array(), 0, buf.position());
            currentSocket.send(outboundPacket);
        } catch (IOException e) {
            if (running) AkashicVoice.LOG.debug("UDP send failed", e);
        }
    }

    private void requireFreshBootstrap() {
        boolean changed = false;
        synchronized (handshakeLock) {
            if (!freshBootstrapRequired) {
                freshBootstrapRequired = true;
                authenticated = false;
                recoveryHandshake = false;
                SessionCrypto previousCrypto = crypto;
                crypto = null;
                if (previousCrypto != null) previousCrypto.close();
                clearArray(clientNonce);
                clearArray(serverNonce);
                clearArray(clientProof);
                clientNonce = null;
                serverNonce = null;
                clientProof = null;
                changed = true;
            }
        }
        if (!changed) return;
        stopMicCapture();
        clearDecoderStreams();
        AkashicVoice.LOG.warn("Voice secure rehandshake failed; requesting a fresh bootstrap");
    }

    private void resetHandshake(long nowNanos, boolean recovery) {
        synchronized (handshakeLock) {
            authenticated = false;
            freshBootstrapRequired = false;
            recoveryHandshake = recovery;
            handshakeStage = HandshakeStage.HELLO;
            stageSinceNanos = nowNanos;
            lastHandshakeAttemptNanos = 0L;
            lastKeepAliveNanos = 0L;
            lastServerPacketNanos = 0L;
            lastMicAttemptNanos = 0L;
            micFailureLogged = false;
            SessionCrypto previousCrypto = crypto;
            crypto = null;
            if (previousCrypto != null) previousCrypto.close();
            clearArray(clientNonce);
            clearArray(serverNonce);
            clearArray(clientProof);
            clientNonce = newNonce();
            serverNonce = null;
            clientProof = null;
        }
        stopMicCapture();
        clearDecoderStreams();
        if (running) sendHello();
    }

    private void clearDecoderStreams() {
        for (Map.Entry<UUID, DecoderStream> entry : decoderStreams.entrySet()) {
            UUID senderId = entry.getKey();
            DecoderStream stream = entry.getValue();
            if (!decoderStreams.remove(senderId, stream)) continue;
            try {
                stream.close();
            } catch (RuntimeException ignored) {
                // best-effort cleanup
            }
            if (playback != null) playback.dropChannel(senderId);
        }
    }

    private static byte[] newNonce() {
        byte[] nonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        return nonce;
    }

    private static void clearArray(byte[] value) {
        if (value != null) Arrays.fill(value, (byte) 0);
    }
}
