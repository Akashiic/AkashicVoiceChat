package dev.akashic.voice.platform.agent;

import dev.akashic.voice.voice.common.crypto.SessionCrypto;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AgentSecureChannel implements AutoCloseable {
    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final SessionCrypto crypto;
    private final Object sendLock = new Object();
    private final ByteBuffer plainSend = ByteBuffer.allocate(AgentProtocol.MAX_PLAINTEXT_BYTES);
    private final ByteBuffer encryptedSend = ByteBuffer.allocate(AgentProtocol.MAX_ENCRYPTED_FRAME_BYTES);
    private final byte[] receiveFrame = new byte[AgentProtocol.MAX_ENCRYPTED_FRAME_BYTES];
    private final ByteBuffer encryptedReceive = ByteBuffer.wrap(receiveFrame);
    private final ByteBuffer plainReceive = ByteBuffer.allocate(AgentProtocol.MAX_PLAINTEXT_BYTES);
    private final AtomicBoolean closed = new AtomicBoolean();

    AgentSecureChannel(Socket socket, SessionCrypto crypto) throws IOException {
        this.socket = Objects.requireNonNull(socket, "socket");
        this.crypto = Objects.requireNonNull(crypto, "crypto");
        this.input = new DataInputStream(socket.getInputStream());
        this.output = new DataOutputStream(socket.getOutputStream());
    }
    public boolean isOpen() {
        return !closed.get() && !socket.isClosed() && socket.isConnected();
    }

    public boolean send(AgentMessage message) {
        if (!isOpen()) return false;
        synchronized (sendLock) {
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
                close();
                return false;
            }
        }
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
        crypto.close();
        Arrays.fill(receiveFrame, (byte) 0);
        try {
            socket.close();
        } catch (IOException ignored) {
            // best effort
        }
    }
}
