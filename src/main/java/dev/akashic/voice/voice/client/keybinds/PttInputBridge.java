package dev.akashic.voice.voice.client.keybinds;

import net.minecraftforge.client.event.MouseEvent;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import dev.akashic.voice.voice.client.MicGate;

/** Updates physical PTT on raw input events; the client tick remains a fallback/reconciliation path. */
public final class PttInputBridge {

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        syncNow();
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        int code = AkashicVoiceKeybinds.PTT.getKeyCode();
        if (code < 0 && event.button == code + 100) {
            MicGate.setPttPressed(event.buttonstate);
            return;
        }
        syncNow();
    }

    public static void syncNow() {
        MicGate.setPttPressed(isPttDown());
    }

    private static boolean isPttDown() {
        try {
            int code = AkashicVoiceKeybinds.PTT.getKeyCode();
            if (code == 0) return false;
            if (code < 0) return Mouse.isButtonDown(code + 100);
            return Keyboard.isKeyDown(code);
        } catch (Throwable failure) {
            return false;
        }
    }
}
