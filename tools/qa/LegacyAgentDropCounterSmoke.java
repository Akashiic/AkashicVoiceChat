package dev.akashic.voice.voice.server.agent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import dev.akashic.voice.voice.common.crypto.SessionCrypto;
import dev.akashic.voice.voice.common.crypto.SessionKeys;

/** Deterministic bounded-queue test; no Minecraft process or physical audio device. */
public final class LegacyAgentDropCounterSmoke {
    private static final UUID A = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID B = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID C = UUID.fromString("33333333-3333-4333-8333-333333333333");

    public static void main(String[] args) throws Exception {
        AtomicLong cumulative = new AtomicLong();
        fillGeneration(cumulative, 300, 44L);
        check(cumulative.get() == 44L, "first generation total mismatch");
        fillGeneration(cumulative, 300, 88L);
        check(cumulative.get() == 88L, "second generation did not preserve prior drops");
        verifyAgentReadsCumulativeField();
        System.out.println("LEGACY_AGENT_DROP_COUNTER_OK generations=2 drops=" + cumulative.get());
    }

    private static void fillGeneration(AtomicLong cumulative, int sends, long expectedTotal) throws Exception {
        ServerSocket listener = new ServerSocket(0);
        Socket client = new Socket("127.0.0.1", listener.getLocalPort());
        Socket peer = listener.accept();
        SessionCrypto crypto = newCrypto();
        LegacyAgentChannel channel = null;
        try {
            Constructor<LegacyAgentChannel> constructor = LegacyAgentChannel.class.getDeclaredConstructor(
                Socket.class, SessionCrypto.class, AtomicLong.class);
            constructor.setAccessible(true);
            channel = constructor.newInstance(client, crypto, cumulative);
            crypto = null; // Channel owns it from here.

            Field lockField = LegacyAgentChannel.class.getDeclaredField("outboundLock");
            lockField.setAccessible(true);
            Object lock = lockField.get(channel);
            byte[] opus = new byte[1024];
            Arrays.fill(opus, (byte) 7);
            LegacyAgentWire.Outbound media = LegacyAgentWire.delivery(
                A, B, C, 1L, false, 48.0F, "legacy-dim:0", 1.0, 2.0, 3.0, opus);

            synchronized (lock) {
                for (int i = 0; i < sends; i++) check(channel.send(media), "media enqueue rejected at " + i);
                check(channel.queuedMediaMessages() == 256, "queue bound mismatch: " + channel.queuedMediaMessages());
                check(channel.droppedMediaFrames() == expectedTotal,
                    "cumulative drop mismatch expected=" + expectedTotal + " actual=" + channel.droppedMediaFrames());
                channel.close();
            }
        } finally {
            if (channel != null) channel.close();
            if (crypto != null) crypto.close();
            peer.close();
            client.close();
            listener.close();
        }
    }

    private static SessionCrypto newCrypto() {
        byte[] master = new byte[SessionKeys.MASTER_SECRET_BYTES];
        byte[] clientNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        byte[] serverNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        Arrays.fill(master, (byte) 3);
        Arrays.fill(clientNonce, (byte) 5);
        Arrays.fill(serverNonce, (byte) 7);
        SessionKeys keys = SessionKeys.derive(master, clientNonce, serverNonce);
        try {
            return SessionCrypto.forClient(keys);
        } finally {
            keys.close();
            Arrays.fill(master, (byte) 0);
            Arrays.fill(clientNonce, (byte) 0);
            Arrays.fill(serverNonce, (byte) 0);
        }
    }

    private static void verifyAgentReadsCumulativeField() throws Exception {
        byte[] psk = new byte[LegacyAgentWire.PSK_BYTES];
        Arrays.fill(psk, (byte) 9);
        LegacyGatewayAgent agent = new LegacyGatewayAgent("legacy", "127.0.0.1", 1, psk);
        Arrays.fill(psk, (byte) 0);
        Field field = LegacyGatewayAgent.class.getDeclaredField("droppedMedia");
        field.setAccessible(true);
        AtomicLong counter = (AtomicLong) field.get(agent);
        counter.set(123L);
        check(agent.droppedMediaFrames() == 123L, "Agent did not read cumulative counter while disconnected");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
