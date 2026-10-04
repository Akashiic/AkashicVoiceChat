package dev.akashic.voice.voice.server.agent;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.common.crypto.HandshakeProof;
import dev.akashic.voice.voice.common.crypto.SessionCrypto;
import dev.akashic.voice.voice.common.crypto.SessionKeys;
import dev.akashic.voice.voice.server.PlayerStateRegistry;

/** Real TCP/HKDF/AES-GCM Agent flow. No Minecraft process or physical audio device is opened. */
public final class LegacyAgentIntegrationSmoke {
    private static final UUID PROXY_A = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PROXY_B = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID LOCAL_A = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID LOCAL_B = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final String BACKEND = "forbidden-1";

    public static void main(String[] args) throws Exception {
        byte[] psk = new byte[32];
        for (int i = 0; i < psk.length; i++) psk[i] = (byte) (i * 7 + 3);
        ServerSocket listener = new ServerSocket(0);
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread gateway = new Thread(new Runnable() {
            @Override public void run() {
                try { runGateway(listener, psk); }
                catch (Throwable error) { failure.set(error); }
            }
        }, "AkashicVoice-QA-FakeGateway");
        gateway.setDaemon(true);
        gateway.start();

        LegacyGatewayAgent agent = new LegacyGatewayAgent(BACKEND, "127.0.0.1", listener.getLocalPort(), psk);
        try {
            PlayerStateRegistry.update(LOCAL_A, 0, 10.0, 64.0, 10.0);
            PlayerStateRegistry.update(LOCAL_B, 0, 14.0, 64.0, 10.0);
            agent.updateLocalIdentity("Alice", LOCAL_A);
            agent.updateLocalIdentity("Bob", LOCAL_B);
            agent.start();
            gateway.join(15_000L);
            check(!gateway.isAlive(), "fake Gateway did not finish");
            if (failure.get() != null) throw new AssertionError("Gateway fixture failed", failure.get());
            check(agent.deliveryCount() == 1L, "expected exactly one in-range delivery");
            check(agent.resetCount() == 1L, "expected exactly one reset delivery");
            System.out.println("LEGACY_AGENT_INTEGRATION_OK deliveries=" + agent.deliveryCount()
                + " resets=" + agent.resetCount() + " connectedOnce=" + agent.reconnectCount());
        } finally {
            agent.close();
            listener.close();
            PlayerStateRegistry.remove(LOCAL_A);
            PlayerStateRegistry.remove(LOCAL_B);
            Arrays.fill(psk, (byte) 0);
        }
    }
    private static void runGateway(ServerSocket listener, byte[] psk) throws Exception {
        Socket socket = listener.accept();
        socket.setSoTimeout(5_000);
        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        SessionCrypto crypto = null;
        try {
            ByteBuffer hello = readRaw(in, 512);
            check(hello.getInt() == LegacyAgentWire.MAGIC, "bad Agent magic");
            check(hello.get() == LegacyAgentWire.VERSION && hello.get() == LegacyAgentWire.HELLO, "bad hello header");
            int backendLength = hello.get() & 0xff;
            byte[] backendBytes = new byte[backendLength];
            hello.get(backendBytes);
            String backend = new String(backendBytes, "US-ASCII");
            check(BACKEND.equals(backend), "wrong backend id");
            byte[] clientNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
            hello.get(clientNonce);
            check(!hello.hasRemaining(), "hello trailing bytes");

            byte[] serverNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
            new SecureRandom(new byte[] { 1, 2, 3, 4 }).nextBytes(serverNonce);
            SessionKeys keys = LegacyAgentWire.derive(psk, backend, clientNonce, serverNonce);
            byte[] serverProof = HandshakeProof.computeServer(
                keys, LegacyAgentWire.routeIdentity(backend), clientNonce, serverNonce);
            byte[] expectedClient = HandshakeProof.computeClient(
                keys, LegacyAgentWire.routeIdentity(backend), clientNonce, serverNonce);
            crypto = SessionCrypto.forServer(keys);
            keys.close();
            ByteBuffer challenge = ByteBuffer.allocate(LegacyAgentWire.CHALLENGE_BYTES);
            challenge.putInt(LegacyAgentWire.MAGIC).put(LegacyAgentWire.VERSION).put(LegacyAgentWire.CHALLENGE);
            challenge.put(serverNonce).put(serverProof);
            writeRaw(out, challenge);

            ByteBuffer auth = readRaw(in, LegacyAgentWire.AUTH_BYTES);
            check(auth.getInt() == LegacyAgentWire.MAGIC, "bad auth magic");
            check(auth.get() == LegacyAgentWire.VERSION && auth.get() == LegacyAgentWire.AUTH_RESPONSE, "bad auth header");
            byte[] actualClient = new byte[HandshakeProof.PROOF_BYTES];
            auth.get(actualClient);
            check(java.security.MessageDigest.isEqual(expectedClient, actualClient), "client proof mismatch");
            check(!auth.hasRemaining(), "auth trailing bytes");
            Arrays.fill(expectedClient, (byte) 0);
            Arrays.fill(actualClient, (byte) 0);

            writeEncrypted(out, crypto, ByteBuffer.wrap(new byte[] { LegacyAgentWire.READY }));
            writeEncrypted(out, crypto, attach(PROXY_A, "Alice"));
            writeEncrypted(out, crypto, attach(PROXY_B, "Bob"));
            byte[] opus = new byte[] { 3, 1, 4, 1, 5, 9, 2, 6 };
            writeEncrypted(out, crypto, speakerFrame(PROXY_A, 7L, opus));

            ByteBuffer delivery = readEncrypted(in, crypto);
            verifyDelivery(delivery, opus, 7L);

            PlayerStateRegistry.update(LOCAL_B, 0, 500.0, 64.0, 500.0);
            writeEncrypted(out, crypto, speakerFrame(PROXY_A, 8L, opus));
            socket.setSoTimeout(500);
            try {
                ByteBuffer unexpected = readEncrypted(in, crypto);
                throw new AssertionError("out-of-range player received frame type=" + unexpected.get());
            } catch (SocketTimeoutException expected) {
                // No delivery is the contract.
            }
            socket.setSoTimeout(5_000);
            ByteBuffer reset = ByteBuffer.allocate(1 + 16);
            reset.put(LegacyAgentWire.SPEAKER_RESET);
            putUuid(reset, PROXY_A);
            reset.flip();
            writeEncrypted(out, crypto, reset);
            ByteBuffer actualReset = readEncrypted(in, crypto);
            check(actualReset.get() == LegacyAgentWire.SVC_RESET, "expected SVC reset");
            check(readUuid(actualReset).equals(PROXY_B), "reset recipient mismatch");
            check(readUuid(actualReset).equals(PROXY_A), "reset sender mismatch");
            check(readUuid(actualReset).equals(LegacyAgentWire.routeIdentity(BACKEND)), "reset channel mismatch");
            check(!actualReset.hasRemaining(), "reset trailing bytes");
        } finally {
            if (crypto != null) crypto.close();
            socket.close();
        }
    }

