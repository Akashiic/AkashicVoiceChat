package dev.akashic.voice.platform.velocity;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LegacyForgeBootstrapCodecTest {
    @Test
    void acceptsOnlyExactForgeRequestFrame() {
        assertTrue(LegacyForgeBootstrapCodec.isBootstrapRequest(new byte[]{0}));
        assertFalse(LegacyForgeBootstrapCodec.isBootstrapRequest(new byte[0]));
        assertFalse(LegacyForgeBootstrapCodec.isBootstrapRequest(new byte[]{0, 0}));
        assertFalse(LegacyForgeBootstrapCodec.isBootstrapRequest(new byte[]{1}));
    }

    @Test
    void rebootstrapUsesRegisteredClientDiscriminator() {
        byte[] wire = LegacyForgeBootstrapCodec.encodeRebootstrap();
        assertArrayEquals(new byte[] {9}, wire);
    }

    @Test
    void responseMatchesForgeSimpleIndexedFraming() {
        UUID id = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        byte[] secret = new byte[32];
        for (int i = 0; i < secret.length; i++) secret[i] = (byte) i;
        byte[] wire = LegacyForgeBootstrapCodec.encodeResponse(id, secret, 24454);
        assertEquals(53, wire.length);
        ByteBuffer in = ByteBuffer.wrap(wire);
        assertEquals(1, in.get());
        assertEquals(id, new UUID(in.getLong(), in.getLong()));
        byte[] decodedSecret = new byte[32];
        in.get(decodedSecret);
        assertArrayEquals(secret, decodedSecret);
        assertEquals(24454, in.getInt());
        assertFalse(in.hasRemaining());
    }

    @Test
    void responseRejectsInvalidInputs() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
            () -> LegacyForgeBootstrapCodec.encodeResponse(id, new byte[31], 24454));
        assertThrows(IllegalArgumentException.class,
            () -> LegacyForgeBootstrapCodec.encodeResponse(id, new byte[32], 0));
    }
}
