package dev.akashic.voice.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import dev.akashic.voice.voice.client.playback.AudioPlayback;

/** Shows the auto-follow output endpoint and live recovery state. */
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
        drawCenteredString(fontRendererObj, I18n.format("akashicvoice.gui.set.selectSpeaker"), cx, 30, 0xFFFFFFFF);
        drawCenteredString(
            fontRendererObj,
            I18n.format("akashicvoice.gui.set.speakerNote1"),
            cx,
            height / 2 - 12,
            0xFFAAAAAA);
        drawCenteredString(
            fontRendererObj,
            I18n.format("akashicvoice.gui.set.speakerNote2"),
            cx,
            height / 2 + 2,
            0xFFAAAAAA);
        AudioPlayback playback = AudioPlayback.get();
        String device = playback != null ? playback.outputDeviceName()
            : I18n.format("akashicvoice.gui.set.speakerUnknown");
        drawCenteredString(
            fontRendererObj,
            I18n.format("akashicvoice.gui.set.speakerDevice", device),
            cx,
            height / 2 + 18,
            0xFFFFFFFF);
        if (playback != null && playback.isOutputRecovering()) {
            drawCenteredString(
                fontRendererObj,
                I18n.format("akashicvoice.gui.set.speakerRecovering"),
                cx,
                height / 2 + 34,
                0xFFFFCC55);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
