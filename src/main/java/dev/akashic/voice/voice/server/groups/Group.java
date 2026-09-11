package dev.akashic.voice.voice.server.groups;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

public final class Group {

    public final UUID id;
    public final String name;
    public final UUID ownerId;
    public final Set<UUID> members = new LinkedHashSet<>();

    public Group(UUID id, String name, UUID ownerId) {
        this.id = id;
        this.name = name;
        this.ownerId = ownerId;
    }

    public NBTTagCompound writeNbt() {
        NBTTagCompound c = new NBTTagCompound();
        c.setLong("idH", id.getMostSignificantBits());
        c.setLong("idL", id.getLeastSignificantBits());
        c.setString("name", name);
        c.setLong("ownH", ownerId.getMostSignificantBits());
        c.setLong("ownL", ownerId.getLeastSignificantBits());
        NBTTagList list = new NBTTagList();
        for (UUID m : members) {
            NBTTagCompound mc = new NBTTagCompound();
            mc.setLong("h", m.getMostSignificantBits());
            mc.setLong("l", m.getLeastSignificantBits());
            list.appendTag(mc);
        }
        c.setTag("members", list);
        return c;
    }

    public static Group readNbt(NBTTagCompound c) {
        UUID id = new UUID(c.getLong("idH"), c.getLong("idL"));
        String name = c.getString("name");
        UUID ownerId = new UUID(c.getLong("ownH"), c.getLong("ownL"));
        Group g = new Group(id, name, ownerId);
        NBTTagList list = c.getTagList("members", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound mc = list.getCompoundTagAt(i);
            g.members.add(new UUID(mc.getLong("h"), mc.getLong("l")));
        }
        return g;
    }
}
