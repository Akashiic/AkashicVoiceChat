package dev.jm.proxychat.net;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import dev.jm.proxychat.voice.server.groups.GroupRegistryHolder;
import io.netty.buffer.ByteBuf;

public class PacketInviteResponse implements IMessage {

    private UUID groupId;
    private boolean accepted;

    public PacketInviteResponse() {}

    public PacketInviteResponse(UUID groupId, boolean accepted) {
        this.groupId = groupId;
        this.accepted = accepted;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        groupId = new UUID(buf.readLong(), buf.readLong());
        accepted = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(groupId.getMostSignificantBits());
        buf.writeLong(groupId.getLeastSignificantBits());
        buf.writeBoolean(accepted);
    }

    public static class Handler implements IMessageHandler<PacketInviteResponse, IMessage> {

        @Override
        public IMessage onMessage(PacketInviteResponse msg, MessageContext ctx) {
            EntityPlayerMP responder = ctx.getServerHandler().playerEntity;
            GroupRegistryHolder.get()
                .respondToInvite(responder, msg.groupId, msg.accepted);
            return null;
        }
    }
}
