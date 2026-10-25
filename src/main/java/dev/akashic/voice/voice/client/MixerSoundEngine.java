package dev.akashic.voice.voice.client;

import java.lang.reflect.Method;

import cpw.mods.fml.common.Loader;

/**
 * Asks AkashicMixer (optional, no compile-time dependency) whether it restarts Minecraft's sound engine by itself after
 * the audio device is lost and comes back ({@code AkashicMixerApi.recoversSoundEngine()}). When it does, voice output
 * recovery waits for the shared OpenAL context instead of issuing its own sound reload: that reload is a full resource
 * reload, repeated every {@code OutputRecoveryController.SHARED_RETRY_NANOS} while no device exists, which freezes the
 * game each time, and two mods restarting the engine undo each other's work. The same holds for a Windows
 * default-device change once AkashicMixer follows it ({@code followsDefaultDevice()}): its device check re-enumerates
 * OpenAL's device list, which is what lets {@code OpenAlOutputHealth} see {@code DEFAULT_CHANGED} at all. Client
 * thread.
 */
final class MixerSoundEngine {

    private static boolean resolved;
    private static Method recovers;
    private static Method followsDefault;

    private MixerSoundEngine() {}

    static boolean recoversSoundEngine() {
        if (!resolved) {
            resolved = true;
            try {
                if (Loader.isModLoaded("akashicmixer")) {
                    Class<?> api = Class.forName("com.akashiic.mixer.api.AkashicMixerApi");
                    recovers = api.getMethod("recoversSoundEngine");
                    try {
                        followsDefault = api.getMethod("followsDefaultDevice");
                    } catch (NoSuchMethodException older) {
                        // An AkashicMixer that recovers but does not follow the default device.
                    }
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // An AkashicMixer without engine recovery: keep the controlled reload.
            }
        }
        return ask(recovers);
    }

    static boolean followsDefaultDevice() {
        recoversSoundEngine();
        return ask(followsDefault);
    }

    private static boolean ask(Method m) {
        if (m == null) return false;
        try {
            return Boolean.TRUE.equals(m.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }
}
