package dev.jm.proxychat.voice.common.groups;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.netty.buffer.ByteBuf;

/**
 * Wire DTO of a group state, sent from server to clients in {@code PacketGroupStateSync}.
 * Contains everything a client needs to render the group view: id, name, owner UUID,
 * and the (UUID, name) pair of every member.
 */
public final class GroupSnapshot {

    public final UUID groupId;
    public final String name;
    public final UUID ownerId;
    public final List<MemberEntry> members;

    public GroupSnapshot(UUID groupId, String name, UUID ownerId, List<MemberEntry> members) {
        this.groupId = groupId;
        this.name = name;
        this.ownerId = ownerId;
        this.members = members;
    }

    public static GroupSnapshot read(ByteBuf buf) {
        UUID groupId = new UUID(buf.readLong(), buf.readLong());
        String name = readString(buf);
        UUID ownerId = new UUID(buf.readLong(), buf.readLong());
        int n = buf.readShort() & 0xFFFF;
        List<MemberEntry> members = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            members.add(MemberEntry.read(buf));
        }
        return new GroupSnapshot(groupId, name, ownerId, members);
    }

    public void write(ByteBuf buf) {
        buf.writeLong(groupId.getMostSignificantBits());
        buf.writeLong(groupId.getLeastSignificantBits());
        writeString(buf, name);
        buf.writeLong(ownerId.getMostSignificantBits());
        buf.writeLong(ownerId.getLeastSignificantBits());
        buf.writeShort(members.size());
        for (MemberEntry m : members) m.write(buf);
    }

    public static String readString(ByteBuf buf) {
        int len = buf.readShort() & 0xFFFF;
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static void writeString(ByteBuf buf, String s) {
        if (s == null) s = "";
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    public static final class MemberEntry {

        public final UUID id;
        public final String name;

        public MemberEntry(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        public static MemberEntry read(ByteBuf buf) {
            UUID id = new UUID(buf.readLong(), buf.readLong());
            return new MemberEntry(id, readString(buf));
        }

        public void write(ByteBuf buf) {
            buf.writeLong(id.getMostSignificantBits());
            buf.writeLong(id.getLeastSignificantBits());
            writeString(buf, name);
        }
    }
}
