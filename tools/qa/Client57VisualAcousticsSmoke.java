package dev.akashic.voice.voice.client.acoustics;

import java.util.UUID;

import dev.akashic.voice.voice.client.MicGate;
import dev.akashic.voice.voice.client.talk.TalkCache;

public final class Client57VisualAcousticsSmoke {
    interface Scenario { void run() throws Exception; }
    static int passed;
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void test(String name, Scenario scenario) throws Exception {
        scenario.run(); passed++; System.out.println("PASS " + name);
    }
    public static void main(String[] args) throws Exception {
        test("open_sky_is_outdoor", () -> check(AcousticClassifier.classify(false,true,1,2)==AcousticProfile.OUTDOOR,"outdoor"));
        test("covered_compact_space_is_room", () -> check(AcousticClassifier.classify(false,false,5,11)==AcousticProfile.ROOM,"room"));
        test("large_enclosed_space_is_cave", () -> check(AcousticClassifier.classify(false,false,3,16)==AcousticProfile.CAVE,"cave"));
        test("unbounded_covered_space_is_cave", () -> check(AcousticClassifier.classify(false,false,0,0)==AcousticProfile.CAVE,"cavern"));
        test("underwater_overrides_geometry", () -> check(AcousticClassifier.classify(true,true,0,0)==AcousticProfile.UNDERWATER,"water"));        test("invalid_geometry_rejected", () -> {
            try { AcousticClassifier.classify(false,false,6,1); throw new AssertionError("invalid accepted"); }
            catch (IllegalArgumentException expected) { }
        });
        test("remote_talk_indicator_expires_quickly", () -> {
            UUID id = UUID.randomUUID(); TalkCache.clear(); TalkCache.markSpeaking(id);
            check(TalkCache.isSpeaking(id), "not active immediately");
            Thread.sleep(180L);
            check(!TalkCache.isSpeaking(id), "remote icon lingered past visual window");
        });
        test("physical_ptt_ui_state_is_immediate", Client57VisualAcousticsSmoke::pttImmediate);
        System.out.println("CLIENT57_VISUAL_ACOUSTICS_OK scenarios=" + passed);
    }

    static void pttImmediate() {
        MicGate.Mode previousMode = MicGate.mode();
        boolean previousMuted = MicGate.isMuted(), previousPressed = MicGate.isPttPressed();
        try {
            MicGate.setMode(MicGate.Mode.PTT); MicGate.setMuted(false); MicGate.setPttPressed(false);
            check(!MicGate.isPttEngaged(), "idle PTT shown active");
            MicGate.setPttPressed(true); check(MicGate.isPttEngaged(), "PTT press not visible immediately");
            MicGate.setPttPressed(false); check(!MicGate.isPttEngaged(), "PTT release not visible immediately");
        } finally {
            MicGate.setMode(previousMode); MicGate.setMuted(previousMuted); MicGate.setPttPressed(previousPressed);
        }
    }
}
