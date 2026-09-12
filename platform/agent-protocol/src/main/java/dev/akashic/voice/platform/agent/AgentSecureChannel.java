package dev.akashic.voice.platform.agent;

import dev.akashic.voice.voice.common.crypto.SessionCrypto;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class AgentSecureChannel implements AutoCloseable {
    static final int MAX_CONTROL_QUEUE = 128;
    static final int MAX_MEDIA_QUEUE = 256;

    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final SessionCrypto crypto;
    private final Object outboundLock = new Object();
    private final ArrayDeque<AgentMessage> controlQueue = new ArrayDeque<>();
    private final ArrayDeque<AgentMessage> mediaQueue = new ArrayDeque<>();
    private final AtomicLong droppedMediaFrames = new AtomicLong();
    private final ByteBuffer plainSend = ByteBuffer.allocate(AgentProtocol.MAX_PLAINTEXT_BYTES);
    private final ByteBuffer encryptedSend = ByteBuffer.allocate(AgentProtocol.MAX_ENCRYPTED_FRAME_BYTES);
    private final byte[] receiveFrame = new byte[AgentProtocol.MAX_ENCRYPTED_FRAME_BYTES];
    private final ByteBuffer encryptedReceive = ByteBuffer.wrap(receiveFrame);
    private final ByteBuffer plainReceive = ByteBuffer.allocate(AgentProtocol.MAX_PLAINTEXT_BYTES);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread writerThread;

    AgentSecureChannel(Socket socket, SessionCrypto crypto) throws IOException {
        this.socket = Objects.requireNonNull(socket, "socket");
        this.crypto = Objects.requireNonNull(crypto, "crypto");
        this.input = new DataInputStream(socket.getInputStream());
        this.output = new DataOutputStream(socket.getOutputStream());
        this.writerThread = new Thread(this::writeLoop, "AkashicVoice-Agent-Writer");
        this.writerThread.setDaemon(true);
        this.writerThread.start();
    }

    public boolean isOpen() {
        return !closed.get() && !socket.isClosed() && socket.isConnected();
    }

    public boolean send(AgentMessage message) {
        Objects.requireNonNull(message, "message");
        if (!isOpen()) return false;
        if (isMedia(message)) return enqueueMedia(message);
        return enqueueControl(message);
    }

    public long droppedMediaFrames() {
        return droppedMediaFrames.get();
    }
    public int queuedMediaMessages() {
        synchronized (outboundLock) {
            return mediaQueue.size();
        }
    }

    public int queuedControlMessages() {
        synchronized (outboundLock) {
            return controlQueue.size();
        }
    }

    private boolean enqueueControl(AgentMessage message) {
        boolean overflow = false;
        synchronized (outboundLock) {
            if (closed.get()) return false;
            if (controlQueue.size() >= MAX_CONTROL_QUEUE) {
                overflow = true;
            } else {
                controlQueue.addLast(message);
                outboundLock.notifyAll();
                return true;
            }
        }
        if (overflow) close();
        return false;
    }

    private boolean enqueueMedia(AgentMessage message) {
        synchronized (outboundLock) {
            if (closed.get()) return false;
            if (mediaQueue.size() >= MAX_MEDIA_QUEUE) {
                mediaQueue.pollFirst();
                droppedMediaFrames.incrementAndGet();
            }
            mediaQueue.addLast(message);
            outboundLock.notifyAll();
            return true;
        }
    }
    private void writeLoop() {
        try {
            while (isOpen()) {
                AgentMessage message = takeOutbound();
                if (message == null) return;
                if (!writeNow(message)) return;
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            close();
        }
    }

    private AgentMessage takeOutbound() throws InterruptedException {
        synchronized (outboundLock) {
            while (!closed.get() && controlQueue.isEmpty() && mediaQueue.isEmpty()) {
                outboundLock.wait();
            }
            if (closed.get()) return null;
            AgentMessage control = controlQueue.pollFirst();
            return control != null ? control : mediaQueue.pollFirst();
        }
    }

    private boolean writeNow(AgentMessage message) {
        if (!isOpen()) return false;
        try {
            plainSend.clear();
            AgentMessageCodec.write(plainSend, message);
            encryptedSend.clear();
            if (!crypto.seal(plainSend, encryptedSend)) return false;
            int length = encryptedSend.position();
            if (length <= 0 || length > AgentProtocol.MAX_ENCRYPTED_FRAME_BYTES) return false;
            output.writeInt(length);
            output.write(encryptedSend.array(), encryptedSend.arrayOffset(), length);
            output.flush();
            return true;
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }
    private static boolean isMedia(AgentMessage message) {
        return message instanceof AgentMessage.SpeakerFrame
            || message instanceof AgentMessage.SpeakerReset
            || message instanceof AgentMessage.SvcDelivery
            || message instanceof AgentMessage.SvcReset;
    }

    public AgentMessage receive() throws IOException {
        if (!isOpen()) throw new EOFException("agent channel closed");
        int length = input.readInt();
        if (length <= 0 || length > AgentProtocol.MAX_ENCRYPTED_FRAME_BYTES) {
            throw new IOException("invalid encrypted agent frame length: " + length);
        }
        input.readFully(receiveFrame, 0, length);
        encryptedReceive.clear();
        encryptedReceive.limit(length);
        plainReceive.clear();
        if (!crypto.open(encryptedReceive, plainReceive)) {
            throw new IOException("invalid encrypted agent frame");
        }
        AgentMessage message = AgentMessageCodec.read(plainReceive);
        if (message == null) throw new IOException("malformed agent message");
        return message;
    }

    public Socket socket() {
        return socket;
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
        } catch (IOException ignored) {
            // best effort
        }
        if (writerThread != Thread.currentThread()) writerThread.interrupt();
    }
}
