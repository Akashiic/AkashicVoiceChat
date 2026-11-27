package dev.akashic.voice.voice.client.playback;

/** Deterministic output-recovery state machine; contains no OpenAL calls. */
final class OutputRecoveryController {

    enum State {
        READY,
        OWNED_PENDING,
        SHARED_REQUESTED,
        SHARED_RELOADING,
        WAITING_CONTEXT
    }

    static final long CONTEXT_SETTLE_NANOS = 500_000_000L;
    static final long SHARED_RETRY_NANOS = 5_000_000_000L;
    static final long OWNED_RETRY_NANOS = 1_000_000_000L;

    private State state = State.READY;
    private long notBeforeNanos;
    private long retryNanos;

    synchronized boolean request(boolean ownsOpenAl, long nowNanos) {
        if (state != State.READY) return false;
        state = ownsOpenAl ? State.OWNED_PENDING : State.SHARED_REQUESTED;
        notBeforeNanos = nowNanos;
        retryNanos = nowNanos;
        return true;
    }

    /**
     * The OpenAL natives were lost under us (for example Minecraft destroyed and is re-creating the shared
     * context). An owner restarts its own context; a shared user must not issue another reload while the
     * external one is in flight, so it waits for the context directly and only falls back to requesting a
     * reload after {@link #SHARED_RETRY_NANOS}.
     */
    synchronized boolean requestAfterBindingLoss(boolean ownsOpenAl, long nowNanos) {
        if (state != State.READY) return false;
        if (ownsOpenAl) {
            state = State.OWNED_PENDING;
            notBeforeNanos = nowNanos + CONTEXT_SETTLE_NANOS;
            retryNanos = notBeforeNanos;
        } else {
            state = State.WAITING_CONTEXT;
            notBeforeNanos = nowNanos + CONTEXT_SETTLE_NANOS;
            retryNanos = nowNanos + SHARED_RETRY_NANOS;
        }
        return true;
    }

    synchronized boolean claimSharedReload() {
        if (state != State.SHARED_REQUESTED) return false;
        state = State.SHARED_RELOADING;
        return true;
    }

    synchronized void sharedReloadIssued(long nowNanos) {
        if (state != State.SHARED_RELOADING) return;
        state = State.WAITING_CONTEXT;
        notBeforeNanos = nowNanos + CONTEXT_SETTLE_NANOS;
        retryNanos = nowNanos + SHARED_RETRY_NANOS;
    }

    synchronized void sharedReloadFailed(long nowNanos) {
        if (state != State.SHARED_RELOADING) return;
        state = State.WAITING_CONTEXT;
        notBeforeNanos = nowNanos + SHARED_RETRY_NANOS;
        retryNanos = notBeforeNanos;
    }

    synchronized boolean shouldProbeContext(long nowNanos) {
        return state == State.WAITING_CONTEXT && nowNanos >= notBeforeNanos;
    }

    synchronized boolean retrySharedIfDue(long nowNanos) {
        if (state != State.WAITING_CONTEXT || nowNanos < retryNanos) return false;
        state = State.SHARED_REQUESTED;
        return true;
    }

    synchronized boolean ownedRestartDue(long nowNanos) {
        return state == State.OWNED_PENDING && nowNanos >= notBeforeNanos;
    }

    synchronized void ownedRestartFailed(long nowNanos) {
        if (state != State.OWNED_PENDING) return;
        notBeforeNanos = nowNanos + OWNED_RETRY_NANOS;
    }

    synchronized void complete() {
        state = State.READY;
        notBeforeNanos = 0L;
        retryNanos = 0L;
    }

    synchronized boolean recovering() {
        return state != State.READY;
    }

    synchronized State state() {
        return state;
    }
}
