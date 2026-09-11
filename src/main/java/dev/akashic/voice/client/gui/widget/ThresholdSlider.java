package dev.akashic.voice.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;

import org.lwjgl.opengl.GL11;

/**
 * Slider for the voice activation threshold. Track is drawn as a green→yellow gradient
 * so the user can visually see "louder = closer to clipping". Knob position picks the
 * dB value in [-80, 0].
 */
public class ThresholdSlider extends GuiButton {

    private static final double MIN_DB = -80.0;
    private static final double MAX_DB = 0.0;

    private double valueDb;
    private boolean dragging;

    public ThresholdSlider(int id, int x, int y, int w, int h, double initialDb) {
        super(id, x, y, w, h, "");
        this.valueDb = clamp(initialDb);
        this.displayString = render();
    }

    public double getValue() {
        return valueDb;
    }

    private double clamp(double db) {
        return Math.max(MIN_DB, Math.min(MAX_DB, db));
    }

    private String render() {
        return I18n.format("akashicvoice.gui.set.threshold") + ": " + ((int) Math.round(valueDb)) + " dB";
    }

    @Override
    public int getHoverState(boolean hover) {
        return 0;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) return;
        if (dragging) updateFromMouse(mouseX);

        // Frame
        drawRect(xPosition - 1, yPosition - 1, xPosition + width + 1, yPosition + height + 1, 0xFF222222);
        drawRect(xPosition, yPosition, xPosition + width, yPosition + height, 0xFF000000);

        // Gradient track up to the knob position; dark to the right.
        double frac = (valueDb - MIN_DB) / (MAX_DB - MIN_DB);
        if (frac < 0) frac = 0;
        if (frac > 1) frac = 1;
        int knobX = xPosition + (int) (frac * (width - 4));

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(0.20f, 0.85f, 0.10f, 1f);
        GL11.glVertex2f(xPosition + 1, yPosition + height - 1);
        GL11.glVertex2f(xPosition + 1, yPosition + 1);
        GL11.glColor4f(0.95f, 0.85f, 0.10f, 1f);
        GL11.glVertex2f(knobX, yPosition + 1);
        GL11.glVertex2f(knobX, yPosition + height - 1);
        GL11.glEnd();
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        // Knob
        drawRect(knobX, yPosition, knobX + 4, yPosition + height, 0xFFFFFFFF);
        drawRect(knobX + 1, yPosition + 1, knobX + 3, yPosition + height - 1, 0xFF7F7F7F);

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
        double frac = (mouseX - (xPosition + 2)) / (double) (width - 4);
        if (frac < 0) frac = 0;
        if (frac > 1) frac = 1;
        valueDb = MIN_DB + frac * (MAX_DB - MIN_DB);
        displayString = render();
    }
}
