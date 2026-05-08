package dev.jm.proxychat.voice.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dev.jm.proxychat.client.gui.widget.TextureIcon;
import dev.jm.proxychat.voice.client.MicGate;

/**
 * Heads-up indicator: persistent small mic icon at bottom-right of the screen.
 * Visible while transmitting (a frame was sent within the last 250 ms) or while
 * the mic is muted. Hidden otherwise so it doesn't clutter the HUD.
 */
public final class MicHudOverlay {

    private static final long ACTIVE_WINDOW_MILLIS = 250L;
    private static final int ICON_HEIGHT = 28;
    private static final int MARGIN = 8;

    @SubscribeEvent
    public void onRenderHud(RenderGameOverlayEvent.Post event) {
        if (event.type != ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        if (mc.currentScreen != null) return;

        boolean muted = MicGate.isMuted();
        long sinceTx = System.currentTimeMillis() - MicGate.lastTransmitMillis();
        boolean transmitting = !muted && sinceTx < ACTIVE_WINDOW_MILLIS;
        if (!muted && !transmitting) return;

        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();

        // Icon centre: bottom-right, with margin.
        int cx = sw - MARGIN - ICON_HEIGHT * 2 / 3 / 2 - 2;
        int cy = sh - MARGIN - ICON_HEIGHT / 2;

        TextureIcon.drawMic(cx, cy, ICON_HEIGHT, muted);
    }
}
