package dev.akashic.voice.platform.agent;

import dev.akashic.voice.platform.bridge.ManagedPlayer;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AgentSocketHandshakeTest {
    @Test
    void authenticatesAndCarriesEncryptedMessagesBothDirections() throws Exception {
        byte[] psk = fixedPsk(7);
        UUID playerId = UUID.randomUUID();
        try (ServerSocket listener = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<AgentSocketHandshake.Accepted> acceptedFuture = executor.submit(() -> {
                Socket socket = listener.accept();
                return AgentSocketHandshake.accept(socket, psk, "lobby"::equals, new SecureRandom());
            });

            try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
                 AgentSecureChannel client = AgentSocketHandshake.connect(socket, "lobby", psk, new SecureRandom())) {
                AgentSocketHandshake.Accepted accepted = acceptedFuture.get(3, TimeUnit.SECONDS);
                try (AgentSecureChannel server = accepted.channel()) {
                    assertEquals("lobby", accepted.backendId());
                    assertTrue(client.send(new AgentMessage.Attach(new ManagedPlayer(playerId, "lobby", "TestPlayer"))));
                    AgentMessage.Attach attach = assertInstanceOf(AgentMessage.Attach.class, server.receive());
                    assertEquals(playerId, attach.player().playerId());
                    assertEquals("lobby", attach.player().backendId());
                    assertEquals("TestPlayer", attach.player().playerName());

                    assertTrue(server.send(new AgentMessage.Ping(987654321L)));
                    AgentMessage.Ping ping = assertInstanceOf(AgentMessage.Ping.class, client.receive());
                    assertEquals(987654321L, ping.timestamp());
                }
            }
        }
    }

    @Test
    void rejectsWrongPreSharedKey() throws Exception {
        byte[] serverPsk = fixedPsk(11);
        byte[] clientPsk = fixedPsk(99);
        try (ServerSocket listener = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<AgentSocketHandshake.Accepted> acceptedFuture = executor.submit(() -> {
                Socket socket = listener.accept();
                return AgentSocketHandshake.accept(socket, serverPsk, "lobby"::equals, new SecureRandom());
            });

            Socket clientSocket = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
            assertThrows(java.io.IOException.class,
                () -> AgentSocketHandshake.connect(clientSocket, "lobby", clientPsk, new SecureRandom()));
            ExecutionException serverFailure = assertThrows(ExecutionException.class,
                () -> acceptedFuture.get(3, TimeUnit.SECONDS));
            assertInstanceOf(java.io.IOException.class, serverFailure.getCause());
        }
    }
    private static byte[] fixedPsk(int seed) {
        byte[] psk = new byte[AgentKeyDerivation.PSK_BYTES];
        for (int i = 0; i < psk.length; i++) psk[i] = (byte) (seed + i * 13);
        return psk;
    }
}
