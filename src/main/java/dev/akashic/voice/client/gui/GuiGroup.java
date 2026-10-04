package dev.akashic.voice.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import dev.akashic.voice.client.gui.widget.PlayerHead;
import dev.akashic.voice.net.AkashicVoiceChannel;
import dev.akashic.voice.net.PacketCreateGroup;
import dev.akashic.voice.net.PacketLeaveGroup;
import dev.akashic.voice.voice.client.groups.ClientGroupCache;
import dev.akashic.voice.voice.common.groups.GroupSnapshot;

/**
 * State-aware group screen.
 * - When you are not in a group: "Create group" button + (currently no other action).
 * - When you are in a group: title = group name, list of members with skin heads,
 * "Invite player" → GuiInvitePlayers, "Leave group", "Done".
 */
public class GuiGroup extends GuiScreen {

    private static final int BTN_CREATE = 0;
    private static final int BTN_INVITE = 1;
    private static final int BTN_LEAVE = 2;
    private static final int BTN_DONE = 3;

    private GroupSnapshot myGroup;

    @Override
    public void initGui() {
        Minecraft mc = Minecraft.getMinecraft();
        myGroup = mc.thePlayer != null ? ClientGroupCache.personalGroup() : null;

        buttonList.clear();
        int cx = width / 2;

        if (myGroup == null) {
            buttonList.add(
                new GuiButton(
                    BTN_CREATE,
                    cx - 100,
                    height - 60,
                    200,
                    22,
                    I18n.format("akashicvoice.gui.group.create")));
            buttonList.add(new GuiButton(BTN_DONE, cx - 50, height - 30, 100, 22, I18n.format("gui.done")));
        } else {
            buttonList.add(
                new GuiButton(
                    BTN_INVITE,
                    cx - 110,
                    height - 60,
                    100,
                    22,
                    I18n.format("akashicvoice.gui.group.invite")));
            buttonList.add(
                new GuiButton(BTN_LEAVE, cx + 10, height - 60, 100, 22, I18n.format("akashicvoice.gui.group.leave")));
            buttonList.add(new GuiButton(BTN_DONE, cx - 50, height - 30, 100, 22, I18n.format("gui.done")));
        }
    }

    @Override
    protected void actionPerformed(GuiButton btn) {
        switch (btn.id) {
            case BTN_CREATE: {
                String myName = mc.thePlayer != null ? mc.thePlayer.getCommandSenderName() : "Player";
                AkashicVoiceChannel.get()
                    .sendToServer(new PacketCreateGroup(myName + "'s group"));
                break;
            }
            case BTN_INVITE:
                if (myGroup != null) mc.displayGuiScreen(new GuiInvitePlayers(this, myGroup.groupId));
                return;
            case BTN_LEAVE:
                if (myGroup != null) {
                    AkashicVoiceChannel.get()
                        .sendToServer(new PacketLeaveGroup(myGroup.groupId));
                }
                break;
            case BTN_DONE:
                mc.displayGuiScreen(new GuiVoiceChatHub());
                return;
            default:
                break;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        // Refresh group state if the server pushed an update while we sit here.
        GroupSnapshot now = mc.thePlayer != null ? ClientGroupCache.personalGroup() : null;
        boolean wasIn = myGroup != null;
        boolean isIn = now != null;
        myGroup = now;
        if (wasIn != isIn) initGui();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;
        if (myGroup == null) {
            drawCenteredString(fontRendererObj, I18n.format("akashicvoice.gui.group.title"), cx, 30, 0xFFFFFFFF);
            drawCenteredString(
                fontRendererObj,
                I18n.format("akashicvoice.gui.group.notInOne"),
                cx,
                height / 2 - 8,
                0xFFAAAAAA);
        } else {
            drawCenteredString(fontRendererObj, myGroup.name, cx, 20, 0xFFFFFFFF);
            int rowH = 22;
            int listX = cx - 110;
            int y = 50;
            for (GroupSnapshot.MemberEntry m : myGroup.members) {
                drawRect(listX, y, listX + 220, y + rowH - 2, 0x55000000);
                PlayerHead.draw(m.name, listX + 3, y + 2, 16);
                fontRendererObj.drawStringWithShadow(m.name, listX + 24, y + 7, 0xFFFFFFFF);
                if (m.id.equals(myGroup.ownerId)) {
                    fontRendererObj.drawStringWithShadow(
                        I18n.format("akashicvoice.gui.group.owner"),
                        listX + 220 - fontRendererObj.getStringWidth(I18n.format("akashicvoice.gui.group.owner")) - 5,
                        y + 7,
                        0xFFFFD040);
                }
                y += rowH;
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
