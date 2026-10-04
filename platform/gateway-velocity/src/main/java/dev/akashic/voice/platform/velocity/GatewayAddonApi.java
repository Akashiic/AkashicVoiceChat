package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.api.AkashicVoiceApi;
import dev.akashic.voice.api.VoiceEventListener;
import dev.akashic.voice.api.VoiceListenerRegistration;
import dev.akashic.voice.api.VoiceModerationEvent;
import dev.akashic.voice.api.VoicePlayer;
import dev.akashic.voice.api.VoiceSessionAction;
import dev.akashic.voice.api.VoiceSessionEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

final class GatewayAddonApi implements AkashicVoiceApi {
    private record Session(UUID playerId, String playerName, String backendId) {}

    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<VoiceEventListener> listeners = new CopyOnWriteArrayList<>();
    private final Consumer<Throwable> errorSink;

    private volatile Predicate<UUID> mutedLookup = ignored -> false;
    private volatile LongSupplier revisionLookup = () -> 0L;
    private volatile boolean available;

    GatewayAddonApi(Consumer<Throwable> errorSink) {
        this.errorSink = Objects.requireNonNull(errorSink, "errorSink");
    }

    void activate(Predicate<UUID> mutedLookup, LongSupplier revisionLookup) {
        this.mutedLookup = Objects.requireNonNull(mutedLookup, "mutedLookup");
        this.revisionLookup = Objects.requireNonNull(revisionLookup, "revisionLookup");
        available = true;
    }

    void deactivate() {
        available = false;
        sessions.clear();
        listeners.clear();
        mutedLookup = ignored -> false;
        revisionLookup = () -> 0L;
    }

    void connected(UUID playerId, String playerName, String backendId) {
        if (!available) return;
        Session session = new Session(
            Objects.requireNonNull(playerId, "playerId"),
            Objects.requireNonNull(playerName, "playerName"),
            Objects.requireNonNull(backendId, "backendId"));
        sessions.put(playerId, session);
        publishSession(session, VoiceSessionAction.CONNECTED);
    }

    void disconnected(UUID playerId) {
        Session session = sessions.remove(playerId);
        if (!available || session == null) return;
        publishSession(session, VoiceSessionAction.DISCONNECTED);
    }

    void moderationChanged(UUID playerId, boolean muted, long revision) {
        if (!available) return;
        VoiceModerationEvent event = new VoiceModerationEvent(playerId, muted, revision);
        for (VoiceEventListener listener : listeners) {
            invoke(() -> listener.onModeration(event));
        }
    }

    @Override
    public int apiVersion() {
        return API_VERSION;
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public List<VoicePlayer> players() {
        if (!available) return List.of();
        ArrayList<VoicePlayer> result = new ArrayList<>(sessions.size());
        for (Session session : sessions.values()) result.add(view(session));
        result.sort(Comparator.comparing(VoicePlayer::playerName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(player -> player.playerId().toString()));
        return available ? List.copyOf(result) : List.of();
    }

    @Override
    public Optional<VoicePlayer> player(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!available) return Optional.empty();
        Session session = sessions.get(playerId);
        return session != null && available ? Optional.of(view(session)) : Optional.empty();
    }

    @Override
    public boolean isGloballyMuted(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return available && mutedLookup.test(playerId);
    }

    @Override
    public long moderationRevision() {
        return available ? Math.max(0L, revisionLookup.getAsLong()) : 0L;
    }

    @Override
    public VoiceListenerRegistration listen(VoiceEventListener listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            if (closed.compareAndSet(false, true)) listeners.remove(listener);
        };
    }

    private VoicePlayer view(Session session) {
        return new VoicePlayer(
            session.playerId(), session.playerName(), session.backendId(), mutedLookup.test(session.playerId()));
    }

    private void publishSession(Session session, VoiceSessionAction action) {
        VoiceSessionEvent event = new VoiceSessionEvent(view(session), action);
        for (VoiceEventListener listener : listeners) {
            invoke(() -> listener.onSession(event));
        }
    }

    private void invoke(Runnable callback) {
        try {
            callback.run();
        } catch (Throwable failure) {
            rethrowFatal(failure);
            try {
                errorSink.accept(failure);
            } catch (Throwable sinkFailure) {
                rethrowFatal(sinkFailure);
            }
        }
    }

    @SuppressWarnings("removal")
    private static void rethrowFatal(Throwable failure) {
        if (failure instanceof ThreadDeath threadDeath) throw threadDeath;
        if (failure instanceof VirtualMachineError vmError) throw vmError;
    }
}
