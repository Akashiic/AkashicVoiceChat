package dev.akashic.voice.platform.velocity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GatewaySettingsTest {
    @TempDir
    Path tempDir;

    @Test
    void createsAndReloadsStableBackendSecret() throws Exception {
        byte[] firstSecret;
        try (GatewaySettings first = GatewaySettings.load(tempDir)) {
            assertEquals(24454, first.udpPort());
            assertEquals(24456, first.agentBind().getPort());
            assertTrue(first.agentBind().getAddress().isLoopbackAddress());
            assertEquals(Set.of("lobby"), first.allowedBackends());            Map<String, byte[]> psks = first.agentPsksCopy();
            try {
                firstSecret = Arrays.copyOf(psks.get("lobby"), 32);
            } finally {
                zero(psks);
            }
        }

        Path config = tempDir.resolve("config.properties");
        assertTrue(Files.isRegularFile(config));
        Properties stored = loadProperties(config);
        assertNotNull(stored.getProperty("agent-psk.lobby.base64"));
        assertNull(stored.getProperty("agent-psk-base64"));
        if (Files.getFileAttributeView(config, PosixFileAttributeView.class) != null) {
            assertEquals(
                Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(config));
        }

        try (GatewaySettings second = GatewaySettings.load(tempDir)) {
            Map<String, byte[]> psks = second.agentPsksCopy();
            try {
                assertArrayEquals(firstSecret, psks.get("lobby"));
            } finally {
                zero(psks);
            }
        } finally {
            Arrays.fill(firstSecret, (byte) 0);
        }
    }
    @Test
    void migratesLegacySecretForSingleBackend() throws Exception {
        byte[] legacy = fixedPsk(17);
        Properties properties = baseProperties("lobby");
        properties.setProperty("agent-psk-base64", Base64.getEncoder().encodeToString(legacy));
        store(tempDir.resolve("config.properties"), properties);

        try (GatewaySettings settings = GatewaySettings.load(tempDir)) {
            Map<String, byte[]> psks = settings.agentPsksCopy();
            try {
                assertArrayEquals(legacy, psks.get("lobby"));
            } finally {
                zero(psks);
            }
        } finally {
            Arrays.fill(legacy, (byte) 0);
        }

        Properties migrated = loadProperties(tempDir.resolve("config.properties"));
        assertNull(migrated.getProperty("agent-psk-base64"));
        assertNotNull(migrated.getProperty("agent-psk.lobby.base64"));
    }

    @Test
    void refusesLegacySecretAcrossMultipleBackends() throws Exception {
        Properties properties = baseProperties("lobby,forbidden-1");
        properties.setProperty("agent-psk-base64",
            Base64.getEncoder().encodeToString(fixedPsk(3)));
        store(tempDir.resolve("config.properties"), properties);
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class, () -> GatewaySettings.load(tempDir));
        assertTrue(error.getMessage().contains("multiple backends"));
    }
    @Test
    void generatesDistinctSecretsForDifferentBackends() throws Exception {
        Properties properties = baseProperties("lobby,forbidden-1");
        store(tempDir.resolve("config.properties"), properties);

        try (GatewaySettings settings = GatewaySettings.load(tempDir)) {
            assertEquals(Set.of("lobby", "forbidden-1"), settings.allowedBackends());
            Map<String, byte[]> psks = settings.agentPsksCopy();
            try {
                assertEquals(32, psks.get("lobby").length);
                assertEquals(32, psks.get("forbidden-1").length);
                assertFalse(Arrays.equals(psks.get("lobby"), psks.get("forbidden-1")));
            } finally {
                zero(psks);
            }
        }
    }

    @Test
    void rejectsWrongSizedScopedPsk() throws Exception {
        Properties properties = baseProperties("lobby");
        properties.setProperty("agent-psk.lobby.base64",
            Base64.getEncoder().encodeToString(new byte[31]));
        store(tempDir.resolve("config.properties"), properties);
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class, () -> GatewaySettings.load(tempDir));
        assertTrue(error.getMessage().contains("32 bytes"));
    }

    private static Properties baseProperties(String backends) {
        Properties properties = new Properties();
        properties.setProperty("udp-port", "24454");
        properties.setProperty("agent-bind-host", "127.0.0.1");        properties.setProperty("agent-port", "24456");
        properties.setProperty("allowed-backends", backends);
        return properties;
    }

    private static void store(Path path, Properties properties) throws Exception {
        try (OutputStream output = Files.newOutputStream(path)) {
            properties.store(output, "test");
        }
    }

    private static Properties loadProperties(Path path) throws Exception {
        Properties properties = new Properties();
        try (java.io.InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
    }

    private static byte[] fixedPsk(int seed) {
        byte[] psk = new byte[32];
        for (int i = 0; i < psk.length; i++) psk[i] = (byte) (seed + i * 11);
        return psk;
    }

    private static void zero(Map<String, byte[]> values) {
        for (byte[] secret : values.values()) {
            if (secret != null) Arrays.fill(secret, (byte) 0);
        }
        values.clear();
    }
}
