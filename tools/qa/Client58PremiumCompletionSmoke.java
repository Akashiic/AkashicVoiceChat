package dev.akashic.voice.voice.client.playback;

import dev.akashic.voice.voice.client.MicGate;
import dev.akashic.voice.voice.client.acoustics.AcousticProfile;
import dev.akashic.voice.voice.client.acoustics.AcousticProfileTracker;
import dev.akashic.voice.voice.client.acoustics.AcousticRoutingPolicy;

public final class Client58PremiumCompletionSmoke {

    private static int passed;

    public static void main(String[] args) {
        sharedRecoveryLifecycle();
        ownedRecoveryBackoff();
        acousticHysteresis();
        acousticRoutingIsolation();
        outputHealthDecisionPolicy();
        sharedReloadRequiresQuiesce();
        physicalPttImmediate();
        System.out.println("CLIENT58_PREMIUM_COMPLETION_OK scenarios=" + passed);
    }

    private static void sharedRecoveryLifecycle() {
        OutputRecoveryController c = new OutputRecoveryController();
        check(c.request(false, 100L), "shared request rejected");
        check(c.state() == OutputRecoveryController.State.SHARED_REQUESTED, "shared state");
        check(c.claimSharedReload(), "reload claim");
        c.sharedReloadIssued(1_000L);
        check(c.state() == OutputRecoveryController.State.WAITING_CONTEXT, "wait state");
        check(!c.shouldProbeContext(1_000L + OutputRecoveryController.CONTEXT_SETTLE_NANOS - 1L),
            "context probed before settle");
        check(c.shouldProbeContext(1_000L + OutputRecoveryController.CONTEXT_SETTLE_NANOS),
            "context not probed after settle");
        check(!c.retrySharedIfDue(1_000L + OutputRecoveryController.SHARED_RETRY_NANOS - 1L),
            "shared retry early");
        check(c.retrySharedIfDue(1_000L + OutputRecoveryController.SHARED_RETRY_NANOS),
            "shared retry missing");
        check(c.state() == OutputRecoveryController.State.SHARED_REQUESTED, "retry state");
        c.complete();
        check(!c.recovering(), "shared completion stuck");
        passed++;
    }

    private static void ownedRecoveryBackoff() {
        OutputRecoveryController c = new OutputRecoveryController();
        check(c.request(true, 10L), "owned request rejected");
        check(c.ownedRestartDue(10L), "owned restart not due");
        c.ownedRestartFailed(20L);
        check(!c.ownedRestartDue(20L + OutputRecoveryController.OWNED_RETRY_NANOS - 1L),
            "owned retry early");
        check(c.ownedRestartDue(20L + OutputRecoveryController.OWNED_RETRY_NANOS),
            "owned retry missing");
        c.complete();
        check(!c.recovering(), "owned completion stuck");
        passed++;
    }

    private static void acousticHysteresis() {
        AcousticProfileTracker tracker = new AcousticProfileTracker();
        check(tracker.update(AcousticProfile.ROOM) == AcousticProfile.OUTDOOR, "room switched too early");
        check(tracker.update(AcousticProfile.ROOM) == AcousticProfile.ROOM, "room not confirmed");
        check(tracker.update(AcousticProfile.CAVE) == AcousticProfile.ROOM, "cave switched too early");
        check(tracker.update(AcousticProfile.CAVE) == AcousticProfile.CAVE, "cave not confirmed");
        check(tracker.update(AcousticProfile.UNDERWATER) == AcousticProfile.UNDERWATER,
            "underwater must switch immediately");
        tracker.reset();
        check(tracker.current() == AcousticProfile.OUTDOOR, "tracker reset");
        passed++;
    }
    private static void acousticRoutingIsolation() {
        check(AcousticRoutingPolicy.effectsEnabled(true, true, AcousticProfile.CAVE, true),
            "cave proximity missing effects");
        check(!AcousticRoutingPolicy.effectsEnabled(true, false, AcousticProfile.CAVE, true),
            "group/radio inherited environmental effects");
        check(!AcousticRoutingPolicy.effectsEnabled(true, true, AcousticProfile.OUTDOOR, true),
            "outdoor proximity retained EFX route");
        check(!AcousticRoutingPolicy.effectsEnabled(false, true, AcousticProfile.CAVE, true),
            "flat global mode retained environmental effects");
        passed++;
    }

    private static void outputHealthDecisionPolicy() {
        check(OpenAlOutputHealth.decide("Speakers", "Speakers", "Speakers", true)
            == OpenAlOutputHealth.Status.HEALTHY, "stable output unhealthy");
        check(OpenAlOutputHealth.decide("AIRPODS", "AirPods", "airpods", true)
            == OpenAlOutputHealth.Status.HEALTHY, "case-only device identity caused recovery");
        check(OpenAlOutputHealth.decide("Speakers", "AirPods", "AirPods", true)
            == OpenAlOutputHealth.Status.HEALTHY, "transparent default migration caused reload");
        check(OpenAlOutputHealth.decide("Speakers", "AirPods", "Speakers", true)
            == OpenAlOutputHealth.Status.DEFAULT_CHANGED, "stale bound output missed default migration");
        check(OpenAlOutputHealth.decide("Speakers", "AirPods", "Speakers", false)
            == OpenAlOutputHealth.Status.DISCONNECTED, "physical disconnect missed");
        passed++;
    }

    private static void sharedReloadRequiresQuiesce() {
        try {
            AudioPlayback playback = new AudioPlayback(referenceDistance -> new NoopChannel());
            java.lang.reflect.Field controllerField = AudioPlayback.class.getDeclaredField("outputRecovery");
            controllerField.setAccessible(true);
            OutputRecoveryController controller = (OutputRecoveryController) controllerField.get(playback);
            check(controller.request(false, 0L), "shared recovery fixture rejected");
            check(!playback.claimSharedOutputReload(), "reload claimed before quiesce");

            java.lang.reflect.Field quiescedField = AudioPlayback.class.getDeclaredField("outputQuiesced");
            quiescedField.setAccessible(true);
            quiescedField.setBoolean(playback, true);
            check(playback.claimSharedOutputReload(), "reload not claimable after quiesce");
            passed++;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("barrier fixture failed", failure);
        }
    }

    private static final class NoopChannel implements AudioPlayback.Channel {
        @Override public void setSpatial(boolean value) { }
        @Override public void setPosition(float x, float y, float z) { }
        @Override public void setMaxDistance(float value) { }
        @Override public void enqueue(short[] pcm) { }
        @Override public void close() { }
    }

    private static void physicalPttImmediate() {
        MicGate.Mode previousMode = MicGate.mode();
        boolean previousMuted = MicGate.isMuted();
        boolean previousPressed = MicGate.isPttPressed();
        try {
            MicGate.setMode(MicGate.Mode.PTT);
            MicGate.setMuted(false);
            MicGate.setPttPressed(false);
            check(!MicGate.isPttEngaged(), "idle PTT active");
            MicGate.setPttPressed(true);
            check(MicGate.isPttEngaged(), "PTT press delayed");
            MicGate.setPttPressed(false);
            check(!MicGate.isPttEngaged(), "PTT release delayed");
            passed++;
        } finally {
            MicGate.setMode(previousMode);
            MicGate.setMuted(previousMuted);
            MicGate.setPttPressed(previousPressed);
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
