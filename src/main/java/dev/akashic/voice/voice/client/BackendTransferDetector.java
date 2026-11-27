package dev.akashic.voice.voice.client;

/**
 * Detects a proxy backend transfer from the client side (issue #4).
 *
 * <p>
 * Behind a BungeeCord/Velocity proxy the TCP connection survives a transfer, so the client never leaves the
 * world, but the new backend sends a fresh join with its own player entity id. In direct mode every backend
 * runs its own voice server, so the old voice endpoint goes silent; without this signal the client only
 * notices after the voice timeout. A plain respawn or dimension change keeps the entity id (1.7.10
 * {@code handleRespawn} copies it), so it does not trigger. Two backends that happen to assign the same id
 * fall back to the existing timeout path. Pure logic, no game classes: testable outside Minecraft.
 */
final class BackendTransferDetector {

    private static final int UNKNOWN = Integer.MIN_VALUE;

    private int observedEntityId = UNKNOWN;

    /** @return {@code true} when the local player entity id changed while staying in a world */
    boolean observe(int entityId) {
        int previous = observedEntityId;
        observedEntityId = entityId;
        return previous != UNKNOWN && previous != entityId;
    }

    /** The client left the world (or is not in one yet). */
    void reset() {
        observedEntityId = UNKNOWN;
    }
}
