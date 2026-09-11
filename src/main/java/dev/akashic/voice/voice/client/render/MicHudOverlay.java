package dev.akashic.voice.voice.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dev.akashic.voice.client.gui.widget.IconRenderer;
import dev.akashic.voice.voice.client.MicGate;
import dev.akashic.voice.voice.client.VoiceClient;
import dev.akashic.voice.voice.client.VoiceClientHolder;

/**
 * Heads-up indicators stacked at bottom-right:
 * - Connection-failed icon (top-most): visible whenever the voice client has been in
 * a world long enough to expect a UDP connection but is not authenticated. Helps
 * diagnose firewall / port-forwarding / Docker-port-mapping problems on sight.
 * - Mic indicator (below): visible while transmitting (a frame was sent within the
 * last 250 ms) or while the mic is muted.
 *
 * Both are hidden while a {@code GuiScreen} is open so they don't fight the menu.
 */
public final class MicHudOverlay {

    private static final long ACTIVE_WINDOW_MILLIS = 250L;
    private static final long CONNECT_GRACE_MILLIS = 5_000L;
    private static final int ICON_SIZE = 24;
    private static final int MARGIN = 8;
    private static final int GAP = 4;

    private long playerSeenSinceMillis = 0L;

    @SubscribeEvent
    public void onRenderHud(RenderGameOverlayEvent.Post event) {
        if (event.type != ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            playerSeenSinceMillis = 0L;
            return;
        }
        if (mc.currentScreen != null) return;

        long now = System.currentTimeMillis();
        if (playerSeenSinceMillis == 0L) playerSeenSinceMillis = now;

        boolean muted = MicGate.isMuted();
        long sinceTx = now - MicGate.lastTransmitMillis();
        boolean transmitting = !muted && sinceTx < ACTIVE_WINDOW_MILLIS;
        boolean showMic = muted || transmitting;

        VoiceClient vc = VoiceClientHolder.get();
        boolean voiceConnected = vc != null && vc.isAuthenticated();
        boolean showConnectFail = !voiceConnected && (now - playerSeenSinceMillis) > CONNECT_GRACE_MILLIS;

        if (!showMic && !showConnectFail) return;

        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();

        // Save GL state — vanilla HUD leaves things in unexpected state sometimes.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();

        int cx = sw - MARGIN - ICON_SIZE / 2;
        int cy = sh - MARGIN - ICON_SIZE / 2;

        if (showMic) {
            IconRenderer.drawMic(cx, cy, ICON_SIZE, muted);
            cy -= ICON_SIZE + GAP;
        }
        if (showConnectFail) {
            IconRenderer.drawConnect(cx, cy, ICON_SIZE);
        }

        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }
}
