package dev.akashic.voice.voice.common;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SoundFrameV2PacketTest {
    @Test
    void roundTripsDistanceWhisperAndPayload() {
        UUID sender = UUID.randomUUID();
        byte[] opus = new byte[] {1, 2, 3, 5, 8, 13};
        SoundFrameV2Packet original = new SoundFrameV2Packet(
            sender, true, true, 1.25F, 64F, -3.5F, 23.5F, -1, opus);
        ByteBuffer encoded = ByteBuffer.allocate(original.byteSize());
        original.write(encoded);
        assertEquals(original.byteSize(), encoded.position());
        encoded.flip();
        assertEquals(WirePacketType.SOUND_FRAME_V2, encoded.get());
        SoundFrameV2Packet decoded = SoundFrameV2Packet.read(encoded);
        assertNotNull(decoded);
        assertEquals(sender, decoded.senderId);
        assertTrue(decoded.isSpatial());
        assertTrue(decoded.isWhispering());
        assertEquals(23.5F, decoded.maxDistance);
        assertEquals(-1, decoded.sequence);        assertArrayEquals(opus, Arrays.copyOfRange(
            decoded.opusData, decoded.opusOffset, decoded.opusOffset + decoded.opusLength));
    }

    @Test
    void rejectsUnknownFlagsAndInvalidDistance() {
        UUID sender = UUID.randomUUID();
        byte[] opus = new byte[] {9, 8, 7};
        SoundFrameV2Packet packet = new SoundFrameV2Packet(
            sender, true, false, 0F, 0F, 0F, 48F, 4, opus);
        ByteBuffer encoded = ByteBuffer.allocate(packet.byteSize());
        packet.write(encoded);
        byte[] bytes = Arrays.copyOf(encoded.array(), encoded.position());

        bytes[1 + 16] = (byte) 0x40;
        ByteBuffer unknownFlags = ByteBuffer.wrap(bytes);
        assertEquals(WirePacketType.SOUND_FRAME_V2, unknownFlags.get());
        assertNull(SoundFrameV2Packet.read(unknownFlags));

        assertThrows(IllegalArgumentException.class, () -> new SoundFrameV2Packet(
            sender, true, false, 0F, 0F, 0F, Float.NaN, 1, opus));
    }
}
