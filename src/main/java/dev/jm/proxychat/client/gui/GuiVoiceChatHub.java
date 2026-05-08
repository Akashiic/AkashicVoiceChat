package dev.jm.proxychat.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import dev.jm.proxychat.client.gui.widget.IconButton;
import dev.jm.proxychat.client.gui.widget.IconRenderer;
import dev.jm.proxychat.config.ProxyChatConfig;
import dev.jm.proxychat.voice.client.MicGate;

/**
 * Top-level "Voice Chat" hub. Two big buttons (Settings / Group) and a row of
 * quick-access icon toggles for mic mute, speaker mute (deafen), and the
 * speaker indicator visibility.
 */
public class GuiVoiceChatHub extends GuiScreen {

    private static final int BTN_SETTINGS = 0;
    private static final int BTN_GROUP = 1;
    private static final int BTN_MIC = 2;
    private static final int BTN_SPEAKER = 3;
    private static final int BTN_INDICATORS = 4;

    private IconButton micBtn;
    private IconButton speakerBtn;
    private IconButton indicatorsBtn;

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        int top = height / 2 - 30;

        buttonList.add(new GuiButton(BTN_SETTINGS, cx - 110, top, 100, 22, I18n.format("proxychat.gui.hub.settings")));
        buttonList.add(new GuiButton(BTN_GROUP, cx + 10, top, 100, 22, I18n.format("proxychat.gui.hub.group")));

        int iconSize = 22;
        int iconY = top + 30;
        micBtn = new IconButton(
            BTN_MIC,
            cx - 110,
            iconY,
            iconSize,
            (px, py, sz) -> IconRenderer.drawMic(px, py, sz, MicGate.isMuted()));
        speakerBtn = new IconButton(
            BTN_SPEAKER,
            cx - 84,
            iconY,
            iconSize,
            (px, py, sz) -> IconRenderer.drawSpeaker(px, py, sz, MicGate.isDeafened()));
        indicatorsBtn = new IconButton(
            BTN_INDICATORS,
            cx + 88,
            iconY,
            iconSize,
            (px, py, sz) -> IconRenderer.drawEye(px, py, sz, ProxyChatConfig.showSpeakerIndicators()));
        buttonList.add(micBtn);
        buttonList.add(speakerBtn);
        buttonList.add(indicatorsBtn);
    }

    @Override
    protected void actionPerformed(GuiButton btn) {
        switch (btn.id) {
            case BTN_SETTINGS:
                mc.displayGuiScreen(new GuiVoiceChatSettings());
                return;
            case BTN_GROUP:
                mc.displayGuiScreen(new GuiGroupPlaceholder());
                return;
            case BTN_MIC:
                MicGate.toggleMute();
                break;
            case BTN_SPEAKER:
                MicGate.toggleDeafened();
                break;
            case BTN_INDICATORS:
                ProxyChatConfig.setShowSpeakerIndicators(!ProxyChatConfig.showSpeakerIndicators());
                ProxyChatConfig.save();
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
        drawCenteredString(fontRendererObj, I18n.format("proxychat.gui.hub.title"), width / 2, 30, 0xFFFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
