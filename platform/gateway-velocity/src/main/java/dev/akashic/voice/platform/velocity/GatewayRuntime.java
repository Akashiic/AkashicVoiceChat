package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.bridge.AkashicSpeakerFrame;
import dev.akashic.voice.platform.bridge.BridgeDelivery;
import dev.akashic.voice.platform.bridge.BridgeStreamReset;
import dev.akashic.voice.platform.bridge.FederatedGroup;
import dev.akashic.voice.platform.bridge.FederatedGroupDirectory;
import dev.akashic.voice.platform.bridge.ManagedPlayer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Objects;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

final class GatewayRuntime implements GatewayUdpServer.Listener, AgentHub.Listener, AutoCloseable {
    interface Listener {
        void onRouteUp(String backendName);
        void onRouteDown(String backendName);
        void onError(String component, Throwable error);
    }

    private final BootstrapSessionRegistry bootstrapSessions;
    private final BackendRouteRegistry routes = new BackendRouteRegistry();
    private final FederatedGroupDirectory groups = new FederatedGroupDirectory();
    private final GatewayRadioDirectory radios = new GatewayRadioDirectory();
    private final GatewayUdpServer udp;
    private final AgentHub agents;
    private final Listener listener;
    private final Set<String> configuredBackends;
    private volatile boolean running;
    private volatile Predicate<UUID> groupAudioEligibility = ignored -> true;
    private volatile Predicate<UUID> transmitEligibility = ignored -> true;
    private volatile GatewayAddonApi addonApi;
    private volatile IntSupplier moderatedPlayerCount = () -> 0;
    private final AtomicLong moderatedFramesDropped = new AtomicLong();

