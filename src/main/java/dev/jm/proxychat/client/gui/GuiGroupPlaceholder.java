package dev.jm.proxychat.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

/**
 * Placeholder Group screen. Real group management lands in Phase 9 — the screen
 * needs server-side packets, persistence, and an invite flow. For now we let the
 * user know it's coming.
 */
public class GuiGroupPlaceholder extends GuiScreen {

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(0, width / 2 - 50, height - 30, 100, 20, I18n.format("gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton btn) {
        if (btn.id == 0) mc.displayGuiScreen(new GuiVoiceChatHub());
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;
        drawCenteredString(fontRendererObj, I18n.format("proxychat.gui.group.title"), cx, 30, 0xFFFFFFFF);
        drawCenteredString(
            fontRendererObj,
            I18n.format("proxychat.gui.group.comingSoon1"),
            cx,
            height / 2 - 8,
            0xFFAAAAAA);
        drawCenteredString(
            fontRendererObj,
            I18n.format("proxychat.gui.group.comingSoon2"),
            cx,
            height / 2 + 6,
            0xFFAAAAAA);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
