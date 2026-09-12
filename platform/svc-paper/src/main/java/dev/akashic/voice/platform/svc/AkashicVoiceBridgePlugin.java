package dev.akashic.voice.platform.svc;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import dev.akashic.voice.platform.agent.AgentHandshake;
import dev.akashic.voice.platform.bridge.DisconnectedGatewayLink;
import dev.akashic.voice.platform.bridge.GatewayLink;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.Base64;

public final class AkashicVoiceBridgePlugin extends JavaPlugin {
    private GatewayLink gateway;
    private SvcBridgeEngine engine;
    private BukkitTask reconcileTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        BukkitVoicechatService voicechatService = getServer().getServicesManager()
            .load(BukkitVoicechatService.class);
        if (voicechatService == null) {
            failEnable("Simple Voice Chat service is unavailable");
            return;
        }

        String backendId = getConfig().getString("backend-id", "lobby");
        if (!AgentHandshake.isValidBackendId(backendId)) {
            failEnable("Invalid backend-id: " + backendId);
            return;
        }
        boolean gatewayEnabled = getConfig().getBoolean("gateway.enabled", false);
        if (gatewayEnabled) {
            String host = getConfig().getString("gateway.host", "127.0.0.1");
            int port = getConfig().getInt("gateway.port", 24_456);
            byte[] psk;
            try {
                psk = decodePsk(getConfig().getString("gateway.psk-base64", ""));
            } catch (IllegalArgumentException e) {
                failEnable(e.getMessage());
                return;
            }
            try {
                gateway = new SocketGatewayLink(host, port, backendId, psk);
            } finally {
                Arrays.fill(psk, (byte) 0);
            }
        } else {
            gateway = new DisconnectedGatewayLink();
        }

        engine = new SvcBridgeEngine(gateway, backendId, new BukkitPositionSnapshots());
        voicechatService.registerPlugin(new AkashicSvcPlugin(engine));
        try {
            gateway.start(engine);
        } catch (Exception e) {
            failEnable("Failed to start Akashic Gateway link: " + e.getMessage());
            return;
        }
        reconcileTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            try {
                engine.tickMainThread();
            } catch (RuntimeException e) {
                getLogger().warning("Bridge reconciliation failed: " + e.getMessage());
            }
        }, 1L, 1L);

        if (gatewayEnabled) {
            getLogger().info("Akashic Voice SVC bridge enabled for backend " + backendId);
        } else {
            getLogger().warning("Gateway transport is disabled; SVC adapter is loaded but disconnected");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 1 || (args.length == 1 && !"status".equalsIgnoreCase(args[0]))) return false;
        SvcBridgeEngine current = engine;
        if (current == null) {
            sender.sendMessage("Akashic Voice SVC bridge is unavailable");
            return true;
        }
        SvcBridgeStatus status = current.statusSnapshot();
        sender.sendMessage("Akashic Voice SVC Bridge");
        sender.sendMessage("Gateway: " + (status.gatewayConnected() ? "CONNECTED" : "DISCONNECTED")
            + " | SVC API: " + (status.voicechatBound() ? "BOUND" : "UNBOUND"));
        sender.sendMessage("Players: desired=" + status.desiredPlayers()
            + " active=" + status.activeSessions()
            + " bindings=" + status.identityBindings());
        sender.sendMessage("Transport: dropped=" + status.gatewayMediaDropped()
            + " mediaQ=" + status.gatewayMediaQueued()
            + " controlQ=" + status.gatewayControlQueued());
        return true;
    }

    @Override
    public void onDisable() {
        if (reconcileTask != null) {
            reconcileTask.cancel();
            reconcileTask = null;
        }
        if (engine != null) {
            engine.close();
            engine = null;
        }
        if (gateway != null) {
            gateway.close();
            gateway = null;
        }
    }

    private byte[] decodePsk(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("gateway.psk-base64 is required when gateway.enabled=true");
        }
        byte[] psk;
        try {
            psk = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("gateway.psk-base64 is not valid Base64", e);
        }
        if (psk.length != 32) {
            Arrays.fill(psk, (byte) 0);
            throw new IllegalArgumentException("gateway.psk-base64 must decode to exactly 32 bytes");
        }
        return psk;
    }

    private void failEnable(String message) {
        getLogger().severe(message + "; disabling bridge");
        getServer().getPluginManager().disablePlugin(this);
    }
}
