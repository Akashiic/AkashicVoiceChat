package dev.akashic.voice.voice.client;

import java.util.Objects;
import java.util.function.LongSupplier;

/** A single screen press. Expiration cannot be revived by a late heartbeat. */
final class TouchPttLease {

    static final long TIMEOUT_NANOS = 1_000_000_000L;
    private final LongSupplier clock;
    private volatile long deadline;
    private volatile boolean cancelled;

    TouchPttLease(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        deadline = clock.getAsLong() + TIMEOUT_NANOS;
    }

    boolean isLive() {
        return !cancelled && clock.getAsLong() - deadline < 0L;
    }

    boolean renew() {
        long now = clock.getAsLong();
        if (cancelled || now - deadline >= 0L) {
            cancelled = true;
            return false;
        }
        deadline = now + TIMEOUT_NANOS;
        return true;
    }

    void cancel() {
        cancelled = true;
    }
}
