package dev.jm.proxychat;

import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import dev.jm.proxychat.voice.client.ClientVoiceBootstrap;
import dev.jm.proxychat.voice.client.keybinds.ProxyChatKeybinds;
import dev.jm.proxychat.voice.client.render.SpeakerOverlay;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        ProxyChatKeybinds.register();
        ClientVoiceBootstrap.install();
        MinecraftForge.EVENT_BUS.register(new SpeakerOverlay());
        FMLCommonHandler.instance()
            .bus()
            .register(new ClientEvents());
    }
}
