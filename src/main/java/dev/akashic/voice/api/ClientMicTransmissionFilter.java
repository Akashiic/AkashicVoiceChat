package dev.akashic.voice.api;

/**
 * Optional client-side gate filter consulted before microphone audio is encoded and transmitted
 * (for example, to enforce magical silence debuffs or roleplay mute states).
 *
 * <p>
 * Invoked on the microphone capture thread. Implementations MUST be fast and non-blocking.
 * Returning {@code false} suppresses transmission of the current frame.
 */
@FunctionalInterface
public interface ClientMicTransmissionFilter {

    boolean allowTransmission();
}
