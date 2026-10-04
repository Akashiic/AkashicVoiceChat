package dev.akashic.voice.voice.client;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/** Real touch/PTT gate state on the desktop Java 8 runtime; no GUI or audio device. */
public final class TouchPttDesktopSmoke {
    interface Scenario { void run() throws Exception; }
    static int passed;
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void test(String name, Scenario scenario) throws Exception {
        reset(); scenario.run(); passed++; System.out.println("PASS " + name);
    }
    static void reset() {
        MicGate.cancelTouchPtt();
        MicGate.setMode(MicGate.Mode.PTT);
        MicGate.setMuted(false);
        MicGate.setPttPressed(false);
        MicGate.setDeafened(false);
    }
    public static void main(String[] args) throws Exception {
        test("press_requires_route", TouchPttDesktopSmoke::requiresRoute);
        test("press_requires_authentication", TouchPttDesktopSmoke::requiresAuth);
        test("press_requires_focus", TouchPttDesktopSmoke::requiresFocus);
        test("press_requires_ptt_mode", TouchPttDesktopSmoke::requiresPtt);
        test("press_succeeds_and_authorizes", TouchPttDesktopSmoke::press);
        test("second_press_is_rejected", TouchPttDesktopSmoke::secondPress);
        test("sustain_renews_same_route", TouchPttDesktopSmoke::sustain);
        test("route_change_cancels", TouchPttDesktopSmoke::routeChange);
        test("authentication_loss_cancels", TouchPttDesktopSmoke::authLoss);
        test("focus_loss_cancels", TouchPttDesktopSmoke::focusLoss);
        test("pointer_release_cancels", TouchPttDesktopSmoke::pointerRelease);
        test("lease_expires_without_heartbeat", TouchPttDesktopSmoke::expiry);
        test("late_renewal_cannot_revive", TouchPttDesktopSmoke::lateRenewal);
        test("close_is_idempotent", TouchPttDesktopSmoke::closeIdempotent);
        test("mute_cancels_touch", TouchPttDesktopSmoke::muteCancels);
        test("mode_change_cancels_touch", TouchPttDesktopSmoke::modeCancels);
        test("keyboard_remains_after_touch_close", TouchPttDesktopSmoke::keyboardIndependent);
        test("touch_remains_when_keyboard_changes", TouchPttDesktopSmoke::keyboardChange);
        test("global_cancel_invalidates_session", TouchPttDesktopSmoke::globalCancel);
        test("new_press_after_close_works", TouchPttDesktopSmoke::newPress);
        test("stale_session_close_cannot_cancel_successor", TouchPttDesktopSmoke::staleClose);
        test("deafen_is_listen_only", TouchPttDesktopSmoke::deafenIndependent);
        test("transmission_identity_rotates", TouchPttDesktopSmoke::identity);
        test("nano_time_wrap_is_bounded", TouchPttDesktopSmoke::wraparound);
        test("repeated_cycles_leave_no_stale_touch", TouchPttDesktopSmoke::cycles);
        System.out.println("TOUCH_PTT_DESKTOP_OK scenarios=" + passed);
    }

    static final class Clock implements LongSupplier {
        final AtomicLong now;
        Clock(long initial) { now = new AtomicLong(initial); }
        public long getAsLong() { return now.get(); }
        void add(long delta) { now.addAndGet(delta); }
    }

    static TouchTalkSession session(Clock clock) { return new TouchTalkSession(clock); }
    static Object route() { return new Object(); }

    static void requiresRoute() {
        Clock c = new Clock(0);
        TouchTalkSession s = session(c);
        check(!s.press(null, true, true), "null route accepted");
        s.close();
    }

    static void requiresAuth() {
        Clock c = new Clock(0);
        TouchTalkSession s = session(c);
        check(!s.press(route(), false, true), "unauthenticated press accepted");
        s.close();
    }

    static void requiresFocus() {
        Clock c = new Clock(0);
        TouchTalkSession s = session(c);
        check(!s.press(route(), true, false), "unfocused press accepted");
        s.close();
    }

