package dev.akashic.voice.net;

import java.util.Arrays;
import java.util.UUID;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import dev.akashic.voice.AkashicVoice;
import dev.akashic.voice.voice.client.ClientSecretCache;
import dev.akashic.voice.voice.common.crypto.SessionKeys;
import io.netty.buffer.ByteBuf;

public class PacketSecret implements IMessage {

    private UUID playerId;
    private byte[] masterSecret;
    private int udpPort;

    public PacketSecret() {}

    public PacketSecret(UUID playerId, byte[] masterSecret, int udpPort) {
        if (masterSecret == null || masterSecret.length != SessionKeys.MASTER_SECRET_BYTES) {
            throw new IllegalArgumentException("invalid master secret");
        }
        this.playerId = playerId;
        this.masterSecret = Arrays.copyOf(masterSecret, masterSecret.length);
        this.udpPort = udpPort;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public byte[] getMasterSecret() {
        return Arrays.copyOf(masterSecret, masterSecret.length);
    }

    public int getUdpPort() {
        return udpPort;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        playerId = new UUID(buf.readLong(), buf.readLong());
        masterSecret = new byte[SessionKeys.MASTER_SECRET_BYTES];
        buf.readBytes(masterSecret);
        udpPort = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(playerId.getMostSignificantBits());
        buf.writeLong(playerId.getLeastSignificantBits());
        buf.writeBytes(masterSecret);
        buf.writeInt(udpPort);
    }

    public static class Handler implements IMessageHandler<PacketSecret, IMessage> {

        @Override
        public IMessage onMessage(PacketSecret msg, MessageContext ctx) {
            byte[] secret = msg.getMasterSecret();
            try {
                ClientSecretCache.store(msg.getPlayerId(), secret, msg.getUdpPort());
            } finally {
                Arrays.fill(secret, (byte) 0);
            }
            AkashicVoice.LOG.info("Received voice bootstrap (port {})", msg.getUdpPort());
            return null;
        }
    }
}
