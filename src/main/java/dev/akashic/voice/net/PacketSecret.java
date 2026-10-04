package dev.akashic.voice.net;

import java.util.Arrays;
import java.util.UUID;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import dev.akashic.voice.AkashicVoice;
import dev.akashic.voice.voice.client.ClientSecretCache;
import dev.akashic.voice.voice.client.VoiceClientHolder;
import dev.akashic.voice.voice.common.crypto.SessionKeys;
import io.netty.buffer.ByteBuf;

public class PacketSecret implements IMessage {

    private UUID playerId;
    private byte[] masterSecret;
    private int udpPort;
    private UUID sessionId;

    public PacketSecret() {}

    public PacketSecret(UUID playerId, byte[] masterSecret, int udpPort) {
        this(playerId, masterSecret, udpPort, null);
    }

    public PacketSecret(UUID playerId, byte[] masterSecret, int udpPort, UUID sessionId) {
        if (playerId == null) throw new IllegalArgumentException("playerId");
        if (masterSecret == null || masterSecret.length != SessionKeys.MASTER_SECRET_BYTES) {
            throw new IllegalArgumentException("invalid master secret");
        }
        if (udpPort < 1 || udpPort > 65_535) throw new IllegalArgumentException("invalid UDP port");
        this.playerId = playerId;
        this.masterSecret = Arrays.copyOf(masterSecret, masterSecret.length);
        this.udpPort = udpPort;
        this.sessionId = sessionId;
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

    public UUID getSessionId() {
        return sessionId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        playerId = new UUID(buf.readLong(), buf.readLong());
        masterSecret = new byte[SessionKeys.MASTER_SECRET_BYTES];
        buf.readBytes(masterSecret);
        udpPort = buf.readInt();
        int remaining = buf.readableBytes();
        if (remaining != 0 && remaining != 16) throw new IllegalArgumentException("invalid session id suffix");
        if (remaining == 16) sessionId = new UUID(buf.readLong(), buf.readLong());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(playerId.getMostSignificantBits());
        buf.writeLong(playerId.getLeastSignificantBits());
        buf.writeBytes(masterSecret);
        buf.writeInt(udpPort);
        if (sessionId != null) {
            buf.writeLong(sessionId.getMostSignificantBits());
            buf.writeLong(sessionId.getLeastSignificantBits());
        }
    }

    public static class Handler implements IMessageHandler<PacketSecret, IMessage> {

        @Override
        public IMessage onMessage(PacketSecret msg, MessageContext ctx) {
            byte[] secret = msg.getMasterSecret();
            try {
                VoiceClientHolder.stop();
                ClientSecretCache.store(msg.getPlayerId(), secret, msg.getUdpPort(), msg.getSessionId());
            } finally {
                Arrays.fill(secret, (byte) 0);
            }
            AkashicVoice.LOG.info(
                "Received voice bootstrap (port {}, session-bound={})",
                msg.getUdpPort(),
                msg.getSessionId() != null);
            return null;
        }
    }
}
