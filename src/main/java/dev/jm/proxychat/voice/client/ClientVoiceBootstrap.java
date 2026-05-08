package dev.jm.proxychat.voice.client;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.NetworkManager;

import dev.jm.proxychat.ProxyChat;

public final class ClientVoiceBootstrap {

    private ClientVoiceBootstrap() {}

    public static void install() {
        ClientSecretCache.setOnAvailable(ClientVoiceBootstrap::onSecretAvailable);
    }

    private static void onSecretAvailable(UUID secret, Integer udpPort) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        EntityPlayer player = mc.thePlayer;
        if (player == null) {
            ProxyChat.LOG.warn("Voice secret arrived but player is null; skipping connect");
            return;
        }
        InetAddress host = resolveServerHost(mc);
        if (host == null) {
            ProxyChat.LOG.warn("Could not resolve voice server host; skipping connect");
            return;
        }
        VoiceClientHolder.connect(player.getUniqueID(), secret, host, udpPort);
    }

    private static InetAddress resolveServerHost(Minecraft mc) {
        try {
            NetHandlerPlayClient handler = mc.getNetHandler();
            if (handler != null) {
                NetworkManager nm = handler.getNetworkManager();
                if (nm != null) {
                    SocketAddress addr = nm.getSocketAddress();
                    if (addr instanceof InetSocketAddress) {
                        return ((InetSocketAddress) addr).getAddress();
                    }
                }
            }
            return InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
