package dev.jm.proxychat;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import dev.jm.proxychat.voice.client.ClientVoiceBootstrap;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        ClientVoiceBootstrap.install();
        FMLCommonHandler.instance()
            .bus()
            .register(new ClientEvents());
    }
}
