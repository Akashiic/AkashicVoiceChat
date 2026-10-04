package dev.akashic.voice.platform.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

final class GatewayRadioCommand implements SimpleCommand {
    static final String USE = "akashicvoice.radio.use";

    private final Supplier<GatewayRuntime> runtime;

    GatewayRadioCommand(Supplier<GatewayRuntime> runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(USE);
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Component.text("This command is player-only."));
            return;
        }
        GatewayRuntime current = runtime.get();
        if (current == null) {
            player.sendMessage(Component.text("Akashic Voice radio is unavailable."));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            showStatus(player, current);
            return;
        }
        if (("join".equalsIgnoreCase(args[0]) || "tune".equalsIgnoreCase(args[0])) && args.length == 2) {
            tune(player, current, args[1]);
            return;
        }
        if ("leave".equalsIgnoreCase(args[0]) && args.length == 1) {
            Optional<String> left = current.leaveRadio(player.getUniqueId());
            player.sendMessage(Component.text(left.map(channel -> "Left radio " + channel + ".")
                .orElse("You are not tuned to a radio channel.")));
            return;
        }
        player.sendMessage(Component.text("Usage: /avradio <status|join <channel>|leave>"));
    }

    private static void tune(Player player, GatewayRuntime runtime, String channel) {
        GatewayRadioDirectory.TuneOutcome outcome = runtime.tuneRadio(player.getUniqueId(), channel);
        String message = switch (outcome.result()) {
            case JOINED -> "Tuned to radio " + outcome.channel() + ".";
            case SWITCHED -> "Switched radio from " + outcome.previousChannel() + " to " + outcome.channel() + ".";
            case UNCHANGED -> "Already tuned to radio " + outcome.channel() + ".";
            case INVALID -> "Invalid radio name. Use 1-24 letters, numbers, '_' or '-'.";
            case CHANNEL_FULL -> "That radio channel is full.";
            case CHANNEL_LIMIT -> "Radio channel limit reached.";
            case PLAYER_LIMIT -> "Radio player limit reached.";
        };
        player.sendMessage(Component.text(message));
    }

    private static void showStatus(Player player, GatewayRuntime runtime) {
        UUID id = player.getUniqueId();
        Optional<String> channel = runtime.radioChannel(id);
        if (channel.isEmpty()) {
            player.sendMessage(Component.text("Radio: not tuned."));
            return;
        }
        int members = runtime.radioMembers(channel.get()).size();
        player.sendMessage(Component.text("Radio: " + channel.get() + " (" + members + " tuned)"));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) return List.of("status", "join", "leave");
        return List.of();
    }
}
