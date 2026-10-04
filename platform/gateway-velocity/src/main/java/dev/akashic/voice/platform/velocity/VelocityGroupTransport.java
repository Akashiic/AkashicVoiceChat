package dev.akashic.voice.platform.velocity;

import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.LegacyChannelIdentifier;
import net.kyori.adventure.text.Component;

import java.util.Objects;
import java.util.UUID;

/** Converts the controller's immutable connection token to the exact current Velocity connection. */
final class VelocityGroupTransport implements GatewayGroupController.Transport {
    private final ProxyServer proxy;
    private final LegacyChannelIdentifier channel;

    VelocityGroupTransport(ProxyServer proxy, LegacyChannelIdentifier channel) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.channel = Objects.requireNonNull(channel, "channel");
    }

    private Player current(UUID id, Object token) {
        Player player = proxy.getPlayer(id).orElse(null);
        return player != null && token instanceof ServerConnection connection
            && GatewayControlRouter.current(player, connection) ? player : null;
    }

    @Override public boolean isCurrent(UUID id, Object token) { return current(id, token) != null; }

    @Override public boolean allowed(UUID id, Object token, String permission) {
        Player player = current(id, token);
        // Ordinary group features preserve the legacy default; explicit denial always wins.
        // Administrative commands use hasPermission and do not inherit this default.
        if (player == null) return false;
        Tristate value = player.getPermissionValue(permission);
        return value == Tristate.TRUE || value == Tristate.UNDEFINED;
    }

    @Override public boolean send(UUID id, Object token, byte[] payload) {
        Player player = current(id, token);
        return player != null && player.sendPluginMessage(channel, payload);
    }

    @Override public void notice(UUID id, Object token, GatewayGroupController.Notice notice) {
        Player player = current(id, token);
        if (player == null) return;
        String message = switch (notice) {
            case NOT_READY -> "Os grupos de voz ainda estão carregando. Tente novamente.";
            case DENIED -> "Você não tem permissão para esta ação no grupo de voz.";
            case INVALID -> "A solicitação do grupo de voz é inválida.";
            case GROUP_UNAVAILABLE -> "Não foi possível alterar o grupo. Verifique seu grupo e os limites.";
            case INVITE_UNAVAILABLE -> "O convite não pôde ser enviado. O jogador precisa estar disponível no voice gateway.";
            case INVITE_SENT -> "Convite para o grupo de voz enviado.";
            case INVITE_EXPIRED -> "O convite expirou, foi revogado ou o grupo está cheio.";
            case JOINED -> "Você entrou no grupo de voz.";
            case LEFT -> "Você saiu do grupo de voz.";
            case STORAGE_FAILED -> "Os grupos de voz estão indisponíveis por uma falha de persistência.";
        };
        player.sendMessage(Component.text("[Akashic Voice] " + message));
    }
}
