package dev.akashic.voice.platform.svc;

import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.packets.EntitySoundPacket;
import de.maxhenkel.voicechat.api.packets.LocationalSoundPacket;
import de.maxhenkel.voicechat.api.packets.SoundPacket;
import de.maxhenkel.voicechat.api.packets.StaticSoundPacket;
import dev.akashic.voice.platform.bridge.BridgeDelivery;
import dev.akashic.voice.platform.bridge.BridgeStreamReset;
import dev.akashic.voice.platform.bridge.PositionSnapshot;
import dev.akashic.voice.platform.bridge.SpatialKind;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SvcPacketMapperTest {
    @Test
    void mapsEntityPacketWithoutTranscoding() {
        UUID recipient = UUID.randomUUID();
        UUID sender = UUID.randomUUID();
        UUID channel = UUID.randomUUID();
        UUID entity = UUID.randomUUID();
        byte[] opus = {7, 8, 9};
        Map<String, Object> values = base(sender, channel, 42L, opus);
        values.put("getEntityUuid", entity);
        values.put("isWhispering", true);
        values.put("getDistance", 48F);
        EntitySoundPacket packet = proxy(EntitySoundPacket.class, values);
        PositionSnapshot entityPos = new PositionSnapshot("minecraft:overworld", 1, 2, 3);

        BridgeDelivery delivery = SvcPacketMapper.mapFrame(
            recipient, packet, new PositionSnapshot("minecraft:overworld", 0, 0, 0), entityPos);

        assertNotNull(delivery);
        assertEquals(SpatialKind.ENTITY, delivery.spatialKind());
        assertEquals(sender, delivery.senderId());
        assertEquals(entity, delivery.entityId());
        assertSame(entityPos, delivery.position());
        assertEquals(42L, delivery.sequence());
        assertTrue(delivery.whispering());
        assertArrayEquals(opus, delivery.opusUnsafe());
    }

    @Test
    void mapsLocationAndStaticPackets() {
        UUID recipient = UUID.randomUUID();
        UUID sender = UUID.randomUUID();
        UUID channel = UUID.randomUUID();
        Map<String, Object> locValues = base(sender, channel, 9L, new byte[]{1, 2});
        locValues.put("getPosition", position(10D, 20D, 30D));
        locValues.put("getDistance", 32F);
        LocationalSoundPacket loc = proxy(LocationalSoundPacket.class, locValues);
        PositionSnapshot recipientPos = new PositionSnapshot("lobby:world", 0, 0, 0);
        BridgeDelivery locDelivery = SvcPacketMapper.mapFrame(recipient, loc, recipientPos, null);
        assertNotNull(locDelivery);
        assertEquals(SpatialKind.LOCATION, locDelivery.spatialKind());
        assertEquals("lobby:world", locDelivery.position().worldKey());
        assertEquals(10D, locDelivery.position().x());

        StaticSoundPacket stat = proxy(StaticSoundPacket.class,
            base(sender, channel, 10L, new byte[]{3, 4}));
        BridgeDelivery staticDelivery = SvcPacketMapper.mapFrame(recipient, stat, null, null);
        assertNotNull(staticDelivery);
        assertEquals(SpatialKind.STATIC, staticDelivery.spatialKind());
        assertNull(staticDelivery.position());
    }

    @Test
    void mapsResetAndRejectsUnknownSoundSubtype() {
        UUID recipient = UUID.randomUUID();
        UUID sender = UUID.randomUUID();
        UUID channel = UUID.randomUUID();
        StaticSoundPacket resetPacket = proxy(StaticSoundPacket.class,
            base(sender, channel, 11L, new byte[0]));
        BridgeStreamReset reset = SvcPacketMapper.mapReset(recipient, resetPacket);
        assertNotNull(reset);
        assertEquals(sender, reset.senderId());
        assertNull(SvcPacketMapper.mapFrame(recipient, resetPacket, null, null));

        SoundPacket unknown = proxy(SoundPacket.class,
            base(sender, channel, 12L, new byte[]{1}));
        assertNull(SvcPacketMapper.mapFrame(recipient, unknown, null, null));
    }

    private static Map<String, Object> base(UUID sender, UUID channel, long sequence, byte[] opus) {
        Map<String, Object> values = new HashMap<>();
        values.put("getSender", sender);
        values.put("getChannelId", channel);
        values.put("getSequenceNumber", sequence);
        values.put("getOpusEncodedData", opus);
        values.put("getCategory", "akashic-test");
        return values;
    }

    private static Position position(double x, double y, double z) {
        return new Position() {
            @Override public double getX() { return x; }
            @Override public double getY() { return y; }
            @Override public double getZ() { return z; }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Map<String, Object> values) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            String name = method.getName();
            if (values.containsKey(name)) return values.get(name);
            if (name.equals("toString")) return "Fake" + type.getSimpleName();
            if (name.equals("hashCode")) return System.identityHashCode(instance);
            if (name.equals("equals")) return instance == args[0];
            Class<?> returnType = method.getReturnType();
            if (!returnType.isPrimitive()) return null;
            if (returnType == boolean.class) return false;
            if (returnType == byte.class) return (byte) 0;
            if (returnType == short.class) return (short) 0;
            if (returnType == int.class) return 0;
            if (returnType == long.class) return 0L;
            if (returnType == float.class) return 0F;
            if (returnType == double.class) return 0D;
            if (returnType == char.class) return '\0';
            throw new AssertionError("Unhandled method: " + method);
        });
    }
}
