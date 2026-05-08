package dev.jm.proxychat.voice.client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import javax.sound.sampled.LineUnavailableException;

import dev.jm.proxychat.ProxyChat;
import dev.jm.proxychat.voice.client.playback.AudioPlayback;
import dev.jm.proxychat.voice.client.talk.TalkCache;
import dev.jm.proxychat.voice.common.AuthAckPacket;
import dev.jm.proxychat.voice.common.AuthenticatePacket;
import dev.jm.proxychat.voice.common.KeepAlivePacket;
import dev.jm.proxychat.voice.common.MicFramePacket;
import dev.jm.proxychat.voice.common.SoundFramePacket;
import dev.jm.proxychat.voice.common.WirePacketType;
import dev.jm.proxychat.voice.common.codec.AudioFormatSpec;
import dev.jm.proxychat.voice.common.codec.OpusCodecFactory;
import dev.jm.proxychat.voice.common.codec.OpusDecoder;

public final class VoiceClient {

    private static final int MAX_DATAGRAM_BYTES = 1500;
    private static final long KEEPALIVE_INTERVAL_MILLIS = 1_000L;

    private final UUID playerId;
    private final UUID secret;
    private final InetAddress serverHost;
    private final int serverPort;
    private final PositionSupplier positionSupplier;

    private DatagramSocket socket;
    private SocketAddress serverAddress;
    private MicCapture micCapture;
    private OpusDecoder decoder;
    private AudioPlayback playback;

    private Thread receiveThread;
    private Thread keepAliveThread;
    private volatile boolean running;
    private volatile boolean authenticated;
    private final AtomicLong receivedFrames = new AtomicLong();

    public VoiceClient(UUID playerId, UUID secret, InetAddress serverHost, int serverPort,
        PositionSupplier positionSupplier) {
        this.playerId = playerId;
        this.secret = secret;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.positionSupplier = positionSupplier != null ? positionSupplier : PositionSupplier.ZERO;
    }

    public synchronized void start() throws IOException {
        if (running) return;
        socket = new DatagramSocket();
        serverAddress = new java.net.InetSocketAddress(serverHost, serverPort);
        running = true;

        decoder = OpusCodecFactory.createDecoder();
        playback = AudioPlayback.start();

        receiveThread = new Thread(this::receiveLoop, "ProxyChat-Voice-Client-Recv");
        receiveThread.setDaemon(true);
        receiveThread.start();

        keepAliveThread = new Thread(this::keepAliveLoop, "ProxyChat-Voice-Client-KeepAlive");
        keepAliveThread.setDaemon(true);
        keepAliveThread.start();

        sendAuthenticate();
        ProxyChat.LOG.info("VoiceClient started, server={}:{}", serverHost.getHostAddress(), serverPort);
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
        if (decoder != null) {
            decoder.close();
            decoder = null;
        }
        AudioPlayback.stop();
        playback = null;
        ProxyChat.LOG.info("VoiceClient stopped");
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
        ByteBuffer buf = ByteBuffer.allocate(AuthenticatePacket.byteSize());
        new AuthenticatePacket(playerId, secret).write(buf);
        sendBuffer(buf);
    }

    private void receiveLoop() {
        byte[] buffer = new byte[MAX_DATAGRAM_BYTES];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        while (running) {
            try {
                socket.receive(packet);
                handleDatagram(packet);
            } catch (SocketException e) {
                if (running) ProxyChat.LOG.warn("Voice client socket error", e);
            } catch (Throwable t) {
                ProxyChat.LOG.warn("Voice client packet handling failed", t);
            }
        }
    }

    private void keepAliveLoop() {
        while (running) {
            try {
                Thread.sleep(KEEPALIVE_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                return;
            }
            if (!authenticated) continue;
            ByteBuffer buf = ByteBuffer.allocate(KeepAlivePacket.byteSize());
            new KeepAlivePacket(System.currentTimeMillis()).write(buf);
            sendBuffer(buf);
        }
    }

    private void handleDatagram(DatagramPacket packet) {
        ByteBuffer buf = ByteBuffer.wrap(packet.getData(), packet.getOffset(), packet.getLength());
        if (!buf.hasRemaining()) return;
        byte type = buf.get();
        switch (type) {
            case WirePacketType.AUTH_ACK:
                handleAuthAck(AuthAckPacket.read(buf));
                break;
            case WirePacketType.KEEPALIVE:
                // server reply; unused for now
                break;
            case WirePacketType.SOUND_FRAME:
                handleSoundFrame(SoundFramePacket.read(buf));
                break;
            default:
                ProxyChat.LOG.debug("Unknown UDP packet type {} from server", type);
        }
    }

    private void handleAuthAck(AuthAckPacket ack) {
        if (ack.status == AuthAckPacket.STATUS_OK) {
            authenticated = true;
            ProxyChat.LOG.info("Voice auth successful");
            startMicCapture();
        } else {
            ProxyChat.LOG.warn("Voice auth rejected (status {})", ack.status);
            stop();
        }
    }

    private void startMicCapture() {
        try {
            javax.sound.sampled.Mixer.Info mixer = dev.jm.proxychat.voice.client.audio.MicDeviceRegistry
                .findByName(dev.jm.proxychat.config.ProxyChatConfig.selectedMicMixer());
            micCapture = new MicCapture(this::onMicFrame, mixer);
            micCapture.start();
        } catch (LineUnavailableException e) {
            ProxyChat.LOG.warn("Microphone unavailable: {}", e.getMessage());
        }
    }

    private void onMicFrame(int sequence, byte[] opusPayload, int payloadLength) {
        byte[] trimmed = new byte[payloadLength];
        System.arraycopy(opusPayload, 0, trimmed, 0, payloadLength);
        MicFramePacket pkt = new MicFramePacket(
            sequence,
            positionSupplier.x(),
            positionSupplier.y(),
            positionSupplier.z(),
            trimmed);
        ByteBuffer buf = ByteBuffer.allocate(pkt.byteSize());
        pkt.write(buf);
        sendBuffer(buf);
    }

    private void handleSoundFrame(SoundFramePacket pkt) {
        long count = receivedFrames.incrementAndGet();
        try {
            short[] pcm = new short[AudioFormatSpec.FRAME_SAMPLES];
            int decoded = decoder.decode(pkt.opusPayload, pkt.opusPayload.length, pcm);
            if (decoded > 0 && playback != null) {
                playback.submitFrame(pkt.senderId, pkt.x, pkt.y, pkt.z, pcm);
                TalkCache.markSpeaking(pkt.senderId);
            }
        } catch (RuntimeException e) {
            ProxyChat.LOG.debug("Sound frame decode failed at seq {}", pkt.sequence, e);
        }
        if (count == 1 || count % 250 == 0) {
            ProxyChat.LOG.info("Decoded {} sound frames so far (last seq {})", count, pkt.sequence);
        }
    }

    private void sendBuffer(ByteBuffer buf) {
        try {
            DatagramPacket out = new DatagramPacket(buf.array(), 0, buf.position(), serverAddress);
            socket.send(out);
        } catch (IOException e) {
            ProxyChat.LOG.debug("UDP send failed", e);
        }
    }
}
