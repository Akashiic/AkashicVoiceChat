package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.bridge.AkashicSpeakerFrame;
import dev.akashic.voice.platform.bridge.BridgeDelivery;
import dev.akashic.voice.platform.bridge.BridgeStreamReset;
import dev.akashic.voice.platform.bridge.ManagedPlayer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.Map;
import java.util.UUID;

final class GatewayRuntime implements GatewayUdpServer.Listener, AgentHub.Listener, AutoCloseable {
    interface Listener {
        void onRouteUp(String backendName);
        void onRouteDown(String backendName);
        void onError(String component, Throwable error);
    }

    private final BootstrapSessionRegistry bootstrapSessions;
    private final BackendRouteRegistry routes = new BackendRouteRegistry();
    private final GatewayUdpServer udp;
    private final AgentHub agents;
    private final Listener listener;
    private volatile boolean running;

    GatewayRuntime(int udpPort, InetSocketAddress agentBind, Map<String, byte[]> agentPsks,
                   BootstrapSessionRegistry bootstrapSessions, Listener listener) {
        this.bootstrapSessions = Objects.requireNonNull(bootstrapSessions, "bootstrapSessions");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.udp = new GatewayUdpServer(udpPort, bootstrapSessions, this);
        this.agents = new AgentHub(agentBind, agentPsks, routes, this);
    }

    synchronized void start() throws IOException {
        if (running) return;
        udp.start();
        try {
            agents.start();
            running = true;
        } catch (IOException failure) {
            udp.close();
            throw failure;
        }
    }

    boolean isBackendReady(String backendName) {
        return running && routes.isReady(backendName);
    }

    boolean hasVoiceSession(UUID proxyPlayerId, String backendName) {
        return running && udp.hasConnection(proxyPlayerId, backendName);
    }

    BootstrapGrant grantFor(UUID proxyPlayerId, String playerName, String backendName) {
        if (!isBackendReady(backendName)) return null;
        return bootstrapSessions.grantFor(proxyPlayerId, playerName, backendName, udp.localPort());
    }

    int udpPort() {
        return udp.localPort();
    }

    int agentPort() {
        return agents.localPort();
    }
    void disconnectProxyPlayer(UUID proxyPlayerId) {
        udp.disconnectProxyPlayer(proxyPlayerId);
        bootstrapSessions.invalidate(proxyPlayerId);
    }

    @Override
    public void onConnected(UUID proxyPlayerId, String backendName, UUID voiceIdentity) {
        BackendRouteRegistry.Route route = routes.readyRoute(backendName);
        if (route == null) return;
        BootstrapSessionRegistry.SessionMaterial material = null;
        try {
            material = bootstrapSessions.findByVoiceIdentity(voiceIdentity);
            if (material == null) return;
            route.sendAttach(new ManagedPlayer(
                proxyPlayerId, backendName, material.playerName()));
        } finally {
            if (material != null) material.close();
        }
    }

    @Override
    public void onDisconnected(UUID proxyPlayerId, String backendName, UUID voiceIdentity) {
        BackendRouteRegistry.Route route = routes.readyRoute(backendName);
        if (route != null) route.sendDetach(proxyPlayerId);
    }

    @Override
    public void onMicFrame(UUID proxyPlayerId, String backendName, UUID voiceIdentity,
                           long sequence, byte[] opus) {
        BackendRouteRegistry.Route route = routes.readyRoute(backendName);
        if (route == null) return;
        route.sendFrame(new AkashicSpeakerFrame(proxyPlayerId, sequence, false, opus));
    }

    @Override
    public void onError(Throwable error) {
        listener.onError("udp", error);
    }

    @Override
    public void onRouteUp(String backendName) {
        BackendRouteRegistry.Route route = routes.readyRoute(backendName);
        if (route != null) {
            for (ManagedPlayer player : udp.managedPlayers(backendName)) {
                if (!route.sendAttach(player)) break;
            }
        }
        listener.onRouteUp(backendName);
    }

    @Override
    public void onRouteDown(String backendName) {
        // Keep authenticated UDP sessions alive across transient agent reconnects.
        listener.onRouteDown(backendName);
    }

    @Override
    public void onSvcDelivery(String backendName, BridgeDelivery delivery) {
        udp.sendBridgeDelivery(backendName, delivery);
    }

    @Override
    public void onSvcReset(String backendName, BridgeStreamReset reset) {
        udp.sendBridgeReset(backendName, reset);
    }

    @Override
    public void onError(String backendName, Throwable error) {
        listener.onError("agent:" + backendName, error);
    }
    @Override
    public synchronized void close() {
        if (!running) {
            agents.close();
            udp.close();
            routes.close();
            return;
        }
        running = false;
        agents.close();
        udp.close();
        routes.close();
    }
}
