package dev.akashic.voice.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import dev.akashic.voice.net.AkashicVoiceChannel;
import dev.akashic.voice.net.PacketInviteNotify;
import dev.akashic.voice.net.PacketInviteResponse;

/**
 * Modal banner shown when an invite arrives. Two buttons: Accept / Decline.
 * The pending-invites queue triggers it from the client tick handler.
 */
public class GuiGroupInviteBanner extends GuiScreen {

    private final PacketInviteNotify invite;

    public GuiGroupInviteBanner(PacketInviteNotify invite) {
        this.invite = invite;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        int cy = height / 2;
        buttonList.add(new GuiButton(0, cx - 110, cy + 20, 100, 22, I18n.format("akashicvoice.gui.invite.accept")));
        buttonList.add(new GuiButton(1, cx + 10, cy + 20, 100, 22, I18n.format("akashicvoice.gui.invite.decline")));
    }

    @Override
    protected void actionPerformed(GuiButton btn) {
        boolean accepted = btn.id == 0;
        AkashicVoiceChannel.get()
            .sendToServer(new PacketInviteResponse(invite.getGroupId(), accepted));
        mc.displayGuiScreen(null);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;
        int cy = height / 2;
        drawCenteredString(fontRendererObj, I18n.format("akashicvoice.gui.invite.title"), cx, cy - 40, 0xFFFFFFFF);
        drawCenteredString(
            fontRendererObj,
            I18n.format("akashicvoice.gui.invite.body", invite.getInviterName(), invite.getGroupName()),
            cx,
            cy - 10,
            0xFFFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
