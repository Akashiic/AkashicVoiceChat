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
    void sessionResponsePreservesLegacyPrefixAndAppendsUuid() {
        UUID id = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        UUID sessionId = UUID.fromString("fedcba98-7654-3210-fedc-ba9876543210");
        byte[] secret = new byte[32];
        for (int i = 0; i < secret.length; i++) secret[i] = (byte) (31 - i);
        byte[] legacy = LegacyForgeBootstrapCodec.encodeResponse(id, secret, 24454);
        byte[] session = LegacyForgeBootstrapCodec.encodeResponse(id, secret, 24454, sessionId);
        assertEquals(53, legacy.length);
        assertEquals(69, session.length);
        assertArrayEquals(legacy, java.util.Arrays.copyOf(session, legacy.length));
        ByteBuffer suffix = ByteBuffer.wrap(session, legacy.length, 16);
        assertEquals(sessionId, new UUID(suffix.getLong(), suffix.getLong()));
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
