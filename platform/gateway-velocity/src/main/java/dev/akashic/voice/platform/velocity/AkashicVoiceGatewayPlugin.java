package dev.akashic.voice.platform.velocity;

import com.google.inject.Inject;
import dev.akashic.voice.api.AkashicVoiceApi;
import dev.akashic.voice.api.AkashicVoiceApiProvider;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.LegacyChannelIdentifier;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Duration;
import dev.akashic.voice.platform.bridge.FederatedGroupPersistence;
import dev.akashic.voice.platform.bridge.FederatedGroupStore;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(
    id = "akashicvoicegateway",
    name = "Akashic Voice Gateway",
    version = "0.1.0-SNAPSHOT",
    authors = {"RedeAkashic"}
)
public final class AkashicVoiceGatewayPlugin implements GatewayRuntime.Listener, AkashicVoiceApiProvider {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final BootstrapSessionRegistry bootstrapSessions = new BootstrapSessionRegistry();
    private final Set<UUID> akashicClients = ConcurrentHashMap.newKeySet();
    private final GatewayAddonApi addonApi;

    private LegacyChannelIdentifier channel;
    private CommandMeta statusCommandMeta;
    private CommandMeta moderationCommandMeta;
    private CommandMeta radioCommandMeta;
    private volatile GatewayRuntime runtime;
    private volatile GatewayModerationService moderation;
    private volatile GatewayGroupController groupController;
    private volatile GatewayControlRouter controlRouter;
    private ScheduledTask groupMaintenance;
    private CommandMeta groupCommandMeta;
    private volatile boolean shuttingDown;
    private volatile FederatedGroupPersistence.Failure reportedGroupFailure = FederatedGroupPersistence.Failure.NONE;

