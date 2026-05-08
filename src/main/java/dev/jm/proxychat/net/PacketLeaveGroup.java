package dev.jm.proxychat.net;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import dev.jm.proxychat.voice.server.groups.GroupRegistryHolder;
import io.netty.buffer.ByteBuf;

public class PacketLeaveGroup implements IMessage {

    private UUID groupId;

    public PacketLeaveGroup() {}

    public PacketLeaveGroup(UUID groupId) {
        this.groupId = groupId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        groupId = new UUID(buf.readLong(), buf.readLong());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(groupId.getMostSignificantBits());
        buf.writeLong(groupId.getLeastSignificantBits());
    }

    public static class Handler implements IMessageHandler<PacketLeaveGroup, IMessage> {

        @Override
        public IMessage onMessage(PacketLeaveGroup msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            GroupRegistryHolder.get()
                .leaveGroup(player, msg.groupId);
            return null;
        }
    }
}
