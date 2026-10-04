package dev.akashic.voice.voice.client.acoustics;

public final class AcousticProfileContractSmoke {
    private static int passed;

    public static void main(String[] args) {
        for (AcousticProfile p : AcousticProfile.values()) validateRanges(p);
        check(AcousticProfile.OUTDOOR.wetGain == 0.0f, "outdoor must remain dry");
        check(AcousticProfile.CAVE.decayTime > AcousticProfile.ROOM.decayTime,
            "cave decay must exceed room decay");
        check(AcousticProfile.UNDERWATER.directGainHf < AcousticProfile.CAVE.directGainHf,
            "underwater must attenuate high frequencies more than cave");
        check(AcousticProfile.CAVE.wetGain > AcousticProfile.ROOM.wetGain,
            "cave reverb send must exceed room");
        System.out.println("ACOUSTIC_PROFILE_CONTRACT_OK checks=" + passed);
    }

    private static void validateRanges(AcousticProfile p) {
        range(p.wetGain, 0f, 1f, p + ".wetGain");
        range(p.density, 0f, 1f, p + ".density");
        range(p.diffusion, 0f, 1f, p + ".diffusion");
        range(p.reverbGain, 0f, 1f, p + ".reverbGain");
        range(p.reverbGainHf, 0f, 1f, p + ".reverbGainHf");
        range(p.decayTime, 0.1f, 20f, p + ".decayTime");
        range(p.reflectionsGain, 0f, 3.16f, p + ".reflectionsGain");
        range(p.lateGain, 0f, 10f, p + ".lateGain");
        range(p.lateDelay, 0f, 0.1f, p + ".lateDelay");
        range(p.directGain, 0f, 1f, p + ".directGain");
        range(p.directGainHf, 0f, 1f, p + ".directGainHf");
    }

    private static void range(float v, float min, float max, String name) {
        check(Float.isFinite(v) && v >= min && v <= max,
            name + " out of range: " + v + " expected [" + min + "," + max + "]");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        passed++;
    }
}
