package dev.akashic.voice.platform.bridge;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
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
import java.util.UUID;

/**
 * Versioned local membership store. Use ONE instance per trusted path from a serial I/O worker,
 * never an audio/render/tick thread. No Gateway lifecycle is registered by this class.
 * SHA-256 detects accidental corruption; it is not a signature or protection from a local attacker.
 * Outstanding invitations and all voice/session credentials are deliberately excluded.
 */
public final class FederatedGroupStore {
    static final int MAGIC = 0x41564752; // AVGR
    static final int VERSION = 1;
    static final int MAX_FILE_BYTES = 1_048_576;
    private static final int HEADER_BYTES = 12;
    private static final int DIGEST_BYTES = 32;
    private static final int MAX_NAME_BYTES = 256;
    @FunctionalInterface interface AtomicMover { void move(Path source, Path destination) throws IOException; }
    private final Path file;
    private final AtomicMover mover;

    public FederatedGroupStore(Path file) {
        this(file, (source, destination) -> Files.move(source, destination,
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    FederatedGroupStore(Path file, AtomicMover mover) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        if (this.file.getFileName() == null) throw new IllegalArgumentException("store path must name a file");
        this.mover = Objects.requireNonNull(mover, "mover");
    }

    /** An invalid/unreadable file never clears or partially changes the target directory. */
    public synchronized boolean loadInto(FederatedGroupDirectory target) throws IOException {
        Objects.requireNonNull(target, "target");
        byte[] bytes;
        try (var input = Files.newInputStream(file)) {
            bytes = input.readNBytes(MAX_FILE_BYTES + 1);
        } catch (NoSuchFileException missing) {
            return false;
        }
        List<FederatedGroup> decoded = decode(bytes, target.limits());
        try {
            target.replaceAll(decoded);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new IOException("Invalid federated group membership", invalid);
        }
        return true;
    }

    /** No non-atomic fallback: a rejected replacement is reported rather than truncating old data. */
    public synchronized void save(FederatedGroupDirectory source) throws IOException {
        Objects.requireNonNull(source, "source");
        // Snapshot is captured inside this store's serialization lock, not queued by a stale caller.
        byte[] data = encode(source.snapshot(), source.limits());
        Path parent = file.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "av-groups-", ".tmp");
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

    private static byte[] encode(List<FederatedGroup> groups, FederatedGroupDirectory.Limits limits) throws IOException {
        if (groups.size() > limits.maxGroups()) throw new IOException("Too many groups to persist");
        List<byte[]> names = new ArrayList<>(groups.size());
        long size = HEADER_BYTES + DIGEST_BYTES;
        for (FederatedGroup group : groups) {
            if (group.members().size() > limits.maxMembers()) throw new IOException("Too many members to persist");
            byte[] name = encodeName(group.name());
            names.add(name);
            size += 16 + 2 + name.length + 16 + 2 + 16L * group.members().size();
        }
        if (size > MAX_FILE_BYTES) throw new IOException("Group store exceeds file budget");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) size);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(groups.size());
            int index = 0;
            for (FederatedGroup group : groups) {
                writeUuid(out, group.id());
                byte[] name = names.get(index++); out.writeShort(name.length); out.write(name);
                writeUuid(out, group.ownerId()); out.writeShort(group.members().size());
                for (UUID member : group.members()) writeUuid(out, member);
            }
            out.flush();
            byte[] body = bytes.toByteArray();
            out.write(sha256(body, body.length));
            out.flush();
        }
        return bytes.toByteArray();
    }

    private static List<FederatedGroup> decode(byte[] data, FederatedGroupDirectory.Limits limits) throws IOException {
        if (data.length < HEADER_BYTES + DIGEST_BYTES || data.length > MAX_FILE_BYTES) {
            throw new IOException("Invalid group store size");
        }
        int bodyLength = data.length - DIGEST_BYTES;
        byte[] actual = sha256(data, bodyLength);
        byte[] expected = Arrays.copyOfRange(data, bodyLength, data.length);
        if (!MessageDigest.isEqual(actual, expected)) throw new IOException("Group store checksum mismatch");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data, 0, bodyLength))) {
            if (in.readInt() != MAGIC) throw new IOException("Invalid group store magic");
            if (in.readInt() != VERSION) throw new IOException("Unsupported group store version");
            int count = in.readInt();
            if (count < 0 || count > limits.maxGroups()) throw new IOException("Invalid group count");
            List<FederatedGroup> groups = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                UUID id = readUuid(in);
                int length = in.readUnsignedShort();
                if (length == 0 || length > MAX_NAME_BYTES || length > in.available()) throw new IOException("Invalid group name length");
                byte[] nameBytes = in.readNBytes(length);
                String name = decodeName(nameBytes);
                if (!name.equals(name.trim())) throw new IOException("Non-canonical group name");
                UUID owner = readUuid(in);
                int members = in.readUnsignedShort();
                if (members == 0 || members > limits.maxMembers() || members > in.available() / 16) {
                    throw new IOException("Invalid member count");
                }
                LinkedHashSet<UUID> membership = new LinkedHashSet<>();
                for (int member = 0; member < members; member++) {
                    if (!membership.add(readUuid(in))) throw new IOException("Duplicate group member");
                }
                groups.add(new FederatedGroup(id, name, owner, membership));
            }
            if (in.available() != 0) throw new IOException("Trailing group store data");
            return List.copyOf(groups);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new IOException("Invalid group store record", invalid);
        }
    }

    private static byte[] encodeName(String name) throws IOException {
        try {
            ByteBuffer bytes = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(name));
            if (bytes.remaining() > MAX_NAME_BYTES) throw new IOException("Group name too long");
            byte[] result = new byte[bytes.remaining()]; bytes.get(result); return result;
        } catch (CharacterCodingException invalid) { throw new IOException("Invalid Unicode group name", invalid); }
    }

    private static String decodeName(byte[] name) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(name)).toString();
        } catch (CharacterCodingException invalid) { throw new IOException("Invalid UTF-8 group name", invalid); }
    }

    private static byte[] sha256(byte[] bytes, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes, 0, length); return digest.digest();
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
    private static void writeUuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits());
    }
    private static UUID readUuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
}
