package dev.akashic.voice.platform.agent;

import dev.akashic.voice.platform.bridge.AkashicSpeakerFrame;
import dev.akashic.voice.platform.bridge.BridgeDelivery;
import dev.akashic.voice.platform.bridge.BridgeStreamReset;
import dev.akashic.voice.platform.bridge.ManagedPlayer;
import dev.akashic.voice.platform.bridge.PositionSnapshot;
import dev.akashic.voice.platform.bridge.SpatialKind;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgentMessageCodecTest {
    @Test
    void roundTripsAllMessageFamilies() {
        UUID player = UUID.randomUUID();
        UUID sender = UUID.randomUUID();
        UUID channel = UUID.randomUUID();
        byte[] opus = new byte[] {1, 3, 5, 7, 9};
        PositionSnapshot pos = new PositionSnapshot("minecraft:overworld", 1.25, 64.0, -3.5);

        List<AgentMessage> messages = List.of(
            new AgentMessage.Ready(),
            new AgentMessage.Attach(new ManagedPlayer(player, "lobby", "TestPlayer")),
            new AgentMessage.Detach(player),
            new AgentMessage.SpeakerFrame(new AkashicSpeakerFrame(player, 42L, true, opus)),
            new AgentMessage.SpeakerReset(player),
            new AgentMessage.SvcDelivery(new BridgeDelivery(
                player, sender, channel, 99L, SpatialKind.ENTITY, sender, pos,
                32F, true, "voice", opus)),
            new AgentMessage.SvcDelivery(new BridgeDelivery(
                player, sender, channel, 100L, SpatialKind.LOCATION, null, pos,
                48F, false, null, opus)),
            new AgentMessage.SvcDelivery(new BridgeDelivery(
                player, sender, channel, 101L, SpatialKind.STATIC, null, null,
                0F, false, "group", opus)),
            new AgentMessage.SvcReset(new BridgeStreamReset(player, sender, channel)),
            new AgentMessage.Ping(123L),
            new AgentMessage.Pong(456L)
        );

        for (AgentMessage message : messages) {
            ByteBuffer encoded = ByteBuffer.allocate(AgentProtocol.MAX_PLAINTEXT_BYTES);
            AgentMessageCodec.write(encoded, message);
            encoded.flip();
            AgentMessage decoded = AgentMessageCodec.read(encoded);
            assertNotNull(decoded, () -> "Failed to decode " + message.getClass().getSimpleName());
            assertEquals(message.getClass(), decoded.getClass());
        }
    }
    @Test
    void preservesSvcMetadataAndRejectsTrailingBytes() {
        UUID recipient = UUID.randomUUID();
        UUID sender = UUID.randomUUID();
        UUID channel = UUID.randomUUID();
        PositionSnapshot pos = new PositionSnapshot("minecraft:the_nether", 10.0, 70.5, -20.25);
        byte[] opus = new byte[] {10, 20, 30, 40};
        BridgeDelivery original = new BridgeDelivery(
            recipient, sender, channel, 0xFFFF_FFFFL, SpatialKind.ENTITY, sender, pos,
            17.5F, true, "proximity", opus);

        ByteBuffer encoded = ByteBuffer.allocate(AgentProtocol.MAX_PLAINTEXT_BYTES);
        AgentMessageCodec.write(encoded, new AgentMessage.SvcDelivery(original));
        int length = encoded.position();
        encoded.flip();
        AgentMessage.SvcDelivery decoded = assertInstanceOf(
            AgentMessage.SvcDelivery.class, AgentMessageCodec.read(encoded));
        BridgeDelivery d = decoded.delivery();
        assertEquals(original.recipientId(), d.recipientId());
        assertEquals(original.senderId(), d.senderId());
        assertEquals(original.channelId(), d.channelId());
        assertEquals(original.sequence(), d.sequence());
        assertEquals(original.spatialKind(), d.spatialKind());
        assertEquals(original.position(), d.position());
        assertEquals(original.distance(), d.distance());
        assertEquals(original.whispering(), d.whispering());
        assertEquals(original.category(), d.category());
        assertArrayEquals(opus, d.opusUnsafe());
        ByteBuffer malformed = ByteBuffer.allocate(length + 1);
        ByteBuffer valid = ByteBuffer.allocate(AgentProtocol.MAX_PLAINTEXT_BYTES);
        AgentMessageCodec.write(valid, new AgentMessage.SvcDelivery(original));
        malformed.put(valid.array(), 0, valid.position()).put((byte) 0x55).flip();
        assertNull(AgentMessageCodec.read(malformed));
    }
}
