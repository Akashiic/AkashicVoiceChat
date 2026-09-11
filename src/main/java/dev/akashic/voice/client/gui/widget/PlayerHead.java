package dev.akashic.voice.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

/**
 * Renders the front of a player's MC skin (the head, UV 8..16 / 8..16 of the legacy
 * 64x64 skin layout) at an arbitrary screen-space size. Falls back to the default
 * Steve skin when we don't have the player's skin texture loaded yet.
 */
public final class PlayerHead {

    private static final ResourceLocation DEFAULT_SKIN = AbstractClientPlayer.locationStevePng;

    private PlayerHead() {}

    public static void draw(String playerName, int x, int y, int size) {
        ResourceLocation skin = resolveSkin(playerName);
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(skin);

        // Two layers: base head face (UV 8..16, 8..16) and hat (UV 40..48, 8..16).
        drawFace(x, y, size, 8, 8, 16, 16);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawFace(x, y, size, 40, 8, 48, 16);
        GL11.glDisable(GL11.GL_BLEND);
    }

    private static void drawFace(int x, int y, int size, int u0, int v0, int u1, int v1) {
        // Skin is 64x64 → divide UVs by 64 to get 0..1.
        float fu0 = u0 / 64f;
        float fv0 = v0 / 64f;
        float fu1 = u1 / 64f;
        float fv1 = v1 / 64f;
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x, y + size, 0, fu0, fv1);
        t.addVertexWithUV(x + size, y + size, 0, fu1, fv1);
        t.addVertexWithUV(x + size, y, 0, fu1, fv0);
        t.addVertexWithUV(x, y, 0, fu0, fv0);
        t.draw();
    }

    private static ResourceLocation resolveSkin(String name) {
        try {
            World w = Minecraft.getMinecraft().theWorld;
            if (w != null) {
                AbstractClientPlayer p = (AbstractClientPlayer) w.getPlayerEntityByName(name);
                if (p != null) return p.getLocationSkin();
            }
        } catch (Throwable ignored) {
            // ignored — fall back below
        }
        return DEFAULT_SKIN;
    }

    @SuppressWarnings("unused")
    private static void unusedSilenceGui(Gui ignored) {} // placeholder so import chain stays consistent
}
