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
            BootstrapGrant first = registry.grantFor(proxyId, 24454);
            BootstrapGrant second = registry.grantFor(proxyId, 24454);
            assertEquals(first.voiceIdentity(), second.voiceIdentity());
            assertArrayEquals(first.masterSecretCopy(), second.masterSecretCopy());
            assertEquals(1, registry.size());

            registry.invalidate(proxyId);
            assertEquals(0, registry.size());
            BootstrapGrant rotated = registry.grantFor(proxyId, 24454);
            assertNotEquals(first.voiceIdentity(), rotated.voiceIdentity());
        }
    }

    @Test
    void grantAndLookupUseDefensiveSecretCopies() {
        UUID proxyId = UUID.randomUUID();
        try (BootstrapSessionRegistry registry = new BootstrapSessionRegistry()) {
            BootstrapGrant grant = registry.grantFor(proxyId, 24454);
            byte[] first = grant.masterSecretCopy();
            byte original = first[0];
            first[0] ^= 0x7F;
            assertEquals(original, grant.masterSecretCopy()[0]);

            BootstrapSessionRegistry.SessionMaterial material =
                registry.findByVoiceIdentity(grant.voiceIdentity());
            assertNotNull(material);
            assertEquals(proxyId, material.proxyPlayerId());
            assertEquals(grant.voiceIdentity(), material.voiceIdentity());
            assertArrayEquals(grant.masterSecretCopy(), material.secretCopy());
        }
    }
}
