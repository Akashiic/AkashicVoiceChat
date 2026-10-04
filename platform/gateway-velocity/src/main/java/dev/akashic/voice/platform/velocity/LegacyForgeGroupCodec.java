package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.bridge.FederatedGroup;

import java.io.OutputStream;
import java.util.Arrays;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

final class LegacyForgeGroupCodec {
    static final byte CREATE = 2;
    static final byte INVITE = 3;
    static final byte RESPOND = 4;
    static final byte LEAVE = 5;
    static final byte INVITE_NOTIFY = 6;
    static final byte GROUP_STATE = 7;
    static final byte ROSTER = 8;
    private static final int MAX_NAME_BYTES = 256;
    private static final int MAX_ENTRIES = 4096;
    // Conservative project budget, including the discriminator and all nested entries.
    // Does not assert that 30,000 is Minecraft's exact protocol maximum.
    static final int MAX_MESSAGE_BYTES = 30_000;
    private static final int MAX_REQUEST_BYTES = 3 + MAX_NAME_BYTES;

    sealed interface Request permits Create, Invite, Respond, Leave {}
    record Create(String name) implements Request {}
    record Invite(UUID groupId, UUID targetId) implements Request {}
    record Respond(UUID groupId, boolean accepted) implements Request {}
    record Leave(UUID groupId) implements Request {}
    record RosterPlayer(UUID id, String name) {}

    private LegacyForgeGroupCodec() {}

    static Request decodeRequest(byte[] data) {
        if (data == null || data.length < 1 || data.length > MAX_REQUEST_BYTES) return null;
        ByteBuffer in = ByteBuffer.wrap(data);
        byte discriminator = in.get();
        try {
            return switch (discriminator) {
                case CREATE -> decodeCreate(in);
                case INVITE -> decodeInvite(in);
                case RESPOND -> decodeRespond(in);
                case LEAVE -> decodeLeave(in);
                default -> null;
            };
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static boolean isGroupRequestDiscriminator(byte discriminator) {
        return discriminator >= CREATE && discriminator <= LEAVE;
    }

    static boolean isServerGroupControlDiscriminator(byte discriminator) {
        return discriminator >= INVITE_NOTIFY && discriminator <= ROSTER;
    }

    private static Create decodeCreate(ByteBuffer in) {
        String name = readString(in);
        if (in.hasRemaining() || name.trim().isEmpty() || name.trim().length() > 64) return null;
        return new Create(name.trim());
    }

    private static Invite decodeInvite(ByteBuffer in) {
        if (in.remaining() != 32) return null;
        return new Invite(readUuid(in), readUuid(in));
    }

    private static Respond decodeRespond(ByteBuffer in) {
        if (in.remaining() != 17) return null;
        UUID groupId = readUuid(in);
        byte accepted = in.get();
        if (accepted != 0 && accepted != 1) return null;
        return new Respond(groupId, accepted != 0);
    }

    private static Leave decodeLeave(ByteBuffer in) {
        if (in.remaining() != 16) return null;
        return new Leave(readUuid(in));
    }

    static byte[] encodeInviteNotify(FederatedGroup group, UUID inviterId, String inviterName) {
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(inviterId, "inviterId");
        return write(out -> {
            out.writeByte(INVITE_NOTIFY);
            writeUuid(out, group.id());
            writeString(out, group.name());
            writeUuid(out, inviterId);
            writeString(out, inviterName);
        });
    }

    static byte[] encodeGroupState(Collection<FederatedGroup> groups, Function<UUID, String> nameResolver) {
        Objects.requireNonNull(groups, "groups");
        Objects.requireNonNull(nameResolver, "nameResolver");
        if (groups.size() > MAX_ENTRIES) throw new IllegalArgumentException("too many groups");
        return write(out -> {
            out.writeByte(GROUP_STATE);
            out.writeShort(groups.size());
            for (FederatedGroup group : groups) {
                writeUuid(out, group.id());
                writeString(out, group.name());
                writeUuid(out, group.ownerId());
                if (group.members().size() > MAX_ENTRIES) throw new IllegalArgumentException("too many members");
                out.writeShort(group.members().size());
                for (UUID memberId : group.members()) {
                    writeUuid(out, memberId);
                    writeString(out, resolvedName(nameResolver, memberId));
                }
            }
        });
    }

    static byte[] encodeRoster(Collection<RosterPlayer> players) {
        Objects.requireNonNull(players, "players");
        if (players.size() > MAX_ENTRIES) throw new IllegalArgumentException("too many roster entries");
        return write(out -> {
            out.writeByte(ROSTER);
            out.writeShort(players.size());
            for (RosterPlayer player : players) {
                writeUuid(out, player.id());
                writeString(out, player.name());
            }
        });
    }

    private static UUID readUuid(ByteBuffer in) {
        return new UUID(in.getLong(), in.getLong());
    }

    private static String readString(ByteBuffer in) {
        if (in.remaining() < 2) throw new IllegalArgumentException("missing string length");
        int length = in.getShort() & 0xFFFF;
        if (length > MAX_NAME_BYTES || in.remaining() < length) {
            throw new IllegalArgumentException("invalid string length");
        }
        ByteBuffer slice = in.slice();
        slice.limit(length);
        try {
            String value = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(slice)
                .toString();
            in.position(in.position() + length);
            return value;
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("invalid UTF-8", e);
        }
    }

    private static void writeUuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        String normalized = Objects.requireNonNullElse(value, "");
        // Check before UTF-8 allocation. Byte length is checked as well, not inferred.
        if (normalized.length() > MAX_NAME_BYTES) throw new IllegalArgumentException("string too long");
        byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_NAME_BYTES) throw new IllegalArgumentException("string too long");
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static String resolvedName(Function<UUID, String> resolver, UUID memberId) {
        String name = resolver.apply(memberId);
        // Match the legacy GroupRegistry display fallback for an offline member.
        return name != null ? name : memberId.toString().substring(0, 8);
    }

    @FunctionalInterface
    private interface PacketWriter {
        void write(DataOutputStream out) throws IOException;
    }

    private static byte[] write(PacketWriter writer) {
        BoundedOutput bytes = new BoundedOutput();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            writer.write(out);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("Could not encode legacy group packet", failure);
        }
    }

    /** No backing allocation or partial result may exceed the aggregate message budget. */
    private static final class BoundedOutput extends OutputStream {
        private byte[] buffer = new byte[128];
        private int count;

        private void ensureRoom(int extra) {
            if (extra < 0 || extra > MAX_MESSAGE_BYTES - count) {
                throw new IllegalArgumentException("legacy group message exceeds aggregate budget");
            }
            int required = count + extra;
            if (required > buffer.length) {
                buffer = Arrays.copyOf(buffer,
                    Math.min(MAX_MESSAGE_BYTES, Math.max(required, buffer.length * 2)));
            }
        }

        @Override
        public void write(int value) {
            ensureRoom(1);
            buffer[count++] = (byte) value;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            ensureRoom(length);
            System.arraycopy(bytes, offset, buffer, count, length);
            count += length;
        }

        byte[] toByteArray() {
            return Arrays.copyOf(buffer, count);
        }
    }
}
