package dev.akashic.voice.net;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import dev.akashic.voice.voice.common.groups.GroupSnapshot;
import dev.akashic.voice.voice.server.groups.GroupRegistryHolder;
import io.netty.buffer.ByteBuf;

public class PacketCreateGroup implements IMessage {

    private String name;

    public PacketCreateGroup() {}

    public PacketCreateGroup(String name) {
        this.name = name;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        name = GroupSnapshot.readString(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        GroupSnapshot.writeString(buf, name);
    }

    public static class Handler implements IMessageHandler<PacketCreateGroup, IMessage> {

        @Override
        public IMessage onMessage(PacketCreateGroup msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            GroupRegistryHolder.get()
                .createGroup(player.getUniqueID(), player.getCommandSenderName(), msg.name);
            return null;
        }
    }
}
