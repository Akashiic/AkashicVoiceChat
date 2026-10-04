package dev.akashic.voice.voice.client.playback;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Session-local controls, indexed only by the authenticated stream's sender UUID. */
public final class SpeakerControls {

    public static final int MAX_SPEAKERS = 4096;

    public static final class State {

        private final int percent;
        private final boolean muted;
        final Object token;

        private State(int percent, boolean muted, Object token) {
            this.percent = percent;
            this.muted = muted;
            this.token = token;
        }

        public Object listeningIdentity() {
            return token;
        }

        public int percent() {
            return percent;
        }

        public boolean muted() {
            return muted;
        }

        public boolean audible() {
            return !muted && percent > 0;
        }

        public float scale() {
            return percent / 100f;
        }
    }

    private static final State DEFAULT = new State(100, false, new Object());

    public static final class Snapshot {

        private final Map<UUID, State> entries;

        private Snapshot(Map<UUID, State> entries) {
            this.entries = Collections.unmodifiableMap(entries);
        }

        public State state(UUID sender) {
            State state = entries.get(sender);
            return state != null ? state : DEFAULT;
        }

        public Set<UUID> speakers() {
            return entries.keySet();
        }
    }

    private volatile Snapshot snapshot = new Snapshot(Collections.<UUID, State>emptyMap());

    public Snapshot snapshot() {
        return snapshot;
    }

    public State state(UUID sender) {
        return snapshot.state(sender);
    }

    /** One allocation/publication per newly heard speaker, never per audio frame. */
    State observe(UUID sender) {
        if (sender == null) throw new IllegalArgumentException("Missing sender");
        Snapshot current = snapshot;
        if (current.entries.containsKey(sender)) return current.state(sender);
        synchronized (this) {
            current = snapshot;
            if (current.entries.containsKey(sender)) return current.state(sender);
            if (current.entries.size() >= MAX_SPEAKERS) return DEFAULT;
            Map<UUID, State> next = new HashMap<>(current.entries);
            State created = new State(100, false, new Object());
            next.put(sender, created);
            snapshot = new Snapshot(next);
            return created;
        }
    }

    /** Percent is an individual attenuator (0..100), applied after the voice master setting. */
    public synchronized boolean update(UUID sender, int percent, boolean muted) {
        if (sender == null || percent < 0 || percent > 100) return false;
        Snapshot current = snapshot;
        State previous = current.entries.get(sender);
        if (previous != null && previous.percent == percent && previous.muted == muted) return true;
        if (previous == null && current.entries.size() >= MAX_SPEAKERS) return false;
        boolean audible = !muted && percent > 0;
        Object token = previous != null && previous.audible() == audible ? previous.token : new Object();
        Map<UUID, State> next = new HashMap<>(current.entries);
        next.put(sender, new State(percent, muted, token));
        snapshot = new Snapshot(next);
        return true;
    }

    /** Reclaims an uncustomized stream while preserving explicit per-speaker preferences. */
    synchronized boolean retireDefault(UUID sender) {
        if (sender == null) return false;
        Snapshot current = snapshot;
        State state = current.entries.get(sender);
        if (state == null || state.percent != 100 || state.muted) return false;
        Map<UUID, State> next = new HashMap<>(current.entries);
        next.remove(sender);
        snapshot = new Snapshot(next);
        return true;
    }
}
