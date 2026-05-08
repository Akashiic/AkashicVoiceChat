package dev.jm.proxychat;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import dev.jm.proxychat.net.PacketRequestSecret;
import dev.jm.proxychat.net.ProxyChatChannel;
import dev.jm.proxychat.voice.client.ClientSecretCache;

public class ClientEvents {

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        ClientSecretCache.clear();
        ProxyChat.LOG.info("Requesting voice secret from server");
        ProxyChatChannel.get()
            .sendToServer(new PacketRequestSecret());
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ClientSecretCache.clear();
    }
}
