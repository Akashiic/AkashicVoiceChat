package dev.akashic.voice.platform.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;

import java.util.Objects;
import java.util.function.Supplier;

/** Administrative diagnostics contain counts/enums only, never identities, credentials or paths. */
final class GatewayGroupStatusCommand implements SimpleCommand {
    private final Supplier<GatewayGroupController> supplier;
    GatewayGroupStatusCommand(Supplier<GatewayGroupController> supplier) {
        this.supplier = Objects.requireNonNull(supplier, "supplier");
    }
    @Override public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("akashicvoice.gateway.status");
    }
    @Override public void execute(Invocation invocation) {
        if (!hasPermission(invocation)) return;
        GatewayGroupController controller = supplier.get();
        if (controller == null) {
            invocation.source().sendMessage(Component.text("Akashic Voice groups: UNAVAILABLE"));
            return;
        }
        String[] args = invocation.arguments();
        if (args.length == 1 && args[0].equalsIgnoreCase("retry")) {
            if (!invocation.source().hasPermission("akashicvoice.groups.admin")) {
                invocation.source().sendMessage(Component.text("Permissão necessária: akashicvoice.groups.admin"));
                return;
            }
            invocation.source().sendMessage(Component.text("Group save retry scheduled: " + controller.retrySave()));
            return;
        }
        if (args.length > 1 || args.length == 1 && !args[0].equalsIgnoreCase("status")) {
            invocation.source().sendMessage(Component.text("Uso: /avgroups [status|retry]"));
            return;
        }
        GatewayGroupController.Status state = controller.status();
        var storage = state.persistence();
        invocation.source().sendMessage(Component.text("Akashic Voice groups: " + storage.state()
            + " | groups=" + state.groups() + " members=" + state.members() + " bindings=" + state.bindings()
            + " invites=" + state.pendingInvites() + " closing=" + state.closing()));
        invocation.source().sendMessage(Component.text("Persistence: requested=" + storage.requestedRevision()
            + " persisted=" + storage.persistedRevision() + " dirty=" + storage.dirty()
            + " failedWrites=" + storage.failedWrites() + " lastFailure=" + storage.lastFailure()));
        invocation.source().sendMessage(Component.text("Control: accepted=" + state.accepted()
            + " rejected=" + state.rejected() + " rateLimited=" + state.rateLimited()
            + " sendFailed=" + state.deliveryFailures() + " rosterTruncated=" + state.rosterTruncations()));
    }
}
