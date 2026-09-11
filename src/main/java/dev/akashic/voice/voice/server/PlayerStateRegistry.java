package dev.akashic.voice.voice.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerStateRegistry {

    public static final class PlayerState {

        public final int dimension;
        public final double x;
        public final double y;
        public final double z;

        public PlayerState(int dimension, double x, double y, double z) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final Map<UUID, PlayerState> STATES = new ConcurrentHashMap<>();

    private PlayerStateRegistry() {}

    public static void update(UUID playerId, int dimension, double x, double y, double z) {
        STATES.put(playerId, new PlayerState(dimension, x, y, z));
    }

    public static PlayerState get(UUID playerId) {
        return STATES.get(playerId);
    }

    public static void remove(UUID playerId) {
        STATES.remove(playerId);
    }

    public static void clear() {
        STATES.clear();
    }
}
