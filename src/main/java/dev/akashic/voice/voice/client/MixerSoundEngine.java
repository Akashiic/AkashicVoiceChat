package dev.akashic.voice.voice.client;

import java.lang.reflect.Method;

import cpw.mods.fml.common.Loader;

/**
 * Asks AkashicMixer (optional, no compile-time dependency) whether it restarts Minecraft's sound engine by itself after
 * the audio device is lost and comes back ({@code AkashicMixerApi.recoversSoundEngine()}). When it does, voice output
 * recovery waits for the shared OpenAL context instead of issuing its own sound reload: that reload is a full resource
 * reload, repeated every {@code OutputRecoveryController.SHARED_RETRY_NANOS} while no device exists, which freezes the
 * game each time, and two mods restarting the engine undo each other's work. Client thread.
 */
final class MixerSoundEngine {

    private static boolean resolved;
    private static Method recovers;

    private MixerSoundEngine() {}

    static boolean recoversSoundEngine() {
        if (!resolved) {
            resolved = true;
            try {
                if (Loader.isModLoaded("akashicmixer")) {
                    recovers = Class.forName("com.akashiic.mixer.api.AkashicMixerApi")
                        .getMethod("recoversSoundEngine");
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // An AkashicMixer without engine recovery: keep the controlled reload.
            }
        }
        if (recovers == null) return false;
        try {
            return Boolean.TRUE.equals(recovers.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }
}
