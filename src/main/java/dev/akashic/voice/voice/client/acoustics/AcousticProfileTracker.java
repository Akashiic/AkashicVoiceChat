package dev.akashic.voice.voice.client.acoustics;

/** Small hysteresis layer so room/cave boundaries do not chatter every sample. */
public final class AcousticProfileTracker {

    private static final int CONFIRM_SAMPLES = 2;

    private AcousticProfile applied = AcousticProfile.OUTDOOR;
    private AcousticProfile pending;
    private int confirmations;

    public AcousticProfile update(AcousticProfile sample) {
        if (sample == null) return applied;
        if (sample == AcousticProfile.UNDERWATER) {
            apply(sample);
            return applied;
        }
        if (sample == applied) {
            pending = null;
            confirmations = 0;
            return applied;
        }
        if (sample != pending) {
            pending = sample;
            confirmations = 1;
            return applied;
        }
        if (++confirmations >= CONFIRM_SAMPLES) apply(sample);
        return applied;
    }

    public AcousticProfile current() {
        return applied;
    }

    public void reset() {
        applied = AcousticProfile.OUTDOOR;
        pending = null;
        confirmations = 0;
    }

    private void apply(AcousticProfile profile) {
        applied = profile;
        pending = null;
        confirmations = 0;
    }
}
