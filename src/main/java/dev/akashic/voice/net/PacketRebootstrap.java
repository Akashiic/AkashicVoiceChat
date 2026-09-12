package dev.akashic.voice.net;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import dev.akashic.voice.voice.client.ClientVoiceBootstrap;
import io.netty.buffer.ByteBuf;

public final class PacketRebootstrap implements IMessage {

    public PacketRebootstrap() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static final class Handler implements IMessageHandler<PacketRebootstrap, IMessage> {

        @Override
        public IMessage onMessage(PacketRebootstrap message, MessageContext context) {
            ClientVoiceBootstrap.requestRebootstrap();
            return null;
        }
    }
}
