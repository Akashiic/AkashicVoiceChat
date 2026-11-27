package dev.akashic.voice.voice.client.playback;

import java.util.UUID;

import org.lwjgl.openal.AL;

/**
 * Issue #3 reproducer against REAL OpenAL natives (no Minecraft, no audio hardware required when run with
 * {@code ALSOFT_DRIVERS=null}). The harness plays the part of Minecraft: it owns the OpenAL context, lets
 * Akashic Voice share it, then performs a sound-system reload ({@code AL.destroy()} followed later by
 * {@code AL.create()}) while voice frames keep arriving - the sequence seen in the field logs (F1).
 *
 * <p>
 * Pass criteria: the playback thread survives, voice output recovers on the new context, EFX acoustics are
 * available again, and frames render after recovery. Needs the LWJGL 2 natives on {@code java.library.path}
 * (for example {@code LD_LIBRARY_PATH=run/natives/lwjgl2}). Evidence grade when it passes: AUTOMATED with
 * real natives; it is not a Minecraft/ArchaicFix live observation.
 */
public final class OpenAlReloadReproSmoke {
    static final UUID SPEAKER = new UUID(77, 88);
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }

    public static void main(String[] args) throws Exception {
        AL.create(); // "Minecraft" owns the shared context: Akashic runs with ownsOpenAL=false
        AudioPlayback playback = AudioPlayback.start();
        try {
            pump(playback, 1000);
            check(alive(), "audio thread died before reload");
            check(!playback.isOutputRecovering(), "recovering before reload");
            check(efxSupported(playback), "EFX unavailable before reload (test environment?)");
            long recoveriesBefore = playback.outputRecoveryCount();

            // Minecraft sound-system reload: natives unlinked while voice frames keep arriving.
            AL.destroy();
            pump(playback, 600);
            AL.create();

            long deadline = System.nanoTime() + 15_000_000_000L;
            while (System.nanoTime() < deadline && (playback.isOutputRecovering() || !efxSupported(playback))) {
                pump(playback, 100);
            }
            check(alive(), "audio thread died during reload");
            check(playback.outputRecoveryCount() > recoveriesBefore, "binding loss never triggered recovery");
            check(!playback.isOutputRecovering(), "voice output did not recover on the new context");
            check(efxSupported(playback), "EFX acoustics not restored after reload");

            long droppedBefore = playback.droppedFrameCount();
            pump(playback, 1000);
            check(alive(), "audio thread died after recovery");
            check(!playback.isOutputRecovering(), "recovery re-triggered after the context returned");
            System.out.println("OPENAL_RELOAD_REPRO_OK recoveries=" + (playback.outputRecoveryCount() - recoveriesBefore)
                + " reason=" + playback.outputRecoveryReason()
                + " droppedAfterRecovery=" + (playback.droppedFrameCount() - droppedBefore));
        } finally {
            AudioPlayback.stop();
            Thread.sleep(200L);
            if (AL.isCreated()) AL.destroy();
        }
    }

    /** Submits one 20 ms frame every 20 ms for the given duration. */
    static void pump(AudioPlayback playback, long millis) throws InterruptedException {
        long end = System.currentTimeMillis() + millis;
        long epoch = 1L;
        while (System.currentTimeMillis() < end) {
            PlaybackFrame frame = playback.acquireFrame();
            if (frame != null && !playback.submitFrame(frame, SPEAKER, true, 1f, 0f, 2f, 48f, false, epoch)) {
                playback.releaseFrame(frame);
            }
            Thread.sleep(20L);
        }
    }

    static boolean alive() {
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if ("AkashicVoice-Audio".equals(t.getName()) && t.isAlive()) return true;
        }
        return false;
    }

    static boolean efxSupported(AudioPlayback playback) throws Exception {
        java.lang.reflect.Field f = AudioPlayback.class.getDeclaredField("environmentEffects");
        f.setAccessible(true);
        VoiceEnvironmentEffects effects = (VoiceEnvironmentEffects) f.get(playback);
        return effects != null && effects.supported();
    }
}