    private static ByteBuffer attach(UUID playerId, String name) throws Exception {
        ByteBuffer out = ByteBuffer.allocate(128);
        out.put(LegacyAgentWire.ATTACH);
        putUuid(out, playerId);
        putString(out, BACKEND);
        putString(out, name);
        out.flip();
        return out;
    }
    private static ByteBuffer speakerFrame(UUID speaker, long sequence, byte[] opus) {
        ByteBuffer out = ByteBuffer.allocate(64 + opus.length);
        out.put(LegacyAgentWire.SPEAKER_FRAME);
        putUuid(out, speaker);
        out.putLong(sequence).put((byte) 0);
        out.putShort((short) opus.length).put(opus);
        out.flip();
        return out;
    }

    private static void verifyDelivery(ByteBuffer in, byte[] expectedOpus, long expectedSequence) throws Exception {
        check(in.get() == LegacyAgentWire.SVC_DELIVERY, "expected SVC delivery");
        check(readUuid(in).equals(PROXY_B), "recipient mismatch");
        check(readUuid(in).equals(PROXY_A), "sender mismatch");
        check(readUuid(in).equals(LegacyAgentWire.routeIdentity(BACKEND)), "channel mismatch");
        check(in.getLong() == expectedSequence, "sequence mismatch");
        check((in.get() & 0xff) == 1, "expected LOCATION spatial kind");
        check(in.get() == 0, "unexpected whisper flag");
        check(Math.abs(in.getFloat() - AkashicVoiceConfig.voiceRadius()) < 0.001F, "radius mismatch");
        check((in.getShort() & 0xffff) == 0xffff, "category should be null");
        check(in.get() == 1, "position missing");
        check("legacy-dim:0".equals(readString(in)), "world key mismatch");
        check(Math.abs(in.getDouble() - 10.0) < 0.0001, "x mismatch");
        check(Math.abs(in.getDouble() - 64.0) < 0.0001, "y mismatch");
        check(Math.abs(in.getDouble() - 10.0) < 0.0001, "z mismatch");
        int opusLength = in.getShort() & 0xffff;
        check(opusLength == expectedOpus.length, "opus length mismatch");
        byte[] actualOpus = new byte[opusLength];
        in.get(actualOpus);
        check(Arrays.equals(actualOpus, expectedOpus), "opus payload changed");
        check(!in.hasRemaining(), "delivery trailing bytes");
    }

