package dev.akashic.voice.platform.agent;

import dev.akashic.voice.platform.bridge.AkashicSpeakerFrame;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AgentSecureChannelBackpressureTest {
    @Test
    void mediaQueueStaysBoundedWhenPeerStopsReading() throws Exception {
        byte[] psk = fixedPsk(41);
        try (ServerSocket listener = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<AgentSocketHandshake.Accepted> acceptedFuture = executor.submit(() -> {
                Socket socket = listener.accept();
                return AgentSocketHandshake.accept(socket,
                    backend -> "lobby".equals(backend) ? Arrays.copyOf(psk, psk.length) : null,
                    new SecureRandom());
            });
            try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
                 AgentSecureChannel client = AgentSocketHandshake.connect(
                     socket, "lobby", psk, new SecureRandom())) {
                AgentSocketHandshake.Accepted accepted = acceptedFuture.get(3, TimeUnit.SECONDS);
                try (AgentSecureChannel server = accepted.channel()) {
                    client.socket().setSendBufferSize(4 * 1024);
                    server.socket().setReceiveBufferSize(4 * 1024);

                    byte[] opus = new byte[1_200];
                    Arrays.fill(opus, (byte) 0x5A);
                    UUID speaker = UUID.randomUUID();
                    long start = System.nanoTime();
                    for (int i = 0; i < 5_000; i++) {
                        assertTrue(client.send(new AgentMessage.SpeakerFrame(
                            new AkashicSpeakerFrame(speaker, i, false, opus))));
                    }
                    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

                    assertTrue(elapsedMillis < 2_000L,
                        "media producer blocked for " + elapsedMillis + " ms");
                    await(() -> client.droppedMediaFrames() > 0L,
                        "bounded media queue never reported a drop");
                    assertTrue(client.queuedMediaMessages() <= AgentSecureChannel.MAX_MEDIA_QUEUE);
                    assertTrue(client.queuedControlMessages() <= AgentSecureChannel.MAX_CONTROL_QUEUE);
                }
            }
        } finally {
            Arrays.fill(psk, (byte) 0);
        }
    }
    @Test
    void resetNeverOvertakesQueuedMedia() throws Exception {
        byte[] psk = fixedPsk(57);
        try (ServerSocket listener = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<AgentSocketHandshake.Accepted> acceptedFuture = executor.submit(() -> {
                Socket socket = listener.accept();
                return AgentSocketHandshake.accept(socket,
                    backend -> "lobby".equals(backend) ? Arrays.copyOf(psk, psk.length) : null,
                    new SecureRandom());
            });
            try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
                 AgentSecureChannel client = AgentSocketHandshake.connect(socket, "lobby", psk, new SecureRandom())) {
                AgentSocketHandshake.Accepted accepted = acceptedFuture.get(3, TimeUnit.SECONDS);
                try (AgentSecureChannel server = accepted.channel()) {
                    client.socket().setSendBufferSize(4 * 1024);
                    server.socket().setReceiveBufferSize(4 * 1024);
                    byte[] opus = new byte[1_200];
                    UUID speaker = UUID.randomUUID();
                    for (int i = 0; i < 1_000; i++) {
                        assertTrue(client.send(new AgentMessage.SpeakerFrame(
                            new AkashicSpeakerFrame(speaker, i, false, opus))));
                    }
                    assertTrue(client.send(new AgentMessage.SpeakerReset(speaker)));
                    boolean sawReset = false;
                    for (int i = 0; i < 2_000 && !sawReset; i++) {
                        AgentMessage message = server.receive();
                        if (message instanceof AgentMessage.SpeakerReset) sawReset = true;
                    }
                    assertTrue(sawReset, "reset was never delivered");
                    server.socket().setSoTimeout(200);
                    assertThrows(java.net.SocketTimeoutException.class, server::receive,
                        "media appeared after reset");
                }
            }
        } finally {
            Arrays.fill(psk, (byte) 0);
        }
    }

    private static void await(java.util.function.BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertTrue(condition.getAsBoolean(), message);
    }

    private static byte[] fixedPsk(int seed) {
        byte[] psk = new byte[AgentKeyDerivation.PSK_BYTES];
        for (int i = 0; i < psk.length; i++) psk[i] = (byte) (seed + i * 7);
        return psk;
    }
}
