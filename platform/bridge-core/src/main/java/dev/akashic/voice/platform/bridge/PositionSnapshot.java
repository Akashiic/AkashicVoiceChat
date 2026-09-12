package dev.akashic.voice.platform.bridge;

public record PositionSnapshot(
    String worldKey,
    double x,
    double y,
    double z
) {
    public PositionSnapshot {
        if (worldKey == null || worldKey.isBlank()) {
            throw new IllegalArgumentException("worldKey");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("non-finite position");
        }
    }
}