    @Inject
    public AkashicVoiceGatewayPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.addonApi = new GatewayAddonApi(error -> logger.warn("Akashic Voice addon listener failed", error));
    }

    @Override
    public AkashicVoiceApi akashicVoiceApi() {
        return addonApi;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        channel = new LegacyChannelIdentifier(LegacyForgeBootstrapCodec.CHANNEL);
        proxy.getChannelRegistrar().register(channel);
        statusCommandMeta = proxy.getCommandManager().metaBuilder("avgateway").plugin(this).build();
        proxy.getCommandManager().register(statusCommandMeta, new GatewayStatusCommand(this::statusSnapshot));
        try (GatewaySettings settings = GatewaySettings.load(dataDirectory)) {
            Map<String, byte[]> psks = settings.agentPsksCopy();
            try {
                GatewayModerationService moderationCreated = new GatewayModerationService(
                    new GatewayModerationStore(dataDirectory.resolve("moderation.dat")));
                GatewayRuntime created = new GatewayRuntime(
                    settings.udpPort(), settings.agentBind(), psks,
                    bootstrapSessions, this);
                addonApi.activate(moderationCreated::isMuted, moderationCreated::revision);
                created.setModeration(id -> !moderationCreated.isMuted(id), moderationCreated::mutedCount);
                created.setAddonApi(addonApi);
                created.start();
                moderation = moderationCreated;
                runtime = created;
                moderationCommandMeta = proxy.getCommandManager().metaBuilder("avvoice").plugin(this).build();
                proxy.getCommandManager().register(moderationCommandMeta,
                    new GatewayModerationCommand(proxy, () -> moderation, () -> runtime, addonApi));
                radioCommandMeta = proxy.getCommandManager().metaBuilder("avradio").plugin(this).build();
                proxy.getCommandManager().register(radioCommandMeta, new GatewayRadioCommand(() -> runtime));
                installGroupControls(created);
                logger.info("Akashic Voice Gateway started: UDP {}, agent {}:{}, backends {}",
                    created.udpPort(), settings.agentBind().getAddress().getHostAddress(),
                    created.agentPort(), settings.allowedBackends());
            } finally {
                zeroPsks(psks);
            }
        } catch (Exception failure) {
            if (moderationCommandMeta != null) {
                proxy.getCommandManager().unregister(moderationCommandMeta);
                moderationCommandMeta = null;
            }
            if (radioCommandMeta != null) {
                proxy.getCommandManager().unregister(radioCommandMeta);
                radioCommandMeta = null;
            }
            GatewayRuntime failedRuntime = runtime;
            runtime = null;
            moderation = null;
            addonApi.deactivate();
            controlRouter = null;
            GatewayGroupController failedGroups = groupController;
            if (failedGroups != null) failedGroups.requestClose();
            if (groupMaintenance != null) groupMaintenance.cancel();
            if (failedRuntime != null) failedRuntime.close();
            logger.error("Akashic Voice Gateway runtime failed; plugin messaging remains fail-open", failure);
        }
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        GatewayControlRouter router = controlRouter;
        if (router != null) router.handle(event);
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        if (event.getPreviousServer() == null) return;
        Player player = event.getPlayer();
        GatewayGroupController groups = groupController;
        if (groups != null) groups.disconnected(player.getUniqueId());
        GatewayRuntime current = runtime;
        if (current != null) {
            current.disconnectProxyPlayer(player.getUniqueId());
        } else {
            bootstrapSessions.invalidate(player.getUniqueId());
        }
        if (akashicClients.contains(player.getUniqueId())) sendRebootstrap(player);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Player registered = proxy.getPlayer(playerId).orElse(null);
        if (registered != null && registered != event.getPlayer()) return;
        GatewayGroupController groups = groupController;
        if (groups != null) groups.disconnected(playerId);
        akashicClients.remove(playerId);
        GatewayRuntime current = runtime;
        if (current != null) {
            current.removeRadioPlayer(playerId);
            current.disconnectProxyPlayer(playerId);
        } else {
            bootstrapSessions.invalidate(playerId);
        }
    }

    @Subscribe
    public EventTask onShutdown(ProxyShutdownEvent event) {
        shuttingDown = true;
        if (groupMaintenance != null) groupMaintenance.cancel();
        GatewayGroupController groups = groupController;
        if (groups != null) groups.requestClose();
        controlRouter = null;
        GatewayRuntime current = runtime;
        runtime = null;
        moderation = null;
        addonApi.deactivate();
        akashicClients.clear();
        if (channel != null) { proxy.getChannelRegistrar().unregister(channel); channel = null; }
        if (statusCommandMeta != null) proxy.getCommandManager().unregister(statusCommandMeta);
        if (groupCommandMeta != null) proxy.getCommandManager().unregister(groupCommandMeta);
        if (moderationCommandMeta != null) proxy.getCommandManager().unregister(moderationCommandMeta);
        if (radioCommandMeta != null) proxy.getCommandManager().unregister(radioCommandMeta);
        return EventTask.async(() -> {
            try {
                if (current != null) current.close();
            } finally {
                bootstrapSessions.close();
                if (groups != null) {
                    try {
                        boolean stopped = groups.awaitStopped(Duration.ofSeconds(5));
                        var status = groups.status().persistence();
                        if (!stopped || status.dirty() || status.state() == FederatedGroupPersistence.State.FAILED) {
                            logger.error("Group persistence did not close cleanly: stopped={}, state={}, dirty={}, failure={}",
                                stopped, status.state(), status.dirty(), status.lastFailure());
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        logger.error("Interrupted while draining group persistence");
                    }
                }
            }
        });
    }

    private GatewayStatusSnapshot statusSnapshot() {
        GatewayRuntime current = runtime;
        return current == null ? null : current.statusSnapshot();
    }

    @Override
    public void onRouteUp(String backendName) {
        if (shuttingDown) return;
        logger.info("Akashic Voice agent route is ready for backend {}", backendName);
        proxy.getScheduler().buildTask(this, () -> rebootstrapMissingSessions(backendName)).schedule();
    }

    @Override
    public void onRouteDown(String backendName) {
        logger.warn("Akashic Voice agent route is unavailable for backend {}; existing UDP sessions are retained",
            backendName);
    }

    @Override
    public void onError(String component, Throwable error) {
        logger.warn("Akashic Voice {} error", component, error);
    }

    private void rebootstrapMissingSessions(String backendName) {
        GatewayRuntime current = runtime;
        if (current == null || !current.isBackendReady(backendName)) return;
        for (Player player : proxy.getAllPlayers()) {
            if (!akashicClients.contains(player.getUniqueId())) continue;
            String currentBackend = player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse(null);
            if (!backendName.equals(currentBackend)) continue;
            if (current.hasVoiceSession(player.getUniqueId(), backendName)) continue;
            sendRebootstrap(player);
        }
    }
    private void sendRebootstrap(Player player) {
        LegacyChannelIdentifier currentChannel = channel;
        if (currentChannel == null) return;
        byte[] payload = LegacyForgeBootstrapCodec.encodeRebootstrap();
        if (!player.sendPluginMessage(currentChannel, payload)) {
            logger.debug("Akashic Voice control message was not delivered to {}", player.getUsername());
        }
    }

    private void installGroupControls(GatewayRuntime current) {
        GatewayGroupController groups = new GatewayGroupController(current.groupDirectory(),
            new FederatedGroupPersistence(current.groupDirectory(),
                new FederatedGroupStore(dataDirectory.resolve("federated-groups.dat"))),
            new VelocityGroupTransport(proxy, channel));
        groupController = groups;
        current.setGroupAudioEligibility(groups::allowsGroupAudio);
        controlRouter = new GatewayControlRouter(channel, groups, new GatewayControlRouter.Bootstrap() {
            @Override public boolean backendReady(String backendName) {
                GatewayRuntime live = runtime;
                return live != null && live.isBackendReady(backendName);
            }
            @Override public boolean deliver(Player player, ServerConnection connection) {
                return deliverBootstrap(player, connection);
            }
            @Override public void abandon(UUID playerId) {
                GatewayRuntime live = runtime;
                if (live != null) live.disconnectProxyPlayer(playerId);
                else bootstrapSessions.invalidate(playerId);
            }
        }, akashicClients);
        groupCommandMeta = proxy.getCommandManager().metaBuilder("avgroups").plugin(this).build();
        proxy.getCommandManager().register(groupCommandMeta, new GatewayGroupStatusCommand(() -> groupController));
        groups.start();
        groupMaintenance = proxy.getScheduler().buildTask(this, this::maintainGroups)
            .repeat(Duration.ofSeconds(1)).schedule();
    }

    private void maintainGroups() {
        if (shuttingDown) return;
        GatewayRuntime live = runtime;
        if (live != null) {
            live.pruneRadio(id -> proxy.getPlayer(id)
                .map(player -> player.hasPermission(GatewayRadioCommand.USE))
                .orElse(false));
        }
        GatewayGroupController groups = groupController;
        if (groups == null || groups.status().closing()) return;
        try {
            groups.tick();
            var failure = groups.status().persistence().lastFailure();
            if (failure != reportedGroupFailure) {
                reportedGroupFailure = failure;
                if (failure != FederatedGroupPersistence.Failure.NONE) {
                    logger.error("Group persistence requires attention: {}. Check /avgroups status", failure);
                }
            }
        } catch (RuntimeException failure) {
            groups.requestClose();
            logger.error("Group control maintenance failed; group mutations disabled", failure);
        }
    }

    private boolean deliverBootstrap(Player player, ServerConnection target) {
        GatewayRuntime current = runtime;
        LegacyChannelIdentifier currentChannel = channel;
        if (current == null || currentChannel == null || !GatewayControlRouter.current(player, target)) return false;
        BootstrapGrant grant = current.grantFor(player.getUniqueId(), player.getUsername(), target.getServerInfo().getName());
        if (grant == null) return false;
        byte[] secret = grant.masterSecretCopy();
        try {
            byte[] response = LegacyForgeBootstrapCodec
                .encodeResponse(grant.voiceIdentity(), secret, grant.udpPort(), grant.sessionId());
            return GatewayControlRouter.current(player, target) && player.sendPluginMessage(currentChannel, response);
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    private static void zeroPsks(Map<String, byte[]> psks) {
        for (byte[] secret : psks.values()) {
            if (secret != null) Arrays.fill(secret, (byte) 0);
        }
        psks.clear();
    }
}
