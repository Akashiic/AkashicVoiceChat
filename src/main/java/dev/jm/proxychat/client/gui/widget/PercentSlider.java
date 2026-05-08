package dev.jm.proxychat.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;

/**
 * Slider that shows a label as "Name: NNN%". Range [min, max] mapped to a percentage
 * (max maps to 200% if you want to allow boosts).
 */
public class PercentSlider extends GuiButton {

    private final String labelKey;
    private final double min;
    private final double max;
    private double value;
    private boolean dragging;

    public PercentSlider(int id, int x, int y, int w, int h, String labelKey, double initial, double min, double max) {
        super(id, x, y, w, h, "");
        this.labelKey = labelKey;
        this.min = min;
        this.max = max;
        this.value = clamp(initial);
        this.displayString = render();
    }

    public double getValue() {
        return value;
    }

    public void setValue(double v) {
        value = clamp(v);
        displayString = render();
    }

    private double clamp(double v) {
        return Math.max(min, Math.min(max, v));
    }

    private String render() {
        return I18n.format(labelKey) + ": " + ((int) Math.round((value / 1.0) * 100)) + "%";
    }

    @Override
    public int getHoverState(boolean hover) {
        return 0;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) return;
        if (dragging) updateFromMouse(mouseX);
        super.drawButton(mc, mouseX, mouseY);
        // Knob
        int knobX = xPosition + (int) ((value - min) / (max - min) * (width - 8));
        drawRect(knobX, yPosition, knobX + 8, yPosition + height, 0xFFFFFFFF);
        drawRect(knobX + 1, yPosition + 1, knobX + 7, yPosition + height - 1, 0xFF7F7F7F);
        mc.fontRenderer.drawStringWithShadow(
            displayString,
            xPosition + width / 2 - mc.fontRenderer.getStringWidth(displayString) / 2,
            yPosition + (height - 8) / 2,
            0xFFFFFF);
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (super.mousePressed(mc, mouseX, mouseY)) {
            dragging = true;
            updateFromMouse(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        dragging = false;
    }

    private void updateFromMouse(int mouseX) {
        double frac = (mouseX - (xPosition + 4)) / (double) (width - 8);
        if (frac < 0) frac = 0;
        if (frac > 1) frac = 1;
        value = min + frac * (max - min);
        displayString = render();
    }
}
