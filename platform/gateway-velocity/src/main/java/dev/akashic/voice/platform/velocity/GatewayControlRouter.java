package dev.akashic.voice.platform.velocity;

import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.LegacyChannelIdentifier;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Derives trusted inputs from Velocity objects, then applies the tested forwarding policy. */
final class GatewayControlRouter {
    interface Bootstrap {
        boolean backendReady(String backendName);
        boolean deliver(Player player, ServerConnection connection);
        void abandon(UUID playerId);
    }
    private final LegacyChannelIdentifier channel;
    private final GatewayGroupController groups;
    private final Bootstrap bootstrap;
    private final Set<UUID> knownClients;

    GatewayControlRouter(LegacyChannelIdentifier channel, GatewayGroupController groups,
                         Bootstrap bootstrap, Set<UUID> knownClients) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.groups = Objects.requireNonNull(groups, "groups");
        this.bootstrap = Objects.requireNonNull(bootstrap, "bootstrap");
        this.knownClients = Objects.requireNonNull(knownClients, "knownClients");
    }

    void handle(PluginMessageEvent event) {
        if (!channel.equals(event.getIdentifier()) || !event.getResult().isAllowed()) return;
        // Consume first. Only explicitly approved native paths are forwarded below.
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        Player player;
        ServerConnection connection;
        GatewayControlPolicy.Direction direction;
        if (event.getSource() instanceof Player from && event.getTarget() instanceof ServerConnection to) {
            player = from; connection = to; direction = GatewayControlPolicy.Direction.CLIENT;
        } else if (event.getSource() instanceof ServerConnection from && event.getTarget() instanceof Player to) {
            player = to; connection = from; direction = GatewayControlPolicy.Direction.BACKEND;
        } else {
            return;
        }
        if (!current(player, connection)) return;
        byte[] payload = event.getData();
        int length = payload == null ? 0 : payload.length;
        int tag = length == 0 ? -1 : payload[0] & 0xFF;
        UUID id = player.getUniqueId();
        boolean bound = groups.isBound(id, connection);
        GatewayControlPolicy.Action action = GatewayControlPolicy.classify(true, false, true, direction,
            tag, length, bootstrap.backendReady(connection.getServerInfo().getName()), bound);
        switch (action) {
            case NATIVE -> {
                if (direction == GatewayControlPolicy.Direction.CLIENT && tag == 0) {
                    knownClients.add(id);
                    if (bound) bootstrap.abandon(id);
                    groups.unbind(id, connection);
                }
                if (current(player, connection)) event.setResult(PluginMessageEvent.ForwardResult.forward());
            }
            case BOOTSTRAP -> {
                knownClients.add(id);
                if (bootstrap.deliver(player, connection)) {
                    groups.bind(id, player.getUsername(), connection);
                } else {
                    bootstrap.abandon(id);
                    groups.unbind(id, connection);
                    if (current(player, connection)) event.setResult(PluginMessageEvent.ForwardResult.forward());
                }
            }
            case GROUP -> groups.request(id, connection, payload);
            case DROP, IGNORE -> { }
        }
    }

    static boolean current(Player player, ServerConnection connection) {
        return player.isActive() && connection.getPlayer() == player
            && player.getCurrentServer().orElse(null) == connection;
    }
}
