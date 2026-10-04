package dev.akashic.voice.platform.velocity;

import dev.akashic.voice.platform.bridge.FederatedGroup;
import dev.akashic.voice.platform.bridge.FederatedGroupDirectory;
import dev.akashic.voice.platform.bridge.FederatedGroupPersistence;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Serial control authority. Disk I/O belongs to persistence; media reads one immutable set. */
final class GatewayGroupController {
    static final String USE = "akashicvoice.groups.use";
    static final String CREATE = "akashicvoice.groups.create";
    static final String INVITE = "akashicvoice.groups.invite";
    static final int MAX_BINDINGS = 512;
    static final int BURST = 16;
    static final long TOKEN_NANOS = 250_000_000L;

    enum Notice { NOT_READY, DENIED, INVALID, GROUP_UNAVAILABLE, INVITE_UNAVAILABLE,
        INVITE_SENT, INVITE_EXPIRED, JOINED, LEFT, STORAGE_FAILED }

    interface Transport {
        boolean isCurrent(UUID playerId, Object connection);
        boolean allowed(UUID playerId, Object connection, String permission);
        boolean send(UUID playerId, Object connection, byte[] payload);
        void notice(UUID playerId, Object connection, Notice notice);
    }

    record Status(FederatedGroupPersistence.Status persistence, int bindings, int groups,
                  int members, int pendingInvites, long accepted, long rejected, long rateLimited,
                  long deliveryFailures, long rosterTruncations, boolean closing) {}

    private static final class Binding {
        final UUID id;
        final String name;
        final Object connection;
        int tokens = BURST;
        long refill;
        Binding(UUID id, String name, Object connection, long now) {
            this.id = id; this.name = name; this.connection = connection; this.refill = now;
        }
        boolean consume(long now) {
            long elapsed = now - refill;
            if (elapsed >= TOKEN_NANOS) {
                long restored = elapsed / TOKEN_NANOS;
                tokens = (int) Math.min(BURST, tokens + Math.min(BURST, restored));
                refill = now - elapsed % TOKEN_NANOS;
            }
            if (tokens == 0) return false;
            tokens--;
            return true;
        }
    }

    private final FederatedGroupDirectory directory;
    private final FederatedGroupPersistence persistence;
    private final Transport transport;
    private final LongSupplier clock;
    private final Map<UUID, Binding> bindings = new HashMap<>();
    private volatile Set<UUID> eligibleAudio = Set.of();
    private boolean closing;
    private boolean failed;
    private boolean wasReady;
    private boolean rostersDirty;
    private long accepted;
    private long rejected;
    private long rateLimited;
    private long deliveryFailures;
    private long rosterTruncations;

    GatewayGroupController(FederatedGroupDirectory directory, FederatedGroupPersistence persistence,
                           Transport transport) {
        this(directory, persistence, transport, System::nanoTime);
    }

    GatewayGroupController(FederatedGroupDirectory directory, FederatedGroupPersistence persistence,
                           Transport transport, LongSupplier clock) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    synchronized void start() { persistence.start(); }

