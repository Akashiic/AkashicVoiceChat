package dev.akashic.voice.voice.common;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SoundResetPacketTest {
    @Test
    void roundTripsAndRejectsTrailingBytes() {
        UUID sender = UUID.randomUUID();
        ByteBuffer encoded = ByteBuffer.allocate(32);
        new SoundResetPacket(sender).write(encoded);
        encoded.flip();

        assertEquals(WirePacketType.SOUND_RESET, encoded.get());
        SoundResetPacket decoded = SoundResetPacket.read(encoded);
        assertNotNull(decoded);
        assertEquals(sender, decoded.senderId);

        ByteBuffer malformed = ByteBuffer.allocate(17);
        malformed.putLong(sender.getMostSignificantBits());
        malformed.putLong(sender.getLeastSignificantBits());
        malformed.put((byte) 1).flip();
        assertNull(SoundResetPacket.read(malformed));
    }
}
