package dev.akashic.voice.platform.velocity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owned by one encrypted receiver session; each incarnation carries its epoch in every frame. */
final class OutboundStreamEpochs {
    static final int CAPACITY = 4096;
    private static final class State {
        UUID source;
        boolean group;
        long epoch;
    }
    private final Map<UUID, State> streams = new HashMap<>();
    private long nextEpoch = 1L;
    long frame(UUID sender, UUID source, boolean group) {
        if (sender == null || source == null) return 0L;
        State state = streams.get(sender);
        if (state == null) {
            if (streams.size() >= CAPACITY) return 0L;
            state = new State();
            streams.put(sender, state);
        }
        if (state.epoch != 0 && state.group == group && source.equals(state.source)) {
            return state.epoch;
        }
        if (nextEpoch == Long.MAX_VALUE) return 0L;
        state.source = source;
        state.group = group;
        state.epoch = nextEpoch++;
        return state.epoch;
    }
    long retire(UUID sender, UUID source, boolean group) {
        State state = streams.get(sender);
        if (state == null || state.group != group || !state.source.equals(source)) return 0L;
        long epoch = state.epoch;
        streams.remove(sender);
        return epoch;
    }
    long retire(UUID sender) {
        State state = streams.get(sender);
        if (state == null || state.epoch == 0L) return 0L;
        long epoch = state.epoch;
        streams.remove(sender);
        return epoch;
    }
    int size() { return streams.size(); }
    void clear() { streams.clear(); }
}
