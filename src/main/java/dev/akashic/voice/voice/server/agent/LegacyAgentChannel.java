package dev.akashic.voice.voice.server.agent;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import dev.akashic.voice.voice.common.crypto.HandshakeProof;
import dev.akashic.voice.voice.common.crypto.SessionCrypto;
import dev.akashic.voice.voice.common.crypto.SessionKeys;

final class LegacyAgentChannel implements AutoCloseable {

    static final int HANDSHAKE_TIMEOUT_MILLIS = 5_000;
    static final int RUNTIME_READ_TIMEOUT_MILLIS = 20_000;
    private static final int MAX_RAW_BYTES = 512;
    private static final int MAX_CONTROL_QUEUE = 128;
    private static final int MAX_MEDIA_QUEUE = 256;

    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final SessionCrypto crypto;
    private final Object outboundLock = new Object();
    private final ArrayDeque<LegacyAgentWire.Outbound> controlQueue = new ArrayDeque<LegacyAgentWire.Outbound>();
    private final ArrayDeque<LegacyAgentWire.Outbound> mediaQueue = new ArrayDeque<LegacyAgentWire.Outbound>();
    private final AtomicLong droppedMedia;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ByteBuffer plainSend = ByteBuffer.allocate(LegacyAgentWire.MAX_PLAINTEXT_BYTES);
    private final ByteBuffer encryptedSend = ByteBuffer.allocate(LegacyAgentWire.MAX_ENCRYPTED_BYTES);
    private final byte[] receiveFrame = new byte[LegacyAgentWire.MAX_ENCRYPTED_BYTES];
    private final ByteBuffer encryptedReceive = ByteBuffer.wrap(receiveFrame);
    private final ByteBuffer plainReceive = ByteBuffer.allocate(LegacyAgentWire.MAX_PLAINTEXT_BYTES);
    private final Thread writerThread;

    private LegacyAgentChannel(Socket socket, SessionCrypto crypto, AtomicLong droppedMedia) throws IOException {
        this.socket = socket;
        this.crypto = crypto;
        if (droppedMedia == null) throw new IllegalArgumentException("droppedMedia");
        this.droppedMedia = droppedMedia;
        this.input = new DataInputStream(socket.getInputStream());
        this.output = new DataOutputStream(socket.getOutputStream());
        this.writerThread = new Thread(new Runnable() {

            @Override
            public void run() {
                writeLoop();
            }
        }, "AkashicVoice-LegacyAgent-Writer");
        this.writerThread.setDaemon(true);
        this.writerThread.start();
    }

    static LegacyAgentChannel connect(String host, int port, String backendId, byte[] psk, SecureRandom random)
        throws IOException {
        return connect(host, port, backendId, psk, random, new AtomicLong());
    }

