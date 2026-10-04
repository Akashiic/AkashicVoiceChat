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
            // In Agent mode the Velocity Gateway is the only bootstrap authority.
            // If its route is down, fail closed instead of exposing backend UDP.
            if (AkashicVoiceConfig.agentMode()) return null;
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            UUID playerId = player.getUniqueID();
            int port = AkashicVoiceConfig.voicePort();
            try (SecretRegistry.Material material = SecretRegistry.getOrCreateMaterial(playerId)) {
                byte[] secret = material.secretCopy();
                try {
                    AkashicVoice.LOG
                        .info("Issued voice bootstrap to {} (port {})", player.getCommandSenderName(), port);
                    return new PacketSecret(playerId, secret, port, material.sessionId());
                } finally {
                    Arrays.fill(secret, (byte) 0);
                }
            }
        }
    }
}
