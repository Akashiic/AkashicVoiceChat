package dev.jm.proxychat.voice.client.render;

import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraftforge.client.event.RenderPlayerEvent;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dev.jm.proxychat.voice.client.talk.TalkCache;

public final class SpeakerOverlay {

    @SubscribeEvent
    public void onRenderPlayer(RenderPlayerEvent.Specials.Post event) {
        if (event.entityPlayer == null) return;
        if (!TalkCache.isSpeaking(event.entityPlayer.getUniqueID())) return;

        float headOffset = event.entityPlayer.height + 0.5f;

        GL11.glPushMatrix();
        GL11.glTranslatef(0f, headOffset, 0f);
        GL11.glNormal3f(0f, 1f, 0f);
        GL11.glRotatef(-RenderManager.instance.playerViewY, 0f, 1f, 0f);
        GL11.glRotatef(RenderManager.instance.playerViewX, 1f, 0f, 0f);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        float r = 0.20f;
        float g = 0.95f;
        float b = 0.30f;
        float size = 0.12f;

        GL11.glColor4f(r, g, b, 0.85f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3f(-size, -size, 0f);
        GL11.glVertex3f(size, -size, 0f);
        GL11.glVertex3f(size, size, 0f);
        GL11.glVertex3f(-size, size, 0f);
        GL11.glEnd();

        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();

        RenderHelper.enableStandardItemLighting();
    }
}
