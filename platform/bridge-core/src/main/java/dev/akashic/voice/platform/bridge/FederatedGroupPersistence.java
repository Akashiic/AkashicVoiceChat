package dev.akashic.voice.platform.bridge;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;

/**
 * Single-use asynchronous lifecycle around one directory/store pair. No disk access in control calls.
 * Integrators MUST serialize mutations and shutdown, wait for READY before exposing the directory,
 * and call requestSave AFTER every successful persistent mutation. Do not share the path with a
 * second coordinator. This class neither registers Gateway handlers nor schedules invitation expiry.
 * A save request is NOT a durability acknowledgement: observe persistedRevision and failure state.
 */
public final class FederatedGroupPersistence {
    public enum State { NEW, LOADING, READY, CLOSING, CLOSED, FAILED }
    public enum Failure { NONE, START, LOAD, SAVE, INTERRUPTED, INTERNAL }

    /** Contains no names, UUIDs, exception messages, paths or credentials. */
    public record Status(State state, boolean loadedExistingFile, long requestedRevision,
                         long persistedRevision, boolean savePending, boolean saving,
                         long successfulWrites, long failedWrites, Failure lastFailure,
                         boolean workerStopped) {
        public boolean dirty() { return requestedRevision > persistedRevision; }
    }

    interface StoreAccess {
        boolean load(FederatedGroupDirectory directory) throws IOException;
        void save(FederatedGroupDirectory directory) throws IOException;
    }

    private final Object monitor = new Object();
    private final FederatedGroupDirectory directory;
    private final StoreAccess storage;
    private final ThreadFactory threadFactory;
    private State state = State.NEW;
    private Failure lastFailure = Failure.NONE;
    private boolean loadedExistingFile;
    private boolean savePending;
    private boolean saving;
    private boolean workerStopped;
    private long requestedRevision;
    private long persistedRevision;
    private long successfulWrites;
    private long failedWrites;

    public FederatedGroupPersistence(FederatedGroupDirectory directory, FederatedGroupStore store) {
        this(directory, adapt(Objects.requireNonNull(store, "store")), runnable -> {
            Thread thread = new Thread(runnable, "AkashicVoice-Group-IO");
            thread.setDaemon(true);
            return thread;
        });
    }

