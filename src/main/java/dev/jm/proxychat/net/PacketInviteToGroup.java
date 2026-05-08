package dev.jm.proxychat.net;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import dev.jm.proxychat.voice.server.groups.GroupRegistryHolder;
import io.netty.buffer.ByteBuf;

public class PacketInviteToGroup implements IMessage {

    private UUID groupId;
    private UUID targetId;

    public PacketInviteToGroup() {}

    public PacketInviteToGroup(UUID groupId, UUID targetId) {
        this.groupId = groupId;
        this.targetId = targetId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        groupId = new UUID(buf.readLong(), buf.readLong());
        targetId = new UUID(buf.readLong(), buf.readLong());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(groupId.getMostSignificantBits());
        buf.writeLong(groupId.getLeastSignificantBits());
        buf.writeLong(targetId.getMostSignificantBits());
        buf.writeLong(targetId.getLeastSignificantBits());
    }

    public static class Handler implements IMessageHandler<PacketInviteToGroup, IMessage> {

        @Override
        public IMessage onMessage(PacketInviteToGroup msg, MessageContext ctx) {
            EntityPlayerMP inviter = ctx.getServerHandler().playerEntity;
            GroupRegistryHolder.get()
                .invite(inviter, msg.groupId, msg.targetId);
            return null;
        }
    }
}
