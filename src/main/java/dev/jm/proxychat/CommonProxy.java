package dev.jm.proxychat;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import dev.jm.proxychat.command.PcDebugCommand;
import dev.jm.proxychat.config.ProxyChatConfig;
import dev.jm.proxychat.net.ProxyChatChannel;
import dev.jm.proxychat.voice.server.VoiceServerHolder;
import dev.jm.proxychat.voice.server.groups.GroupRegistryHolder;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        ProxyChatConfig.load(event.getSuggestedConfigurationFile());
        ProxyChatChannel.init();
        ProxyChat.LOG.info("ProxyChat preInit (version {}, voicePort={})", Tags.VERSION, ProxyChatConfig.voicePort());
    }

    public void init(FMLInitializationEvent event) {
        FMLCommonHandler.instance()
            .bus()
            .register(new CommonEvents());
    }

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new PcDebugCommand());
        VoiceServerHolder.start(ProxyChatConfig.voicePort());
    }

    public void serverStarted(FMLServerStartedEvent event) {
        // World data is available now — load (or create) the group registry.
        GroupRegistryHolder.initFromOverworld();
    }

    public void serverStopping(FMLServerStoppingEvent event) {
        VoiceServerHolder.stop();
        GroupRegistryHolder.clear();
    }
}
