package dev.jm.proxychat;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import dev.jm.proxychat.voice.server.SecretRegistry;

public class CommonEvents {

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SecretRegistry.invalidate(event.player.getUniqueID());
        ProxyChat.LOG.info("Invalidated voice secret for {}", event.player.getCommandSenderName());
    }
}
