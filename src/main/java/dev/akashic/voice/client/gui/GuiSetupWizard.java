package dev.akashic.voice.client.gui;

import java.util.List;

import javax.sound.sampled.Mixer;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import dev.akashic.voice.client.gui.widget.LevelMeterWidget;
import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.client.audio.MicDeviceRegistry;
import dev.akashic.voice.voice.client.audio.MicLevelMonitor;

public class GuiSetupWizard extends GuiScreen {

    private static final int BTN_PREV = 0;
    private static final int BTN_NEXT = 1;
    private static final int BTN_DONE = 2;
    private static final int BTN_CANCEL = 3;

    private List<Mixer.Info> mixers;
    private int selectedIndex = -1;
    private MicLevelMonitor monitor;
    private LevelMeterWidget meter;

    @Override
    public void initGui() {
        mixers = MicDeviceRegistry.listInputMixers();

        // Resolve previously selected mixer to an index, fallback to 0.
        String saved = AkashicVoiceConfig.selectedMicMixer();
        selectedIndex = -1;
        if (saved != null && !saved.isEmpty()) {
            for (int i = 0; i < mixers.size(); i++) {
                if (saved.equals(
                    mixers.get(i)
                        .getName())) {
                    selectedIndex = i;
                    break;
                }
            }
        }
        if (selectedIndex == -1 && !mixers.isEmpty()) selectedIndex = 0;

        int cx = width / 2;
        buttonList.clear();
        buttonList.add(new GuiButton(BTN_PREV, cx - 130, height / 2 - 10, 30, 20, "<"));
        buttonList.add(new GuiButton(BTN_NEXT, cx + 100, height / 2 - 10, 30, 20, ">"));
        buttonList.add(new GuiButton(BTN_DONE, cx - 100, height - 40, 90, 20, I18n.format("akashicvoice.gui.done")));
        buttonList.add(new GuiButton(BTN_CANCEL, cx + 10, height - 40, 90, 20, I18n.format("gui.cancel")));

        meter = new LevelMeterWidget(cx - 100, height / 2 + 30, 200, 12);

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

    @Override
    protected void actionPerformed(GuiButton btn) {
        switch (btn.id) {
            case BTN_PREV:
                if (mixers.isEmpty()) return;
                selectedIndex = (selectedIndex - 1 + mixers.size()) % mixers.size();
                startMonitor();
                break;
            case BTN_NEXT:
                if (mixers.isEmpty()) return;
                selectedIndex = (selectedIndex + 1) % mixers.size();
                startMonitor();
                break;
            case BTN_DONE:
                String name = (selectedIndex >= 0 && selectedIndex < mixers.size()) ? mixers.get(selectedIndex)
                    .getName() : "";
                AkashicVoiceConfig.setSelectedMicMixer(name);
                AkashicVoiceConfig.setFirstRunComplete(true);
                AkashicVoiceConfig.save();
                close();
                break;
            case BTN_CANCEL:
                AkashicVoiceConfig.setFirstRunComplete(true); // don't keep popping the wizard
                AkashicVoiceConfig.save();
                close();
                break;
            default:
                break;
        }
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

        drawCenteredString(fontRendererObj, I18n.format("akashicvoice.gui.wizard.title"), cx, 30, 0xFFFFFFFF);
        drawCenteredString(fontRendererObj, I18n.format("akashicvoice.gui.wizard.subtitle"), cx, 50, 0xFFAAAAAA);

        String name = (selectedIndex >= 0 && selectedIndex < mixers.size()) ? mixers.get(selectedIndex)
            .getName() : I18n.format("akashicvoice.gui.wizard.noMics");
        drawCenteredString(
            fontRendererObj,
            I18n.format("akashicvoice.gui.wizard.device") + ": " + name,
            cx,
            height / 2 - 30,
            0xFFFFFFFF);

        drawCenteredString(
            fontRendererObj,
            I18n.format("akashicvoice.gui.wizard.testHint"),
            cx,
            height / 2 + 15,
            0xFFAAAAAA);

        if (meter != null && monitor != null) {
            meter.draw(monitor.latestRms());
        }
        if (monitor != null && monitor.error() != null) {
            drawCenteredString(fontRendererObj, monitor.error(), cx, height / 2 + 50, 0xFFFF6644);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
