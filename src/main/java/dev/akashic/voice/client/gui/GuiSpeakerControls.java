package dev.akashic.voice.client.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

import dev.akashic.voice.voice.client.groups.ClientGroupCache;
import dev.akashic.voice.voice.client.groups.ClientPlayerRoster;
import dev.akashic.voice.voice.client.playback.AudioPlayback;
import dev.akashic.voice.voice.client.playback.SpeakerControls;
import dev.akashic.voice.voice.common.groups.GroupSnapshot;
import dev.akashic.voice.voice.common.groups.RosterEntry;

/** Session-local listening controls. No server packet or native audio call originates in this screen. */
public final class GuiSpeakerControls extends GuiScreen {

    private static final int PREVIOUS = 100, NEXT = 101, DOWN = 102, UP = 103;
    private static final int MUTE = 104, RESET = 105, BACK = 106;
    private final GuiScreen parent;
    private final SpeakerPanelModel model = new SpeakerPanelModel();
    private final GuiButton[] rows = new GuiButton[8];
    private GuiTextField search;
    private SpeakerControls displayedControls;
    private int panelX, panelWidth, rowCount, ticks;
    private boolean compact;

    public GuiSpeakerControls(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        String query = search != null ? search.getText() : "";
        buttonList.clear();
        compact = height < 200 || width < 300;
        buttonList.add(new GuiButton(BACK, width / 2 - 50, height - 25, 100, 20, I18n.format("gui.back")));
        if (compact) return;
        panelWidth = Math.min(420, width - 24);
        panelX = (width - panelWidth) / 2;
        rowCount = Math.max(1, Math.min(rows.length, (height - 170) / 22));
        search = new GuiTextField(fontRendererObj, panelX, 44, panelWidth, 18);
        search.setMaxStringLength(64);
        search.setText(query);
        for (int i = 0; i < rowCount; i++) {
            rows[i] = new GuiButton(i, panelX, 68 + i * 22, panelWidth, 20, "");
            buttonList.add(rows[i]);
        }
        buttonList.add(new GuiButton(PREVIOUS, panelX, height - 77, 36, 20, "<"));
        buttonList.add(new GuiButton(NEXT, panelX + panelWidth - 36, height - 77, 36, 20, ">"));
        int cell = (panelWidth - 12) / 4;
        buttonList.add(new GuiButton(DOWN, panelX, height - 51, cell, 20, "-10%"));
        buttonList.add(new GuiButton(UP, panelX + cell + 4, height - 51, cell, 20, "+10%"));
        buttonList.add(new GuiButton(MUTE, panelX + 2 * (cell + 4), height - 51, cell, 20, ""));
        buttonList.add(new GuiButton(RESET, panelX + 3 * (cell + 4), height - 51, cell, 20, text("reset")));
        model.filter(query);
        refresh();
    }

    private static SpeakerControls currentControls() {
        AudioPlayback output = AudioPlayback.get();
        return output != null ? output.speakerControls() : null;
    }

    private void refresh() {
        if (compact) return;
        Map<UUID, String> names = new HashMap<>();
        for (RosterEntry entry : ClientPlayerRoster.all()) {
            if (entry != null && entry.id != null) names.put(entry.id, entry.name);
        }
        for (GroupSnapshot group : ClientGroupCache.all()) {
            if (group == null) continue;
            for (GroupSnapshot.MemberEntry member : group.members) {
                if (member != null && member.id != null) names.putIfAbsent(member.id, member.name);
            }
        }
        displayedControls = currentControls();
        model.refresh(displayedControls, names, rowCount);
        updateButtons();
    }

