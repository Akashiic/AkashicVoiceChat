package dev.akashic.voice.platform.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

final class GatewayStatusCommand implements SimpleCommand {
    private static final String PERMISSION = "akashicvoice.gateway.status";
    private final Supplier<GatewayStatusSnapshot> snapshotSupplier;

    GatewayStatusCommand(Supplier<GatewayStatusSnapshot> snapshotSupplier) {
        this.snapshotSupplier = snapshotSupplier;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length > 1 || (args.length == 1 && !"status".equalsIgnoreCase(args[0]))) {
            invocation.source().sendMessage(Component.text("Usage: /avgateway status", NamedTextColor.YELLOW));
            return;
        }
        GatewayStatusSnapshot status = snapshotSupplier.get();
        if (status == null) {
            invocation.source().sendMessage(Component.text("Akashic Voice Gateway runtime unavailable", NamedTextColor.RED));
            return;
        }

        String routes = status.backendRoutes().entrySet().stream()
            .map(entry -> entry.getKey() + "=" + (entry.getValue() ? "UP" : "DOWN"))
            .collect(Collectors.joining(", "));

        invocation.source().sendMessage(Component.text("Akashic Voice Gateway", NamedTextColor.AQUA));
        invocation.source().sendMessage(Component.text(
            "Runtime: " + (status.running() ? "ONLINE" : "OFFLINE")
                + " | UDP " + status.udpPort() + " | Agent " + status.agentPort()));
        invocation.source().sendMessage(Component.text(
            "Sessions: active=" + status.activeUdpSessions()
                + " pending=" + status.pendingHandshakes()
                + " opened=" + status.sessionsOpened()
                + " closed=" + status.sessionsClosed()));
        invocation.source().sendMessage(Component.text("Routes: " + (routes.isEmpty() ? "none" : routes)));
        invocation.source().sendMessage(Component.text(
            "Media: micIn=" + status.micFramesAccepted()
                + " svcOut=" + status.soundFramesSent()
                + " groupOut=" + status.groupFramesSent()
                + " radioOut=" + status.radioFramesSent()
                + " resets=" + status.soundResetsSent()));
        invocation.source().sendMessage(Component.text(
            "Groups: active=" + status.federatedGroups()
                + " members=" + status.federatedMembers()
                + " | Radio: channels=" + status.radioChannels()
                + " tuned=" + status.radioMembers()));
        invocation.source().sendMessage(Component.text(
            "Moderation: muted=" + status.moderatedPlayers()
                + " droppedFrames=" + status.moderatedFramesDropped()));
        invocation.source().sendMessage(Component.text(
            "Agent queue: dropped=" + status.agentMediaDropped()
                + " mediaQ=" + status.agentMediaQueued()
                + " controlQ=" + status.agentControlQueued()));
        invocation.source().sendMessage(Component.text(
            "Rejected: helloRate=" + status.helloRateLimited()
                + " mediaRate=" + status.mediaRateLimited()
                + " encrypted=" + status.encryptedRejected()));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PERMISSION);
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length <= 1) return List.of("status");
        return List.of();
    }
}
