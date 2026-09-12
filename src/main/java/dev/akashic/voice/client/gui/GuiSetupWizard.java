package dev.akashic.voice.client.gui;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import dev.akashic.voice.client.gui.widget.LevelMeterWidget;
import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.client.audio.MicLevelMonitor;
import dev.akashic.voice.voice.client.audio.capture.AudioCaptureBackends;
import dev.akashic.voice.voice.client.audio.capture.AudioCaptureDeviceInfo;

public class GuiSetupWizard extends GuiScreen {

    private static final int BTN_PREV = 0;
    private static final int BTN_NEXT = 1;
    private static final int BTN_DONE = 2;
    private static final int BTN_CANCEL = 3;

    private List<AudioCaptureDeviceInfo> devices;
    private int selectedIndex = -1;
    private MicLevelMonitor monitor;
    private LevelMeterWidget meter;

    @Override
    public void initGui() {
        devices = AudioCaptureBackends.listDevices();

        // Resolve the previously selected capture device to an index, fallback to system default.
        String saved = AkashicVoiceConfig.selectedMicDevice();
        selectedIndex = -1;
        if (saved != null && !saved.isEmpty()) {
            for (int i = 0; i < devices.size(); i++) {
                if (saved.equals(
                    devices.get(i)
                        .id())) {
                    selectedIndex = i;
                    break;
                }
            }
        }
        if (selectedIndex == -1 && !devices.isEmpty()) selectedIndex = 0;

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
        String deviceId = (selectedIndex >= 0 && selectedIndex < devices.size()) ? devices.get(selectedIndex)
            .id() : "";
        monitor = new MicLevelMonitor(deviceId);
        monitor.start();
    }

    @Override
    protected void actionPerformed(GuiButton btn) {
        switch (btn.id) {
            case BTN_PREV:
                if (devices.isEmpty()) return;
                selectedIndex = (selectedIndex - 1 + devices.size()) % devices.size();
                startMonitor();
                break;
            case BTN_NEXT:
                if (devices.isEmpty()) return;
                selectedIndex = (selectedIndex + 1) % devices.size();
                startMonitor();
                break;
            case BTN_DONE:
                String deviceId = (selectedIndex >= 0 && selectedIndex < devices.size()) ? devices.get(selectedIndex)
                    .id() : "";
                AkashicVoiceConfig.setSelectedMicDevice(deviceId);
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

        String name = (selectedIndex >= 0 && selectedIndex < devices.size()) ? devices.get(selectedIndex)
            .displayName() : I18n.format("akashicvoice.gui.wizard.noMics");
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
