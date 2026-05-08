package dev.jm.proxychat.voice.server.groups;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import dev.jm.proxychat.ProxyChat;
import dev.jm.proxychat.net.PacketGroupStateSync;
import dev.jm.proxychat.net.PacketInviteNotify;
import dev.jm.proxychat.net.PacketRosterSync;
import dev.jm.proxychat.net.ProxyChatChannel;
import dev.jm.proxychat.voice.common.groups.GroupSnapshot;
import dev.jm.proxychat.voice.common.groups.RosterEntry;

/**
 * Server-side authority for groups: create, invite, accept/decline, leave.
 * Distribution check {@link #haveCommonGroup(UUID, UUID)} is read on the UDP
 * receive thread, so all maps are concurrent or guarded by the registry's
 * intrinsic lock.
 */
public final class GroupRegistry {

    private final GroupSavedData saved;
    /** group -> set of invited (pending) playerIds */
    private final Map<UUID, Set<UUID>> pendingInvites = new HashMap<>();

    public GroupRegistry(GroupSavedData saved) {
        this.saved = saved;
    }

    /* --- queries used by VoiceServer --- */

    public synchronized boolean haveCommonGroup(UUID a, UUID b) {
        for (Group g : saved.groups.values()) {
            if (g.members.contains(a) && g.members.contains(b)) return true;
        }
        return false;
    }

    public synchronized List<GroupSnapshot> snapshotsFor(UUID playerId) {
        List<GroupSnapshot> out = new ArrayList<>();
        for (Group g : saved.groups.values()) {
            if (!g.members.contains(playerId)) continue;
            out.add(toSnapshot(g));
        }
        return out;
    }

    /* --- mutations --- */

    public synchronized void createGroup(UUID ownerId, String ownerName, String requestedName) {
        // One group per player at a time.
        for (Group g : saved.groups.values()) {
            if (g.members.contains(ownerId)) return;
        }
        UUID id = UUID.randomUUID();
        String name = requestedName != null && !requestedName.isEmpty() ? requestedName : ownerName + "'s group";
        Group g = new Group(id, name, ownerId);
        g.members.add(ownerId);
        saved.groups.put(id, g);
        saved.markDirty();
        broadcastGroupState(g);
        ProxyChat.LOG.info("Group '{}' created by {}", name, ownerName);
    }

    public synchronized void invite(EntityPlayerMP inviter, UUID groupId, UUID targetId) {
        Group g = saved.groups.get(groupId);
        if (g == null) return;
        if (!g.members.contains(inviter.getUniqueID())) return; // only members can invite
        if (g.members.contains(targetId)) return; // already in
        // already in any other group? skip (one group per player rule).
        for (Group other : saved.groups.values()) {
            if (other.members.contains(targetId)) return;
        }
        pendingInvites.computeIfAbsent(groupId, k -> new HashSet<>())
            .add(targetId);

        EntityPlayerMP target = findOnline(targetId);
        if (target != null) {
            ProxyChatChannel.get()
                .sendTo(
                    new PacketInviteNotify(groupId, g.name, inviter.getUniqueID(), inviter.getCommandSenderName()),
                    target);
        }
    }

    public synchronized void respondToInvite(EntityPlayerMP responder, UUID groupId, boolean accepted) {
        Set<UUID> pending = pendingInvites.get(groupId);
        if (pending == null || !pending.remove(responder.getUniqueID())) return;
        if (pending.isEmpty()) pendingInvites.remove(groupId);
        if (!accepted) return;
        Group g = saved.groups.get(groupId);
        if (g == null) return;
        g.members.add(responder.getUniqueID());
        saved.markDirty();
        broadcastGroupState(g);
        ProxyChat.LOG.info("{} joined group '{}'", responder.getCommandSenderName(), g.name);
    }

    public synchronized void leaveGroup(EntityPlayerMP player, UUID groupId) {
        Group g = saved.groups.get(groupId);
        if (g == null) return;
        if (!g.members.remove(player.getUniqueID())) return;
        saved.markDirty();
        if (g.members.isEmpty()) {
            saved.groups.remove(groupId);
            pendingInvites.remove(groupId);
            ProxyChat.LOG.info("Group '{}' dissolved (last member left)", g.name);
            // Send empty state to the leaver so client clears the group.
            sendStateTo(player.getUniqueID());
            return;
        }
        broadcastGroupState(g);
        sendStateTo(player.getUniqueID()); // leaver also gets refreshed (now empty / minus this group)
        ProxyChat.LOG.info("{} left group '{}'", player.getCommandSenderName(), g.name);
    }

    public synchronized void onPlayerLogout(UUID playerId) {
        // Pending invites: drop.
        for (Set<UUID> set : pendingInvites.values()) set.remove(playerId);
    }

    /* --- broadcasts --- */

    public synchronized void sendStateTo(UUID playerId) {
        EntityPlayerMP target = findOnline(playerId);
        if (target == null) return;
        ProxyChatChannel.get()
            .sendTo(new PacketGroupStateSync(snapshotsFor(playerId)), target);
    }

    public synchronized void broadcastGroupState(Group g) {
        for (UUID memberId : g.members) sendStateTo(memberId);
    }

    public synchronized void broadcastRoster() {
        ServerConfigurationManager scm = MinecraftServer.getServer()
            .getConfigurationManager();
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> players = scm.playerEntityList;
        List<RosterEntry> entries = new ArrayList<>(players.size());
        for (EntityPlayerMP p : players) {
            entries.add(new RosterEntry(p.getUniqueID(), p.getCommandSenderName()));
        }
        for (EntityPlayerMP p : players) {
            ProxyChatChannel.get()
                .sendTo(new PacketRosterSync(entries), p);
        }
    }

    /* --- helpers --- */

    private GroupSnapshot toSnapshot(Group g) {
        List<GroupSnapshot.MemberEntry> members = new ArrayList<>(g.members.size());
        for (UUID memberId : g.members) {
            EntityPlayerMP online = findOnline(memberId);
            String name = online != null ? online.getCommandSenderName()
                : memberId.toString()
                    .substring(0, 8);
            members.add(new GroupSnapshot.MemberEntry(memberId, name));
        }
        return new GroupSnapshot(g.id, g.name, g.ownerId, members);
    }

    private EntityPlayerMP findOnline(UUID id) {
        ServerConfigurationManager scm = MinecraftServer.getServer()
            .getConfigurationManager();
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> players = scm.playerEntityList;
        for (EntityPlayerMP p : players) {
            if (p.getUniqueID()
                .equals(id)) return p;
        }
        return null;
    }

    public synchronized Collection<Group> debugGroups() {
        return Collections.unmodifiableCollection(new ArrayList<>(saved.groups.values()));
    }

    /** Notify the user when their inviter sends a message — used for chat fallback. */
    @SuppressWarnings("unused")
    private void chat(EntityPlayerMP target, String text) {
        target.addChatMessage(new ChatComponentText(text));
    }

    public static GroupRegistry loadFor(World world) {
        GroupSavedData data = (GroupSavedData) world.mapStorage.loadData(GroupSavedData.class, GroupSavedData.KEY);
        if (data == null) {
            data = new GroupSavedData();
            world.mapStorage.setData(GroupSavedData.KEY, data);
        }
        return new GroupRegistry(data);
    }
}
