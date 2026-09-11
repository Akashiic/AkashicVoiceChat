package dev.akashic.voice.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import dev.akashic.voice.client.gui.widget.IconButton;
import dev.akashic.voice.client.gui.widget.IconRenderer;
import dev.akashic.voice.voice.client.MicGate;

/**
 * Top-level "Voice Chat" hub. Two big buttons (Settings / Group) and two icon
 * toggles (mic mute, speaker mute / deafen). All other prefs live inside Settings.
 */
public class GuiVoiceChatHub extends GuiScreen {

    private static final int BTN_SETTINGS = 0;
    private static final int BTN_GROUP = 1;
    private static final int BTN_MIC = 2;
    private static final int BTN_SPEAKER = 3;

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        int top = height / 2 - 30;

        buttonList
            .add(new GuiButton(BTN_SETTINGS, cx - 110, top, 100, 22, I18n.format("akashicvoice.gui.hub.settings")));
        buttonList.add(new GuiButton(BTN_GROUP, cx + 10, top, 100, 22, I18n.format("akashicvoice.gui.hub.group")));

        int iconSize = 24;
        int iconY = top + 30;
        buttonList.add(
            new IconButton(
                BTN_MIC,
                cx - iconSize - 4,
                iconY,
                iconSize,
                (px, py, sz) -> IconRenderer.drawMic(px, py, sz, MicGate.isMuted())));
        buttonList.add(
            new IconButton(
                BTN_SPEAKER,
                cx + 4,
                iconY,
                iconSize,
                (px, py, sz) -> IconRenderer.drawSpeaker(px, py, sz, MicGate.isDeafened())));
    }

    @Override
    protected void actionPerformed(GuiButton btn) {
        switch (btn.id) {
            case BTN_SETTINGS:
                mc.displayGuiScreen(new GuiVoiceChatSettings());
                return;
            case BTN_GROUP:
                mc.displayGuiScreen(new GuiGroup());
                return;
            case BTN_MIC:
                MicGate.toggleMute();
                break;
            case BTN_SPEAKER:
                MicGate.toggleDeafened();
                break;
            default:
                break;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("akashicvoice.gui.hub.title"), width / 2, 30, 0xFFFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
