package dev.akashic.voice.voice.server.groups;

import net.minecraft.server.MinecraftServer;

public final class GroupRegistryHolder {

    private static volatile GroupRegistry instance;

    private GroupRegistryHolder() {}

    public static synchronized void initFromOverworld() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;
        instance = GroupRegistry.loadFor(server.worldServerForDimension(0));
    }

    public static synchronized void clear() {
        instance = null;
    }

    public static GroupRegistry get() {
        return instance;
    }
}
