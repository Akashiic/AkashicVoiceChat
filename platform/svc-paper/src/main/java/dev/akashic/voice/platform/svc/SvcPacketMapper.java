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

import java.util.UUID;

final class SvcPacketMapper {
    private SvcPacketMapper() {
    }

    static BridgeStreamReset mapReset(UUID recipientId, SoundPacket packet) {
        return mapReset(recipientId, packet.getSender(), packet);
    }

    static BridgeStreamReset mapReset(UUID recipientId, UUID senderId, SoundPacket packet) {
        if (packet.getOpusEncodedData().length != 0) return null;
        return new BridgeStreamReset(recipientId, senderId, packet.getChannelId());
    }

    static BridgeDelivery mapFrame(UUID recipientId, SoundPacket packet,
                                   PositionSnapshot recipientPosition,
                                   PositionSnapshot entityPosition) {
        return mapFrame(recipientId, packet.getSender(), packet, recipientPosition, entityPosition);
    }

    static BridgeDelivery mapFrame(UUID recipientId, UUID senderId, SoundPacket packet,
                                   PositionSnapshot recipientPosition,
                                   PositionSnapshot entityPosition) {
        byte[] opus = packet.getOpusEncodedData();
        if (opus.length == 0) return null;

        SpatialKind kind;
        UUID entityId = null;
        PositionSnapshot position = null;
        float distance = 0F;
        boolean whispering = false;

        if (packet instanceof EntitySoundPacket entity) {
            kind = SpatialKind.ENTITY;
            entityId = entity.getEntityUuid();
            position = entityPosition;
            distance = entity.getDistance();
            whispering = entity.isWhispering();
        } else if (packet instanceof LocationalSoundPacket locational) {
            if (recipientPosition == null) return null;
            kind = SpatialKind.LOCATION;
            Position p = locational.getPosition();
            position = new PositionSnapshot(
                recipientPosition.worldKey(), p.getX(), p.getY(), p.getZ());
            distance = locational.getDistance();
        } else if (packet instanceof StaticSoundPacket) {
            kind = SpatialKind.STATIC;
        } else {
            return null;
        }

        return new BridgeDelivery(
            recipientId,
            senderId,
            packet.getChannelId(),
            packet.getSequenceNumber(),
            kind,
            entityId,
            position,
            distance,
            whispering,
            packet.getCategory(),
            opus);
    }
}
