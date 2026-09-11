package dev.akashic.voice.net;

import java.util.ArrayList;
import java.util.List;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import dev.akashic.voice.voice.client.groups.ClientPlayerRoster;
import dev.akashic.voice.voice.common.groups.RosterEntry;
import io.netty.buffer.ByteBuf;

public class PacketRosterSync implements IMessage {

    private List<RosterEntry> roster;

    public PacketRosterSync() {}

    public PacketRosterSync(List<RosterEntry> roster) {
        this.roster = roster;
    }

    public List<RosterEntry> getRoster() {
        return roster;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int n = buf.readShort() & 0xFFFF;
        roster = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            roster.add(RosterEntry.read(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeShort(roster.size());
        for (RosterEntry e : roster) e.write(buf);
    }

    public static class Handler implements IMessageHandler<PacketRosterSync, IMessage> {

        @Override
        public IMessage onMessage(PacketRosterSync msg, MessageContext ctx) {
            ClientPlayerRoster.update(msg.roster);
            return null;
        }
    }
}
