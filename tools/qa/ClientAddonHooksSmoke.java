package dev.akashic.voice.voice.client;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import dev.akashic.voice.api.AkashicVoiceClientHooks;
import dev.akashic.voice.api.ClientMicFrameListener;

/**
 * Client addon hooks (PR #19 follow-up): privacy gate, failure isolation, defensive copies and
 * registration lifecycle. Drives the production capture loop with the fake device of
 * CaptureAuthorizationSmoke; never opens a microphone or starts the game.
 */
public final class ClientAddonHooksSmoke {
    interface Scenario { void run() throws Exception; }
    static int passed, failed;
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void test(String name, Scenario scenario) {
        try { scenario.run(); passed++; System.out.println("PASS " + name); }
        catch (Throwable error) { failed++; System.out.println("FAIL " + name + ": " + error); }
    }

    public static void main(String[] args) throws Exception {
        MicGate.Mode mode = MicGate.mode(); boolean muted = MicGate.isMuted();
        boolean ptt = MicGate.isPttPressed(), deaf = MicGate.isDeafened();
        try {
            test("no_hooks_keeps_normal_capture", ClientAddonHooksSmoke::noHooks);
            test("unmuted_frames_reach_listener", ClientAddonHooksSmoke::unmutedFrames);
            test("muted_frames_never_reach_listener", ClientAddonHooksSmoke::mutedFrames);
            test("ptt_released_frames_never_reach_listener", ClientAddonHooksSmoke::pttReleased);
            test("ptt_held_frames_reach_listener", ClientAddonHooksSmoke::pttHeld);
            test("filter_false_blocks_transmission", ClientAddonHooksSmoke::filterBlocks);
            test("runtime_failing_filter_fails_open", () -> failingFilter(new IllegalStateException("qa")));
            test("linkage_failing_filter_fails_open", () -> failingFilter(new NoClassDefFoundError("qa/Missing")));
            test("failing_listener_is_isolated", ClientAddonHooksSmoke::failingListener);
            test("listener_receives_defensive_copy", ClientAddonHooksSmoke::defensiveCopy);
            test("registration_close_is_idempotent", ClientAddonHooksSmoke::closeIdempotent);
        } finally {
            MicGate.setMode(mode); MicGate.setMuted(muted); MicGate.setPttPressed(ptt); MicGate.setDeafened(deaf);
        }
        System.out.println("CLIENT_ADDON_HOOKS " + (failed == 0 ? "OK" : "FAIL") + " passed=" + passed + " failed=" + failed);
        if (failed != 0) System.exit(1);
    }

    static void defaults() {
        MicGate.setMuted(false); MicGate.setPttPressed(false); MicGate.setMode(MicGate.Mode.ALWAYS);
        MicGate.setDeafened(false);
        check(!AkashicVoiceClientHooks.hasMicFrameListeners(), "listener leaked from previous scenario");
        check(!AkashicVoiceClientHooks.hasTransmissionFilters(), "filter leaked from previous scenario");
    }

    static final class Recorder implements ClientMicFrameListener {
        final List<Boolean> transmitting = new CopyOnWriteArrayList<Boolean>();
        volatile short[] last;
        public void onMicFrame(short[] pcmCopy, int sampleRate, double rms, boolean sending) {
            check(sampleRate == 48000, "unexpected sample rate " + sampleRate);
            check(pcmCopy.length == 960, "unexpected frame length " + pcmCopy.length);
            check(rms >= 0.0 && rms <= 1.0, "rms out of range " + rms);
            last = pcmCopy; transmitting.add(sending);
        }
    }

    /** Runs three fake frames through the production capture loop. */
    static List<Integer> capture() throws Exception {
        try (CaptureAuthorizationSmoke.Fixture f = new CaptureAuthorizationSmoke.Fixture(3, false, false)) {
            f.start(); f.await(); f.checkClosed();
            return new ArrayList<Integer>(f.sequences);
        }
    }

    static void noHooks() throws Exception {
        defaults();
        check(AkashicVoiceClientHooks.isTransmissionAllowed(), "empty filter list must allow");
        check(capture().equals(Arrays.asList(0, 1, 2)), "capture changed without hooks");
    }

    static void unmutedFrames() throws Exception {
        defaults(); Recorder r = new Recorder();
        try (AutoCloseable h = AkashicVoiceClientHooks.addMicFrameListener(r)) {
            check(capture().equals(Arrays.asList(0, 1, 2)), "listener changed transmission");
        }
        check(r.transmitting.equals(Arrays.asList(true, true, true)), "listener frames " + r.transmitting);
    }

