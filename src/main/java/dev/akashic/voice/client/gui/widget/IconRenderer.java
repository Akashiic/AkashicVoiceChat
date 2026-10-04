package dev.akashic.voice.client.gui.widget;

import org.lwjgl.opengl.GL11;

/**
 * Programmatic icons drawn via GL primitives — no external textures.
 * Each draw method centres its icon in a (size x size) box at (cx, cy).
 */
public final class IconRenderer {

    private IconRenderer() {}

    public static void drawMic(int cx, int cy, int size, boolean muted) {
        float capsuleW = size * 0.34f;
        float capsuleH = size * 0.55f;
        float left = cx - capsuleW / 2f;
        float right = cx + capsuleW / 2f;
        float top = cy - capsuleH / 2f;
        float bot = cy + capsuleH / 2f - size * 0.08f;

        push();
        if (muted) color(0.50f, 0.50f, 0.50f);
        else color(0.90f, 0.90f, 0.90f);
        rect(left, top, right, bot);

        // Stand
        float standW = size * 0.5f;
        rect(cx - standW / 2f, bot + size * 0.04f, cx + standW / 2f, bot + size * 0.10f);

        // Vertical stand line
        rect(cx - size * 0.04f, bot - size * 0.02f, cx + size * 0.04f, bot + size * 0.04f);

        if (muted) {
            color(0.85f, 0.20f, 0.20f);
            // diagonal slash
            GL11.glLineWidth(2f);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(cx - size * 0.4f, cy - size * 0.4f);
            GL11.glVertex2f(cx + size * 0.4f, cy + size * 0.4f);
            GL11.glEnd();
        }
        pop();
    }

    /** Active voice indicator: the same microphone language as the local HUD, with a cyan voice pulse. */
    public static void drawTalkingMic(int cx, int cy, int size) {
        push();
        float w = size * 0.34f;
        float top = cy - size * 0.30f;
        float bot = cy + size * 0.18f;
        color(0.96f, 0.96f, 0.98f);
        rect(cx - w / 2f, top, cx + w / 2f, bot);
        color(0.18f, 0.88f, 0.96f);
        rect(cx - size * 0.28f, bot + size * 0.05f, cx + size * 0.28f, bot + size * 0.11f);
        rect(cx - size * 0.035f, bot, cx + size * 0.035f, bot + size * 0.08f);
        GL11.glLineWidth(Math.max(1f, size * 0.12f));
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(cx + size * 0.30f, cy - size * 0.18f);
        GL11.glVertex2f(cx + size * 0.39f, cy - size * 0.08f);
        GL11.glVertex2f(cx + size * 0.30f, cy + size * 0.04f);
        GL11.glVertex2f(cx + size * 0.39f, cy + size * 0.14f);
        GL11.glEnd();
        pop();
    }

    public static void drawSpeaker(int cx, int cy, int size, boolean muted) {
        push();
        if (muted) color(0.50f, 0.50f, 0.50f);
        else color(0.90f, 0.90f, 0.90f);

        // Body trapezoid
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(cx - size * 0.30f, cy + size * 0.20f);
        GL11.glVertex2f(cx - size * 0.05f, cy + size * 0.20f);
        GL11.glVertex2f(cx - size * 0.05f, cy - size * 0.20f);
        GL11.glVertex2f(cx - size * 0.30f, cy - size * 0.20f);
        GL11.glEnd();

        // Cone
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(cx - size * 0.05f, cy + size * 0.20f);
        GL11.glVertex2f(cx + size * 0.20f, cy + size * 0.35f);
        GL11.glVertex2f(cx + size * 0.20f, cy - size * 0.35f);
        GL11.glVertex2f(cx - size * 0.05f, cy + size * 0.20f);
        GL11.glVertex2f(cx + size * 0.20f, cy - size * 0.35f);
        GL11.glVertex2f(cx - size * 0.05f, cy - size * 0.20f);
        GL11.glEnd();

        if (muted) {
            color(0.85f, 0.20f, 0.20f);
            GL11.glLineWidth(2f);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(cx - size * 0.4f, cy - size * 0.4f);
            GL11.glVertex2f(cx + size * 0.4f, cy + size * 0.4f);
            GL11.glEnd();
        }
        pop();
    }

