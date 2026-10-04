package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.bridge.FederatedGroup;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class LegacyForgeGroupCodecTest {
    private static final UUID GROUP = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
    private static final UUID PLAYER = UUID.fromString("10213243-5465-7687-98a9-bacbdcedfe0f");

    @Test
    void createTrimsNameAndPreservesUnicode() {
        var request = assertInstanceOf(LegacyForgeGroupCodec.Create.class,
            LegacyForgeGroupCodec.decodeRequest(create("  R\u00e1dio  ")));
        assertEquals("R\u00e1dio", request.name());
    }

    @Test
    void invitePreservesBothUuidFields() {
        var request = assertInstanceOf(LegacyForgeGroupCodec.Invite.class,
            LegacyForgeGroupCodec.decodeRequest(invite()));
        assertEquals(GROUP, request.groupId());
        assertEquals(PLAYER, request.targetId());
    }

    @Test
    void responseAndLeaveHaveExactWireContracts() {
        for (boolean accepted : new boolean[] {false, true}) {
            var request = assertInstanceOf(LegacyForgeGroupCodec.Respond.class,
                LegacyForgeGroupCodec.decodeRequest(response(accepted ? 1 : 0)));
            assertEquals(GROUP, request.groupId());
            assertEquals(accepted, request.accepted());
        }
        var leave = assertInstanceOf(LegacyForgeGroupCodec.Leave.class,
            LegacyForgeGroupCodec.decodeRequest(leave()));
        assertEquals(GROUP, leave.groupId());
        assertNull(LegacyForgeGroupCodec.decodeRequest(response(2)));
        assertNull(LegacyForgeGroupCodec.decodeRequest(response(255)));
    }

    @Test
    void nullAndEmptyRequestsAreRejected() {
        assertNull(LegacyForgeGroupCodec.decodeRequest(null));
        assertNull(LegacyForgeGroupCodec.decodeRequest(new byte[0]));
    }

    @Test
    void everyTruncatedPrefixAndTrailingByteAreRejected() {
        for (byte[] valid : List.of(create("Squad"), invite(), response(1), leave())) {
            for (int length = 0; length < valid.length; length++) {
                assertNull(LegacyForgeGroupCodec.decodeRequest(Arrays.copyOf(valid, length)),
                    "Accepted truncated packet: length=" + length);
            }
            assertNull(LegacyForgeGroupCodec.decodeRequest(Arrays.copyOf(valid, valid.length + 1)));
        }
    }

    @Test
    void invalidUtf8AndStringBoundsAreRejected() {
        assertNull(LegacyForgeGroupCodec.decodeRequest(new byte[] {2, 0, 2, (byte) 0xC3, 0x28}));
        assertNull(LegacyForgeGroupCodec.decodeRequest(new byte[] {2, (byte) 0xFF, (byte) 0xFF}));
        assertNull(LegacyForgeGroupCodec.decodeRequest(create("   ")));
        assertNull(LegacyForgeGroupCodec.decodeRequest(create("a".repeat(65))));
        assertNull(LegacyForgeGroupCodec.decodeRequest(create("a".repeat(257))));
        assertNotNull(LegacyForgeGroupCodec.decodeRequest(create("a".repeat(64))));
    }

    @Test
    void requestAndServerDiscriminatorsRemainDisjoint() {
        for (int value = 0; value < 256; value++) {
            byte tag = (byte) value;
            assertEquals(value >= 2 && value <= 5, LegacyForgeGroupCodec.isGroupRequestDiscriminator(tag));
            assertEquals(value >= 6 && value <= 8, LegacyForgeGroupCodec.isServerGroupControlDiscriminator(tag));
        }
    }

    @Test
    void inviteNotificationMatchesLegacyFieldOrder() throws Exception {
        try (var in = input(LegacyForgeGroupCodec.encodeInviteNotify(group(), PLAYER, "Akashic"))) {
            assertEquals(6, in.readUnsignedByte());
            assertEquals(GROUP, uuid(in));
            assertEquals("Squad", string(in));
            assertEquals(PLAYER, uuid(in));
            assertEquals("Akashic", string(in));
            assertEquals(-1, in.read());
        }
    }

    @Test
    void groupStateMatchesLegacyLayoutAndOfflineNameFallback() throws Exception {
        try (var in = input(LegacyForgeGroupCodec.encodeGroupState(List.of(group()), id -> null))) {
            assertEquals(7, in.readUnsignedByte());
            assertEquals(1, in.readUnsignedShort());
            assertEquals(GROUP, uuid(in));
            assertEquals("Squad", string(in));
            assertEquals(PLAYER, uuid(in));
            assertEquals(1, in.readUnsignedShort());
            assertEquals(PLAYER, uuid(in));
            assertEquals("10213243", string(in));
            assertEquals(-1, in.read());
        }
    }

    @Test
    void rosterUsesUtf8ByteLengthsAndEmptyCollectionsClearState() throws Exception {
        var player = new LegacyForgeGroupCodec.RosterPlayer(PLAYER, "Jos\u00e9");
        try (var in = input(LegacyForgeGroupCodec.encodeRoster(List.of(player)))) {
            assertEquals(8, in.readUnsignedByte());
            assertEquals(1, in.readUnsignedShort());
            assertEquals(PLAYER, uuid(in));
            assertEquals("Jos\u00e9", string(in));
            assertEquals(-1, in.read());
        }
        assertArrayEquals(new byte[] {8, 0, 0}, LegacyForgeGroupCodec.encodeRoster(List.of()));
        assertArrayEquals(new byte[] {7, 0, 0},
            LegacyForgeGroupCodec.encodeGroupState(List.of(), id -> null));
    }

    @Test
    void oversizedCollectionsAndNamesAreRejected() {
        var player = new LegacyForgeGroupCodec.RosterPlayer(PLAYER, "Akashic");
        assertThrows(IllegalArgumentException.class,
            () -> LegacyForgeGroupCodec.encodeRoster(Collections.nCopies(4097, player)));
        assertThrows(IllegalArgumentException.class,
            () -> LegacyForgeGroupCodec.encodeGroupState(Collections.nCopies(4097, group()), id -> "Name"));
        assertThrows(IllegalArgumentException.class,
            () -> LegacyForgeGroupCodec.encodeInviteNotify(group(), PLAYER, "a".repeat(257)));
    }

    @Test
    void deterministicMalformedInputNeverEscapesParser() {
        Random random = new Random(0x41564C47L);
        for (int i = 0; i < 10_000; i++) {
            byte[] data = new byte[random.nextInt(513)];
            random.nextBytes(data);
            if (data.length > 0) data[0] = (byte) (2 + i % 4);
            assertDoesNotThrow(() -> LegacyForgeGroupCodec.decodeRequest(data));
        }
    }

    private static FederatedGroup group() {
        return new FederatedGroup(GROUP, "Squad", PLAYER, Set.of(PLAYER));
    }

    private static byte[] create(String name) {
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(3 + bytes.length).put((byte) 2)
            .putShort((short) bytes.length).put(bytes).array();
    }

    private static byte[] invite() {
        return ByteBuffer.allocate(33).put((byte) 3)
            .putLong(GROUP.getMostSignificantBits()).putLong(GROUP.getLeastSignificantBits())
            .putLong(PLAYER.getMostSignificantBits()).putLong(PLAYER.getLeastSignificantBits()).array();
    }

    private static byte[] response(int accepted) {
        return ByteBuffer.allocate(18).put((byte) 4)
            .putLong(GROUP.getMostSignificantBits()).putLong(GROUP.getLeastSignificantBits())
            .put((byte) accepted).array();
    }

    private static byte[] leave() {
        return ByteBuffer.allocate(17).put((byte) 5)
            .putLong(GROUP.getMostSignificantBits()).putLong(GROUP.getLeastSignificantBits()).array();
    }

    private static DataInputStream input(byte[] data) {
        return new DataInputStream(new ByteArrayInputStream(data));
    }

    private static UUID uuid(DataInputStream in) throws Exception {
        return new UUID(in.readLong(), in.readLong());
    }

    private static String string(DataInputStream in) throws Exception {
        int length = in.readUnsignedShort();
        byte[] bytes = in.readNBytes(length);
        assertEquals(length, bytes.length);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
