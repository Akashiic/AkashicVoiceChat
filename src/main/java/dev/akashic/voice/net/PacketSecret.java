package dev.akashic.voice.net;

import java.util.UUID;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import dev.akashic.voice.AkashicVoice;
import dev.akashic.voice.voice.client.ClientSecretCache;
import io.netty.buffer.ByteBuf;

public class PacketSecret implements IMessage {

    private UUID playerId;
    private UUID secret;
    private int udpPort;

    public PacketSecret() {}

    public PacketSecret(UUID playerId, UUID secret, int udpPort) {
        this.playerId = playerId;
        this.secret = secret;
        this.udpPort = udpPort;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public UUID getSecret() {
        return secret;
    }

    public int getUdpPort() {
        return udpPort;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        playerId = new UUID(buf.readLong(), buf.readLong());
        secret = new UUID(buf.readLong(), buf.readLong());
        udpPort = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(playerId.getMostSignificantBits());
        buf.writeLong(playerId.getLeastSignificantBits());
        buf.writeLong(secret.getMostSignificantBits());
        buf.writeLong(secret.getLeastSignificantBits());
        buf.writeInt(udpPort);
    }

    public static class Handler implements IMessageHandler<PacketSecret, IMessage> {

        @Override
        public IMessage onMessage(PacketSecret msg, MessageContext ctx) {
            ClientSecretCache.store(msg.getPlayerId(), msg.getSecret(), msg.getUdpPort());
            AkashicVoice.LOG.info("Received voice secret (port {})", msg.getUdpPort());
            return null;
        }
    }
}
