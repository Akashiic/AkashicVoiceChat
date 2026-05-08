package dev.jm.proxychat.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ChatComponentText;

import dev.jm.proxychat.client.gui.widget.PlayerHead;
import dev.jm.proxychat.net.PacketInviteToGroup;
import dev.jm.proxychat.net.ProxyChatChannel;
import dev.jm.proxychat.voice.client.groups.ClientGroupCache;
import dev.jm.proxychat.voice.client.groups.ClientPlayerRoster;
import dev.jm.proxychat.voice.common.groups.GroupSnapshot;
import dev.jm.proxychat.voice.common.groups.RosterEntry;

/**
 * Player picker shown after the user clicks "Invite player" in {@link GuiGroup}.
 * Lists every other voice-mod-online player; row click sends a {@link PacketInviteToGroup}
 * and shows a chat confirmation.
 */
public class GuiInvitePlayers extends GuiScreen {

    private final GuiScreen parent;
    private final UUID groupId;

    private List<RosterEntry> candidates;
    private int rowH = 22;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int scrollOffset;

    public GuiInvitePlayers(GuiScreen parent, UUID groupId) {
        this.parent = parent;
        this.groupId = groupId;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;

        listX = cx - 110;
        listY = 40;
        listW = 220;
        listH = height - 80;

        recomputeCandidates();

        buttonList.add(new GuiButton(0, cx - 50, height - 30, 100, 22, I18n.format("gui.cancel")));
    }

    private void recomputeCandidates() {
        Minecraft mc = Minecraft.getMinecraft();
        UUID myId = mc.thePlayer != null ? mc.thePlayer.getUniqueID() : null;
        GroupSnapshot myGroup = myId != null ? ClientGroupCache.myGroup(myId) : null;
        candidates = new ArrayList<>();
        for (RosterEntry e : ClientPlayerRoster.all()) {
            if (myId != null && e.id.equals(myId)) continue;
            if (myGroup != null && containsMember(myGroup, e.id)) continue;
            candidates.add(e);
        }
    }

    private static boolean containsMember(GroupSnapshot g, UUID id) {
        for (GroupSnapshot.MemberEntry m : g.members) {
            if (m.id.equals(id)) return true;
        }
        return false;
    }

    @Override
    protected void actionPerformed(GuiButton btn) {
        if (btn.id == 0) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (button != 0) return;
        if (mouseX < listX || mouseX > listX + listW) return;
        if (mouseY < listY || mouseY > listY + listH) return;
        int idx = (mouseY - listY) / rowH + scrollOffset;
        if (idx < 0 || idx >= candidates.size()) return;
        RosterEntry chosen = candidates.get(idx);
        ProxyChatChannel.get()
            .sendToServer(new PacketInviteToGroup(groupId, chosen.id));
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(I18n.format("proxychat.gui.group.invited", chosen.name)));
        }
        mc.displayGuiScreen(parent);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dwheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (dwheel == 0) return;
        int visibleRows = listH / rowH;
        int maxOffset = Math.max(0, candidates.size() - visibleRows);
        if (dwheel > 0) scrollOffset = Math.max(0, scrollOffset - 1);
        else scrollOffset = Math.min(maxOffset, scrollOffset + 1);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;
        drawCenteredString(fontRendererObj, I18n.format("proxychat.gui.group.invitePicker"), cx, 20, 0xFFFFFFFF);

        int visibleRows = listH / rowH;
        int end = Math.min(candidates.size(), scrollOffset + visibleRows);

        if (candidates.isEmpty()) {
            drawCenteredString(
                fontRendererObj,
                I18n.format("proxychat.gui.group.noOne"),
                cx,
                height / 2 - 8,
                0xFFAAAAAA);
        }

        for (int i = scrollOffset; i < end; i++) {
            RosterEntry e = candidates.get(i);
            int y = listY + (i - scrollOffset) * rowH;
            boolean hover = mouseX >= listX && mouseX <= listX + listW && mouseY >= y && mouseY < y + rowH - 2;
            drawRect(listX, y, listX + listW, y + rowH - 2, hover ? 0x66FFFFFF : 0x55000000);
            PlayerHead.draw(e.name, listX + 3, y + 2, 16);
            fontRendererObj.drawStringWithShadow(e.name, listX + 24, y + 7, 0xFFFFFFFF);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
