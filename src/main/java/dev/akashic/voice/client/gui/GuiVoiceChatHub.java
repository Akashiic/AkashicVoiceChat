package dev.akashic.voice.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import dev.akashic.voice.client.gui.widget.IconButton;
import dev.akashic.voice.client.gui.widget.IconRenderer;
import dev.akashic.voice.voice.client.MicGate;
import dev.akashic.voice.voice.client.VoiceClientHolder;
import dev.akashic.voice.voice.client.VoiceClientStatusSnapshot;

/**
 * Top-level "Voice Chat" hub. Settings, Group and Player Voices buttons and two icon
 * toggles (mic mute, speaker mute / deafen). All other prefs live inside Settings.
 */
public class GuiVoiceChatHub extends GuiScreen {

    private static final int BTN_SETTINGS = 0;
    private static final int BTN_GROUP = 1;
    private static final int BTN_MIC = 2;
    private static final int BTN_SPEAKER = 3;
    private static final int BTN_PLAYERS = 4;
    private static final int BTN_QUALITY = 5;
    private static final int BTN_TOUCH = 6;

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        int top = Math.max(40, height / 2 - 42);

        buttonList
            .add(new GuiButton(BTN_SETTINGS, cx - 110, top, 100, 22, I18n.format("akashicvoice.gui.hub.settings")));
        buttonList.add(new GuiButton(BTN_GROUP, cx + 10, top, 100, 22, I18n.format("akashicvoice.gui.hub.group")));

        buttonList
            .add(new GuiButton(BTN_PLAYERS, cx - 110, top + 28, 108, 22, I18n.format("akashicvoice.gui.hub.players")));
        buttonList
            .add(new GuiButton(BTN_QUALITY, cx + 2, top + 28, 108, 22, I18n.format("akashicvoice.gui.quality.title")));
        buttonList
            .add(new GuiButton(BTN_TOUCH, cx - 110, top + 56, 220, 22, I18n.format("akashicvoice.gui.touch.title")));

        int iconSize = 28;
        int iconY = top + 84;
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
            case BTN_PLAYERS:
                mc.displayGuiScreen(new GuiSpeakerControls(this));
                return;
            case BTN_QUALITY:
                mc.displayGuiScreen(new GuiVoiceQuality(this));
                return;
            case BTN_TOUCH:
                mc.displayGuiScreen(new GuiTouchTalk(this));
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
        drawCenteredString(
            fontRendererObj,
            I18n.format("akashicvoice.gui.hub.title"),
            width / 2,
            Math.max(12, Math.min(30, height / 2 - 64)),
            0xFFFFFFFF);
        VoiceClientStatusSnapshot status = VoiceClientHolder.statusSnapshot();
        String state = status.isAuthenticated() ? "online"
            : status.needsFreshBootstrap() ? "refreshing" : status.isPresent() ? "connecting" : "offline";
        String statusText = I18n.format(
            "akashicvoice.gui.hub.status." + state,
            status.receivedFrames(),
            status.activeDecoderStreams(),
            status.maxDecoderStreams(),
            status.decoderCapacityDrops());
        int statusColor = status.decoderCapacityDrops() > 0L ? 0xFFFFAA00 : 0xFFAAAAAA;
        drawCenteredString(fontRendererObj, statusText, width / 2, Math.max(32, height - 18), statusColor);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
