package dev.akashic.voice.platform.bridge;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BridgeModelTest {
    @Test
    void speakerFrameOwnsPayload() {
        byte[] source = {1, 2, 3};
        AkashicSpeakerFrame frame = new AkashicSpeakerFrame(UUID.randomUUID(), 7L, false, source);
        source[0] = 99;
        assertArrayEquals(new byte[]{1, 2, 3}, frame.opusUnsafe());
    }

    @Test
    void speakerFrameRejectsInvalidPayload() {
        assertThrows(IllegalArgumentException.class,
            () -> new AkashicSpeakerFrame(UUID.randomUUID(), 0L, false, new byte[0]));
        assertThrows(IllegalArgumentException.class,
            () -> new AkashicSpeakerFrame(UUID.randomUUID(), -1L, false, new byte[]{1}));
    }

    @Test
    void deliveryRequiresSpatialMetadata() {
        UUID recipient = UUID.randomUUID();
        UUID sender = UUID.randomUUID();
        UUID channel = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new BridgeDelivery(
            recipient, sender, channel, 1L, SpatialKind.ENTITY,
            null, null, 48F, false, null, new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> new BridgeDelivery(
            recipient, sender, channel, 1L, SpatialKind.LOCATION,
            null, null, 48F, false, null, new byte[]{1}));
    }

    @Test
    void positionRejectsNonFiniteCoordinates() {
        assertThrows(IllegalArgumentException.class,
            () -> new PositionSnapshot("minecraft:overworld", Double.NaN, 0D, 0D));
    }
}
