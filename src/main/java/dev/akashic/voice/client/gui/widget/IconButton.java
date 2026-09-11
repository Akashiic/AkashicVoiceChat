package dev.akashic.voice.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/**
 * Square button that delegates icon drawing to a callback. The button still uses the
 * vanilla widget background (so it feels at home next to other GuiButtons), and we
 * paint the icon on top in the centre.
 */
public class IconButton extends GuiButton {

    public interface IconDrawer {

        void draw(int cx, int cy, int size);
    }

    private final IconDrawer drawer;

    public IconButton(int id, int x, int y, int size, IconDrawer drawer) {
        super(id, x, y, size, size, "");
        this.drawer = drawer;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        super.drawButton(mc, mouseX, mouseY);
        if (visible && drawer != null) {
            int cx = xPosition + width / 2;
            int cy = yPosition + height / 2;
            drawer.draw(cx, cy, Math.min(width, height));
        }
    }
}
