package dev.jm.proxychat.voice.common.groups;

import java.util.UUID;

import io.netty.buffer.ByteBuf;

public final class RosterEntry {

    public final UUID id;
    public final String name;

    public RosterEntry(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public static RosterEntry read(ByteBuf buf) {
        UUID id = new UUID(buf.readLong(), buf.readLong());
        return new RosterEntry(id, GroupSnapshot.readString(buf));
    }

    public void write(ByteBuf buf) {
        buf.writeLong(id.getMostSignificantBits());
        buf.writeLong(id.getLeastSignificantBits());
        GroupSnapshot.writeString(buf, name);
    }
}