    private void updateButtons() {
        SpeakerPanelModel.Row selected = model.selected();
        for (int i = 0; i < rowCount; i++) {
            rows[i].visible = i < model.rows()
                .size();
            if (!rows[i].visible) continue;
            SpeakerPanelModel.Row row = model.rows()
                .get(i);
            String prefix = selected != null && selected.id.equals(row.id) ? "> " : "";
            String label = fontRendererObj.trimStringToWidth(row.name, panelWidth - 145);
            rows[i].displayString = prefix + label
                + " ["
                + row.id.toString()
                    .substring(0, 8)
                + "] "
                + row.percent
                + "%"
                + (row.muted ? " " + text("muted") : "");
        }
        for (Object value : buttonList) {
            GuiButton button = (GuiButton) value;
            if (button.id == PREVIOUS) button.enabled = model.pageIndex() > 0;
            else if (button.id == NEXT) button.enabled = model.pageIndex() + 1 < model.pageCount();
            else if (button.id >= DOWN && button.id <= RESET) {
                button.enabled = selected != null && model.connected();
                if (button.id == DOWN && selected != null) button.enabled &= selected.percent > 0;
                if (button.id == UP && selected != null) button.enabled &= selected.percent < 100;
                if (button.id == MUTE)
                    button.displayString = text(selected != null && selected.muted ? "unmute" : "mute");
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) return;
        if (button.id == BACK) {
            mc.displayGuiScreen(parent);
            return;
        }
        SpeakerControls current = currentControls();
        if (compact || current != displayedControls) {
            refresh();
            return;
        }
        SpeakerPanelModel.Row selected = model.selected();
        if (button.id >= 0 && button.id < rowCount) model.select(button.id);
        else if (button.id == PREVIOUS) model.turnPage(-1);
        else if (button.id == NEXT) model.turnPage(1);
        else if (selected != null) {
            if (button.id == DOWN) model.setPercent(current, Math.max(0, selected.percent - 10));
            else if (button.id == UP) model.setPercent(current, Math.min(100, selected.percent + 10));
            else if (button.id == MUTE) model.toggleMute(current);
            else if (button.id == RESET) model.reset(current);
        }
        updateButtons();
    }

    @Override
    public void updateScreen() {
        if (compact) return;
        search.updateCursorCounter();
        if (++ticks % 10 == 0) refresh();
    }

    @Override
    protected void keyTyped(char character, int keyCode) {
        if (keyCode == 1) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (!compact && search.textboxKeyTyped(character, keyCode)) {
            model.filter(search.getText());
            updateButtons();
        }
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        if (!compact) search.mouseClicked(x, y, button);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        if (compact) return;
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel != 0) {
            model.turnPage(wheel > 0 ? -1 : 1);
            updateButtons();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static String text(String suffix, Object... values) {
        return I18n.format("akashicvoice.gui.speakers." + suffix, values);
    }

    @Override
    public void drawScreen(int x, int y, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, text("title"), width / 2, 16, 0xFFFFFFFF);
        if (compact) {
            drawCenteredString(fontRendererObj, text("small"), width / 2, height / 2, 0xFFDDDDDD);
        } else {
            drawCenteredString(fontRendererObj, text("session"), width / 2, 30, 0xFFAAAAAA);
            search.drawTextBox();
            if (search.getText()
                .isEmpty() && !search.isFocused()) {
                fontRendererObj.drawString(text("search"), panelX + 5, 49, 0xFF777777);
            }
            if (model.rows()
                .isEmpty()) {
                drawCenteredString(
                    fontRendererObj,
                    text(model.connected() ? "empty" : "offline"),
                    width / 2,
                    83,
                    0xFFAAAAAA);
            }
            SpeakerPanelModel.Row selected = model.selected();
            drawCenteredString(
                fontRendererObj,
                selected != null ? selected.id.toString() : text("select"),
                width / 2,
                height - 94,
                0xFFAAAAAA);
            drawCenteredString(
                fontRendererObj,
                text("page", model.pageIndex() + 1, model.pageCount(), model.total()),
                width / 2,
                height - 71,
                0xFFDDDDDD);
        }
        super.drawScreen(x, y, partialTicks);
    }
}