    static LegacyAgentChannel connect(String host, int port, String backendId, byte[] psk, SecureRandom random,
        AtomicLong droppedMedia) throws IOException {
        if (!LegacyAgentWire.validBackendId(backendId)) throw new IllegalArgumentException("backendId");
        if (psk == null || psk.length != LegacyAgentWire.PSK_BYTES) throw new IllegalArgumentException("psk");
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), HANDSHAKE_TIMEOUT_MILLIS);
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        socket.setSoTimeout(HANDSHAKE_TIMEOUT_MILLIS);
        byte[] localPsk = Arrays.copyOf(psk, psk.length);
        byte[] clientNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        random.nextBytes(clientNonce);
        SessionCrypto sessionCrypto = null;
        byte[] clientProof = null;
        LegacyAgentWire.Challenge challenge = null;
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            ByteBuffer hello = ByteBuffer.allocate(LegacyAgentWire.helloBytes(backendId));
            LegacyAgentWire.writeHello(hello, backendId, clientNonce);
            writeRaw(out, hello);
            challenge = LegacyAgentWire.readChallenge(readRaw(in, LegacyAgentWire.CHALLENGE_BYTES));
            if (challenge == null) throw new IOException("invalid agent challenge");
            SessionKeys keys = LegacyAgentWire.derive(localPsk, backendId, clientNonce, challenge.serverNonce);
            try {
                UUID routeId = LegacyAgentWire.routeIdentity(backendId);
                if (!HandshakeProof
                    .verifyServer(keys, routeId, clientNonce, challenge.serverNonce, challenge.serverProof)) {
                    throw new IOException("agent server proof rejected");
                }
                clientProof = HandshakeProof.computeClient(keys, routeId, clientNonce, challenge.serverNonce);
                sessionCrypto = SessionCrypto.forClient(keys);
            } finally {
                keys.close();
            }

            ByteBuffer response = ByteBuffer.allocate(LegacyAgentWire.AUTH_BYTES);
            LegacyAgentWire.writeAuthResponse(response, clientProof);
            writeRaw(out, response);
            Arrays.fill(clientProof, (byte) 0);

            LegacyAgentChannel channel = new LegacyAgentChannel(socket, sessionCrypto, droppedMedia);
            sessionCrypto = null;
            LegacyAgentWire.Inbound ready = channel.receive();
            if (ready == null || ready.type != LegacyAgentWire.READY) {
                channel.close();
                throw new IOException("agent gateway did not send READY");
            }
            socket.setSoTimeout(RUNTIME_READ_TIMEOUT_MILLIS);
            return channel;
        } catch (IOException | RuntimeException failure) {
            if (sessionCrypto != null) sessionCrypto.close();
            try {
                socket.close();
            } catch (IOException ignored) {}
            throw failure;
        } finally {
            if (clientProof != null) Arrays.fill(clientProof, (byte) 0);
            if (challenge != null) {
                Arrays.fill(challenge.serverNonce, (byte) 0);
                Arrays.fill(challenge.serverProof, (byte) 0);
            }
            Arrays.fill(localPsk, (byte) 0);
            Arrays.fill(clientNonce, (byte) 0);
        }
    }

    boolean isOpen() {
        return !closed.get() && !socket.isClosed() && socket.isConnected();
    }

    boolean send(LegacyAgentWire.Outbound message) {
        if (message == null || !isOpen()) return false;
        return message.media() ? enqueueMedia(message) : enqueueControl(message);
    }

    long droppedMediaFrames() {
        return droppedMedia.get();
    }

    int queuedMediaMessages() {
        synchronized (outboundLock) {
            return mediaQueue.size();
        }
    }

    int queuedControlMessages() {
        synchronized (outboundLock) {
            return controlQueue.size();
        }
    }

    private boolean enqueueControl(LegacyAgentWire.Outbound message) {
        synchronized (outboundLock) {
            if (closed.get()) return false;
            if (controlQueue.size() >= MAX_CONTROL_QUEUE) {
                close();
                return false;
            }
            controlQueue.addLast(message);
            outboundLock.notifyAll();
            return true;
        }
    }

    private boolean enqueueMedia(LegacyAgentWire.Outbound message) {
        synchronized (outboundLock) {
            if (closed.get()) return false;
            if (mediaQueue.size() >= MAX_MEDIA_QUEUE) {
                mediaQueue.pollFirst();
                droppedMedia.incrementAndGet();
            }
            mediaQueue.addLast(message);
            outboundLock.notifyAll();
            return true;
        }
    }

    private void writeLoop() {
        try {
            while (isOpen()) {
                LegacyAgentWire.Outbound message = takeOutbound();
                if (message == null || !writeNow(message)) return;
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread()
                .interrupt();
        } finally {
            close();
        }
    }

    private LegacyAgentWire.Outbound takeOutbound() throws InterruptedException {
        synchronized (outboundLock) {
            while (!closed.get() && controlQueue.isEmpty() && mediaQueue.isEmpty()) outboundLock.wait();
            if (closed.get()) return null;
            LegacyAgentWire.Outbound control = controlQueue.pollFirst();
            return control != null ? control : mediaQueue.pollFirst();
        }
    }

    private boolean writeNow(LegacyAgentWire.Outbound message) {
        if (!isOpen()) return false;
        try {
            plainSend.clear();
            message.write(plainSend);
            if (plainSend.position() <= 0 || plainSend.position() > LegacyAgentWire.MAX_PLAINTEXT_BYTES) return false;
            encryptedSend.clear();
            if (!crypto.seal(plainSend, encryptedSend)) return false;
            int length = encryptedSend.position();
            if (length <= 0 || length > LegacyAgentWire.MAX_ENCRYPTED_BYTES) return false;
            output.writeInt(length);
            output.write(encryptedSend.array(), encryptedSend.arrayOffset(), length);
            output.flush();
            return true;
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    LegacyAgentWire.Inbound receive() throws IOException {
        if (!isOpen()) throw new EOFException("agent channel closed");
        int length = input.readInt();
        if (length <= 0 || length > LegacyAgentWire.MAX_ENCRYPTED_BYTES) {
            throw new IOException("invalid encrypted agent frame length: " + length);
        }
        input.readFully(receiveFrame, 0, length);
        encryptedReceive.clear();
        encryptedReceive.limit(length);
        plainReceive.clear();
        if (!crypto.open(encryptedReceive, plainReceive)) throw new IOException("invalid encrypted agent frame");
        LegacyAgentWire.Inbound message = LegacyAgentWire.readInbound(plainReceive);
        if (message == null) throw new IOException("malformed agent message");
        return message;
    }

    private static void writeRaw(DataOutputStream out, ByteBuffer buffer) throws IOException {
        int length = buffer.position();
        if (length <= 0 || length > MAX_RAW_BYTES) throw new IOException("invalid raw agent frame length");
        out.writeInt(length);
        out.write(buffer.array(), buffer.arrayOffset(), length);
        out.flush();
    }

    private static ByteBuffer readRaw(DataInputStream in, int maxLength) throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > maxLength || length > MAX_RAW_BYTES) {
            throw new IOException("invalid raw agent frame length: " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return ByteBuffer.wrap(bytes);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        synchronized (outboundLock) {
            controlQueue.clear();
            mediaQueue.clear();
            outboundLock.notifyAll();
        }
        crypto.close();
        Arrays.fill(receiveFrame, (byte) 0);
        try {
            socket.close();
        } catch (IOException ignored) {}
        if (writerThread != Thread.currentThread()) writerThread.interrupt();
    }
}
