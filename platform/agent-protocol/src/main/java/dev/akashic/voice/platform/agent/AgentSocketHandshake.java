package dev.akashic.voice.platform.agent;

import dev.akashic.voice.voice.common.crypto.HandshakeProof;
import dev.akashic.voice.voice.common.crypto.SessionCrypto;
import dev.akashic.voice.voice.common.crypto.SessionKeys;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class AgentSocketHandshake {
    public static final int HANDSHAKE_TIMEOUT_MILLIS = 5_000;
    public static final int RUNTIME_READ_TIMEOUT_MILLIS = 20_000;
    private static final int MAX_RAW_FRAME_BYTES = 512;

    private AgentSocketHandshake() {}

    @FunctionalInterface
    public interface PskProvider {
        /** Returns a 32-byte secret copy owned by the caller, or null for an unknown backend. */
        byte[] pskCopyFor(String backendId);
    }

    public static AgentSecureChannel connect(Socket socket, String backendId, byte[] psk,
                                             SecureRandom random) throws IOException {
        Objects.requireNonNull(socket, "socket");
        Objects.requireNonNull(random, "random");
        configureHandshakeSocket(socket);
        byte[] localPsk = copyPsk(psk);
        byte[] clientNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        random.nextBytes(clientNonce);
        SessionCrypto crypto = null;
        byte[] clientProof = null;
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            ByteBuffer hello = ByteBuffer.allocate(AgentHandshake.helloBytes(backendId));
            AgentHandshake.writeHello(hello, backendId, clientNonce);
            writeRaw(out, hello);
            AgentHandshake.Challenge challenge = AgentHandshake.readChallenge(readRaw(in, AgentHandshake.CHALLENGE_BYTES));
            if (challenge == null) throw new IOException("invalid agent challenge");
            SessionKeys keys = AgentKeyDerivation.derive(localPsk, backendId, clientNonce, challenge.serverNonce());
            try {
                UUID routeId = AgentKeyDerivation.routeIdentity(backendId);
                if (!HandshakeProof.verifyServer(
                    keys, routeId, clientNonce, challenge.serverNonce(), challenge.serverProof())) {
                    throw new IOException("agent server proof rejected");
                }
                clientProof = HandshakeProof.computeClient(keys, routeId, clientNonce, challenge.serverNonce());
                crypto = SessionCrypto.forClient(keys);
            } finally {
                keys.close();
                Arrays.fill(challenge.serverNonce(), (byte) 0);
                Arrays.fill(challenge.serverProof(), (byte) 0);
            }

            ByteBuffer response = ByteBuffer.allocate(AgentHandshake.AUTH_RESPONSE_BYTES);
            AgentHandshake.writeAuthResponse(response, clientProof);
            writeRaw(out, response);
            Arrays.fill(clientProof, (byte) 0);

            AgentSecureChannel channel = new AgentSecureChannel(socket, crypto);
            crypto = null;
            AgentMessage ready = channel.receive();
            if (!(ready instanceof AgentMessage.Ready)) {
                channel.close();
                throw new IOException("agent gateway did not send READY");
            }
            socket.setSoTimeout(RUNTIME_READ_TIMEOUT_MILLIS);
            return channel;
        } catch (IOException | RuntimeException failure) {
            if (crypto != null) crypto.close();
            closeSocket(socket);
            throw failure;
        } finally {
            if (clientProof != null) Arrays.fill(clientProof, (byte) 0);
            Arrays.fill(localPsk, (byte) 0);
            Arrays.fill(clientNonce, (byte) 0);
        }
    }

    public static Accepted accept(Socket socket, PskProvider pskProvider,
                                  SecureRandom random) throws IOException {
        Objects.requireNonNull(socket, "socket");
        Objects.requireNonNull(pskProvider, "pskProvider");
        Objects.requireNonNull(random, "random");
        configureHandshakeSocket(socket);
        byte[] localPsk = null;
        byte[] serverNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        SessionCrypto crypto = null;
        AgentHandshake.Hello hello = null;
        AgentHandshake.AuthResponse response = null;
        byte[] serverProof = null;
        byte[] expectedClientProof = null;
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            hello = AgentHandshake.readHello(readRaw(in, MAX_RAW_FRAME_BYTES));
            if (hello == null) throw new IOException("agent hello rejected");
            localPsk = pskProvider.pskCopyFor(hello.backendId());
            if (localPsk == null || localPsk.length != AgentKeyDerivation.PSK_BYTES) {
                if (localPsk != null) Arrays.fill(localPsk, (byte) 0);
                localPsk = null;
                throw new IOException("agent backend rejected");
            }

            random.nextBytes(serverNonce);
            SessionKeys keys = AgentKeyDerivation.derive(
                localPsk, hello.backendId(), hello.clientNonce(), serverNonce);
            try {
                UUID routeId = AgentKeyDerivation.routeIdentity(hello.backendId());
                serverProof = HandshakeProof.computeServer(
                    keys, routeId, hello.clientNonce(), serverNonce);
                expectedClientProof = HandshakeProof.computeClient(
                    keys, routeId, hello.clientNonce(), serverNonce);
                crypto = SessionCrypto.forServer(keys);
            } finally {
                keys.close();
            }

            ByteBuffer challenge = ByteBuffer.allocate(AgentHandshake.CHALLENGE_BYTES);
            AgentHandshake.writeChallenge(challenge, serverNonce, serverProof);
            writeRaw(out, challenge);
            Arrays.fill(serverProof, (byte) 0);

            response = AgentHandshake.readAuthResponse(
                readRaw(in, AgentHandshake.AUTH_RESPONSE_BYTES));
            if (response == null || !java.security.MessageDigest.isEqual(
                expectedClientProof, response.clientProof())) {
                throw new IOException("agent client proof rejected");
            }
            Arrays.fill(expectedClientProof, (byte) 0);
            Arrays.fill(response.clientProof(), (byte) 0);

            AgentSecureChannel channel = new AgentSecureChannel(socket, crypto);
            crypto = null;
            if (!channel.send(new AgentMessage.Ready())) {
                channel.close();
                throw new IOException("could not send agent READY");
            }
            socket.setSoTimeout(RUNTIME_READ_TIMEOUT_MILLIS);
            Arrays.fill(hello.clientNonce(), (byte) 0);
            return new Accepted(hello.backendId(), channel);
        } catch (IOException | RuntimeException failure) {
            if (crypto != null) crypto.close();
            closeSocket(socket);
            throw failure;
        } finally {
            if (hello != null) Arrays.fill(hello.clientNonce(), (byte) 0);
            if (response != null) Arrays.fill(response.clientProof(), (byte) 0);
            if (serverProof != null) Arrays.fill(serverProof, (byte) 0);
            if (expectedClientProof != null) Arrays.fill(expectedClientProof, (byte) 0);
            if (localPsk != null) Arrays.fill(localPsk, (byte) 0);
            Arrays.fill(serverNonce, (byte) 0);
        }
    }

    private static void configureHandshakeSocket(Socket socket) throws IOException {
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        socket.setSoTimeout(HANDSHAKE_TIMEOUT_MILLIS);
    }

    private static byte[] copyPsk(byte[] psk) {
        if (psk == null || psk.length != AgentKeyDerivation.PSK_BYTES) {
            throw new IllegalArgumentException("agent PSK must be 32 bytes");
        }
        return Arrays.copyOf(psk, psk.length);
    }

    private static void writeRaw(DataOutputStream out, ByteBuffer buffer) throws IOException {
        int length = buffer.position();
        if (length <= 0 || length > MAX_RAW_FRAME_BYTES) throw new IOException("invalid raw agent frame length");
        out.writeInt(length);
        out.write(buffer.array(), buffer.arrayOffset(), length);
        out.flush();
    }
    private static ByteBuffer readRaw(DataInputStream in, int maxLength) throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > maxLength || length > MAX_RAW_FRAME_BYTES) {
            throw new IOException("invalid raw agent frame length: " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return ByteBuffer.wrap(bytes);
    }

    private static void closeSocket(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // best effort
        }
    }

    public record Accepted(String backendId, AgentSecureChannel channel) {
        public Accepted {
            Objects.requireNonNull(backendId, "backendId");
            Objects.requireNonNull(channel, "channel");
        }
    }
}
