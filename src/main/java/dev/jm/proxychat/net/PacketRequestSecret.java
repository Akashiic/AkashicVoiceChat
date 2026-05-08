package dev.jm.proxychat.net;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import dev.jm.proxychat.ProxyChat;
import dev.jm.proxychat.config.ProxyChatConfig;
import dev.jm.proxychat.voice.server.SecretRegistry;
import io.netty.buffer.ByteBuf;

public class PacketRequestSecret implements IMessage {

    public PacketRequestSecret() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<PacketRequestSecret, PacketSecret> {

        @Override
        public PacketSecret onMessage(PacketRequestSecret msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            UUID playerId = player.getUniqueID();
            UUID secret = SecretRegistry.getOrCreate(playerId);
            int port = ProxyChatConfig.voicePort();

            ProxyChat.LOG.info("Issued voice secret to {} (port {})", player.getCommandSenderName(), port);
            return new PacketSecret(secret, port);
        }
    }
}
