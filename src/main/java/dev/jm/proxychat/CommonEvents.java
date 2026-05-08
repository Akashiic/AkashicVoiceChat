package dev.jm.proxychat;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.jm.proxychat.voice.server.PlayerStateRegistry;
import dev.jm.proxychat.voice.server.SecretRegistry;
import dev.jm.proxychat.voice.server.VoiceServer;
import dev.jm.proxychat.voice.server.VoiceServerHolder;

public class CommonEvents {

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SecretRegistry.invalidate(event.player.getUniqueID());
        PlayerStateRegistry.remove(event.player.getUniqueID());
        VoiceServer server = VoiceServerHolder.get();
        if (server != null) {
            server.disconnectPlayer(event.player.getUniqueID());
        }
        ProxyChat.LOG.info("Cleaned up voice state for {}", event.player.getCommandSenderName());
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;
        List<EntityPlayerMP> players = server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP p : players) {
            PlayerStateRegistry.update(p.getUniqueID(), p.dimension, p.posX, p.posY, p.posZ);
        }
    }
}
