package dev.akashic.voice.voice.client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
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
import dev.akashic.voice.voice.common.AuthenticatePacket;
import dev.akashic.voice.voice.common.KeepAlivePacket;
import dev.akashic.voice.voice.common.MicFramePacket;
import dev.akashic.voice.voice.common.SoundFramePacket;
import dev.akashic.voice.voice.common.WirePacketType;

public final class VoiceClient {

    private static final int MAX_DATAGRAM_BYTES = 1500;
    private static final long AUTH_RETRY_INTERVAL_NANOS = 1_000_000_000L;
    private static final long KEEPALIVE_INTERVAL_NANOS = 1_000_000_000L;
    private static final long MAINTENANCE_SLEEP_MILLIS = 20L;
    private static final long STREAM_IDLE_NANOS = 30_000_000_000L;

    private final UUID playerId;
    private final UUID secret;
    private final InetAddress serverHost;
    private final int serverPort;
    private final Object sendLock = new Object();
    private final ByteBuffer micSendBuffer = ByteBuffer.allocate(MAX_DATAGRAM_BYTES);
    private final ByteBuffer controlSendBuffer = ByteBuffer.allocate(64);
    private final DatagramPacket micOutboundPacket = new DatagramPacket(new byte[0], 0);
    private final DatagramPacket controlOutboundPacket = new DatagramPacket(new byte[0], 0);

    private DatagramSocket socket;
    private SocketAddress serverAddress;
    private MicCapture micCapture;
    private final Map<UUID, DecoderStream> decoderStreams = new ConcurrentHashMap<>();
    private AudioPlayback playback;

    private Thread receiveThread;
    private Thread keepAliveThread;
    private volatile boolean running;
    private volatile boolean authenticated;
    private final AtomicLong receivedFrames = new AtomicLong();
    private volatile long lastAuthAttemptNanos;
    private volatile long lastKeepAliveNanos;

    public VoiceClient(UUID playerId, UUID secret, InetAddress serverHost, int serverPort) {
        this.playerId = playerId;
        this.secret = secret;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }

    public synchronized void start() throws IOException {
        if (running) return;
        socket = new DatagramSocket();
        serverAddress = new java.net.InetSocketAddress(serverHost, serverPort);
        socket.connect(serverAddress);
        micOutboundPacket.setSocketAddress(serverAddress);
        controlOutboundPacket.setSocketAddress(serverAddress);
        running = true;
        playback = AudioPlayback.start();

        receiveThread = new Thread(this::receiveLoop, "AkashicVoice-Voice-Client-Recv");
        receiveThread.setDaemon(true);
        receiveThread.start();

        keepAliveThread = new Thread(this::keepAliveLoop, "AkashicVoice-Voice-Client-KeepAlive");
        keepAliveThread.setDaemon(true);
        keepAliveThread.start();

        sendAuthenticate();
        AkashicVoice.LOG.info("VoiceClient started, server={}:{}", serverHost.getHostAddress(), serverPort);
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        authenticated = false;
        if (micCapture != null) {
            micCapture.stop();
            micCapture = null;
        }
        if (socket != null) socket.close();
        if (receiveThread != null) receiveThread.interrupt();
        if (keepAliveThread != null) keepAliveThread.interrupt();
        for (DecoderStream stream : decoderStreams.values()) {
            try {
                stream.close();
            } catch (RuntimeException ignored) {
                // best-effort shutdown
            }
        }
        decoderStreams.clear();
        AudioPlayback.stop();
        playback = null;
        AkashicVoice.LOG.info("VoiceClient stopped");
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public synchronized void restartMicCapture() {
        if (!running || !authenticated) return;
        if (micCapture != null) {
            micCapture.stop();
            micCapture = null;
        }
        startMicCapture();
    }

    public long receivedFrameCount() {
        return receivedFrames.get();
    }

    private void sendAuthenticate() {
        lastAuthAttemptNanos = System.nanoTime();
        controlSendBuffer.clear();
        new AuthenticatePacket(playerId, secret).write(controlSendBuffer);
        sendBuffer(controlSendBuffer, controlOutboundPacket);
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
                AkashicVoice.LOG.warn("Voice client packet handling failed", t);
            }
        }
    }

    private void keepAliveLoop() {
        while (running) {
            try {
                Thread.sleep(MAINTENANCE_SLEEP_MILLIS);
            } catch (InterruptedException e) {
                return;
            }
            long now = System.nanoTime();
            if (!authenticated) {
                if (now - lastAuthAttemptNanos >= AUTH_RETRY_INTERVAL_NANOS) sendAuthenticate();
                continue;
            }
            drainDecoderStreams(now);
            if (now - lastKeepAliveNanos >= KEEPALIVE_INTERVAL_NANOS) {
                lastKeepAliveNanos = now;
                controlSendBuffer.clear();
                new KeepAlivePacket(System.currentTimeMillis()).write(controlSendBuffer);
                sendBuffer(controlSendBuffer, controlOutboundPacket);
            }
        }
    }

    private void handleDatagram(DatagramPacket packet) {
        if (!serverAddress.equals(packet.getSocketAddress())) return;
        ByteBuffer buf = ByteBuffer.wrap(packet.getData(), packet.getOffset(), packet.getLength());
        if (!buf.hasRemaining()) return;
        byte type = buf.get();
        switch (type) {
            case WirePacketType.AUTH_ACK: {
                AuthAckPacket ack = AuthAckPacket.read(buf);
                if (ack != null) handleAuthAck(ack);
                break;
            }
            case WirePacketType.KEEPALIVE:
                break;
            case WirePacketType.SOUND_FRAME: {
                SoundFramePacket sound = SoundFramePacket.read(buf);
                if (sound != null) handleSoundFrame(sound);
                break;
            }
            default:
                break;
        }
    }

    private void handleAuthAck(AuthAckPacket ack) {
        if (ack.status == AuthAckPacket.STATUS_OK) {
            authenticated = true;
            lastKeepAliveNanos = System.nanoTime();
            AkashicVoice.LOG.info("Voice auth successful");
            startMicCapture();
        } else {
            AkashicVoice.LOG.warn("Voice auth rejected (status {})", ack.status);
            stop();
        }
    }

    private void startMicCapture() {
        try {
            micCapture = new MicCapture(
                this::onMicFrame,
                dev.akashic.voice.config.AkashicVoiceConfig.selectedMicMixer());
            micCapture.start();
        } catch (AudioCaptureException e) {
            AkashicVoice.LOG.warn("Microphone unavailable: {}", e.getMessage());
        }
    }

    private void onMicFrame(int sequence, byte[] opusPayload, int payloadLength) {
        micSendBuffer.clear();
        MicFramePacket.write(micSendBuffer, sequence, opusPayload, payloadLength);
        sendBuffer(micSendBuffer, micOutboundPacket);
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

    private void sendBuffer(ByteBuffer buf, DatagramPacket packet) {
        try {
            synchronized (sendLock) {
                packet.setData(buf.array(), 0, buf.position());
                socket.send(packet);
            }
        } catch (IOException e) {
            AkashicVoice.LOG.debug("UDP send failed", e);
        }
    }
}
