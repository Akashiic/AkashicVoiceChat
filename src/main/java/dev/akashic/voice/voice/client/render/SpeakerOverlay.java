package dev.akashic.voice.voice.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderLivingEvent;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dev.akashic.voice.client.gui.widget.IconRenderer;
import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.client.talk.TalkCache;

/** Renders Akashic's active-mic glyph as a real suffix of the vanilla player nametag. */
public final class SpeakerOverlay {

    private static final float NAMEPLATE_SCALE = 0.016666668F * 1.6F;
    private static final int ICON_SIZE = 11;
    private static final int ICON_GAP = 3;
    private static final int ICON_BASELINE_Y = 3;

    @SubscribeEvent
    public void onRenderNametag(RenderLivingEvent.Specials.Post event) {
        if (!(event.entity instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.entity;
        if (!AkashicVoiceConfig.showSpeakerIndicators() || !TalkCache.isSpeaking(player.getUniqueID())) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!Minecraft.isGuiEnabled() || mc.thePlayer == null) return;
        if (player == RenderManager.instance.livingPlayer || player.isInvisibleToPlayer(mc.thePlayer)) return;
        if (player.riddenByEntity != null) return;
        float nameRange = player.isSneaking() ? RendererLivingEntity.NAME_TAG_RANGE_SNEAK
            : RendererLivingEntity.NAME_TAG_RANGE;
        if (player.getDistanceSqToEntity(mc.thePlayer) >= (double) (nameRange * nameRange)) return;

        String label = player.func_145748_c_()
            .getFormattedText();
        FontRenderer font = mc.fontRenderer;
        int halfName = font.getStringWidth(label) / 2;
        int iconX = halfName + ICON_GAP + ICON_SIZE / 2;
        int iconY = !player.isSneaking() && "deadmau5".equals(label) ? ICON_BASELINE_Y - 10 : ICON_BASELINE_Y;

        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_LINE_BIT);
        GL11.glPushMatrix();
        // Match vanilla 1.7.10, including the sleeping nameplate origin adjustment.
        double vanillaY = event.y - (player.isPlayerSleeping() ? 1.5D : 0.0D);
        GL11.glTranslatef((float) event.x, (float) vanillaY + player.height + 0.5F, (float) event.z);
        GL11.glNormal3f(0F, 1F, 0F);
        GL11.glRotatef(-RenderManager.instance.playerViewY, 0F, 1F, 0F);
        GL11.glRotatef(RenderManager.instance.playerViewX, 1F, 0F, 0F);
        GL11.glScalef(-NAMEPLATE_SCALE, -NAMEPLATE_SCALE, NAMEPLATE_SCALE);
        if (player.isSneaking()) GL11.glTranslatef(0F, 0.25F / NAMEPLATE_SCALE, 0F);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        IconRenderer.drawTalkingMic(iconX, iconY, ICON_SIZE);

        GL11.glDepthMask(true);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
        RenderHelper.enableStandardItemLighting();
    }
}
