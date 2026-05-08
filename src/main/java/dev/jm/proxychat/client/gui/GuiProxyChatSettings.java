package dev.jm.proxychat.client.gui;

import java.util.List;

import javax.sound.sampled.Mixer;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import dev.jm.proxychat.client.gui.widget.LevelMeterWidget;
import dev.jm.proxychat.config.ProxyChatConfig;
import dev.jm.proxychat.voice.client.MicGate;
import dev.jm.proxychat.voice.client.audio.MicDeviceRegistry;
import dev.jm.proxychat.voice.client.audio.MicLevelMonitor;

public class GuiProxyChatSettings extends GuiScreen {

    private static final int BTN_MODE = 0;
    private static final int BTN_MUTE = 1;
    private static final int BTN_VOL_DOWN = 2;
    private static final int BTN_VOL_UP = 3;
    private static final int BTN_MIC_PREV = 4;
    private static final int BTN_MIC_NEXT = 5;
    private static final int BTN_RUN_WIZARD = 6;
    private static final int BTN_CLOSE = 7;

    private List<Mixer.Info> mixers;
    private int selectedIndex = -1;
    private MicLevelMonitor monitor;
    private LevelMeterWidget meter;

    private GuiButton modeBtn;
    private GuiButton muteBtn;

    @Override
    public void initGui() {
        mixers = MicDeviceRegistry.listInputMixers();
        String saved = ProxyChatConfig.selectedMicMixer();
        selectedIndex = -1;
        for (int i = 0; i < mixers.size(); i++) {
            if (saved != null && saved.equals(
                mixers.get(i)
                    .getName())) {
                selectedIndex = i;
                break;
            }
        }
        if (selectedIndex == -1 && !mixers.isEmpty()) selectedIndex = 0;

        int cx = width / 2;
        int row = 50;

        buttonList.clear();
        modeBtn = new GuiButton(BTN_MODE, cx - 100, row, 200, 20, modeLabel());
        buttonList.add(modeBtn);

        row += 25;
        muteBtn = new GuiButton(BTN_MUTE, cx - 100, row, 200, 20, muteLabel());
        buttonList.add(muteBtn);

        row += 25;
        buttonList.add(new GuiButton(BTN_VOL_DOWN, cx - 100, row, 30, 20, "-"));
        buttonList.add(new GuiButton(BTN_VOL_UP, cx + 70, row, 30, 20, "+"));

        row += 35;
        buttonList.add(new GuiButton(BTN_MIC_PREV, cx - 130, row, 30, 20, "<"));
        buttonList.add(new GuiButton(BTN_MIC_NEXT, cx + 100, row, 30, 20, ">"));

        row += 50;
        buttonList.add(new GuiButton(BTN_RUN_WIZARD, cx - 100, row, 200, 20, I18n.format("proxychat.gui.runWizard")));

        buttonList.add(new GuiButton(BTN_CLOSE, cx - 50, height - 30, 100, 20, I18n.format("gui.done")));

        meter = new LevelMeterWidget(cx - 100, row - 17, 200, 10);
        startMonitor();
    }

    private void startMonitor() {
        if (monitor != null) {
            monitor.stop();
            monitor = null;
        }
        Mixer.Info info = (selectedIndex >= 0 && selectedIndex < mixers.size()) ? mixers.get(selectedIndex) : null;
        monitor = new MicLevelMonitor(info);
        monitor.start();
    }

    private String modeLabel() {
        return I18n.format("proxychat.gui.mode") + ": "
            + I18n.format("proxychat.gui.mode." + ProxyChatConfig.activationMode());
    }

    private String muteLabel() {
        return I18n.format("proxychat.gui.mic") + ": "
            + I18n.format(MicGate.isMuted() ? "proxychat.gui.muted" : "proxychat.gui.unmuted");
    }

