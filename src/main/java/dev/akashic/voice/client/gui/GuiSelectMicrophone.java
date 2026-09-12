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

public class GuiSelectMicrophone extends GuiScreen {

    private final GuiScreen parent;
    private List<AudioCaptureDeviceInfo> devices;
    private int selectedIndex = -1;
    private MicLevelMonitor monitor;
    private LevelMeterWidget meter;

    public GuiSelectMicrophone(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        devices = AudioCaptureBackends.listDevices();
        String saved = AkashicVoiceConfig.selectedMicDevice();
        selectedIndex = -1;
        for (int i = 0; i < devices.size(); i++) {
            if (saved != null && saved.equals(
                devices.get(i)
                    .id())) {
                selectedIndex = i;
                break;
            }
        }
        if (selectedIndex == -1 && !devices.isEmpty()) selectedIndex = 0;

        buttonList.clear();
        int cx = width / 2;
        buttonList.add(new GuiButton(0, cx - 130, height / 2 - 10, 30, 20, "<"));
        buttonList.add(new GuiButton(1, cx + 100, height / 2 - 10, 30, 20, ">"));
        buttonList.add(new GuiButton(2, cx - 50, height - 30, 100, 20, I18n.format("gui.done")));

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
        if (devices.isEmpty()) {
            if (btn.id == 2) close();
            return;
        }
        switch (btn.id) {
            case 0:
                selectedIndex = (selectedIndex - 1 + devices.size()) % devices.size();
                startMonitor();
                break;
            case 1:
                selectedIndex = (selectedIndex + 1) % devices.size();
                startMonitor();
                break;
            case 2:
                save();
                close();
                break;
            default:
                break;
        }
    }

    private void save() {
        String deviceId = (selectedIndex >= 0 && selectedIndex < devices.size()) ? devices.get(selectedIndex)
            .id() : "";
        AkashicVoiceConfig.setSelectedMicDevice(deviceId);
        AkashicVoiceConfig.save();
        dev.akashic.voice.voice.client.VoiceClientHolder.notifyMicChanged();
    }

    private void close() {
        if (monitor != null) {
            monitor.stop();
            monitor = null;
        }
        mc.displayGuiScreen(parent);
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
        drawCenteredString(fontRendererObj, I18n.format("akashicvoice.gui.set.selectMic"), cx, 30, 0xFFFFFFFF);

        String name = (selectedIndex >= 0 && selectedIndex < devices.size()) ? devices.get(selectedIndex)
            .displayName() : I18n.format("akashicvoice.gui.wizard.noMics");
        drawCenteredString(fontRendererObj, name, cx, height / 2 - 30, 0xFFFFFFFF);
        drawCenteredString(
            fontRendererObj,
            I18n.format("akashicvoice.gui.wizard.testHint"),
            cx,
            height / 2 + 15,
            0xFFAAAAAA);

        if (meter != null && monitor != null) meter.draw(monitor.latestRms());
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
