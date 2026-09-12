package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.agent.AgentMessage;
import dev.akashic.voice.platform.agent.AgentSecureChannel;
import dev.akashic.voice.platform.agent.AgentSocketHandshake;
import dev.akashic.voice.platform.bridge.AkashicSpeakerFrame;
import dev.akashic.voice.platform.bridge.BridgeDelivery;
import dev.akashic.voice.platform.bridge.BridgeStreamReset;
import dev.akashic.voice.platform.bridge.ManagedPlayer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

final class AgentHub implements AutoCloseable {
    interface Listener {
        void onRouteUp(String backendName);
        void onRouteDown(String backendName);
        void onSvcDelivery(String backendName, BridgeDelivery delivery);
        void onSvcReset(String backendName, BridgeStreamReset reset);
        void onError(String backendName, Throwable error);
    }
    private final InetSocketAddress bindAddress;
    private final byte[] psk;
    private final Set<String> allowedBackends;
    private final BackendRouteRegistry routes;
    private final Listener listener;
    private final SecureRandom random = new SecureRandom();
    private final ExecutorService handshakes = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "AkashicVoice-Agent-Handshake");
        thread.setDaemon(true);
        return thread;
    });

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean running;

    AgentHub(InetSocketAddress bindAddress, byte[] psk, Set<String> allowedBackends,
             BackendRouteRegistry routes, Listener listener) {
        this.bindAddress = Objects.requireNonNull(bindAddress, "bindAddress");
        if (psk == null || psk.length != 32) throw new IllegalArgumentException("agent PSK must be 32 bytes");
        this.psk = Arrays.copyOf(psk, psk.length);
        this.allowedBackends = Set.copyOf(allowedBackends);
        if (this.allowedBackends.isEmpty()) throw new IllegalArgumentException("allowedBackends");
        this.routes = Objects.requireNonNull(routes, "routes");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    synchronized void start() throws IOException {
        if (running) return;
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(bindAddress, 16);
        serverSocket = socket;
        running = true;
        acceptThread = new Thread(this::acceptLoop, "AkashicVoice-Agent-Accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }
    int localPort() {
        ServerSocket socket = serverSocket;
        return socket != null ? socket.getLocalPort() : bindAddress.getPort();
    }

    private void acceptLoop() {
        while (running) {
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (SocketException closed) {
                if (running) safeError("<accept>", closed);
                return;
            } catch (IOException failure) {
                if (running) safeError("<accept>", failure);
                continue;
            }
            try {
                handshakes.execute(() -> authenticate(socket));
            } catch (RejectedExecutionException rejected) {
                closeSocket(socket);
                if (running) safeError("<accept>", rejected);
            }
        }
    }

    private void authenticate(Socket socket) {
        try {
            AgentSocketHandshake.Accepted accepted = AgentSocketHandshake.accept(
                socket, psk, allowedBackends::contains, random);
            AgentRoute route = new AgentRoute(accepted.backendId(), accepted.channel());
            BackendRouteRegistry.Route previous = routes.register(route);
            if (previous != null && previous != route) previous.close();
            safeRouteUp(route.backendName());
            route.startReader();
        } catch (IOException | RuntimeException failure) {
            closeSocket(socket);
            if (running) safeError("<handshake>", failure);
        }
    }
    private final class AgentRoute implements BackendRouteRegistry.Route {
        private final String backendName;
        private final AgentSecureChannel channel;
        private final AtomicBoolean closed = new AtomicBoolean();
        private Thread readerThread;

        private AgentRoute(String backendName, AgentSecureChannel channel) {
            this.backendName = backendName;
            this.channel = channel;
        }

        void startReader() {
            readerThread = new Thread(this::readLoop, "AkashicVoice-Agent-" + backendName);
            readerThread.setDaemon(true);
            readerThread.start();
        }

        @Override
        public String backendName() {
            return backendName;
        }

        @Override
        public boolean isOpen() {
            return !closed.get() && channel.isOpen();
        }

        @Override
        public boolean sendAttach(ManagedPlayer player) {
            if (!backendName.equals(player.backendId())) return false;
            return channel.send(new AgentMessage.Attach(player));
        }

        @Override
        public boolean sendDetach(UUID playerId) {
            return channel.send(new AgentMessage.Detach(playerId));
        }
        @Override
        public boolean sendFrame(AkashicSpeakerFrame frame) {
            return channel.send(new AgentMessage.SpeakerFrame(frame));
        }

        @Override
        public boolean sendReset(UUID speakerId) {
            return channel.send(new AgentMessage.SpeakerReset(speakerId));
        }

        private void readLoop() {
            try {
                while (isOpen()) {
                    AgentMessage message;
                    try {
                        message = channel.receive();
                    } catch (SocketTimeoutException timeout) {
                        if (!channel.send(new AgentMessage.Ping(System.currentTimeMillis()))) break;
                        continue;
                    }
                    if (message instanceof AgentMessage.SvcDelivery svc) {
                        safeSvcDelivery(backendName, svc.delivery());
                    } else if (message instanceof AgentMessage.SvcReset reset) {
                        safeSvcReset(backendName, reset.reset());
                    } else if (message instanceof AgentMessage.Ping ping) {
                        if (!channel.send(new AgentMessage.Pong(ping.timestamp()))) break;
                    } else if (message instanceof AgentMessage.Pong) {
                        // heartbeat response
                    } else {
                        throw new IOException("unexpected agent->gateway message: " + message.getClass().getSimpleName());
                    }
                }
            } catch (IOException | RuntimeException failure) {
                if (isOpen()) safeError(backendName, failure);
            } finally {
                close();
            }
        }
        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            boolean removed = routes.unregister(this);
            channel.close();
            Thread reader = readerThread;
            if (reader != null && reader != Thread.currentThread()) reader.interrupt();
            if (removed) safeRouteDown(backendName);
        }
    }

    private void safeRouteUp(String backendName) {
        safeCallback(backendName, () -> listener.onRouteUp(backendName));
    }

    private void safeRouteDown(String backendName) {
        safeCallback(backendName, () -> listener.onRouteDown(backendName));
    }

    private void safeSvcDelivery(String backendName, BridgeDelivery delivery) {
        safeCallback(backendName, () -> listener.onSvcDelivery(backendName, delivery));
    }
    private void safeSvcReset(String backendName, BridgeStreamReset reset) {
        safeCallback(backendName, () -> listener.onSvcReset(backendName, reset));
    }

    private void safeError(String backendName, Throwable error) {
        rethrowFatal(error);
        try {
            listener.onError(backendName, error);
        } catch (Throwable listenerFailure) {
            rethrowFatal(listenerFailure);
        }
    }

    private void safeCallback(String backendName, Runnable callback) {
        try {
            callback.run();
        } catch (Throwable failure) {
            rethrowFatal(failure);
            safeError(backendName, failure);
        }
    }

    private static void rethrowFatal(Throwable error) {
        if (error instanceof VirtualMachineError vmError) throw vmError;
    }
    @Override
    public synchronized void close() {
        if (!running && serverSocket == null) return;
        running = false;
        ServerSocket socket = serverSocket;
        serverSocket = null;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
        if (acceptThread != null) acceptThread.interrupt();
        handshakes.shutdownNow();
        routes.close();
        Arrays.fill(psk, (byte) 0);
    }

    private static void closeSocket(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // best effort
        }
    }
}
