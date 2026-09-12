package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.bridge.AkashicSpeakerFrame;
import dev.akashic.voice.platform.bridge.ManagedPlayer;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class BackendRouteRegistry implements AutoCloseable {
    interface Route extends AutoCloseable {
        String backendName();
        boolean isOpen();
        boolean sendAttach(ManagedPlayer player);
        boolean sendDetach(UUID playerId);
        boolean sendFrame(AkashicSpeakerFrame frame);
        boolean sendReset(UUID speakerId);
        @Override
        void close();
    }

    private final ConcurrentHashMap<String, Route> routes = new ConcurrentHashMap<>();

    Route register(Route route) {
        Objects.requireNonNull(route, "route");
        String backend = validateBackend(route.backendName());
        return routes.put(backend, route);
    }
    Route readyRoute(String backendName) {
        Route route = routes.get(backendName);
        if (route == null || !route.isOpen()) return null;
        return route;
    }

    boolean isReady(String backendName) {
        return readyRoute(backendName) != null;
    }

    boolean unregister(Route route) {
        if (route == null) return false;
        return routes.remove(route.backendName(), route);
    }

    @Override
    public void close() {
        ArrayList<Route> snapshot = new ArrayList<>(routes.values());
        routes.clear();
        for (Route route : snapshot) {
            try {
                route.close();
            } catch (RuntimeException ignored) {
                // best-effort shutdown
            }
        }
    }

    private static String validateBackend(String backendName) {
        if (backendName == null || backendName.isBlank() || backendName.length() > 64) {
            throw new IllegalArgumentException("invalid backend name");
        }
        return backendName;
    }
}