    static void mutedFrames() throws Exception {
        defaults(); MicGate.setMuted(true); Recorder r = new Recorder();
        try (AutoCloseable h = AkashicVoiceClientHooks.addMicFrameListener(r)) {
            check(capture().isEmpty(), "muted capture transmitted");
        }
        check(r.transmitting.isEmpty(), "muted audio leaked to addon: " + r.transmitting.size() + " frames");
    }

    static void pttReleased() throws Exception {
        defaults(); MicGate.setMode(MicGate.Mode.PTT); Recorder r = new Recorder();
        try (AutoCloseable h = AkashicVoiceClientHooks.addMicFrameListener(r)) {
            check(capture().isEmpty(), "released PTT transmitted");
        }
        check(r.transmitting.isEmpty(), "PTT-released audio leaked to addon: " + r.transmitting.size() + " frames");
    }

    static void pttHeld() throws Exception {
        defaults(); MicGate.setMode(MicGate.Mode.PTT); MicGate.setPttPressed(true); Recorder r = new Recorder();
        try (AutoCloseable h = AkashicVoiceClientHooks.addMicFrameListener(r)) {
            check(capture().equals(Arrays.asList(0, 1, 2)), "held PTT did not transmit");
        }
        check(r.transmitting.equals(Arrays.asList(true, true, true)), "held PTT frames " + r.transmitting);
    }

    static void filterBlocks() throws Exception {
        defaults(); Recorder r = new Recorder();
        try (AutoCloseable f = AkashicVoiceClientHooks.addTransmissionFilter(() -> false);
             AutoCloseable h = AkashicVoiceClientHooks.addMicFrameListener(r)) {
            check(!AkashicVoiceClientHooks.isTransmissionAllowed(), "filter ignored");
            check(capture().isEmpty(), "blocked frames were sent");
        }
        check(r.transmitting.equals(Arrays.asList(false, false, false)), "blocked frames " + r.transmitting);
    }

    static void failingFilter(final Throwable failure) throws Exception {
        defaults();
        try (AutoCloseable f = AkashicVoiceClientHooks.addTransmissionFilter(() -> {
                 if (failure instanceof RuntimeException) throw (RuntimeException) failure;
                 throw (Error) failure;
             })) {
            check(AkashicVoiceClientHooks.isTransmissionAllowed(), "failing filter must fail open");
            check(capture().equals(Arrays.asList(0, 1, 2)), "failing filter interrupted capture");
        }
    }

    static void failingListener() throws Exception {
        defaults(); Recorder r = new Recorder(); final AtomicInteger calls = new AtomicInteger();
        try (AutoCloseable bad = AkashicVoiceClientHooks.addMicFrameListener((pcm, rate, rms, sending) -> {
                 calls.incrementAndGet(); throw new NoClassDefFoundError("qa/Missing");
             });
             AutoCloseable good = AkashicVoiceClientHooks.addMicFrameListener(r)) {
            check(capture().equals(Arrays.asList(0, 1, 2)), "failing listener interrupted capture");
        }
        check(calls.get() == 3, "failing listener not invoked per frame");
        check(r.transmitting.size() == 3, "healthy listener starved by failing listener");
    }

    static void defensiveCopy() throws Exception {
        short[] pcm = new short[960]; Arrays.fill(pcm, (short) 1234);
        final AtomicReference<short[]> seen = new AtomicReference<short[]>();
        try (AutoCloseable h = AkashicVoiceClientHooks.addMicFrameListener((copy, rate, rms, sending) -> {
                 seen.set(copy); Arrays.fill(copy, (short) 0);
             })) {
            AkashicVoiceClientHooks.dispatchMicFrame(pcm, 48000, true);
        }
        check(seen.get() != null && seen.get() != pcm, "listener received the live capture buffer");
        for (short s : pcm) check(s == 1234, "listener mutated capture buffer");
    }

    static void closeIdempotent() throws Exception {
        defaults();
        AutoCloseable a = AkashicVoiceClientHooks.addMicFrameListener((pcm, rate, rms, sending) -> {});
        AutoCloseable f = AkashicVoiceClientHooks.addTransmissionFilter(() -> true);
        a.close(); a.close(); f.close(); f.close();
        check(!AkashicVoiceClientHooks.hasMicFrameListeners(), "listener not removed");
        check(!AkashicVoiceClientHooks.hasTransmissionFilters(), "filter not removed");
    }
}
