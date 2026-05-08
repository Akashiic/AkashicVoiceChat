package dev.jm.proxychat.net;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class ProxyChatChannel {

    public static final String CHANNEL_NAME = "ProxyChat";

    private static SimpleNetworkWrapper channel;

    private ProxyChatChannel() {}

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL_NAME);

        // C → S
        channel.registerMessage(PacketRequestSecret.Handler.class, PacketRequestSecret.class, 0, Side.SERVER);
        channel.registerMessage(PacketCreateGroup.Handler.class, PacketCreateGroup.class, 2, Side.SERVER);
        channel.registerMessage(PacketInviteToGroup.Handler.class, PacketInviteToGroup.class, 3, Side.SERVER);
        channel.registerMessage(PacketInviteResponse.Handler.class, PacketInviteResponse.class, 4, Side.SERVER);
        channel.registerMessage(PacketLeaveGroup.Handler.class, PacketLeaveGroup.class, 5, Side.SERVER);

        // S → C
        channel.registerMessage(PacketSecret.Handler.class, PacketSecret.class, 1, Side.CLIENT);
        channel.registerMessage(PacketInviteNotify.Handler.class, PacketInviteNotify.class, 6, Side.CLIENT);
        channel.registerMessage(PacketGroupStateSync.Handler.class, PacketGroupStateSync.class, 7, Side.CLIENT);
        channel.registerMessage(PacketRosterSync.Handler.class, PacketRosterSync.class, 8, Side.CLIENT);
    }

    public static SimpleNetworkWrapper get() {
        if (channel == null) {
            throw new IllegalStateException("ProxyChatChannel not initialized");
        }
        return channel;
    }
}
