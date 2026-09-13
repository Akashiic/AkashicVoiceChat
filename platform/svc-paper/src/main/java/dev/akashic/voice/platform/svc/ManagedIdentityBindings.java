package dev.akashic.voice.platform.svc;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class ManagedIdentityBindings {
    private final ConcurrentHashMap<UUID, UUID> localToProxy = new ConcurrentHashMap<>();

    boolean bind(UUID proxyPlayerId, UUID localPlayerId) {
        if (proxyPlayerId == null || localPlayerId == null) return false;
        UUID existing = localToProxy.putIfAbsent(localPlayerId, proxyPlayerId);
        return existing == null || existing.equals(proxyPlayerId);
    }

    void unbind(UUID proxyPlayerId, UUID localPlayerId) {
        if (proxyPlayerId == null || localPlayerId == null) return;
        localToProxy.remove(localPlayerId, proxyPlayerId);
    }

    boolean isManagedLocal(UUID localPlayerId) {
        return localPlayerId != null && localToProxy.containsKey(localPlayerId);
    }

    boolean isSameManagedPlayer(UUID proxyPlayerId, UUID localPlayerId) {
        if (proxyPlayerId == null || localPlayerId == null) return false;
        return proxyPlayerId.equals(localToProxy.get(localPlayerId));
    }

    UUID proxyForLocal(UUID localPlayerId) {
        return localToProxy.get(localPlayerId);
    }

    int size() {
        return localToProxy.size();
    }

    void clear() {
        localToProxy.clear();
    }
}
