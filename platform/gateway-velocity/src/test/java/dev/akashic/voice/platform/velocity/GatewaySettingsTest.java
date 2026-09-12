package dev.akashic.voice.platform.velocity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Base64;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class GatewaySettingsTest {
    @TempDir
    Path tempDir;

    @Test
    void createsAndReloadsStableSecretWithoutChangingDefaults() throws Exception {
        byte[] firstSecret;
        try (GatewaySettings first = GatewaySettings.load(tempDir)) {
            assertEquals(24454, first.udpPort());
            assertEquals(24456, first.agentBind().getPort());
            assertTrue(first.agentBind().getAddress().isLoopbackAddress());
            assertEquals(java.util.Set.of("lobby"), first.allowedBackends());
            firstSecret = first.agentPskCopy();
            assertEquals(32, firstSecret.length);
        }
        Path config = tempDir.resolve("config.properties");
        assertTrue(Files.isRegularFile(config));
        if (Files.getFileAttributeView(config, PosixFileAttributeView.class) != null) {
            assertEquals(
                java.util.Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(config));
        }
        try (GatewaySettings second = GatewaySettings.load(tempDir)) {
            byte[] secondSecret = second.agentPskCopy();
            try {
                assertArrayEquals(firstSecret, secondSecret);
            } finally {
                Arrays.fill(secondSecret, (byte) 0);
            }
        } finally {
            Arrays.fill(firstSecret, (byte) 0);
        }
    }

    @Test
    void rejectsWrongSizedPsk() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("udp-port", "24454");
        properties.setProperty("agent-bind-host", "127.0.0.1");
        properties.setProperty("agent-port", "24456");
        properties.setProperty("allowed-backends", "lobby");
        properties.setProperty("agent-psk-base64",
            Base64.getEncoder().encodeToString(new byte[31]));
        try (java.io.OutputStream output = Files.newOutputStream(tempDir.resolve("config.properties"))) {
            properties.store(output, "test");
        }
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class, () -> GatewaySettings.load(tempDir));
        assertTrue(error.getMessage().contains("32 bytes"));
    }
}
