package dev.jm.proxychat.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

/**
 * Speaker selection screen — placeholder. Output device selection on top of LWJGL 2's
 * OpenAL is non-trivial (system default only without an ALC reinit). For now we let
 * the user know and keep the system default.
 */
public class GuiSelectSpeaker extends GuiScreen {

    private final GuiScreen parent;

    public GuiSelectSpeaker(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(0, width / 2 - 50, height - 30, 100, 20, I18n.format("gui.done")));
    }

    @Override
    protected void actionPerformed(GuiButton btn) {
        if (btn.id == 0) mc.displayGuiScreen(parent);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;
        drawCenteredString(fontRendererObj, I18n.format("proxychat.gui.set.selectSpeaker"), cx, 30, 0xFFFFFFFF);
        drawCenteredString(
            fontRendererObj,
            I18n.format("proxychat.gui.set.speakerNote1"),
            cx,
            height / 2 - 12,
            0xFFAAAAAA);
        drawCenteredString(
            fontRendererObj,
            I18n.format("proxychat.gui.set.speakerNote2"),
            cx,
            height / 2 + 2,
            0xFFAAAAAA);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