    public static void drawGroup(int cx, int cy, int size) {
        push();
        color(0.90f, 0.90f, 0.90f);
        // Two heads
        circle(cx - size * 0.18f, cy - size * 0.12f, size * 0.13f, 14);
        circle(cx + size * 0.18f, cy - size * 0.12f, size * 0.13f, 14);
        // Two shoulders (rough rectangles)
        rect(cx - size * 0.34f, cy + size * 0.05f, cx - size * 0.02f, cy + size * 0.30f);
        rect(cx + size * 0.02f, cy + size * 0.05f, cx + size * 0.34f, cy + size * 0.30f);
        pop();
    }

    public static void drawEye(int cx, int cy, int size, boolean enabled) {
        push();
        color(enabled ? 0.95f : 0.55f, enabled ? 0.95f : 0.55f, enabled ? 0.95f : 0.55f);
        // Outer almond (two arcs roughly).
        circle(cx, cy, size * 0.35f, 24);
        // Pupil
        color(0.10f, 0.30f, enabled ? 0.95f : 0.40f);
        circle(cx, cy, size * 0.15f, 18);
        if (!enabled) {
            color(0.85f, 0.20f, 0.20f);
            GL11.glLineWidth(2f);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(cx - size * 0.4f, cy - size * 0.4f);
            GL11.glVertex2f(cx + size * 0.4f, cy + size * 0.4f);
            GL11.glEnd();
        }
        pop();
    }

    public static void drawLeaveArrow(int cx, int cy, int size) {
        push();
        color(0.90f, 0.30f, 0.20f);
        // Doorframe
        GL11.glLineWidth(2f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(cx - size * 0.32f, cy - size * 0.30f);
        GL11.glVertex2f(cx - size * 0.05f, cy - size * 0.30f);
        GL11.glVertex2f(cx - size * 0.05f, cy + size * 0.30f);
        GL11.glVertex2f(cx - size * 0.32f, cy + size * 0.30f);
        GL11.glEnd();
        // Arrow
        rect(cx - size * 0.10f, cy - size * 0.05f, cx + size * 0.20f, cy + size * 0.05f);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(cx + size * 0.20f, cy - size * 0.18f);
        GL11.glVertex2f(cx + size * 0.36f, cy + 0.0f);
        GL11.glVertex2f(cx + size * 0.20f, cy + size * 0.18f);
        GL11.glEnd();
        pop();
    }

    public static void drawAddPerson(int cx, int cy, int size) {
        push();
        color(0.90f, 0.90f, 0.90f);
        circle(cx - size * 0.10f, cy - size * 0.10f, size * 0.13f, 14);
        rect(cx - size * 0.30f, cy + size * 0.05f, cx + size * 0.10f, cy + size * 0.30f);
        // Plus sign
        color(0.30f, 0.85f, 0.30f);
        rect(cx + size * 0.18f, cy - size * 0.05f, cx + size * 0.40f, cy + size * 0.05f);
        rect(cx + size * 0.28f, cy - size * 0.15f, cx + size * 0.32f, cy + size * 0.15f);
        rect(cx + size * 0.24f, cy - size * 0.11f, cx + size * 0.36f, cy + size * 0.11f);
        pop();
    }

    public static void drawConnect(int cx, int cy, int size) {
        push();
        color(0.92f, 0.35f, 0.25f);
        GL11.glLineWidth(2f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(cx - size * 0.28f, cy - size * 0.28f);
        GL11.glVertex2f(cx + size * 0.28f, cy + size * 0.28f);
        GL11.glVertex2f(cx - size * 0.28f, cy + size * 0.28f);
        GL11.glVertex2f(cx + size * 0.28f, cy - size * 0.28f);
        GL11.glEnd();
        color(0.85f, 0.85f, 0.85f);
        rect(cx - size * 0.35f, cy - size * 0.08f, cx - size * 0.10f, cy + size * 0.08f);
        rect(cx + size * 0.10f, cy - size * 0.08f, cx + size * 0.35f, cy + size * 0.08f);
        pop();
    }

    private static void push() {
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void pop() {
        GL11.glLineWidth(1f);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
    }

    private static void color(float r, float g, float b) {
        GL11.glColor4f(r, g, b, 1f);
    }

    private static void rect(float x0, float y0, float x1, float y1) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x0, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x1, y0);
        GL11.glVertex2f(x0, y0);
        GL11.glEnd();
    }

    private static void circle(float cx, float cy, float r, int segments) {
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        for (int i = 0; i <= segments; i++) {
            double a = (i * 2.0 * Math.PI) / segments;
            GL11.glVertex2f(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r);
        }
        GL11.glEnd();
    }
}