    GatewayRuntime(int udpPort, InetSocketAddress agentBind, Map<String, byte[]> agentPsks,
                   BootstrapSessionRegistry bootstrapSessions, Listener listener) {
        this.bootstrapSessions = Objects.requireNonNull(bootstrapSessions, "bootstrapSessions");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.configuredBackends = Set.copyOf(Objects.requireNonNull(agentPsks, "agentPsks").keySet());
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

    synchronized BootstrapGrant grantFor(UUID proxyPlayerId, String playerName, String backendName) {
        if (!isBackendReady(backendName)) return null;
        int port = udp.localPort();
        if (!bootstrapSessions.matches(proxyPlayerId, playerName, backendName, port)) {
            // Revoke the old transport before rotating bootstrap authority. This closes the
            // transfer window where stale media could still route to the previous backend.
            udp.disconnectProxyPlayer(proxyPlayerId);
        }
        return bootstrapSessions.grantFor(proxyPlayerId, playerName, backendName, port);
    }

    int udpPort() {
        return udp.localPort();
    }

    int agentPort() {
        return agents.localPort();
    }

    FederatedGroupDirectory groupDirectory() {
        return groups;
    }

    void setGroupAudioEligibility(Predicate<UUID> eligibility) {
        groupAudioEligibility = Objects.requireNonNull(eligibility, "eligibility");
    }

    void setModeration(Predicate<UUID> transmitEligibility, IntSupplier mutedCount) {
        this.transmitEligibility = Objects.requireNonNull(transmitEligibility, "transmitEligibility");
        this.moderatedPlayerCount = Objects.requireNonNull(mutedCount, "mutedCount");
    }

    synchronized void setAddonApi(GatewayAddonApi addonApi) {
        if (running) throw new IllegalStateException("runtime already started");
        this.addonApi = Objects.requireNonNull(addonApi, "addonApi");
    }

    int hardResetSpeaker(UUID senderId) {
        return udp.hardResetSender(senderId);
    }

    private boolean shareActiveGroup(UUID sender, UUID recipient) {
        Predicate<UUID> eligibility = groupAudioEligibility;
        return eligibility.test(sender) && eligibility.test(recipient) && groups.haveCommonGroup(sender, recipient);
    }

    private boolean shareDirectAudio(UUID sender, UUID recipient) {
        return shareActiveGroup(sender, recipient) || radios.sameChannel(sender, recipient);
    }

    GatewayRadioDirectory.TuneOutcome tuneRadio(UUID playerId, String channel) {
        Set<UUID> before = radios.peers(playerId);
        GatewayRadioDirectory.TuneOutcome outcome = radios.tune(playerId, channel);
        if (outcome.changed()) resetRadioTransitions(playerId, before, radios.peers(playerId));
        return outcome;
    }

    Optional<String> leaveRadio(UUID playerId) {
        Set<UUID> before = radios.peers(playerId);
        Optional<String> previous = radios.leave(playerId);
        if (previous.isPresent()) resetRadioTransitions(playerId, before, Set.of());
        return previous;
    }

    Optional<String> radioChannel(UUID playerId) { return radios.channel(playerId); }
    Set<UUID> radioMembers(String channel) { return radios.members(channel); }

    void pruneRadio(Predicate<UUID> keep) {
        Objects.requireNonNull(keep, "keep");
        for (UUID playerId : radios.tunedPlayers()) if (!keep.test(playerId)) leaveRadio(playerId);
    }

    void removeRadioPlayer(UUID playerId) { leaveRadio(playerId); }

    private void resetRadioTransitions(UUID playerId, Set<UUID> before, Set<UUID> after) {
        HashSet<UUID> changed = new HashSet<>(before);
        changed.addAll(after);
        for (UUID peer : changed) {
            if (before.contains(peer) == after.contains(peer)) continue;
            if (shareActiveGroup(playerId, peer)) continue;
            udp.hardResetPair(peer, playerId);
            udp.hardResetPair(playerId, peer);
        }
    }

    GatewayStatusSnapshot statusSnapshot() {
        GatewayUdpSnapshot metrics = udp.snapshot();
        TreeMap<String, Boolean> backendRoutes = new TreeMap<>();
        for (String backend : configuredBackends) {
            backendRoutes.put(backend, routes.isReady(backend));
        }
        return new GatewayStatusSnapshot(
            running, udp.localPort(), agents.localPort(),
            metrics.activeSessions(), metrics.pendingHandshakes(), backendRoutes,
            metrics.helloRateLimited(), metrics.mediaRateLimited(), metrics.encryptedRejected(),
            metrics.micFramesAccepted(), metrics.soundFramesSent(), metrics.groupFramesSent(), metrics.radioFramesSent(),
            metrics.soundResetsSent(),
            agents.droppedMediaFrames(), agents.queuedMediaMessages(), agents.queuedControlMessages(),
            groups.groupCount(), groups.memberCount(), radios.channelCount(), radios.memberCount(),
            moderatedPlayerCount.getAsInt(), moderatedFramesDropped.get(),
            metrics.sessionsOpened(), metrics.sessionsClosed());
    }

    void disconnectProxyPlayer(UUID proxyPlayerId) {
        udp.disconnectProxyPlayer(proxyPlayerId);
        bootstrapSessions.invalidate(proxyPlayerId);
    }

    @Override
    public void onConnected(UUID proxyPlayerId, String backendName, UUID voiceIdentity) {
        BootstrapSessionRegistry.SessionMaterial material = null;
        try {
            material = bootstrapSessions.findByVoiceIdentity(voiceIdentity);
            if (material == null) return;
            ManagedPlayer player = new ManagedPlayer(proxyPlayerId, backendName, material.playerName());
            GatewayAddonApi api = addonApi;
            if (api != null) api.connected(player.playerId(), player.playerName(), player.backendId());
            BackendRouteRegistry.Route route = routes.readyRoute(backendName);
            if (route != null) route.sendAttach(player);
        } finally {
            if (material != null) material.close();
        }
    }

    @Override
    public void onDisconnected(UUID proxyPlayerId, String backendName, UUID voiceIdentity) {
        GatewayAddonApi api = addonApi;
        if (api != null) api.disconnected(proxyPlayerId);
        BackendRouteRegistry.Route route = routes.readyRoute(backendName);
        if (route != null) route.sendDetach(proxyPlayerId);
    }

    @Override
    public void onMicFrame(UUID proxyPlayerId, String backendName, UUID voiceIdentity,
                           long sequence, byte[] opus) {
        if (!transmitEligibility.test(proxyPlayerId)) {
            moderatedFramesDropped.incrementAndGet();
            return;
        }
        BackendRouteRegistry.Route route = routes.readyRoute(backendName);
        if (route != null) {
            route.sendFrame(new AkashicSpeakerFrame(proxyPlayerId, sequence, false, opus));
        }
        Predicate<UUID> groupEligibility = groupAudioEligibility;
        boolean senderGroupEligible = groupEligibility.test(proxyPlayerId);
        FederatedGroup group = senderGroupEligible ? groups.groupFor(proxyPlayerId).orElse(null) : null;
        Set<UUID> groupMembers = group == null ? Set.of() : group.members();
        for (UUID memberId : groupMembers) {
            if (!memberId.equals(proxyPlayerId) && groupEligibility.test(memberId)) {
                udp.sendGroupFrame(memberId, proxyPlayerId, voiceIdentity, sequence, opus);
            }
        }
        for (UUID memberId : radios.membersForPlayer(proxyPlayerId)) {
            if (memberId.equals(proxyPlayerId)) continue;
            if (senderGroupEligible && groupMembers.contains(memberId) && groupEligibility.test(memberId)) continue;
            udp.sendRadioFrame(memberId, proxyPlayerId, voiceIdentity, sequence, opus);
        }
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
        if (!transmitEligibility.test(delivery.senderId())) {
            moderatedFramesDropped.incrementAndGet();
            return;
        }
        if (shareDirectAudio(delivery.senderId(), delivery.recipientId())) return;
        udp.sendBridgeDelivery(backendName, delivery);
    }

    @Override
    public void onSvcReset(String backendName, BridgeStreamReset reset) {
        if (shareDirectAudio(reset.senderId(), reset.recipientId())) return;
        udp.sendBridgeReset(backendName, reset);
    }

    @Override
    public void onError(String backendName, Throwable error) {
        listener.onError("agent:" + backendName, error);
    }
    @Override
    public synchronized void close() {
        if (!running) {
            radios.clear();
            agents.close();
            udp.close();
            routes.close();
            return;
        }
        running = false;
        radios.clear();
        agents.close();
        udp.close();
        routes.close();
    }
}
