package dev.akashic.voice.platform.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

final class GatewayModerationCommand implements SimpleCommand {
    static final String PERMISSION = "akashicvoice.moderation.admin";
    private final ProxyServer proxy;
    private final Supplier<GatewayModerationService> moderation;
    private final Supplier<GatewayRuntime> runtime;
    private final GatewayAddonApi addonApi;

    GatewayModerationCommand(ProxyServer proxy, Supplier<GatewayModerationService> moderation,
                             Supplier<GatewayRuntime> runtime, GatewayAddonApi addonApi) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.moderation = Objects.requireNonNull(moderation, "moderation");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.addonApi = Objects.requireNonNull(addonApi, "addonApi");
    }
    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PERMISSION);
    }

    @Override
    public void execute(Invocation invocation) {
        if (!hasPermission(invocation)) return;
        GatewayModerationService service = moderation.get();
        if (service == null) {
            invocation.source().sendMessage(Component.text("Akashic Voice moderation unavailable", NamedTextColor.RED));
            return;
        }
        String[] args = invocation.arguments();
        if (args.length == 0 || args.length == 1 && args[0].equalsIgnoreCase("status")) {
            invocation.source().sendMessage(Component.text(
                "Akashic Voice moderation: muted=" + service.mutedCount() + " revision=" + service.revision(),
                NamedTextColor.AQUA));
            return;
        }
        if (args.length != 2) {
            usage(invocation);
            return;
        }
        UUID target = resolve(args[1]);
        if (target == null) {
            invocation.source().sendMessage(Component.text("Player/UUID não encontrado: " + args[1], NamedTextColor.RED));
            return;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        try {
            if (action.equals("mute")) {
                boolean changed = service.mute(target);
                if (changed) {
                    GatewayRuntime live = runtime.get();
                    int resets = live != null ? live.hardResetSpeaker(target) : 0;
                    addonApi.moderationChanged(target, true, service.revision());
                    notifyTarget(target, true);
                    invocation.source().sendMessage(Component.text(
                        "Voice server-mute aplicado: " + target + " | resets=" + resets, NamedTextColor.GREEN));
                } else {
                    invocation.source().sendMessage(Component.text("Player já está server-muted", NamedTextColor.YELLOW));
                }
                return;
            }
            if (action.equals("unmute")) {
                boolean changed = service.unmute(target);
                if (changed) {
                    addonApi.moderationChanged(target, false, service.revision());
                    notifyTarget(target, false);
                }
                invocation.source().sendMessage(Component.text(
                    changed ? "Voice server-mute removido: " + target : "Player não estava server-muted",
                    changed ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                return;
            }
            if (action.equals("check")) {
                invocation.source().sendMessage(Component.text(
                    target + " muted=" + service.isMuted(target), NamedTextColor.AQUA));
                return;
            }
            usage(invocation);
        } catch (IOException failure) {
            invocation.source().sendMessage(Component.text(
                "Falha persistindo moderação; estado anterior preservado", NamedTextColor.RED));
        }
    }
    private UUID resolve(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return proxy.getPlayer(value).map(Player::getUniqueId).orElse(null);
        }
    }

    private void notifyTarget(UUID playerId, boolean muted) {
        Optional<Player> target = proxy.getPlayer(playerId);
        if (target.isEmpty()) return;
        target.get().sendMessage(Component.text(
            muted ? "Sua transmissão de voz foi silenciada pela moderação."
                : "Sua transmissão de voz foi reativada pela moderação.",
            muted ? NamedTextColor.RED : NamedTextColor.GREEN));
    }

    private static void usage(Invocation invocation) {
        invocation.source().sendMessage(Component.text(
            "Uso: /avvoice [status|mute <player|uuid>|unmute <player|uuid>|check <player|uuid>]",
            NamedTextColor.YELLOW));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) return List.of("status", "mute", "unmute", "check");
        if (args.length != 2) return List.of();
        String prefix = args[1].toLowerCase(Locale.ROOT);
        ArrayList<String> names = new ArrayList<>();
        for (Player player : proxy.getAllPlayers()) {
            if (player.getUsername().toLowerCase(Locale.ROOT).startsWith(prefix)) names.add(player.getUsername());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }
}
