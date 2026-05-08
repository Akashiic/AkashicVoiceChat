package dev.jm.proxychat.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

/**
 * Draws PNG icons via {@link Tessellator} at full UV (0..1). Works regardless of source
 * texture dimensions — unlike vanilla {@code Gui.drawTexturedModalRect} which assumes a
 * 256×256 reference. The icons authored by the user are 2:3 aspect (taller than wide);
 * we centre them inside the requested square box.
 */
public final class TextureIcon {

    public static final ResourceLocation MIC_ON = new ResourceLocation(
        "proxychat",
        "textures/gui/microphone_active.png");
    public static final ResourceLocation MIC_OFF = new ResourceLocation("proxychat", "textures/gui/microphone_off.png");
    public static final ResourceLocation SPEAKER_ON = new ResourceLocation(
        "proxychat",
        "textures/gui/sound_active.png");
    public static final ResourceLocation SPEAKER_OFF = new ResourceLocation("proxychat", "textures/gui/sound_off.png");

    // Source aspect: 2 wide : 3 tall.
    private static final float ASPECT_W = 2f;
    private static final float ASPECT_H = 3f;

    private TextureIcon() {}

    public static void draw(ResourceLocation tex, int cx, int cy, int boxSize) {
        // Fit the 2:3 icon inside a (boxSize × boxSize) box, preserving aspect.
        float iconH = boxSize * 0.95f;
        float iconW = iconH * (ASPECT_W / ASPECT_H);
        if (iconW > boxSize * 0.95f) {
            iconW = boxSize * 0.95f;
            iconH = iconW * (ASPECT_H / ASPECT_W);
        }

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(tex);

        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        float x0 = cx - iconW / 2f;
        float y0 = cy - iconH / 2f;
        float x1 = cx + iconW / 2f;
        float y1 = cy + iconH / 2f;

        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x0, y1, 0, 0.0, 1.0);
        t.addVertexWithUV(x1, y1, 0, 1.0, 1.0);
        t.addVertexWithUV(x1, y0, 0, 1.0, 0.0);
        t.addVertexWithUV(x0, y0, 0, 0.0, 0.0);
        t.draw();

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    public static void drawMic(int cx, int cy, int boxSize, boolean muted) {
        draw(muted ? MIC_OFF : MIC_ON, cx, cy, boxSize);
    }

    public static void drawSpeaker(int cx, int cy, int boxSize, boolean muted) {
        draw(muted ? SPEAKER_OFF : SPEAKER_ON, cx, cy, boxSize);
    }
}
