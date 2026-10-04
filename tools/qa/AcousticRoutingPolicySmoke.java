package dev.akashic.voice.voice.client.acoustics;

public final class AcousticRoutingPolicySmoke {
    private static int passed;
    public static void main(String[] args) {
        test("cave_proximity_has_effects", true, true, AcousticProfile.CAVE, true, true);
        test("room_proximity_has_effects", true, true, AcousticProfile.ROOM, true, true);
        test("underwater_proximity_has_effects", true, true, AcousticProfile.UNDERWATER, true, true);
        test("outdoor_remains_dry", true, true, AcousticProfile.OUTDOOR, true, false);
        test("group_chat_remains_clean", true, false, AcousticProfile.CAVE, true, false);
        test("global_3d_off_remains_clean", false, true, AcousticProfile.CAVE, true, false);
        test("missing_efx_remains_clean", true, true, AcousticProfile.CAVE, false, false);
        System.out.println("ACOUSTIC_ROUTING_POLICY_OK scenarios=" + passed);
    }
    private static void test(String name, boolean globalSpatial, boolean sourceSpatial,
        AcousticProfile profile, boolean supported, boolean expected) {
        boolean actual = AcousticRoutingPolicy.effectsEnabled(globalSpatial, sourceSpatial, profile, supported);
        if (actual != expected) throw new AssertionError(name + ": expected=" + expected + " actual=" + actual);
        passed++;
        System.out.println("PASS " + name);
    }
}
