package dev.jm.proxychat.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

/**
 * Draws square PNG icons via {@link Tessellator} at full UV (0..1). Defensive about GL
 * state so the icons render reliably both inside our GUIs and in the HUD overlay
 * context (where colour/blending may be in unexpected states after vanilla draws).
 */
public final class TextureIcon {

    public static final ResourceLocation MIC_ON = new ResourceLocation("proxychat", "textures/gui/mic_on.png");
    public static final ResourceLocation MIC_OFF = new ResourceLocation(
        "proxychat",
        "textures/gui/microphone_mute.png");
    public static final ResourceLocation SPEAKER_ON = new ResourceLocation("proxychat", "textures/gui/volume_on.png");
    public static final ResourceLocation SPEAKER_OFF = new ResourceLocation("proxychat", "textures/gui/volume_off.png");
    public static final ResourceLocation CONNECT = new ResourceLocation("proxychat", "textures/gui/connect.png");

    private TextureIcon() {}

    public static void draw(ResourceLocation tex, int cx, int cy, int boxSize) {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(tex);

        // Defensive state: ensure 2D textures + alpha blending are on, colour reset to white.
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glDisable(GL11.GL_LIGHTING);

        float half = boxSize * 0.5f;
        float x0 = cx - half;
        float y0 = cy - half;
        float x1 = cx + half;
        float y1 = cy + half;

        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x0, y1, 0, 0.0, 1.0);
        t.addVertexWithUV(x1, y1, 0, 1.0, 1.0);
        t.addVertexWithUV(x1, y0, 0, 1.0, 0.0);
        t.addVertexWithUV(x0, y0, 0, 0.0, 0.0);
        t.draw();

        GL11.glDisable(GL11.GL_BLEND);
    }

    public static void drawMic(int cx, int cy, int boxSize, boolean muted) {
        draw(muted ? MIC_OFF : MIC_ON, cx, cy, boxSize);
    }

    public static void drawSpeaker(int cx, int cy, int boxSize, boolean muted) {
        draw(muted ? SPEAKER_OFF : SPEAKER_ON, cx, cy, boxSize);
    }

    public static void drawConnect(int cx, int cy, int boxSize) {
        draw(CONNECT, cx, cy, boxSize);
    }
}
