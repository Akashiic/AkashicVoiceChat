package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.agent.AgentHandshake;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

final class GatewaySettings implements AutoCloseable {
    private static final String FILE_NAME = "config.properties";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final int udpPort;
    private final InetSocketAddress agentBind;
    private final byte[] agentPsk;
    private final Set<String> allowedBackends;

    private GatewaySettings(int udpPort, InetSocketAddress agentBind,
                            byte[] agentPsk, Set<String> allowedBackends) {
        this.udpPort = udpPort;
        this.agentBind = agentBind;
        this.agentPsk = Arrays.copyOf(agentPsk, agentPsk.length);
        this.allowedBackends = Set.copyOf(allowedBackends);
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
        String bindHost = properties.getProperty("agent-bind-host", "127.0.0.1").trim();
        if (bindHost.isEmpty()) throw new IllegalArgumentException("agent-bind-host is empty");
        InetSocketAddress bind = new InetSocketAddress(bindHost, agentPort);
        if (bind.isUnresolved()) throw new IllegalArgumentException("agent-bind-host cannot be resolved");

        byte[] psk = decodePsk(properties.getProperty("agent-psk-base64", ""));
        try {
            Set<String> allowed = parseBackends(properties.getProperty("allowed-backends", "lobby"));
            return new GatewaySettings(udpPort, bind, psk, allowed);
        } finally {
            Arrays.fill(psk, (byte) 0);
        }
    }
    int udpPort() { return udpPort; }
    InetSocketAddress agentBind() { return agentBind; }
    Set<String> allowedBackends() { return allowedBackends; }
    byte[] agentPskCopy() { return Arrays.copyOf(agentPsk, agentPsk.length); }

    @Override
    public void close() {
        Arrays.fill(agentPsk, (byte) 0);
    }

    private static void writeDefaults(Path config) throws IOException {
        Properties defaults = new Properties();
        defaults.setProperty("udp-port", "24454");
        defaults.setProperty("agent-bind-host", "127.0.0.1");
        defaults.setProperty("agent-port", "24456");
        defaults.setProperty("allowed-backends", "lobby");
        byte[] psk = new byte[32];
        RANDOM.nextBytes(psk);
        try {
            defaults.setProperty("agent-psk-base64", Base64.getEncoder().encodeToString(psk));
        } finally {
            Arrays.fill(psk, (byte) 0);
        }

        Path temp = config.resolveSibling(config.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temp)) {
            defaults.store(output, "Akashic Voice Gateway - keep agent-psk-base64 private");
        }
        moveAtomically(temp, config);
        restrictPosixPermissions(config);
    }
    private static void restrictPosixPermissions(Path config) throws IOException {
        PosixFileAttributeView view = Files.getFileAttributeView(config, PosixFileAttributeView.class);
        if (view != null) {
            Files.setPosixFilePermissions(config, PosixFilePermissions.fromString("rw-------"));
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static int parsePort(Properties properties, String key, int defaultValue) {
        String raw = properties.getProperty(key, Integer.toString(defaultValue)).trim();
        final int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be an integer", e);
        }
        if (value < 1 || value > 65_535) throw new IllegalArgumentException(key + " is out of range");
        return value;
    }

    private static byte[] decodePsk(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("agent-psk-base64 is required");
        }
        final byte[] psk;
        try {
            psk = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("agent-psk-base64 is not valid Base64", e);
        }
        if (psk.length != 32) {
            Arrays.fill(psk, (byte) 0);
            throw new IllegalArgumentException("agent-psk-base64 must decode to 32 bytes");
        }
        return psk;
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
}
