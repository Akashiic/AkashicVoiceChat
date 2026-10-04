package dev.akashic.voice.voice.client.acoustics;

/** Listener-environment presets applied only to Akashic Voice sources. */
public enum AcousticProfile {

    OUTDOOR(0.00f, 0.20f, 0.90f, 0.18f, 0.90f, 0.45f, 0.08f, 0.70f, 0.015f, 1.00f, 1.00f),
    ROOM(0.24f, 0.78f, 0.82f, 0.26f, 0.72f, 0.90f, 0.18f, 0.95f, 0.020f, 1.00f, 0.92f),
    CAVE(0.46f, 1.00f, 0.96f, 0.36f, 0.56f, 2.80f, 0.42f, 1.35f, 0.045f, 1.00f, 0.78f),
    UNDERWATER(0.13f, 0.88f, 0.75f, 0.18f, 0.25f, 1.35f, 0.10f, 0.65f, 0.028f, 0.82f, 0.12f);

    public final float wetGain;
    public final float density;
    public final float diffusion;
    public final float reverbGain;
    public final float reverbGainHf;
    public final float decayTime;
    public final float reflectionsGain;
    public final float lateGain;
    public final float lateDelay;
    public final float directGain;
    public final float directGainHf;

    AcousticProfile(float wetGain, float density, float diffusion, float reverbGain, float reverbGainHf,
        float decayTime, float reflectionsGain, float lateGain, float lateDelay, float directGain, float directGainHf) {
        this.wetGain = wetGain;
        this.density = density;
        this.diffusion = diffusion;
        this.reverbGain = reverbGain;
        this.reverbGainHf = reverbGainHf;
        this.decayTime = decayTime;
        this.reflectionsGain = reflectionsGain;
        this.lateGain = lateGain;
        this.lateDelay = lateDelay;
        this.directGain = directGain;
        this.directGainHf = directGainHf;
    }
}
