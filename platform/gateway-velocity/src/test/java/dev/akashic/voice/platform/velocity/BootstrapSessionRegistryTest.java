package dev.akashic.voice.platform.velocity;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BootstrapSessionRegistryTest {
    @Test
    void grantIsIdempotentUntilInvalidated() {
        UUID proxyId = UUID.randomUUID();
        try (BootstrapSessionRegistry registry = new BootstrapSessionRegistry()) {
            BootstrapGrant first = registry.grantFor(proxyId, "TestPlayer", "lobby", 24454);
            BootstrapGrant second = registry.grantFor(proxyId, "TestPlayer", "lobby", 24454);
            assertEquals(first.voiceIdentity(), second.voiceIdentity());
            assertEquals(first.sessionId(), second.sessionId());
            assertArrayEquals(first.masterSecretCopy(), second.masterSecretCopy());
            assertEquals(1, registry.size());

            registry.invalidate(proxyId);
            assertEquals(0, registry.size());
            BootstrapGrant rotated = registry.grantFor(proxyId, "TestPlayer", "lobby", 24454);
            assertNotEquals(first.voiceIdentity(), rotated.voiceIdentity());
            assertNotEquals(first.sessionId(), rotated.sessionId());
        }
    }

    @Test
    void grantAndLookupUseDefensiveSecretCopies() {
        UUID proxyId = UUID.randomUUID();
        try (BootstrapSessionRegistry registry = new BootstrapSessionRegistry()) {
            BootstrapGrant grant = registry.grantFor(proxyId, "TestPlayer", "lobby", 24454);
            byte[] first = grant.masterSecretCopy();
            byte original = first[0];
            first[0] ^= 0x7F;
            assertEquals(original, grant.masterSecretCopy()[0]);

            BootstrapSessionRegistry.SessionMaterial material =
                registry.findByVoiceIdentity(grant.voiceIdentity());
            assertNotNull(material);
            assertEquals(proxyId, material.proxyPlayerId());
            assertEquals("TestPlayer", material.playerName());
            assertEquals("lobby", material.backendName());
            assertEquals(grant.voiceIdentity(), material.voiceIdentity());
            assertEquals(grant.sessionId(), material.sessionId());
            assertArrayEquals(grant.masterSecretCopy(), material.secretCopy());
            assertNotNull(registry.findByVoiceIdentity(grant.voiceIdentity(), grant.sessionId()));
            assertNull(registry.findByVoiceIdentity(grant.voiceIdentity(), UUID.randomUUID()));
        }
    }
}
