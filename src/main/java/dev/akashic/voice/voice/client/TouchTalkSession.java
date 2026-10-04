package dev.akashic.voice.voice.client;

import java.util.Objects;
import java.util.function.LongSupplier;

/** Session-bound press state; rendering and hardware polling remain in the GUI. */
public final class TouchTalkSession implements AutoCloseable {

    private final LongSupplier clock;
    private Object route;
    private TouchPttLease lease;

    public TouchTalkSession() {
        this(System::nanoTime);
    }

    TouchTalkSession(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean press(Object currentRoute, boolean authenticated, boolean focused) {
        if (lease != null || currentRoute == null || !authenticated || !focused) return false;
        TouchPttLease next = MicGate.beginTouchPtt(clock);
        if (next == null) return false;
        route = currentRoute;
        lease = next;
        return true;
    }

    public boolean sustain(Object currentRoute, boolean authenticated, boolean pointerDown, boolean focused) {
        if (lease != null && currentRoute == route
            && authenticated
            && pointerDown
            && focused
            && MicGate.renewTouchPtt(lease)) return true;
        close();
        return false;
    }

    public boolean isHeld() {
        return lease != null && MicGate.isTouchPttHeld(lease);
    }

    @Override
    public void close() {
        if (lease != null) MicGate.endTouchPtt(lease);
        lease = null;
        route = null;
    }
}