    private static void writeEncrypted(DataOutputStream out, SessionCrypto crypto, ByteBuffer plain) throws Exception {
        ByteBuffer source = ByteBuffer.allocate(plain.remaining());
        source.put(plain);
        ByteBuffer encrypted = ByteBuffer.allocate(LegacyAgentWire.MAX_ENCRYPTED_BYTES);
        check(crypto.seal(source, encrypted), "could not seal Agent frame");
        int length = encrypted.position();
        out.writeInt(length);
        out.write(encrypted.array(), encrypted.arrayOffset(), length);
        out.flush();
    }

    private static ByteBuffer readEncrypted(DataInputStream in, SessionCrypto crypto) throws Exception {
        int length = in.readInt();
        check(length > 0 && length <= LegacyAgentWire.MAX_ENCRYPTED_BYTES, "bad encrypted length");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        ByteBuffer plain = ByteBuffer.allocate(LegacyAgentWire.MAX_PLAINTEXT_BYTES);
        check(crypto.open(ByteBuffer.wrap(bytes), plain), "could not open Agent frame");
        return plain;
    }
    private static ByteBuffer readRaw(DataInputStream in, int max) throws Exception {
        int length = in.readInt();
        check(length > 0 && length <= max, "bad raw length");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return ByteBuffer.wrap(bytes);
    }

    private static void writeRaw(DataOutputStream out, ByteBuffer buffer) throws Exception {
        int length = buffer.position();
        out.writeInt(length);
        out.write(buffer.array(), buffer.arrayOffset(), length);
        out.flush();
    }

    private static void putUuid(ByteBuffer out, UUID id) {
        out.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(ByteBuffer in) {
        return new UUID(in.getLong(), in.getLong());
    }

    private static void putString(ByteBuffer out, String value) throws Exception {
        byte[] bytes = value.getBytes("UTF-8");
        out.putShort((short) bytes.length).put(bytes);
    }
    private static String readString(ByteBuffer in) throws Exception {
        int length = in.getShort() & 0xffff;
        byte[] bytes = new byte[length];
        in.get(bytes);
        return new String(bytes, "UTF-8");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