    static void requiresPtt() {
        MicGate.setMode(MicGate.Mode.ALWAYS);
        Clock c = new Clock(0);
        TouchTalkSession s = session(c);
        check(!s.press(route(), true, true), "touch started outside PTT mode");
        s.close();
    }

    static void press() {
        Clock c = new Clock(0);
        Object r = route();
        TouchTalkSession s = session(c);
        check(s.press(r, true, true) && s.isHeld(), "press did not start");
        check(MicGate.permitsTransmission(MicGate.transmissionState()), "touch did not authorize transmission");
        s.close();
    }

    static void secondPress() {
        Clock c = new Clock(0);
        Object r = route();
        TouchTalkSession a = session(c), b = session(c);
        check(a.press(r, true, true), "first press failed");
        check(!b.press(r, true, true), "second press stole active lease");
        a.close();
        b.close();
    }

    static void sustain() {
        Clock c = new Clock(0);
        Object r = route();
        TouchTalkSession s = session(c);
        check(s.press(r, true, true), "press failed");
        c.add(700_000_000L);
        check(s.sustain(r, true, true, true) && s.isHeld(), "renewal failed");
        c.add(700_000_000L);
        check(s.sustain(r, true, true, true), "renewed deadline was not extended");
        s.close();
    }

    static void routeChange() {
        Clock c = new Clock(0);
        Object first = route(), second = route();
        TouchTalkSession s = session(c);
        check(s.press(first, true, true), "press failed");
        check(!s.sustain(second, true, true, true) && !s.isHeld(), "route change retained touch");
    }

    static void authLoss() {
        Clock c = new Clock(0); Object r = route(); TouchTalkSession s = session(c);
        check(s.press(r, true, true), "press failed");
        check(!s.sustain(r, false, true, true) && !s.isHeld(), "auth loss retained touch");
    }

    static void focusLoss() {
        Clock c = new Clock(0); Object r = route(); TouchTalkSession s = session(c);
        check(s.press(r, true, true), "press failed");
        check(!s.sustain(r, true, true, false) && !s.isHeld(), "focus loss retained touch");
    }

    static void pointerRelease() {
        Clock c = new Clock(0); Object r = route(); TouchTalkSession s = session(c);
        check(s.press(r, true, true), "press failed");
        check(!s.sustain(r, true, false, true) && !s.isHeld(), "release retained touch");
    }

    static void expiry() {
        Clock c = new Clock(0); Object r = route(); TouchTalkSession s = session(c);
        check(s.press(r, true, true), "press failed");
        c.add(TouchPttLease.TIMEOUT_NANOS);
        check(!s.isHeld(), "expired lease reported held");
        check(!MicGate.permitsTransmission(MicGate.transmissionState()), "expired lease still authorized");
        s.close();
    }

    static void lateRenewal() {
        Clock c = new Clock(0); Object r = route(); TouchTalkSession s = session(c);
        check(s.press(r, true, true), "press failed");
        c.add(TouchPttLease.TIMEOUT_NANOS + 1L);
        check(!s.sustain(r, true, true, true), "expired lease renewed");
        c.now.set(1L);
        check(!s.isHeld(), "clock rewind revived expired lease");
    }

    static void closeIdempotent() {
        Clock c = new Clock(0); Object r = route(); TouchTalkSession s = session(c);
        check(s.press(r, true, true), "press failed");
        s.close(); s.close();
        check(!s.isHeld() && !MicGate.permitsTransmission(MicGate.transmissionState()), "close not idempotent");
    }

    static void muteCancels() {
        Clock c = new Clock(0); Object r = route(); TouchTalkSession s = session(c);
        check(s.press(r, true, true), "press failed");
        MicGate.setMuted(true);
        check(!s.isHeld() && !MicGate.permitsTransmission(MicGate.transmissionState()), "mute left touch active");
        MicGate.setMuted(false);
        check(!s.isHeld(), "unmute revived cancelled touch");
        s.close();
    }

    static void modeCancels() {
        Clock c = new Clock(0); Object r = route(); TouchTalkSession s = session(c);
        check(s.press(r, true, true), "press failed");
        MicGate.setMode(MicGate.Mode.VOICE);
        check(!s.isHeld(), "mode change left touch active");
        MicGate.setMode(MicGate.Mode.PTT);
        check(!s.isHeld(), "PTT return revived old touch");
        s.close();
    }

