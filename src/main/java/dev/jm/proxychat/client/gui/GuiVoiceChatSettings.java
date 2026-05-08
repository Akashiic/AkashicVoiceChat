package dev.jm.proxychat.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import dev.jm.proxychat.client.gui.widget.LevelMeterWidget;
import dev.jm.proxychat.client.gui.widget.PercentSlider;
import dev.jm.proxychat.client.gui.widget.ThresholdSlider;
import dev.jm.proxychat.config.ProxyChatConfig;
import dev.jm.proxychat.voice.client.MicGate;
import dev.jm.proxychat.voice.client.audio.MicLevelMonitor;

/**
 * Detail settings screen — laid out top to bottom like a vertical stack of full-width
 * controls, similar to a typical voice-chat options menu.
 */
public class GuiVoiceChatSettings extends GuiScreen {

    private static final int BTN_NOISE = 100;
    private static final int BTN_ACTIVATION = 101;
    private static final int BTN_TEST_MIC = 102;
    private static final int BTN_3D_AUDIO = 103;
    private static final int BTN_SELECT_MIC = 104;
    private static final int BTN_SELECT_SPEAKER = 105;
    private static final int BTN_INDICATORS = 107;
    private static final int BTN_DONE = 106;

    private PercentSlider volumeSlider;
    private PercentSlider micAmpSlider;
    private ThresholdSlider thresholdSlider;
    private GuiButton noiseBtn;
    private GuiButton activationBtn;
    private GuiButton testMicBtn;
    private GuiButton audio3dBtn;
    private GuiButton indicatorsBtn;

    private boolean micTesting;
    private MicLevelMonitor monitor;

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        int rowH = 22;
        int rowY = 30;
        int btnW = 200;

        volumeSlider = new PercentSlider(
            1,
            cx - btnW / 2,
            rowY,
            btnW,
            rowH,
            "proxychat.gui.set.volume",
            ProxyChatConfig.playbackVolume(),
            0.0,
            3.0);
        buttonList.add(volumeSlider);
        rowY += rowH + 3;

        micAmpSlider = new PercentSlider(
            2,
            cx - btnW / 2,
            rowY,
            btnW,
            rowH,
            "proxychat.gui.set.micAmp",
            ProxyChatConfig.micAmplification(),
            0.0,
            3.0);
        buttonList.add(micAmpSlider);
        rowY += rowH + 3;

        noiseBtn = new GuiButton(BTN_NOISE, cx - btnW / 2, rowY, btnW, rowH, noiseLabel());
        buttonList.add(noiseBtn);
        rowY += rowH + 3;

        activationBtn = new GuiButton(BTN_ACTIVATION, cx - btnW / 2, rowY, btnW, rowH, activationLabel());
        buttonList.add(activationBtn);
        rowY += rowH + 3;

        thresholdSlider = new ThresholdSlider(3, cx - btnW / 2, rowY, btnW, rowH, ProxyChatConfig.thresholdDb());
        buttonList.add(thresholdSlider);
        rowY += rowH + 3;

        testMicBtn = new GuiButton(BTN_TEST_MIC, cx - btnW / 2, rowY, btnW, rowH, testMicLabel());
        buttonList.add(testMicBtn);
        rowY += rowH + 3;

        audio3dBtn = new GuiButton(BTN_3D_AUDIO, cx - btnW / 2, rowY, btnW, rowH, audio3dLabel());
        buttonList.add(audio3dBtn);
        rowY += rowH + 3;

        indicatorsBtn = new GuiButton(BTN_INDICATORS, cx - btnW / 2, rowY, btnW, rowH, indicatorsLabel());
        buttonList.add(indicatorsBtn);
        rowY += rowH + 6;

        int halfW = (btnW - 4) / 2;
        buttonList.add(
            new GuiButton(
                BTN_SELECT_MIC,
                cx - btnW / 2,
                rowY,
                halfW,
                rowH,
                I18n.format("proxychat.gui.set.selectMic")));
        buttonList.add(
            new GuiButton(
                BTN_SELECT_SPEAKER,
                cx - btnW / 2 + halfW + 4,
                rowY,
                halfW,
                rowH,
                I18n.format("proxychat.gui.set.selectSpeaker")));
        rowY += rowH + 8;

