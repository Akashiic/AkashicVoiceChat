package dev.jm.proxychat.client.gui.widget;

import net.minecraft.client.gui.Gui;

import org.lwjgl.opengl.GL11;

import dev.jm.proxychat.config.ProxyChatConfig;

/**
 * Horizontal level meter rendered on a dB scale (the audio-correct way) so quiet
 * speech actually registers visually. Range -60..0 dBFS mapped to the bar width.
 *
 * Two display modes:
 * - PLAIN: solid green→yellow→red ramp.
 * - GRADIENT_BACKGROUND: a static green→yellow gradient is drawn underneath
 * the (clipping) bar, so the user can see where their voice falls relative
 * to the activation threshold (matches SVC's threshold-slider visualisation).
 */
public final class LevelMeterWidget extends Gui {

    public enum Style {
        PLAIN,
        GRADIENT_BACKGROUND
    }

    private static final double DB_MIN = -60.0;
    private static final double DB_MAX = 0.0;

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final Style style;

    public LevelMeterWidget(int x, int y, int width, int height) {
        this(x, y, width, height, Style.PLAIN);
    }

    public LevelMeterWidget(int x, int y, int width, int height, Style style) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.style = style;
    }

    public void draw(double rms) {
        // Frame
        drawRect(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF222222);
        drawRect(x, y, x + width, y + height, 0xFF000000);

        if (style == Style.GRADIENT_BACKGROUND) {
            drawGradientBackground();
        }

        double db = ProxyChatConfig.rmsToDb(rms);
        double frac = (db - DB_MIN) / (DB_MAX - DB_MIN);
        if (frac < 0.0) frac = 0.0;
        if (frac > 1.0) frac = 1.0;
        int fillW = (int) Math.round(frac * width);
        if (fillW <= 0) return;

        if (style == Style.GRADIENT_BACKGROUND) {
            // Just darken the unused portion via overlay so the gradient shows through filled area.
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(0f, 0f, 0f, 0.55f);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2i(x + fillW, y + height);
            GL11.glVertex2i(x + width, y + height);
            GL11.glVertex2i(x + width, y);
            GL11.glVertex2i(x + fillW, y);
            GL11.glEnd();
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            return;
        }

        // PLAIN mode: ramp colour by level.
        float r;
        float g;
        float b = 0.10f;
        if (frac < 0.5) {
            r = 0.20f;
            g = 0.85f;
        } else if (frac < 0.8) {
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

    private void drawGradientBackground() {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBegin(GL11.GL_QUADS);
        // green left -> yellow right (matches SVC's threshold-slider track).
        GL11.glColor4f(0.20f, 0.85f, 0.10f, 1.0f);
        GL11.glVertex2i(x, y + height);
        GL11.glVertex2i(x, y);
        GL11.glColor4f(0.95f, 0.85f, 0.10f, 1.0f);
        GL11.glVertex2i(x + width, y);
        GL11.glVertex2i(x + width, y + height);
        GL11.glEnd();
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
}