    static void keyboardIndependent() {
        Clock c = new Clock(0); Object r = route(); TouchTalkSession s = session(c);
        MicGate.setPttPressed(true);
        check(s.press(r, true, true), "touch press failed with physical PTT down");
        s.close();
        check(MicGate.isPttPressed(), "touch close cleared physical key state");
        check(MicGate.permitsTransmission(MicGate.transmissionState()), "physical PTT lost after touch close");
        MicGate.setPttPressed(false);
    }

    static void keyboardChange() {
        Clock c = new Clock(0); Object r = route(); TouchTalkSession s = session(c);
        check(s.press(r, true, true), "touch press failed");
        MicGate.setPttPressed(true);
        check(s.isHeld(), "physical key change cancelled touch");
        MicGate.setPttPressed(false);
        check(s.isHeld(), "physical key release cancelled touch");
        s.close();
    }

    static void globalCancel() {
        Clock c = new Clock(0); Object r = route(); TouchTalkSession s = session(c);
        check(s.press(r, true, true), "press failed");
        MicGate.cancelTouchPtt();
        check(!s.isHeld(), "global cancel did not invalidate session");
        check(!s.sustain(r, true, true, true), "cancelled session sustained");
        s.close();
    }

    static void newPress() {
        Clock c = new Clock(0); Object r = route();
        TouchTalkSession first = session(c); check(first.press(r, true, true), "first press failed"); first.close();
        TouchTalkSession second = session(c); check(second.press(r, true, true), "new press after close failed"); second.close();
    }

    static void staleClose() {
        Clock c = new Clock(0); Object r = route();
        TouchTalkSession first = session(c); check(first.press(r, true, true), "first press failed");
        MicGate.cancelTouchPtt();
        TouchTalkSession second = session(c); check(second.press(r, true, true), "successor press failed");
        first.close();
        check(second.isHeld(), "stale screen close cancelled successor lease");
        second.close();
    }

    static void deafenIndependent() {
        Clock c = new Clock(0); Object r = route(); TouchTalkSession s = session(c);
        check(s.press(r, true, true), "press failed");
        MicGate.setDeafened(true);
        check(s.isHeld() && MicGate.permitsTransmission(MicGate.transmissionState()), "deafen cancelled transmit");
        MicGate.setDeafened(false);
        check(s.isHeld(), "undeafen changed touch state");
        s.close();
    }

    static void identity() {
        Clock c = new Clock(0); Object r = route();
        MicGate.TransmissionState before = MicGate.transmissionState();
        TouchTalkSession s = session(c); check(s.press(r, true, true), "press failed");
        MicGate.TransmissionState active = MicGate.transmissionState();
        check(active != before && !MicGate.permitsTransmission(before), "press did not rotate authorization identity");
        s.close();
        check(MicGate.transmissionState() != active && !MicGate.permitsTransmission(active), "release did not rotate identity");
    }

    static void wraparound() {
        Clock c = new Clock(Long.MAX_VALUE - TouchPttLease.TIMEOUT_NANOS / 2);
        Object r = route(); TouchTalkSession s = session(c);
        check(s.press(r, true, true) && s.isHeld(), "lease failed near nanoTime wrap");
        c.add(TouchPttLease.TIMEOUT_NANOS / 4);
        check(s.sustain(r, true, true, true), "renew failed across wrap boundary");
        c.add(TouchPttLease.TIMEOUT_NANOS + 1L);
        check(!s.isHeld(), "wrapped lease exceeded renewed deadline");
        s.close();
    }

    static void cycles() {
        Clock c = new Clock(0); Object r = route();
        for (int i = 0; i < 1000; i++) {
            TouchTalkSession s = session(c);
            check(s.press(r, true, true), "cycle press failed at " + i);
            c.add(1_000_000L);
            check(s.sustain(r, true, true, true), "cycle sustain failed at " + i);
            s.close();
            check(!s.isHeld(), "cycle leaked touch at " + i);
        }
        check(!MicGate.permitsTransmission(MicGate.transmissionState()), "final cycle left PTT authorized");
    }
}