        buttonList.add(new GuiButton(BTN_DONE, cx - 50, rowY, 100, rowH, I18n.format("gui.done")));
    }

    private String noiseLabel() {
        return I18n.format("proxychat.gui.set.noise") + ": "
            + I18n.format(ProxyChatConfig.noiseSuppression() ? "options.on" : "options.off");
    }

    private String activationLabel() {
        return I18n.format("proxychat.gui.set.activation") + ": "
            + I18n.format("proxychat.gui.mode." + ProxyChatConfig.activationMode());
    }

    private String testMicLabel() {
        return I18n.format(micTesting ? "proxychat.gui.set.disableMicTest" : "proxychat.gui.set.enableMicTest");
    }

    private String audio3dLabel() {
        return I18n.format("proxychat.gui.set.audio3d") + ": "
            + I18n.format("proxychat.gui.set.audio3d." + ProxyChatConfig.audio3dMode());
    }

    private String indicatorsLabel() {
        return I18n.format("proxychat.gui.set.indicators") + ": "
            + I18n.format(ProxyChatConfig.showSpeakerIndicators() ? "options.on" : "options.off");
    }

    @Override
    protected void actionPerformed(GuiButton btn) {
        if (btn == volumeSlider || btn == micAmpSlider || btn == thresholdSlider) return;

        switch (btn.id) {
            case BTN_NOISE:
                ProxyChatConfig.setNoiseSuppression(!ProxyChatConfig.noiseSuppression());
                ProxyChatConfig.save();
                noiseBtn.displayString = noiseLabel();
                break;
            case BTN_ACTIVATION: {
                String cur = ProxyChatConfig.activationMode();
                String next = "ptt".equals(cur) ? "voice" : "voice".equals(cur) ? "always" : "ptt";
                ProxyChatConfig.setActivationMode(next);
                if ("voice".equals(next)) MicGate.setMode(MicGate.Mode.VOICE);
                else if ("always".equals(next)) MicGate.setMode(MicGate.Mode.ALWAYS);
                else MicGate.setMode(MicGate.Mode.PTT);
                ProxyChatConfig.save();
                activationBtn.displayString = activationLabel();
                break;
            }
            case BTN_TEST_MIC:
                micTesting = !micTesting;
                if (micTesting) startMonitor();
                else stopMonitor();
                testMicBtn.displayString = testMicLabel();
                break;
            case BTN_3D_AUDIO: {
                String next = "normal".equals(ProxyChatConfig.audio3dMode()) ? "off" : "normal";
                ProxyChatConfig.setAudio3dMode(next);
                ProxyChatConfig.save();
                audio3dBtn.displayString = audio3dLabel();
                break;
            }
            case BTN_INDICATORS:
                ProxyChatConfig.setShowSpeakerIndicators(!ProxyChatConfig.showSpeakerIndicators());
                ProxyChatConfig.save();
                indicatorsBtn.displayString = indicatorsLabel();
                break;
            case BTN_SELECT_MIC:
                stopMonitor();
                mc.displayGuiScreen(new GuiSelectMicrophone(this));
                return;
            case BTN_SELECT_SPEAKER:
                stopMonitor();
                mc.displayGuiScreen(new GuiSelectSpeaker(this));
                return;
            case BTN_DONE:
                close();
                return;
            default:
                break;
        }
    }

    public void persistSliderValues() {
        if (volumeSlider != null) {
            ProxyChatConfig.setPlaybackVolume(volumeSlider.getValue());
        }
        if (micAmpSlider != null) {
            ProxyChatConfig.setMicAmplification(micAmpSlider.getValue());
        }
        if (thresholdSlider != null) {
            ProxyChatConfig.setThresholdDb(thresholdSlider.getValue());
        }
        ProxyChatConfig.save();
    }

    @Override
    public void mouseMovedOrUp(int x, int y, int evt) {
        super.mouseMovedOrUp(x, y, evt);
        if (evt == 0) {
            // Mouse-up. Persist current slider snapshots.
            persistSliderValues();
        }
    }

    private void startMonitor() {
        stopMonitor();
        javax.sound.sampled.Mixer.Info info = dev.jm.proxychat.voice.client.audio.MicDeviceRegistry
            .findByName(ProxyChatConfig.selectedMicMixer());
        // loopback=true so the user actually hears their mic during testing
        monitor = new MicLevelMonitor(info, true);
        monitor.start();
    }

    private void stopMonitor() {
        if (monitor != null) {
            monitor.stop();
            monitor = null;
        }
    }

    private void close() {
        persistSliderValues();
        stopMonitor();
        mc.displayGuiScreen(null);
    }

    @Override
    public void onGuiClosed() {
        stopMonitor();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, I18n.format("proxychat.gui.set.title"), width / 2, 8, 0xFFFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Live level overlay drawn AFTER the buttons so it sits on top of the slider track.
        // Anchored just below the threshold slider for visibility.
        if (micTesting && monitor != null && thresholdSlider != null) {
            int barX = thresholdSlider.xPosition;
            int barY = thresholdSlider.yPosition + thresholdSlider.height + 1;
            int barW = thresholdSlider.width;
            int barH = 4;
            LevelMeterWidget bar = new LevelMeterWidget(barX, barY, barW, barH);
            bar.draw(monitor.latestRms());
        }
    }
}
