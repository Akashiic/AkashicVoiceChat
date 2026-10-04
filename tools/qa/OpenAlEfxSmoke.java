package dev.akashic.voice.voice.client.playback;

import org.lwjgl.openal.AL;

import dev.akashic.voice.voice.client.acoustics.AcousticProfile;

/** Opens an isolated OpenAL context; never starts Minecraft or microphone capture. */
public final class OpenAlEfxSmoke {
    public static void main(String[] args) throws Exception {
        boolean created = false;
        VoiceEnvironmentEffects effects = null;
        PlaybackChannel channel = null;
        try {
            if (!AL.isCreated()) {
                AL.create();
                created = true;
            }
            effects = VoiceEnvironmentEffects.create();
            System.out.println("EFX_SUPPORTED=" + effects.supported());
            if (effects.supported()) {
                for (AcousticProfile profile : AcousticProfile.values()) {
                    if (!effects.apply(profile) && profile != AcousticProfile.OUTDOOR) {
                        throw new AssertionError("Profile not applied: " + profile);
                    }
                }
                channel = new PlaybackChannel(4f);                channel.setEnvironmentEffects(effects.auxiliarySlot(), effects.directFilter());
                channel.setSpatial(true);
                channel.setPosition(2f, 1f, -3f);
                channel.close();
                channel = null;
            }
            System.out.println("OPENAL_EFX_SMOKE_OK");
        } finally {
            if (channel != null) try { channel.close(); } catch (Throwable ignored) { }
            if (effects != null) effects.close();
            if (created && AL.isCreated()) AL.destroy();
        }
    }
}
