package dev.akashic.voice;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import dev.akashic.voice.command.AvDebugCommand;
import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.net.AkashicVoiceChannel;
import dev.akashic.voice.voice.server.SecretRegistry;
import dev.akashic.voice.voice.server.VoiceServerHolder;
import dev.akashic.voice.voice.server.groups.GroupRegistryHolder;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        AkashicVoiceConfig.load(event.getSuggestedConfigurationFile());
        AkashicVoiceChannel.init();
        AkashicVoice.LOG
            .info("Akashic Voice preInit (version {}, voicePort={})", Tags.VERSION, AkashicVoiceConfig.voicePort());
    }

    public void init(FMLInitializationEvent event) {
        FMLCommonHandler.instance()
            .bus()
            .register(new CommonEvents());
    }

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new AvDebugCommand());
        VoiceServerHolder.start(AkashicVoiceConfig.voicePort());
    }

    public void serverStarted(FMLServerStartedEvent event) {
        // World data is available now — load (or create) the group registry.
        GroupRegistryHolder.initFromOverworld();
    }

    public void serverStopping(FMLServerStoppingEvent event) {
        VoiceServerHolder.stop();
        SecretRegistry.clear();
        GroupRegistryHolder.clear();
    }
}
