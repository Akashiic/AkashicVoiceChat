package dev.akashic.voice.net;

import java.util.Arrays;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import dev.akashic.voice.AkashicVoice;
import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.server.SecretRegistry;
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
            byte[] secret = SecretRegistry.getOrCreate(playerId);
            int port = AkashicVoiceConfig.voicePort();
            try {
                AkashicVoice.LOG.info("Issued voice bootstrap to {} (port {})", player.getCommandSenderName(), port);
                return new PacketSecret(playerId, secret, port);
            } finally {
                Arrays.fill(secret, (byte) 0);
            }
        }
    }
}
