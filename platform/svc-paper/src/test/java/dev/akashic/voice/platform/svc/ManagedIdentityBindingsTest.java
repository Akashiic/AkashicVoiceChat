package dev.akashic.voice.platform.svc;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ManagedIdentityBindingsTest {
    @Test
    void supportsDifferentProxyAndLocalUuids() {
        ManagedIdentityBindings bindings = new ManagedIdentityBindings();
        UUID proxyId = UUID.randomUUID();
        UUID localId = UUID.randomUUID();

        assertNotEquals(proxyId, localId);
        assertTrue(bindings.bind(proxyId, localId));
        assertEquals(1, bindings.size());
        assertTrue(bindings.isManagedLocal(localId));
        assertTrue(bindings.isSameManagedPlayer(proxyId, localId));
        assertFalse(bindings.isSameManagedPlayer(UUID.randomUUID(), localId));
        assertEquals(proxyId, bindings.proxyForLocal(localId));

        bindings.unbind(proxyId, localId);
        assertFalse(bindings.isManagedLocal(localId));
        assertEquals(0, bindings.size());
        assertNull(bindings.proxyForLocal(localId));
    }
    @Test
    void rejectsSecondProxyClaimingSameLocalUuid() {
        ManagedIdentityBindings bindings = new ManagedIdentityBindings();
        UUID firstProxy = UUID.randomUUID();
        UUID secondProxy = UUID.randomUUID();
        UUID localId = UUID.randomUUID();

        assertTrue(bindings.bind(firstProxy, localId));
        assertFalse(bindings.bind(secondProxy, localId));
        assertEquals(firstProxy, bindings.proxyForLocal(localId));

        bindings.unbind(secondProxy, localId);
        assertEquals(firstProxy, bindings.proxyForLocal(localId));
        bindings.unbind(firstProxy, localId);
        assertNull(bindings.proxyForLocal(localId));
    }
}
