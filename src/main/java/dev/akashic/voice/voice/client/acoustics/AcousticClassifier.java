package dev.akashic.voice.voice.client.acoustics;

/** Pure classifier so environment policy is testable without a Minecraft world or OpenAL. */
public final class AcousticClassifier {

    private AcousticClassifier() {}

    public static AcousticProfile classify(boolean underwater, boolean skyVisible, int surfaces, int distanceSum) {
        if (underwater) return AcousticProfile.UNDERWATER;
        if (surfaces < 0 || surfaces > 5 || distanceSum < 0) throw new IllegalArgumentException("Invalid sample");
        if (skyVisible && surfaces <= 2) return AcousticProfile.OUTDOOR;
        if (!skyVisible) {
            if (surfaces == 0) return AcousticProfile.CAVE;
            float averageDistance = distanceSum / (float) surfaces;
            return surfaces >= 4 && averageDistance <= 3.25f ? AcousticProfile.ROOM : AcousticProfile.CAVE;
        }
        return AcousticProfile.ROOM;
    }
}
