package dev.jm.proxychat.client.gui.widget;

import net.minecraft.client.gui.Gui;

import org.lwjgl.opengl.GL11;

/**
 * Horizontal RMS bar. Fill colour ramps green → yellow → red as the level rises.
 * RMS is in [0, 1]; values above ~0.4 are unusual for a normal voice.
 */
public final class LevelMeterWidget extends Gui {

    private final int x;
    private final int y;
    private final int width;
    private final int height;

    public LevelMeterWidget(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void draw(double rms) {
        // Frame
        drawRect(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF222222);
        drawRect(x, y, x + width, y + height, 0xFF000000);

        double clamped = Math.max(0.0, Math.min(1.0, rms * 2.5));
        int fillW = (int) Math.round(clamped * width);
        if (fillW <= 0) return;

        // Gradient: 0..0.5 = green, 0.5..0.8 = yellow, 0.8..1 = red
        float r;
        float g;
        float b = 0.10f;
        if (clamped < 0.5) {
            r = 0.20f;
            g = 0.85f;
        } else if (clamped < 0.8) {
            r = 0.95f;
            g = 0.85f;
        } else {
            r = 0.95f;
            g = 0.25f;
        }

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(r, g, b, 1.0f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2i(x, y + height);
        GL11.glVertex2i(x + fillW, y + height);
        GL11.glVertex2i(x + fillW, y);
        GL11.glVertex2i(x, y);
        GL11.glEnd();
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
}
