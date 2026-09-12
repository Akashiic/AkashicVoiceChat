package dev.akashic.voice.platform.svc;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import dev.akashic.voice.platform.bridge.DisconnectedGatewayLink;
import dev.akashic.voice.platform.bridge.GatewayLink;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

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
            getLogger().severe("Simple Voice Chat service is unavailable; disabling bridge");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        String backendId = getConfig().getString("backend-id", "lobby");
        gateway = new DisconnectedGatewayLink();
        engine = new SvcBridgeEngine(gateway, backendId, new BukkitPositionSnapshots());
        voicechatService.registerPlugin(new AkashicSvcPlugin(engine));
        try {
            gateway.start(engine);
        } catch (Exception e) {
            getLogger().severe("Failed to start Akashic Gateway link: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        reconcileTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            try {
                engine.tickMainThread();
            } catch (RuntimeException e) {
                getLogger().warning("Bridge reconciliation failed: " + e.getMessage());
            }
        }, 1L, 1L);

        getLogger().warning(
            "Gateway transport is not implemented in this milestone; SVC adapter is loaded but disconnected");
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
}