    @Override
    protected void actionPerformed(GuiButton btn) {
        switch (btn.id) {
            case BTN_MODE: {
                String cur = ProxyChatConfig.activationMode();
                String next;
                if ("ptt".equalsIgnoreCase(cur)) next = "voice";
                else if ("voice".equalsIgnoreCase(cur)) next = "always";
                else next = "ptt";
                ProxyChatConfig.setActivationMode(next);
                applyModeToGate(next);
                ProxyChatConfig.save();
                modeBtn.displayString = modeLabel();
                break;
            }
            case BTN_MUTE:
                MicGate.toggleMute();
                muteBtn.displayString = muteLabel();
                break;
            case BTN_VOL_DOWN: {
                double v = Math.max(0.0, Math.round((ProxyChatConfig.playbackVolume() - 0.5) * 10) / 10.0);
                ProxyChatConfig.setPlaybackVolume(v);
                ProxyChatConfig.save();
                break;
            }
            case BTN_VOL_UP: {
                double v = Math.min(10.0, Math.round((ProxyChatConfig.playbackVolume() + 0.5) * 10) / 10.0);
                ProxyChatConfig.setPlaybackVolume(v);
                ProxyChatConfig.save();
                break;
            }
            case BTN_MIC_PREV:
                if (mixers.isEmpty()) return;
                selectedIndex = (selectedIndex - 1 + mixers.size()) % mixers.size();
                applySelectedMixer();
                break;
            case BTN_MIC_NEXT:
                if (mixers.isEmpty()) return;
                selectedIndex = (selectedIndex + 1) % mixers.size();
                applySelectedMixer();
                break;
            case BTN_RUN_WIZARD:
                if (monitor != null) monitor.stop();
                mc.displayGuiScreen(new GuiSetupWizard());
                return;
            case BTN_CLOSE:
                close();
                return;
            default:
                break;
        }
    }

    private void applySelectedMixer() {
        String name = (selectedIndex >= 0 && selectedIndex < mixers.size()) ? mixers.get(selectedIndex)
            .getName() : "";
        ProxyChatConfig.setSelectedMicMixer(name);
        ProxyChatConfig.save();
        startMonitor();
        // Notify VoiceClient so live transmission switches device.
        dev.jm.proxychat.voice.client.VoiceClientHolder.notifyMicChanged();
    }

    private void applyModeToGate(String mode) {
        if ("voice".equalsIgnoreCase(mode)) MicGate.setMode(MicGate.Mode.VOICE);
        else if ("always".equalsIgnoreCase(mode)) MicGate.setMode(MicGate.Mode.ALWAYS);
        else MicGate.setMode(MicGate.Mode.PTT);
    }

    private void close() {
        if (monitor != null) {
            monitor.stop();
            monitor = null;
        }
        mc.displayGuiScreen(null);
    }

    @Override
    public void onGuiClosed() {
        if (monitor != null) {
            monitor.stop();
            monitor = null;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;

        drawCenteredString(fontRendererObj, I18n.format("proxychat.gui.settings.title"), cx, 20, 0xFFFFFFFF);

        // Volume label between -/+ buttons
        int volRow = 50 + 25 + 25;
        drawCenteredString(
            fontRendererObj,
            I18n.format("proxychat.gui.volume") + ": " + String.format("%.1f", ProxyChatConfig.playbackVolume()) + "x",
            cx,
            volRow + 6,
            0xFFFFFFFF);

        // Mic device label between </> buttons
        int micRow = volRow + 35;
        String name = (selectedIndex >= 0 && selectedIndex < mixers.size()) ? mixers.get(selectedIndex)
            .getName() : I18n.format("proxychat.gui.wizard.noMics");
        drawCenteredString(
            fontRendererObj,
            I18n.format("proxychat.gui.wizard.device") + ": " + truncate(name, 30),
            cx,
            micRow + 6,
            0xFFFFFFFF);

        if (meter != null && monitor != null) {
            meter.draw(monitor.latestRms());
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