    FederatedGroupPersistence(FederatedGroupDirectory directory, StoreAccess storage,
                              ThreadFactory threadFactory) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.threadFactory = Objects.requireNonNull(threadFactory, "threadFactory");
    }

    private static StoreAccess adapt(FederatedGroupStore store) {
        return new StoreAccess() {
            @Override public boolean load(FederatedGroupDirectory target) throws IOException {
                return store.loadInto(target);
            }
            @Override public void save(FederatedGroupDirectory source) throws IOException {
                store.save(source);
            }
        };
    }

    /** Starts loading on one dedicated worker. May be called once. No I/O on the calling thread. */
    public void start() {
        synchronized (monitor) {
            if (state != State.NEW) throw new IllegalStateException("Persistence already started or closed");
            state = State.LOADING;
            try {
                Thread thread = Objects.requireNonNull(threadFactory.newThread(this::run), "worker");
                thread.start();
            } catch (RuntimeException | Error failure) {
                state = State.FAILED;
                lastFailure = Failure.START;
                workerStopped = true;
                monitor.notifyAll();
                throw failure;
            }
        }
    }

    /**
     * Call AFTER an authorized persistent mutation. O(1) pending state: bursts do not allocate tasks
     * or snapshots per request. Revisions are in-memory receipts, not persisted identifiers.
     */
    public long requestSave() {
        synchronized (monitor) {
            requireReady();
            if (requestedRevision == Long.MAX_VALUE) throw new IllegalStateException("Save revision exhausted");
            requestedRevision++;
            savePending = true;
            monitor.notifyAll();
            return requestedRevision;
        }
    }

    /** Explicit bounded retry trigger; a failed write is not retried in an uncontrolled hot loop. */
    public boolean retryPendingSave() {
        synchronized (monitor) {
            requireReady();
            if (requestedRevision == persistedRevision || savePending || saving) return false;
            savePending = true;
            monitor.notifyAll();
            return true;
        }
    }

    /**
     * Stops admitting requests, drains accepted changes, then stops. Idempotent and non-waiting.
     * A final write failure produces FAILED with dirty=true, never a false successful close.
     * Do not mutate the directory after calling this method.
     */
    public void requestClose() {
        synchronized (monitor) {
            switch (state) {
                case CLOSED, FAILED, CLOSING -> { return; }
                case NEW -> { state = State.CLOSED; workerStopped = true; }
                case LOADING -> state = State.CLOSING;
                case READY -> {
                    state = State.CLOSING;
                    if (!saving && requestedRevision > persistedRevision) savePending = true;
                }
            }
            monitor.notifyAll();
        }
    }

    public Status status() {
        synchronized (monitor) {
            return new Status(state, loadedExistingFile, requestedRevision, persistedRevision,
                savePending, saving, successfulWrites, failedWrites, lastFailure, workerStopped);
        }
    }

    /** Bounded joining helper for shutdown/QA only. NEVER call on audio, render, or server tick threads. */
    public boolean awaitStopped(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        long budget;
        try { budget = timeout.toNanos(); }
        catch (ArithmeticException overflow) { throw new IllegalArgumentException("Timeout too large", overflow); }
        if (budget < 0) throw new IllegalArgumentException("Negative timeout");
        long start = System.nanoTime();
        synchronized (monitor) {
            while (!workerStopped) {
                long remaining = budget - (System.nanoTime() - start);
                if (remaining <= 0) return false;
                monitor.wait(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
            }
            return true;
        }
    }

    private void requireReady() {
        if (state != State.READY) throw new IllegalStateException("Group persistence is not READY");
    }

    private void run() {
        try {
            final boolean loaded;
            try {
                loaded = storage.load(directory);
            } catch (IOException | RuntimeException invalid) {
                fail(Failure.LOAD);
                return;
            }
            synchronized (monitor) {
                loadedExistingFile = loaded;
                if (state == State.CLOSING) {
                    state = State.CLOSED;
                    monitor.notifyAll();
                    return;
                }
                state = State.READY;
                monitor.notifyAll();
            }
            runWrites();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fail(Failure.INTERRUPTED);
        } finally {
            synchronized (monitor) {
                if (state != State.CLOSED && state != State.FAILED) {
                    state = State.FAILED;
                    lastFailure = Failure.INTERNAL;
                }
                saving = false;
                savePending = false;
                workerStopped = true;
                monitor.notifyAll();
            }
        }
    }

    private void runWrites() throws InterruptedException {
        while (true) {
            long target;
            synchronized (monitor) {
                while (state == State.READY && !savePending) monitor.wait();
                if (state == State.CLOSING && !savePending) {
                    state = State.CLOSED;
                    monitor.notifyAll();
                    return;
                }
                target = requestedRevision;
                savePending = false;
                saving = true;
            }
            try {
                // Store takes its snapshot here, NOT in the caller or as stale queued work.
                storage.save(directory);
            } catch (IOException | RuntimeException unavailable) {
                synchronized (monitor) {
                    failedWrites++;
                    lastFailure = Failure.SAVE;
                    saving = false;
                    if (state == State.CLOSING) {
                        state = State.FAILED;
                        monitor.notifyAll();
                        return;
                    }
                    // A newer request may already be pending. Otherwise wait for an explicit retry.
                    monitor.notifyAll();
                }
                continue;
            }
            synchronized (monitor) {
                persistedRevision = target;
                successfulWrites++;
                lastFailure = Failure.NONE;
                saving = false;
                monitor.notifyAll();
            }
        }
    }

    private void fail(Failure reason) {
        synchronized (monitor) {
            state = State.FAILED;
            lastFailure = reason;
            monitor.notifyAll();
        }
    }
}