    /** Bind only AFTER bootstrap delivery to this exact live backend connection succeeded. */
    synchronized boolean bind(UUID id, String name, Object connection) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(connection, "connection");
        if (closing || failed || name == null || name.isBlank() || name.length() > 64
            || name.getBytes(StandardCharsets.UTF_8).length > 256 || !transport.isCurrent(id, connection)) {
            return false;
        }
        Binding old = bindings.get(id);
        if (old == null && bindings.size() >= MAX_BINDINGS) return false;
        // A repeated bootstrap on one connection must not reset its rate budget.
        if (old != null && old.connection == connection) {
            if (old.consume(clock.getAsLong())) {
                syncState(old);
                syncRoster(old, onlineBindings());
            }
            return true;
        }
        if (old != null) directory.onPlayerDisconnected(id);
        bindings.put(id, new Binding(id, name, connection, clock.getAsLong()));
        rostersDirty = true; // Batch other recipients on the maintenance tick.
        refreshEligibility();
        syncState(bindings.get(id));
        syncRoster(bindings.get(id), onlineBindings());
        return true;
    }

    synchronized boolean isBound(UUID id, Object connection) {
        Binding binding = bindings.get(id);
        return binding != null && binding.connection == connection;
    }

    /** Exact token protects a replacement connection from an old disconnect callback. */
    synchronized void unbind(UUID id, Object connection) {
        Binding binding = bindings.get(id);
        if (binding == null || binding.connection != connection) return;
        bindings.remove(id);
        directory.onPlayerDisconnected(id);
        refreshEligibility();
        rostersDirty = true;
    }

    synchronized void disconnected(UUID id) {
        Binding binding = bindings.remove(id);
        if (binding == null) return;
        directory.onPlayerDisconnected(id);
        refreshEligibility();
        rostersDirty = true;
    }

    /** Caller has consumed the plugin message; no path here forwards it to a backend. */
    synchronized void request(UUID id, Object connection, byte[] payload) {
        Binding binding = bindings.get(id);
        if (binding == null || binding.connection != connection || !current(binding)) {
            rejected++;
            return;
        }
        if (!binding.consume(clock.getAsLong())) {
            rateLimited++; // No chat response per rejected frame.
            return;
        }
        if (!ready()) { reject(binding, failed ? Notice.STORAGE_FAILED : Notice.NOT_READY); return; }
        LegacyForgeGroupCodec.Request request = LegacyForgeGroupCodec.decodeRequest(payload);
        if (request == null) { reject(binding, Notice.INVALID); return; }
        // Leaving/declining remains possible after a permission revocation.
        boolean exit = request instanceof LegacyForgeGroupCodec.Leave
            || request instanceof LegacyForgeGroupCodec.Respond response && !response.accepted();
        if (!exit && !allowed(binding, USE)) { reject(binding, Notice.DENIED); refreshEligibility(); return; }
        if (request instanceof LegacyForgeGroupCodec.Create create) {
            if (!allowed(binding, CREATE)) { reject(binding, Notice.DENIED); return; }
            FederatedGroup group = directory.create(id, create.name()).orElse(null);
            if (group == null) { reject(binding, Notice.GROUP_UNAVAILABLE); return; }
            if (!save(binding)) return;
            accepted++;
            syncMembers(group.members());
        } else if (request instanceof LegacyForgeGroupCodec.Invite invite) {
            if (!allowed(binding, INVITE)) { reject(binding, Notice.DENIED); return; }
            Binding target = bindings.get(invite.targetId());
            if (target == null || !current(target) || !allowed(target, USE) || id.equals(target.id)
                || !directory.invite(id, invite.groupId(), target.id)) {
                reject(binding, Notice.INVITE_UNAVAILABLE); return;
            }
            FederatedGroup group = directory.group(invite.groupId()).orElseThrow();
            boolean sent = send(target, LegacyForgeGroupCodec.encodeInviteNotify(group, id, binding.name));
            if (!sent) {
                directory.respondToInvite(target.id, group.id(), false);
                reject(binding, Notice.INVITE_UNAVAILABLE); return;
            }
            accepted++;
            notice(binding, Notice.INVITE_SENT);
        } else if (request instanceof LegacyForgeGroupCodec.Respond response) {
            if (response.accepted()) {
                UUID inviterId = directory.inviterFor(response.groupId(), id).orElse(null);
                Binding inviter = bindings.get(inviterId);
                if (inviter == null || !allowed(inviter, USE) || !allowed(inviter, INVITE)) {
                    directory.respondToInvite(id, response.groupId(), false);
                    reject(binding, Notice.INVITE_EXPIRED); return;
                }
            }
            FederatedGroupDirectory.InviteResponse result = directory.respondToInvite(id,
                response.groupId(), response.accepted());
            if (result == FederatedGroupDirectory.InviteResponse.JOINED) {
                if (!save(binding)) return;
                accepted++;
                syncMembers(directory.groupFor(id).orElseThrow().members());
                notice(binding, Notice.JOINED);
            } else if (result == FederatedGroupDirectory.InviteResponse.DECLINED) {
                accepted++;
            } else {
                reject(binding, Notice.INVITE_EXPIRED);
            }
        } else if (request instanceof LegacyForgeGroupCodec.Leave leave) {
            FederatedGroup before = directory.groupFor(id).orElse(null);
            if (before == null || !directory.leave(id, leave.groupId())) {
                reject(binding, Notice.GROUP_UNAVAILABLE); return;
            }
            if (!save(binding)) return;
            accepted++;
            syncMembers(before.members());
            notice(binding, Notice.LEFT);
        }
        refreshEligibility();
    }

    private boolean save(Binding actor) {
        try {
            persistence.requestSave();
            return true;
        } catch (IllegalStateException unavailable) {
            // Never claim durability; freeze controls if the worker changed state during mutation.
            failed = true;
            eligibleAudio = Set.of();
            reject(actor, Notice.STORAGE_FAILED);
            return false;
        }
    }

    /** Called by one repeating task. No disk access, no per-frame work. */
    synchronized void tick() {
        if (closing) return;
        boolean changed = false;
        var iterator = bindings.values().iterator();
        while (iterator.hasNext()) {
            Binding binding = iterator.next();
            if (!current(binding)) {
                iterator.remove();
                directory.onPlayerDisconnected(binding.id);
                changed = true;
            }
        }
        directory.expireInvites();
        Set<UUID> prior = eligibleAudio;
        refreshEligibility();
        boolean nowReady = ready();
        if (nowReady != wasReady || !prior.equals(eligibleAudio)) {
            for (Binding binding : bindings.values()) syncState(binding);
            changed = true;
        }
        wasReady = nowReady;
        if (changed || rostersDirty) { syncRosters(); rostersDirty = false; }
    }

    boolean allowsGroupAudio(UUID playerId) { return eligibleAudio.contains(playerId); }

    synchronized boolean retrySave() {
        return ready() && persistence.retryPendingSave();
    }

    synchronized Status status() {
        return new Status(persistence.status(), bindings.size(), directory.groupCount(), directory.memberCount(),
            directory.pendingInviteCount(), accepted, rejected, rateLimited, deliveryFailures,
            rosterTruncations, closing);
    }

    synchronized void requestClose() {
        if (closing) return;
        closing = true;
        eligibleAudio = Set.of();
        for (UUID id : bindings.keySet()) directory.onPlayerDisconnected(id);
        bindings.clear();
        persistence.requestClose();
    }

    boolean awaitStopped(Duration timeout) throws InterruptedException { return persistence.awaitStopped(timeout); }

    private boolean ready() {
        return !closing && !failed && persistence.status().state() == FederatedGroupPersistence.State.READY;
    }

    private boolean current(Binding binding) { return transport.isCurrent(binding.id, binding.connection); }
    private boolean allowed(Binding binding, String node) {
        return current(binding) && transport.allowed(binding.id, binding.connection, node);
    }

    private void refreshEligibility() {
        if (!ready()) { eligibleAudio = Set.of(); return; }
        Set<UUID> next = new HashSet<>();
        for (Binding binding : bindings.values()) if (allowed(binding, USE)) next.add(binding.id);
        eligibleAudio = Set.copyOf(next);
    }

    private void syncMembers(Set<UUID> members) {
        for (UUID id : members) {
            Binding binding = bindings.get(id);
            if (binding != null) syncState(binding);
        }
    }

    private void syncState(Binding binding) {
        if (binding == null || !current(binding)) return;
        FederatedGroup group = ready() && allowed(binding, USE) ? directory.groupFor(binding.id).orElse(null) : null;
        send(binding, LegacyForgeGroupCodec.encodeGroupState(group == null ? List.of() : List.of(group), id -> {
            Binding known = bindings.get(id);
            return known == null ? null : known.name;
        }));
    }

    private List<Binding> onlineBindings() {
        if (!ready()) return List.of();
        return bindings.values().stream().filter(this::current)
            .filter(binding -> allowed(binding, USE))
            .sorted(Comparator.comparing(binding -> binding.id)).toList();
    }

    private void syncRosters() {
        if (closing) return;
        List<Binding> online = onlineBindings();
        for (Binding recipient : bindings.values()) syncRoster(recipient, online);
    }

    private void syncRoster(Binding recipient, List<Binding> online) {
        if (closing || !current(recipient)) return;
        if (!ready() || !allowed(recipient, USE)) {
            send(recipient, LegacyForgeGroupCodec.encodeRoster(List.of()));
            return;
        }
        List<LegacyForgeGroupCodec.RosterPlayer> roster = new ArrayList<>();
        roster.add(new LegacyForgeGroupCodec.RosterPlayer(recipient.id, recipient.name));
        int bytes = 3 + 18 + recipient.name.getBytes(StandardCharsets.UTF_8).length;
        for (Binding candidate : online) {
            if (candidate.id.equals(recipient.id)) continue;
            int addition = 18 + candidate.name.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + addition > LegacyForgeGroupCodec.MAX_MESSAGE_BYTES) {
                rosterTruncations++;
                continue;
            }
            roster.add(new LegacyForgeGroupCodec.RosterPlayer(candidate.id, candidate.name));
            bytes += addition;
        }
        send(recipient, LegacyForgeGroupCodec.encodeRoster(roster));
    }

    private boolean send(Binding recipient, byte[] payload) {
        if (!current(recipient)) return false;
        try {
            if (transport.send(recipient.id, recipient.connection, payload)) return true;
        } catch (RuntimeException unavailable) {
            // A departing client cannot interrupt the control task for everyone else.
        }
        deliveryFailures++;
        return false;
    }

    private void notice(Binding recipient, Notice message) {
        if (!current(recipient)) return;
        try { transport.notice(recipient.id, recipient.connection, message); }
        catch (RuntimeException unavailable) { deliveryFailures++; }
    }

    private void reject(Binding recipient, Notice message) { rejected++; notice(recipient, message); }
}
