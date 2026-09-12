package dev.akashic.voice.platform.bridge;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class BridgeDelivery {
    private final UUID recipientId;
    private final UUID senderId;
    private final UUID channelId;
    private final long sequence;
    private final SpatialKind spatialKind;
    private final UUID entityId;
    private final PositionSnapshot position;
    private final float distance;
    private final boolean whispering;
    private final String category;
    private final byte[] opus;

    public BridgeDelivery(UUID recipientId, UUID senderId, UUID channelId, long sequence,
                          SpatialKind spatialKind, UUID entityId, PositionSnapshot position,
                          float distance, boolean whispering, String category, byte[] opus) {
        this.recipientId = Objects.requireNonNull(recipientId, "recipientId");
        this.senderId = Objects.requireNonNull(senderId, "senderId");
        this.channelId = Objects.requireNonNull(channelId, "channelId");
        this.spatialKind = Objects.requireNonNull(spatialKind, "spatialKind");
        if (sequence < 0L) throw new IllegalArgumentException("sequence");
        if (!Float.isFinite(distance) || distance < 0F) throw new IllegalArgumentException("distance");
        if (spatialKind == SpatialKind.ENTITY && entityId == null) throw new IllegalArgumentException("entityId");
        if (spatialKind == SpatialKind.LOCATION && position == null) throw new IllegalArgumentException("position");
        if (opus == null || opus.length == 0 || opus.length > BridgeConstants.MAX_OPUS_PAYLOAD) {
            throw new IllegalArgumentException("invalid Opus payload length");
        }
        this.sequence = sequence;
        this.entityId = entityId;
        this.position = position;
        this.distance = distance;
        this.whispering = whispering;
        this.category = category;
        this.opus = Arrays.copyOf(opus, opus.length);
    }

    public UUID recipientId() { return recipientId; }
    public UUID senderId() { return senderId; }
    public UUID channelId() { return channelId; }
    public long sequence() { return sequence; }
    public SpatialKind spatialKind() { return spatialKind; }
    public UUID entityId() { return entityId; }
    public PositionSnapshot position() { return position; }
    public float distance() { return distance; }
    public boolean whispering() { return whispering; }
    public String category() { return category; }
    public byte[] opusUnsafe() { return opus; }
}
