package dev.akashic.voice.platform.agent;

import dev.akashic.voice.voice.common.crypto.SessionCrypto;
import dev.akashic.voice.voice.common.crypto.SessionKeys;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class AgentCryptoTest {
    @Test
    void domainSeparatedKeysRoundTripAndWrongPskFails() {
        byte[] psk = new byte[32];
        byte[] wrong = new byte[32];
        byte[] clientNonce = new byte[16];
        byte[] serverNonce = new byte[16];
        for (int i = 0; i < 32; i++) { psk[i] = (byte) (i + 1); wrong[i] = (byte) (0x7f - i); }
        for (int i = 0; i < 16; i++) { clientNonce[i] = (byte) (i * 3); serverNonce[i] = (byte) (i * 7); }

        SessionKeys clientKeys = AgentKeyDerivation.derive(psk, "lobby", clientNonce, serverNonce);
        SessionKeys serverKeys = AgentKeyDerivation.derive(psk, "lobby", clientNonce, serverNonce);
        SessionKeys wrongKeys = AgentKeyDerivation.derive(wrong, "lobby", clientNonce, serverNonce);
        try (SessionCrypto client = SessionCrypto.forClient(clientKeys);
             SessionCrypto server = SessionCrypto.forServer(serverKeys);
             SessionCrypto impostor = SessionCrypto.forServer(wrongKeys)) {
            ByteBuffer plain = ByteBuffer.allocate(64);
            plain.put(AgentProtocol.PING).putLong(123456789L);
            ByteBuffer encrypted = ByteBuffer.allocate(256);
            assertTrue(client.seal(plain, encrypted));
            byte[] wire = Arrays.copyOf(encrypted.array(), encrypted.position());

            ByteBuffer decoded = ByteBuffer.allocate(64);
            assertTrue(server.open(ByteBuffer.wrap(wire), decoded));
            assertEquals(AgentProtocol.PING, decoded.get());
            assertEquals(123456789L, decoded.getLong());
            assertFalse(decoded.hasRemaining());

            ByteBuffer rejected = ByteBuffer.allocate(64);
            assertFalse(impostor.open(ByteBuffer.wrap(wire), rejected));
        } finally {
            clientKeys.close();
            serverKeys.close();
            wrongKeys.close();
            Arrays.fill(psk, (byte) 0);
            Arrays.fill(wrong, (byte) 0);
        }
    }

    @Test
    void routeIdentityIsStableAndBackendSpecific() {
        assertEquals(AgentKeyDerivation.routeIdentity("lobby"), AgentKeyDerivation.routeIdentity("lobby"));
        assertNotEquals(AgentKeyDerivation.routeIdentity("lobby"), AgentKeyDerivation.routeIdentity("forbidden-1"));
    }
}
