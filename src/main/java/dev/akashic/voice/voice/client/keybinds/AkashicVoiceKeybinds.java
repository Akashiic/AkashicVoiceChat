package dev.akashic.voice.voice.client.keybinds;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;

public final class AkashicVoiceKeybinds {

    public static final String CATEGORY = "key.akashicvoice.category";

    public static final KeyBinding PTT = new KeyBinding("key.akashicvoice.ptt", Keyboard.KEY_V, CATEGORY);
    public static final KeyBinding MUTE_TOGGLE = new KeyBinding("key.akashicvoice.mute", Keyboard.KEY_N, CATEGORY);
    public static final KeyBinding OPEN_SETTINGS = new KeyBinding(
        "key.akashicvoice.openSettings",
        Keyboard.KEY_O,
        CATEGORY);

    private AkashicVoiceKeybinds() {}

    public static void register() {
        ClientRegistry.registerKeyBinding(PTT);
        ClientRegistry.registerKeyBinding(MUTE_TOGGLE);
        ClientRegistry.registerKeyBinding(OPEN_SETTINGS);
    }
}
