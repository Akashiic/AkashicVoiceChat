package dev.akashic.voice;

import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import dev.akashic.voice.voice.client.ClientVoiceBootstrap;
import dev.akashic.voice.voice.client.keybinds.AkashicVoiceKeybinds;
import dev.akashic.voice.voice.client.keybinds.PttInputBridge;
import dev.akashic.voice.voice.client.render.MicHudOverlay;
import dev.akashic.voice.voice.client.render.SpeakerOverlay;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        AkashicVoiceKeybinds.register();
        ClientVoiceBootstrap.install();
        MinecraftForge.EVENT_BUS.register(new SpeakerOverlay());
        MinecraftForge.EVENT_BUS.register(new MicHudOverlay());
        PttInputBridge pttInput = new PttInputBridge();
        MinecraftForge.EVENT_BUS.register(pttInput);
        FMLCommonHandler.instance()
            .bus()
            .register(pttInput);
        FMLCommonHandler.instance()
            .bus()
            .register(new ClientEvents());
    }
}
