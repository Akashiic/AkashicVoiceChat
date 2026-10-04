package dev.akashic.voice.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import dev.akashic.voice.AkashicVoice;
import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.client.MicGate;
import dev.akashic.voice.voice.client.TouchTalkSession;
import dev.akashic.voice.voice.client.VoiceClient;
import dev.akashic.voice.voice.client.VoiceClientHolder;

/** Hold-to-talk surface for mouse/touch launchers. Opening it never starts transmission. */
public final class GuiTouchTalk extends GuiScreen {

    private static final int HOLD = 0;
    private static final int MODE = 1;
    private static final int BACK = 2;

    private final GuiScreen parent;
    private final TouchTalkSession touch = new TouchTalkSession();
    private GuiButton holdButton;
    private GuiButton modeButton;
    private boolean pointerHeld;
    private boolean compact;
    private boolean saveFailed;
    private int lastMouseX;
    private int lastMouseY;

    public GuiTouchTalk(GuiScreen parent) {
        this.parent = parent;
    }

    private static String text(String key, Object... args) {
        return I18n.format("akashicvoice.gui.touch." + key, args);
    }

    @Override
    public void initGui() {
        endPress();
        buttonList.clear();
        compact = width < 300 || height < 215;
        buttonList.add(new GuiButton(BACK, width / 2 - 50, height - 27, 100, 20, I18n.format("gui.back")));
        if (compact) return;

        int panelWidth = Math.min(360, width - 24);
        int left = (width - panelWidth) / 2;
        holdButton = new GuiButton(HOLD, left, 62, panelWidth, 54, text("hold"));
        modeButton = new GuiButton(MODE, left, 126, panelWidth, 22, "");
        buttonList.add(holdButton);
        buttonList.add(modeButton);
        updateLabels();
    }

    private static boolean focused() {
        try {
            return Display.isCreated() && Display.isActive();
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    private static boolean physicalLeftDown() {
        try {
            return Mouse.isCreated() && Mouse.isButtonDown(0);
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    private boolean insideHold(int x, int y) {
        return holdButton != null && x >= holdButton.xPosition
            && x < holdButton.xPosition + holdButton.width
            && y >= holdButton.yPosition
            && y < holdButton.yPosition + holdButton.height;
    }

    private void beginPress() {
        VoiceClient client = VoiceClientHolder.get();
        pointerHeld = client != null && MicGate.mode() == MicGate.Mode.PTT
            && !MicGate.isMuted()
            && touch.press(client, client.isAuthenticated(), focused());
    }

    private void sustainPress() {
        if (!pointerHeld) return;
        VoiceClient client = VoiceClientHolder.get();
        boolean down = physicalLeftDown();
        boolean inside = insideHold(lastMouseX, lastMouseY);
        if (!touch.sustain(client, client != null && client.isAuthenticated(), down && inside, focused())) {
            pointerHeld = false;
        }
    }

    private void endPress() {
        pointerHeld = false;
        touch.close();
    }

    private boolean ready() {
        VoiceClient client = VoiceClientHolder.get();
        return client != null && client.isAuthenticated()
            && focused()
            && MicGate.mode() == MicGate.Mode.PTT
            && !MicGate.isMuted();
    }

    private String statusKey() {
        if (touch.isHeld()) return "talking";
        if (MicGate.isMuted()) return "muted";
        if (MicGate.mode() != MicGate.Mode.PTT) return "needsPtt";
        VoiceClient client = VoiceClientHolder.get();
        if (client == null) return "offline";
        if (!client.isAuthenticated()) return "connecting";
        if (!focused()) return "unfocused";
        return "ready";
    }

    private void updateLabels() {
        if (compact) return;
        boolean ptt = MicGate.mode() == MicGate.Mode.PTT;
        holdButton.displayString = text(touch.isHeld() ? "talking" : "hold");
        holdButton.enabled = touch.isHeld() || ready();
        modeButton.displayString = text(ptt ? "pttEnabled" : "enablePtt");
        modeButton.enabled = !ptt;
    }

    private void enablePttMode() {
        endPress();
        AkashicVoiceConfig.setActivationMode("ptt");
        MicGate.setMode(MicGate.Mode.PTT);
        try {
            AkashicVoiceConfig.save();
            saveFailed = false;
        } catch (RuntimeException failure) {
            saveFailed = true;
            AkashicVoice.LOG.warn("PTT mode changed in memory; config save failed", failure);
        }
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        lastMouseX = x;
        lastMouseY = y;
        super.mouseClicked(x, y, button);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) return;
        if (button.id == BACK) {
            endPress();
            mc.displayGuiScreen(parent);
            return;
        }
        if (compact) return;
        if (button.id == HOLD) {
            beginPress();
        } else if (button.id == MODE) {
            enablePttMode();
        }
        updateLabels();
    }

    @Override
    protected void mouseMovedOrUp(int x, int y, int button) {
        if (button == 0) endPress();
        super.mouseMovedOrUp(x, y, button);
    }

    @Override
    protected void mouseClickMove(int x, int y, int button, long elapsed) {
        lastMouseX = x;
        lastMouseY = y;
        if (button == 0) sustainPress();
        super.mouseClickMove(x, y, button, elapsed);
    }

    @Override
    public void updateScreen() {
        sustainPress();
        updateLabels();
    }

    @Override
    protected void keyTyped(char character, int keyCode) {
        if (keyCode == 1) {
            endPress();
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    public void onGuiClosed() {
        endPress();
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private int drawWrappedCentered(String value, int y, int maxWidth, int color) {
        @SuppressWarnings("unchecked")
        java.util.List<String> lines = fontRendererObj.listFormattedStringToWidth(value, Math.max(40, maxWidth));
        for (String line : lines) {
            drawCenteredString(fontRendererObj, line, width / 2, y, color);
            y += fontRendererObj.FONT_HEIGHT + 2;
        }
        return y;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        sustainPress();
        updateLabels();
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, text("title"), width / 2, 17, 0xFFFFFFFF);
        if (compact) {
            drawCenteredString(fontRendererObj, text("small"), width / 2, height / 2, 0xFFCCCCCC);
        } else {
            drawCenteredString(
                fontRendererObj,
                text(statusKey()),
                width / 2,
                38,
                touch.isHeld() ? 0xFF55FF55 : 0xFFDDDDDD);
            int hintY = drawWrappedCentered(text("holdHint"), 157, Math.min(360, width - 24), 0xFFBBBBBB);
            drawWrappedCentered(text("keyboardHint"), hintY + 1, Math.min(360, width - 24), 0xFFAAAAAA);
            if (saveFailed) drawCenteredString(fontRendererObj, text("saveFailed"), width / 2, height - 44, 0xFFFF7777);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
