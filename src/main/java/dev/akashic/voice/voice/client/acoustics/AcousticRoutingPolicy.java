package dev.akashic.voice.voice.client.acoustics;

/** Pure policy: environmental acoustics belong only to positional proximity voice. */
public final class AcousticRoutingPolicy {

    private AcousticRoutingPolicy() {}

    public static boolean effectsEnabled(boolean globalSpatial, boolean sourceSpatial, AcousticProfile profile,
        boolean efxSupported) {
        return efxSupported && globalSpatial && sourceSpatial && profile != null && profile != AcousticProfile.OUTDOOR;
    }
}
