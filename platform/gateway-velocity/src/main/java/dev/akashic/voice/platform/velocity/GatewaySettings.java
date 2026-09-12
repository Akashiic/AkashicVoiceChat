package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.agent.AgentHandshake;
import dev.akashic.voice.platform.agent.AgentKeyDerivation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

final class GatewaySettings implements AutoCloseable {
    private static final String FILE_NAME = "config.properties";
    private static final String LEGACY_PSK_KEY = "agent-psk-base64";
    private static final String PSK_PREFIX = "agent-psk.";
    private static final String PSK_SUFFIX = ".base64";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final int udpPort;
    private final InetSocketAddress agentBind;
    private final Map<String, byte[]> agentPsks = new LinkedHashMap<>();

    private GatewaySettings(int udpPort, InetSocketAddress agentBind, Map<String, byte[]> psks) {
        this.udpPort = udpPort;
        this.agentBind = agentBind;
        copyPsks(psks, agentPsks);
    }

    static GatewaySettings load(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);
        Path config = dataDirectory.resolve(FILE_NAME);
        if (Files.notExists(config)) writeDefaults(config);
        restrictPosixPermissions(config);

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(config)) {
            properties.load(input);
        }

        int udpPort = parsePort(properties, "udp-port", 24_454);
        int agentPort = parsePort(properties, "agent-port", 24_456);
        InetSocketAddress bind = parseBind(properties, agentPort);
        Set<String> backends = parseBackends(properties.getProperty("allowed-backends", "lobby"));
        ParseResult parsed = parseBackendPsks(properties, backends);
        try {
            if (parsed.changed) store(config, properties);
            return new GatewaySettings(udpPort, bind, parsed.psks);
        } finally {
            zeroPsks(parsed.psks);
        }
    }
    int udpPort() { return udpPort; }
    InetSocketAddress agentBind() { return agentBind; }
    Set<String> allowedBackends() { return Set.copyOf(agentPsks.keySet()); }

    Map<String, byte[]> agentPsksCopy() {
        LinkedHashMap<String, byte[]> copy = new LinkedHashMap<>();
        copyPsks(agentPsks, copy);
        return copy;
    }

    @Override
    public void close() {
        zeroPsks(agentPsks);
        agentPsks.clear();
    }

    private static InetSocketAddress parseBind(Properties properties, int agentPort) {
        String host = properties.getProperty("agent-bind-host", "127.0.0.1").trim();
        if (host.isEmpty()) throw new IllegalArgumentException("agent-bind-host is empty");
        InetSocketAddress bind = new InetSocketAddress(host, agentPort);
        if (bind.isUnresolved()) throw new IllegalArgumentException("agent-bind-host cannot be resolved");
        return bind;
    }

    private static void writeDefaults(Path config) throws IOException {
        Properties defaults = new Properties();
        defaults.setProperty("udp-port", "24454");
        defaults.setProperty("agent-bind-host", "127.0.0.1");
        defaults.setProperty("agent-port", "24456");
        defaults.setProperty("allowed-backends", "lobby");        byte[] psk = randomPsk();
        try {
            defaults.setProperty(pskKey("lobby"), Base64.getEncoder().encodeToString(psk));
        } finally {
            Arrays.fill(psk, (byte) 0);
        }
        store(config, defaults);
    }

    private static ParseResult parseBackendPsks(Properties properties, Set<String> backends) {
        LinkedHashMap<String, byte[]> result = new LinkedHashMap<>();
        boolean changed = false;
        String legacy = properties.getProperty(LEGACY_PSK_KEY);
        if (legacy != null && !legacy.isBlank() && backends.size() != 1) {
            throw new IllegalArgumentException(
                LEGACY_PSK_KEY + " cannot be reused across multiple backends; configure one PSK per backend");
        }

        for (String backend : backends) {
            String key = pskKey(backend);
            String encoded = properties.getProperty(key);
            if ((encoded == null || encoded.isBlank()) && legacy != null && !legacy.isBlank()) {
                encoded = legacy;
                properties.setProperty(key, legacy.trim());
                changed = true;
            }
            if (encoded == null || encoded.isBlank()) {
                byte[] generated = randomPsk();
                try {
                    encoded = Base64.getEncoder().encodeToString(generated);
                    properties.setProperty(key, encoded);
                    changed = true;
                } finally {
                    Arrays.fill(generated, (byte) 0);
                }
            }
            result.put(backend, decodePsk(encoded, key));
        }
        if (legacy != null) {
            properties.remove(LEGACY_PSK_KEY);
            changed = true;
        }
        return new ParseResult(result, changed);
    }

    private static byte[] decodePsk(String encoded, String key) {
        final byte[] psk;
        try {
            psk = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(key + " is not valid Base64", e);
        }
        if (psk.length != AgentKeyDerivation.PSK_BYTES) {
            Arrays.fill(psk, (byte) 0);
            throw new IllegalArgumentException(key + " must decode to 32 bytes");
        }
        return psk;
    }

    private static byte[] randomPsk() {
        byte[] psk = new byte[AgentKeyDerivation.PSK_BYTES];
        RANDOM.nextBytes(psk);
        return psk;
    }

    private static String pskKey(String backend) {
        return PSK_PREFIX + backend + PSK_SUFFIX;
    }

    private static int parsePort(Properties properties, String key, int defaultValue) {
        String raw = properties.getProperty(key, Integer.toString(defaultValue)).trim();
        final int value;        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be an integer", e);
        }
        if (value < 1 || value > 65_535) throw new IllegalArgumentException(key + " is out of range");
        return value;
    }

    private static Set<String> parseBackends(String raw) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (raw != null) {
            for (String token : raw.split(",")) {
                String backend = token.trim();
                if (backend.isEmpty()) continue;
                if (!AgentHandshake.isValidBackendId(backend)) {
                    throw new IllegalArgumentException("Invalid backend in allowed-backends: " + backend);
                }
                values.add(backend);
            }
        }
        if (values.isEmpty()) throw new IllegalArgumentException("allowed-backends must not be empty");
        return Set.copyOf(values);
    }

    private static void store(Path config, Properties properties) throws IOException {
        Path temp = config.resolveSibling(config.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(
            temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            properties.store(output, "Akashic Voice Gateway - keep backend agent PSKs private");
        }
        moveAtomically(temp, config);
        restrictPosixPermissions(config);
    }
    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void restrictPosixPermissions(Path config) throws IOException {
        PosixFileAttributeView view = Files.getFileAttributeView(config, PosixFileAttributeView.class);
        if (view != null) {
            Files.setPosixFilePermissions(config, PosixFilePermissions.fromString("rw-------"));
        }
    }

    private static void copyPsks(Map<String, byte[]> source, Map<String, byte[]> target) {
        for (Map.Entry<String, byte[]> entry : source.entrySet()) {
            byte[] value = entry.getValue();
            if (value == null || value.length != AgentKeyDerivation.PSK_BYTES) {
                throw new IllegalArgumentException("invalid PSK for backend " + entry.getKey());
            }
            target.put(entry.getKey(), Arrays.copyOf(value, value.length));
        }
    }

    private static void zeroPsks(Map<String, byte[]> values) {
        for (byte[] secret : values.values()) {
            if (secret != null) Arrays.fill(secret, (byte) 0);
        }
    }

    private record ParseResult(Map<String, byte[]> psks, boolean changed) {}
}
