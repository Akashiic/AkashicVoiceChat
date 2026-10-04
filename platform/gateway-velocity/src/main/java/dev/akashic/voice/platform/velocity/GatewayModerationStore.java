package dev.akashic.voice.platform.velocity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Versioned persistent server-mute set. Contains identities only, never credentials or addresses. */
final class GatewayModerationStore {
    static final int MAGIC = 0x41564d44; // AVMD
    static final int VERSION = 1;
    static final int MAX_ENTRIES = 4096;
    private static final int HEADER_BYTES = 12;
    private static final int DIGEST_BYTES = 32;
    private static final int MAX_FILE_BYTES = HEADER_BYTES + 16 * MAX_ENTRIES + DIGEST_BYTES;
    @FunctionalInterface interface AtomicMover { void move(Path source, Path destination) throws IOException; }
    private final Path file;
    private final AtomicMover mover;

    GatewayModerationStore(Path file) {
        this(file, (source, destination) -> Files.move(source, destination,
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    GatewayModerationStore(Path file, AtomicMover mover) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        if (this.file.getFileName() == null) throw new IllegalArgumentException("store path must name a file");
        this.mover = Objects.requireNonNull(mover, "mover");
    }

    synchronized Set<UUID> load() throws IOException {
        byte[] bytes;
        try (var input = Files.newInputStream(file)) {
            bytes = input.readNBytes(MAX_FILE_BYTES + 1);
        } catch (NoSuchFileException missing) {
            return Set.of();
        }
        return decode(bytes);
    }

    synchronized void save(Set<UUID> muted) throws IOException {
        byte[] data = encode(muted);
        Path parent = file.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "av-moderation-", ".tmp");
        Throwable primary = null;
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(data);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            mover.move(temporary, file);
        } catch (IOException | RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanup) {
                if (primary != null) primary.addSuppressed(cleanup);
                else throw cleanup;
            }
        }
    }

    private static byte[] encode(Set<UUID> muted) throws IOException {
        Objects.requireNonNull(muted, "muted");
        if (muted.size() > MAX_ENTRIES) throw new IOException("Too many moderation entries");
        List<UUID> ordered = new ArrayList<>(muted);
        if (ordered.stream().anyMatch(Objects::isNull)) throw new IOException("Null moderation identity");
        ordered.sort(UUID::compareTo);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(HEADER_BYTES + ordered.size() * 16 + DIGEST_BYTES);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(ordered.size());
            for (UUID id : ordered) {
                out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits());
            }
            out.flush();
            byte[] body = bytes.toByteArray();
            out.write(sha256(body, body.length));
            out.flush();
        }
        return bytes.toByteArray();
    }

    private static Set<UUID> decode(byte[] data) throws IOException {
        if (data.length < HEADER_BYTES + DIGEST_BYTES || data.length > MAX_FILE_BYTES) {
            throw new IOException("Invalid moderation store size");
        }
        int bodyLength = data.length - DIGEST_BYTES;
        byte[] actual = sha256(data, bodyLength);
        byte[] expected = Arrays.copyOfRange(data, bodyLength, data.length);
        if (!MessageDigest.isEqual(actual, expected)) throw new IOException("Moderation store checksum mismatch");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data, 0, bodyLength))) {
            if (in.readInt() != MAGIC) throw new IOException("Invalid moderation store magic");
            if (in.readInt() != VERSION) throw new IOException("Unsupported moderation store version");
            int count = in.readInt();
            if (count < 0 || count > MAX_ENTRIES || in.available() != count * 16) {
                throw new IOException("Invalid moderation entry count");
            }
            LinkedHashSet<UUID> result = new LinkedHashSet<>(count);
            for (int i = 0; i < count; i++) {
                UUID id = new UUID(in.readLong(), in.readLong());
                if (!result.add(id)) throw new IOException("Duplicate moderation identity");
            }
            if (in.available() != 0) throw new IOException("Trailing moderation store data");
            return Set.copyOf(result);
        }
    }

    private static byte[] sha256(byte[] bytes, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes, 0, length);
            return digest.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
