package dev.akashic.voice.platform.velocity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Bounded ephemeral radio tuning state. Mutations are synchronized; media reads immutable snapshots. */
final class GatewayRadioDirectory {
    static final int MAX_CHANNELS = 128;
    static final int MAX_MEMBERS_PER_CHANNEL = 64;
    static final int MAX_TUNED_PLAYERS = 4096;
    static final int MAX_CHANNEL_NAME = 24;

    enum TuneResult { JOINED, SWITCHED, UNCHANGED, INVALID, CHANNEL_FULL, CHANNEL_LIMIT, PLAYER_LIMIT }

    record TuneOutcome(TuneResult result, String previousChannel, String channel) {
        boolean changed() { return result == TuneResult.JOINED || result == TuneResult.SWITCHED; }
    }

    private final Map<UUID, String> byPlayer = new HashMap<>();
    private final Map<String, Set<UUID>> byChannel = new HashMap<>();
    private volatile Map<UUID, String> playerSnapshot = Map.of();
    private volatile Map<String, Set<UUID>> channelSnapshot = Map.of();

    synchronized TuneOutcome tune(UUID playerId, String requestedChannel) {
        Objects.requireNonNull(playerId, "playerId");
        String channel = normalize(requestedChannel);
        String previous = byPlayer.get(playerId);
        if (channel == null) return new TuneOutcome(TuneResult.INVALID, previous, null);
        if (channel.equals(previous)) return new TuneOutcome(TuneResult.UNCHANGED, previous, channel);
        if (previous == null && byPlayer.size() >= MAX_TUNED_PLAYERS) {
            return new TuneOutcome(TuneResult.PLAYER_LIMIT, null, channel);
        }

        Set<UUID> target = byChannel.get(channel);
        if (target == null && byChannel.size() >= MAX_CHANNELS) {
            return new TuneOutcome(TuneResult.CHANNEL_LIMIT, previous, channel);
        }
        if (target != null && target.size() >= MAX_MEMBERS_PER_CHANNEL) {
            return new TuneOutcome(TuneResult.CHANNEL_FULL, previous, channel);
        }

        if (previous != null) removeFromChannel(playerId, previous);
        target = byChannel.computeIfAbsent(channel, ignored -> new HashSet<>());
        target.add(playerId);
        byPlayer.put(playerId, channel);
        publish();
        return new TuneOutcome(previous == null ? TuneResult.JOINED : TuneResult.SWITCHED, previous, channel);
    }

    synchronized Optional<String> leave(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        String previous = byPlayer.remove(playerId);
        if (previous == null) return Optional.empty();
        removeFromChannel(playerId, previous);
        publish();
        return Optional.of(previous);
    }

    Optional<String> channel(UUID playerId) {
        if (playerId == null) return Optional.empty();
        return Optional.ofNullable(playerSnapshot.get(playerId));
    }

    boolean sameChannel(UUID first, UUID second) {
        if (first == null || second == null || first.equals(second)) return false;
        Map<UUID, String> snapshot = playerSnapshot;
        String channel = snapshot.get(first);
        return channel != null && channel.equals(snapshot.get(second));
    }

    Set<UUID> peers(UUID playerId) {
        if (playerId == null) return Set.of();
        Map<UUID, String> players = playerSnapshot;
        String channel = players.get(playerId);
        if (channel == null) return Set.of();
        Set<UUID> members = channelSnapshot.getOrDefault(channel, Set.of());
        if (!members.contains(playerId) || members.size() <= 1) return Set.of();
        HashSet<UUID> result = new HashSet<>(members);
        result.remove(playerId);
        return Set.copyOf(result);
    }

    Set<UUID> membersForPlayer(UUID playerId) {
        if (playerId == null) return Set.of();
        Map<UUID, String> players = playerSnapshot;
        String channel = players.get(playerId);
        if (channel == null) return Set.of();
        Set<UUID> members = channelSnapshot.getOrDefault(channel, Set.of());
        return members.contains(playerId) ? members : Set.of();
    }

    Set<UUID> tunedPlayers() { return playerSnapshot.keySet(); }

    Set<UUID> members(String requestedChannel) {
        String channel = normalize(requestedChannel);
        if (channel == null) return Set.of();
        return channelSnapshot.getOrDefault(channel, Set.of());
    }

    int channelCount() { return channelSnapshot.size(); }
    int memberCount() { return playerSnapshot.size(); }

    synchronized void clear() {
        byPlayer.clear();
        byChannel.clear();
        publish();
    }

    private void removeFromChannel(UUID playerId, String channel) {
        Set<UUID> members = byChannel.get(channel);
        if (members == null) return;
        members.remove(playerId);
        if (members.isEmpty()) byChannel.remove(channel);
    }

    private void publish() {
        playerSnapshot = Map.copyOf(byPlayer);
        TreeMap<String, Set<UUID>> channels = new TreeMap<>();
        for (Map.Entry<String, Set<UUID>> entry : byChannel.entrySet()) {
            channels.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        channelSnapshot = Map.copyOf(channels);
    }

    static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > MAX_CHANNEL_NAME) return null;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '_' && c != '-') return null;
        }
        return normalized;
    }
}
