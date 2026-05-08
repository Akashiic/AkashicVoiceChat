package dev.jm.proxychat.net;

import java.util.UUID;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import dev.jm.proxychat.voice.client.groups.PendingInviteQueue;
import dev.jm.proxychat.voice.common.groups.GroupSnapshot;
import io.netty.buffer.ByteBuf;

public class PacketInviteNotify implements IMessage {

    private UUID groupId;
    private String groupName;
    private UUID inviterId;
    private String inviterName;

    public PacketInviteNotify() {}

    public PacketInviteNotify(UUID groupId, String groupName, UUID inviterId, String inviterName) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.inviterId = inviterId;
        this.inviterName = inviterName;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public UUID getInviterId() {
        return inviterId;
    }

    public String getInviterName() {
        return inviterName;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        groupId = new UUID(buf.readLong(), buf.readLong());
        groupName = GroupSnapshot.readString(buf);
        inviterId = new UUID(buf.readLong(), buf.readLong());
        inviterName = GroupSnapshot.readString(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(groupId.getMostSignificantBits());
        buf.writeLong(groupId.getLeastSignificantBits());
        GroupSnapshot.writeString(buf, groupName);
        buf.writeLong(inviterId.getMostSignificantBits());
        buf.writeLong(inviterId.getLeastSignificantBits());
        GroupSnapshot.writeString(buf, inviterName);
    }

    public static class Handler implements IMessageHandler<PacketInviteNotify, IMessage> {

        @Override
        public IMessage onMessage(PacketInviteNotify msg, MessageContext ctx) {
            PendingInviteQueue.enqueue(msg);
            return null;
        }
    }
}
