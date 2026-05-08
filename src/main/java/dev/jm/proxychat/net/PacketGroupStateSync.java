package dev.jm.proxychat.net;

import java.util.ArrayList;
import java.util.List;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import dev.jm.proxychat.voice.client.groups.ClientGroupCache;
import dev.jm.proxychat.voice.common.groups.GroupSnapshot;
import io.netty.buffer.ByteBuf;

public class PacketGroupStateSync implements IMessage {

    private List<GroupSnapshot> groups;

    public PacketGroupStateSync() {}

    public PacketGroupStateSync(List<GroupSnapshot> groups) {
        this.groups = groups;
    }

    public List<GroupSnapshot> getGroups() {
        return groups;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int n = buf.readShort() & 0xFFFF;
        groups = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            groups.add(GroupSnapshot.read(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeShort(groups.size());
        for (GroupSnapshot g : groups) g.write(buf);
    }

    public static class Handler implements IMessageHandler<PacketGroupStateSync, IMessage> {

        @Override
        public IMessage onMessage(PacketGroupStateSync msg, MessageContext ctx) {
            ClientGroupCache.update(msg.groups);
            return null;
        }
    }
}
