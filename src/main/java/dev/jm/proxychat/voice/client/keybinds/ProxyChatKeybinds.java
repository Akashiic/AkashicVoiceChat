package dev.jm.proxychat.voice.client.keybinds;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;

public final class ProxyChatKeybinds {

    public static final String CATEGORY = "key.proxychat.category";

    public static final KeyBinding PTT = new KeyBinding("key.proxychat.ptt", Keyboard.KEY_V, CATEGORY);
    public static final KeyBinding MUTE_TOGGLE = new KeyBinding("key.proxychat.mute", Keyboard.KEY_N, CATEGORY);
    public static final KeyBinding OPEN_SETTINGS = new KeyBinding(
        "key.proxychat.openSettings",
        Keyboard.KEY_O,
        CATEGORY);

    private ProxyChatKeybinds() {}

    public static void register() {
        ClientRegistry.registerKeyBinding(PTT);
        ClientRegistry.registerKeyBinding(MUTE_TOGGLE);
        ClientRegistry.registerKeyBinding(OPEN_SETTINGS);
    }
}
