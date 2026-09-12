package dev.akashic.voice.platform.svc;

import dev.akashic.voice.platform.agent.AgentMessage;
import dev.akashic.voice.platform.agent.AgentSecureChannel;
import dev.akashic.voice.platform.agent.AgentSocketHandshake;
import dev.akashic.voice.platform.bridge.BridgeDelivery;
import dev.akashic.voice.platform.bridge.BridgeStreamReset;
import dev.akashic.voice.platform.bridge.GatewayLink;
import dev.akashic.voice.platform.bridge.ManagedPlayer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

final class SocketGatewayLink implements GatewayLink {
    private static final long MIN_RETRY_MILLIS = 1_000L;
    private static final long MAX_RETRY_MILLIS = 10_000L;

    private final String host;
    private final int port;
    private final String backendId;
    private final byte[] psk;
    private final SecureRandom random = new SecureRandom();
    private final AtomicReference<AgentSecureChannel> channel = new AtomicReference<>();
    private final Set<UUID> attachedPlayers = ConcurrentHashMap.newKeySet();
    private volatile Listener listener;
    private volatile boolean running;
    private Thread worker;

    SocketGatewayLink(String host, int port, String backendId, byte[] psk) {
        if (host == null || host.isBlank()) throw new IllegalArgumentException("host");
        if (port < 1 || port > 65_535) throw new IllegalArgumentException("port");
        if (backendId == null || backendId.isBlank()) throw new IllegalArgumentException("backendId");
        if (psk == null || psk.length != 32) throw new IllegalArgumentException("agent PSK must be 32 bytes");
        this.host = host;
        this.port = port;
        this.backendId = backendId;
        this.psk = Arrays.copyOf(psk, psk.length);
    }

    @Override
    public synchronized void start(Listener listener) {
        if (running) return;
        this.listener = listener;
        running = true;
        worker = new Thread(this::runLoop, "AkashicVoice-GatewayLink");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public boolean isConnected() {
        AgentSecureChannel current = channel.get();
        return running && current != null && current.isOpen();
    }

    @Override
    public void publishSvcDelivery(BridgeDelivery delivery) {
        send(new AgentMessage.SvcDelivery(delivery));
    }

    @Override
    public void publishSvcReset(BridgeStreamReset reset) {
        send(new AgentMessage.SvcReset(reset));
    }

    private void send(AgentMessage message) {
        AgentSecureChannel current = channel.get();
        if (current == null) return;
        if (!current.send(message)) disconnectCurrent(current);
    }

    private void runLoop() {
        long retryMillis = MIN_RETRY_MILLIS;
        while (running) {
            try {
                AgentSecureChannel connected = connect();
                AgentSecureChannel previous = channel.getAndSet(connected);
                if (previous != null && previous != connected) previous.close();
                retryMillis = MIN_RETRY_MILLIS;
                readLoop(connected);
            } catch (IOException | RuntimeException ignored) {
                // Reconnect is expected; plugin logs only state changes at a higher level.
            } finally {
                AgentSecureChannel current = channel.getAndSet(null);
                if (current != null) current.close();
                detachAll();
            }
            if (!running) break;
            sleepRetry(retryMillis);
            retryMillis = Math.min(MAX_RETRY_MILLIS, retryMillis * 2L);
        }
    }
    private AgentSecureChannel connect() throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), AgentSocketHandshake.HANDSHAKE_TIMEOUT_MILLIS);
        return AgentSocketHandshake.connect(socket, backendId, psk, random);
    }

    private void readLoop(AgentSecureChannel current) throws IOException {
        while (running && channel.get() == current && current.isOpen()) {
            AgentMessage message;
            try {
                message = current.receive();
            } catch (SocketTimeoutException timeout) {
                if (!current.send(new AgentMessage.Ping(System.currentTimeMillis()))) return;
                continue;
            }
            if (message instanceof AgentMessage.Attach attach) {
                ManagedPlayer player = attach.player();
                attachedPlayers.add(player.playerId());
                listener.onManagedPlayerAttached(player);
            } else if (message instanceof AgentMessage.Detach detach) {
                attachedPlayers.remove(detach.playerId());
                listener.onManagedPlayerDetached(detach.playerId());
            } else if (message instanceof AgentMessage.SpeakerFrame frame) {
                listener.onAkashicSpeakerFrame(frame.frame());
            } else if (message instanceof AgentMessage.SpeakerReset reset) {
                listener.onAkashicSpeakerReset(reset.speakerId());
            } else if (message instanceof AgentMessage.Ping ping) {
                if (!current.send(new AgentMessage.Pong(ping.timestamp()))) return;
            } else if (message instanceof AgentMessage.Pong) {
                // heartbeat response
            } else {
                throw new IOException("unexpected gateway->agent message: " + message.getClass().getSimpleName());
            }
        }
    }
    private void disconnectCurrent(AgentSecureChannel current) {
        if (channel.compareAndSet(current, null)) current.close();
    }

    private void detachAll() {
        Listener currentListener = listener;
        if (currentListener == null) {
            attachedPlayers.clear();
            return;
        }
        for (UUID playerId : Set.copyOf(attachedPlayers)) {
            if (!attachedPlayers.remove(playerId)) continue;
            try {
                currentListener.onManagedPlayerDetached(playerId);
            } catch (RuntimeException ignored) {
                // best-effort route teardown; reconciliation is idempotent.
            }
        }
    }

    private void sleepRetry(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public synchronized void close() {
        if (!running && listener == null) return;
        running = false;
        AgentSecureChannel current = channel.getAndSet(null);
        if (current != null) current.close();
        if (worker != null) worker.interrupt();
        detachAll();
        listener = null;
        Arrays.fill(psk, (byte) 0);
    }
}
