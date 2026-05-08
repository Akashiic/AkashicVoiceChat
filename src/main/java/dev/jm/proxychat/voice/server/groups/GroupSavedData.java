package dev.jm.proxychat.voice.server.groups;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldSavedData;

public class GroupSavedData extends WorldSavedData {

    public static final String KEY = "proxychat_groups";

    final Map<UUID, Group> groups = new HashMap<>();

    public GroupSavedData() {
        super(KEY);
    }

    public GroupSavedData(String key) {
        super(key);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        groups.clear();
        NBTTagList list = nbt.getTagList("groups", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            Group g = Group.readNbt(list.getCompoundTagAt(i));
            groups.put(g.id, g);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (Group g : groups.values()) {
            list.appendTag(g.writeNbt());
        }
        nbt.setTag("groups", list);
    }
}
