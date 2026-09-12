package dev.akashic.voice.platform.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
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
public final class AkashicVoiceGatewayPlugin implements GatewayRuntime.Listener {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final BootstrapSessionRegistry bootstrapSessions = new BootstrapSessionRegistry();
    private final Set<UUID> akashicClients = ConcurrentHashMap.newKeySet();

    private LegacyChannelIdentifier channel;
    private volatile GatewayRuntime runtime;

    @Inject
    public AkashicVoiceGatewayPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        channel = new LegacyChannelIdentifier(LegacyForgeBootstrapCodec.CHANNEL);
        proxy.getChannelRegistrar().register(channel);
        try (GatewaySettings settings = GatewaySettings.load(dataDirectory)) {
            Map<String, byte[]> psks = settings.agentPsksCopy();
            try {
                GatewayRuntime created = new GatewayRuntime(
                    settings.udpPort(), settings.agentBind(), psks,
                    bootstrapSessions, this);
                created.start();
                runtime = created;
                logger.info("Akashic Voice Gateway started: UDP {}, agent {}:{}, backends {}",
                    created.udpPort(), settings.agentBind().getAddress().getHostAddress(),
                    created.agentPort(), settings.allowedBackends());
            } finally {
                zeroPsks(psks);
            }
        } catch (Exception failure) {
            logger.error("Akashic Voice Gateway runtime failed; plugin messaging remains fail-open", failure);
        }
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        LegacyChannelIdentifier currentChannel = channel;
        if (currentChannel == null || !currentChannel.equals(event.getIdentifier())) return;
        if (!(event.getSource() instanceof Player player)) return;
        if (!(event.getTarget() instanceof ServerConnection target)) return;
        byte[] data = event.getData();
        if (!LegacyForgeBootstrapCodec.isBootstrapRequest(data)) return;
        akashicClients.add(player.getUniqueId());

        GatewayRuntime current = runtime;
        String backendName = target.getServerInfo().getName();
        if (current == null || !current.isBackendReady(backendName)) return;

        BootstrapGrant grant = current.grantFor(
            player.getUniqueId(), player.getUsername(), backendName);
        if (grant == null) return;
        byte[] secret = grant.masterSecretCopy();
        try {
            byte[] response = LegacyForgeBootstrapCodec.encodeResponse(
                grant.voiceIdentity(), secret, grant.udpPort());
            if (player.sendPluginMessage(currentChannel, response)) {
                event.setResult(PluginMessageEvent.ForwardResult.handled());
            } else {
                logger.warn("Could not deliver Akashic Voice bootstrap to {}", player.getUsername());
            }
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        if (event.getPreviousServer() == null) return;
        Player player = event.getPlayer();
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
        akashicClients.remove(playerId);
        GatewayRuntime current = runtime;
        if (current != null) {
            current.disconnectProxyPlayer(playerId);
        } else {
            bootstrapSessions.invalidate(playerId);
        }
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        GatewayRuntime current = runtime;
        runtime = null;
        if (current != null) current.close();
        akashicClients.clear();
        if (channel != null) {
            proxy.getChannelRegistrar().unregister(channel);
            channel = null;
        }
        bootstrapSessions.close();
    }

    @Override
    public void onRouteUp(String backendName) {
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

    private static void zeroPsks(Map<String, byte[]> psks) {
        for (byte[] secret : psks.values()) {
            if (secret != null) Arrays.fill(secret, (byte) 0);
        }
        psks.clear();
    }
}
