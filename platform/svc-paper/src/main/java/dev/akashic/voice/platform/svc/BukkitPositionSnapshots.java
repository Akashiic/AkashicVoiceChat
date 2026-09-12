package dev.akashic.voice.platform.svc;

import dev.akashic.voice.platform.bridge.PositionSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class BukkitPositionSnapshots {
    private volatile Map<UUID, PositionSnapshot> snapshots = Map.of();

    void refresh() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Position snapshots must refresh on the Bukkit main thread");
        }
        Map<UUID, PositionSnapshot> next = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location location = player.getLocation();
            next.put(player.getUniqueId(), new PositionSnapshot(
                player.getWorld().getKey().toString(),
                location.getX(), location.getY(), location.getZ()));
        }
        snapshots = Map.copyOf(next);
    }

    PositionSnapshot get(UUID playerId) {
        return snapshots.get(playerId);
    }
}
