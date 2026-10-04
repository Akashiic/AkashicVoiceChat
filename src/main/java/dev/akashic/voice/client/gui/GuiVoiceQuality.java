package dev.akashic.voice.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import dev.akashic.voice.AkashicVoice;
import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.config.VoiceQualityPreset;

/** Changes only local encoder targets and the existing high-pass option; never opens a microphone. */
public final class GuiVoiceQuality extends GuiScreen {

    private static final VoiceQualityPreset[] PRESETS = VoiceQualityPreset.values();
    private static final int FILTER = 100, BACK = 101;
    private final GuiScreen parent;
    private final GuiButton[] presetButtons = new GuiButton[PRESETS.length];
    private GuiButton filterButton;
    private boolean compact;
    private boolean saveFailed;

    public GuiVoiceQuality(GuiScreen parent) {
        this.parent = parent;
    }

    private static String text(String key, Object... args) {
        return I18n.format("akashicvoice.gui.quality." + key, args);
    }

    @Override
    public void initGui() {
        buttonList.clear();
        compact = width < 320 || height < 230;
        buttonList.add(new GuiButton(BACK, width / 2 - 50, height - 25, 100, 20, I18n.format("gui.back")));
        if (compact) return;
        int panelWidth = Math.min(360, width - 24);
        int left = (width - panelWidth) / 2;
        int cell = (panelWidth - 6) / 2;
        for (int i = 0; i < PRESETS.length; i++) {
            VoiceQualityPreset preset = PRESETS[i];
            presetButtons[i] = new GuiButton(
                i,
                left + (i % 2) * (cell + 6),
                54 + (i / 2) * 26,
                cell,
                22,
                text(preset.id()) + " - " + preset.bitrate() / 1000 + " kbit/s");
            buttonList.add(presetButtons[i]);
        }
        filterButton = new GuiButton(FILTER, left, 139, panelWidth, 22, "");
        buttonList.add(filterButton);
        updateLabels();
    }

    private void updateLabels() {
        if (compact) return;
        int rate = AkashicVoiceConfig.opusBitrate();
        for (int i = 0; i < PRESETS.length; i++) presetButtons[i].enabled = rate != PRESETS[i].bitrate();
        filterButton.displayString = text("filter") + ": "
            + I18n.format(AkashicVoiceConfig.noiseSuppression() ? "options.on" : "options.off");
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) return;
        if (button.id == BACK) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (compact) return;
        if (button.id >= 0 && button.id < PRESETS.length) AkashicVoiceConfig.setVoiceQuality(PRESETS[button.id]);
        else if (button.id == FILTER) AkashicVoiceConfig.setNoiseSuppression(!AkashicVoiceConfig.noiseSuppression());
        else return;
        try {
            AkashicVoiceConfig.save();
            saveFailed = false;
        } catch (RuntimeException failure) {
            saveFailed = true;
            AkashicVoice.LOG.warn("Voice quality changed in memory; config save failed", failure);
        }
        updateLabels();
    }

    @Override
    public void updateScreen() {
        updateLabels();
    }

    @Override
    protected void keyTyped(char character, int keyCode) {
        if (keyCode == 1) mc.displayGuiScreen(parent);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int x, int y, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, text("title"), width / 2, 17, 0xFFFFFFFF);
        if (compact) {
            drawCenteredString(fontRendererObj, text("small"), width / 2, height / 2, 0xFFCCCCCC);
        } else {
            VoiceQualityPreset preset = AkashicVoiceConfig.voiceQuality();
            drawCenteredString(
                fontRendererObj,
                text("current", text(preset != null ? preset.id() : "custom"), AkashicVoiceConfig.opusBitrate()),
                width / 2,
                36,
                0xFFDDDDDD);
            drawCenteredString(fontRendererObj, text("apply"), width / 2, 112, 0xFFBBBBBB);
            drawCenteredString(fontRendererObj, text("format"), width / 2, 125, 0xFFAAAAAA);
            drawCenteredString(fontRendererObj, text("filterHelp"), width / 2, 168, 0xFFBBBBBB);
            drawCenteredString(fontRendererObj, text("notDenoiser"), width / 2, 181, 0xFFAAAAAA);
            if (saveFailed) drawCenteredString(fontRendererObj, text("saveFailed"), width / 2, height - 39, 0xFFFF7777);
        }
        super.drawScreen(x, y, partialTicks);
    }
}
