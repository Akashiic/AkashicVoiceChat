package dev.akashic.voice.voice.client.playback;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCcontext;
import org.lwjgl.openal.ALCdevice;
import org.lwjgl.openal.EFX10;

import dev.akashic.voice.AkashicVoice;
import dev.akashic.voice.voice.client.acoustics.AcousticProfile;

/** Owns the OpenAL EFX objects used only by Akashic Voice. Audio-thread confined. */
final class VoiceEnvironmentEffects {

    private final boolean supported;
    private final int effectId;
    private final int slotId;
    private final int filterId;
    private AcousticProfile applied;

    private VoiceEnvironmentEffects(boolean supported, int effectId, int slotId, int filterId) {
        this.supported = supported;
        this.effectId = effectId;
        this.slotId = slotId;
        this.filterId = filterId;
    }

    static VoiceEnvironmentEffects create() {
        try {
            ALCcontext context = ALC10.alcGetCurrentContext();
            ALCdevice device = context == null ? null : ALC10.alcGetContextsDevice(context);
            if (device == null || !ALC10.alcIsExtensionPresent(device, EFX10.ALC_EXT_EFX_NAME)) {
                AkashicVoice.LOG.info("OpenAL EFX unavailable; environmental voice acoustics disabled");
                return new VoiceEnvironmentEffects(false, 0, 0, 0);
            }
            int effect = EFX10.alGenEffects();
            int slot = EFX10.alGenAuxiliaryEffectSlots();
            int filter = EFX10.alGenFilters();
            try {
                EFX10.alEffecti(effect, EFX10.AL_EFFECT_TYPE, EFX10.AL_EFFECT_REVERB);
                EFX10.alFilteri(filter, EFX10.AL_FILTER_TYPE, EFX10.AL_FILTER_LOWPASS);
                VoiceEnvironmentEffects effects = new VoiceEnvironmentEffects(true, effect, slot, filter);
                effects.apply(AcousticProfile.OUTDOOR);
                AkashicVoice.LOG.info("OpenAL EFX enabled for environmental voice acoustics");
                return effects;
            } catch (RuntimeException | LinkageError failure) {
                try {
                    EFX10.alDeleteFilters(filter);
                } catch (Throwable ignored) {}
                try {
                    EFX10.alDeleteAuxiliaryEffectSlots(slot);
                } catch (Throwable ignored) {}
                try {
                    EFX10.alDeleteEffects(effect);
                } catch (Throwable ignored) {}
                throw failure;
            }
        } catch (RuntimeException | LinkageError failure) {
            AkashicVoice.LOG.warn("OpenAL EFX probe failed; environmental voice acoustics disabled", failure);
            return new VoiceEnvironmentEffects(false, 0, 0, 0);
        }
    }

    boolean supported() {
        return supported;
    }

    int auxiliarySlot() {
        return supported ? slotId : 0;
    }

    int directFilter() {
        return supported ? filterId : 0;
    }

    boolean ensureApplied(AcousticProfile profile) {
        if (!supported || profile == null) return false;
        return profile == applied || apply(profile);
    }

    boolean apply(AcousticProfile profile) {
        if (!supported || profile == null || profile == applied) return false;
        try {
            EFX10.alEffectf(effectId, EFX10.AL_REVERB_DENSITY, profile.density);
            EFX10.alEffectf(effectId, EFX10.AL_REVERB_DIFFUSION, profile.diffusion);
            EFX10.alEffectf(effectId, EFX10.AL_REVERB_GAIN, profile.reverbGain);
            EFX10.alEffectf(effectId, EFX10.AL_REVERB_GAINHF, profile.reverbGainHf);
            EFX10.alEffectf(effectId, EFX10.AL_REVERB_DECAY_TIME, profile.decayTime);
            EFX10.alEffectf(effectId, EFX10.AL_REVERB_DECAY_HFRATIO, 0.75f);
            EFX10.alEffectf(effectId, EFX10.AL_REVERB_REFLECTIONS_GAIN, profile.reflectionsGain);
            EFX10.alEffectf(effectId, EFX10.AL_REVERB_REFLECTIONS_DELAY, 0.018f);
            EFX10.alEffectf(effectId, EFX10.AL_REVERB_LATE_REVERB_GAIN, profile.lateGain);
            EFX10.alEffectf(effectId, EFX10.AL_REVERB_LATE_REVERB_DELAY, profile.lateDelay);
            EFX10.alEffecti(effectId, EFX10.AL_REVERB_DECAY_HFLIMIT, AL10.AL_TRUE);
            EFX10.alFilterf(filterId, EFX10.AL_LOWPASS_GAIN, profile.directGain);
            EFX10.alFilterf(filterId, EFX10.AL_LOWPASS_GAINHF, profile.directGainHf);
            EFX10.alAuxiliaryEffectSloti(slotId, EFX10.AL_EFFECTSLOT_EFFECT, effectId);
            EFX10.alAuxiliaryEffectSlotf(slotId, EFX10.AL_EFFECTSLOT_GAIN, profile.wetGain);
            applied = profile;
            return true;
        } catch (RuntimeException | LinkageError failure) {
            AkashicVoice.LOG.warn("Environmental voice effect update failed; retaining prior profile", failure);
            return false;
        }
    }

    void close() {
        if (!supported) return;
        try {
            EFX10.alAuxiliaryEffectSloti(slotId, EFX10.AL_EFFECTSLOT_EFFECT, EFX10.AL_EFFECTSLOT_NULL);
        } catch (Throwable ignored) {}
        try {
            EFX10.alDeleteFilters(filterId);
        } catch (Throwable ignored) {}
        try {
            EFX10.alDeleteAuxiliaryEffectSlots(slotId);
        } catch (Throwable ignored) {}
        try {
            EFX10.alDeleteEffects(effectId);
        } catch (Throwable ignored) {}
    }
}
