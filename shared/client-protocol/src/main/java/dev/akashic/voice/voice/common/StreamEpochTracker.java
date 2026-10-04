package dev.akashic.voice.voice.common;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded anti-resurrection state. Clear only when the enclosing transport session changes. */
public final class StreamEpochTracker {
    public enum Decision { REJECT, SAME, NEW }
    public static final int DEFAULT_CAPACITY = 4096;
    private static final class State {
        long epoch;
        boolean retired;
        State(long epoch, boolean retired) { this.epoch = epoch; this.retired = retired; }
    }
    private final Map<UUID, State> states = new HashMap<UUID, State>();
    private final int capacity;
    public StreamEpochTracker() { this(DEFAULT_CAPACITY); }
    public StreamEpochTracker(int capacity) {
        if (capacity <= 0 || capacity > DEFAULT_CAPACITY) throw new IllegalArgumentException("invalid capacity");
        this.capacity = capacity;
    }
    public synchronized Decision frame(UUID sender, long epoch) {
        if (sender == null || epoch <= 0) return Decision.REJECT;
        State state = states.get(sender);
        if (state == null) {
            if (states.size() >= capacity) return Decision.REJECT;
            states.put(sender, new State(epoch, false));
            return Decision.NEW;
        }
        if (epoch < state.epoch || (epoch == state.epoch && state.retired)) return Decision.REJECT;
        if (epoch == state.epoch) return Decision.SAME;
        state.epoch = epoch;
        state.retired = false;
        return Decision.NEW;
    }
    public synchronized boolean reset(UUID sender, long epoch) {
        if (sender == null || epoch <= 0) return false;
        State state = states.get(sender);
        if (state == null) {
            if (states.size() >= capacity) return false;
            states.put(sender, new State(epoch, true));
            return true;
        }
        if (epoch < state.epoch || (epoch == state.epoch && state.retired)) return false;
        state.epoch = epoch;
        state.retired = true;
        return true;
    }
    public synchronized boolean hasEpoch(UUID sender) { return states.containsKey(sender); }
    public synchronized boolean isActive(UUID sender, long epoch) {
        State state = states.get(sender);
        return state != null && state.epoch == epoch && !state.retired;
    }
    public synchronized int size() { return states.size(); }
    public synchronized void clear() { states.clear(); }
}
