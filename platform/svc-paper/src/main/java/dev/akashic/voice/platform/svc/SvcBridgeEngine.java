package dev.akashic.voice.platform.svc;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiolistener.PlayerAudioListener;
import de.maxhenkel.voicechat.api.audiosender.AudioSender;
import de.maxhenkel.voicechat.api.packets.SoundPacket;
import dev.akashic.voice.platform.bridge.AkashicSpeakerFrame;
import dev.akashic.voice.platform.bridge.BridgeDelivery;
import dev.akashic.voice.platform.bridge.BridgeStreamReset;
import dev.akashic.voice.platform.bridge.GatewayLink;
import dev.akashic.voice.platform.bridge.ManagedPlayer;
import dev.akashic.voice.platform.bridge.PositionSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class SvcBridgeEngine implements GatewayLink.Listener, AutoCloseable {
    private final GatewayLink gateway;
    private final String backendId;
    private final BukkitPositionSnapshots positions;
    private final Map<UUID, ManagedPlayer> desiredPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, ManagedSession> sessions = new ConcurrentHashMap<>();
    private final ManagedIdentityBindings identities = new ManagedIdentityBindings();

    private volatile VoicechatServerApi voicechat;

    SvcBridgeEngine(GatewayLink gateway, String backendId, BukkitPositionSnapshots positions) {
        this.gateway = gateway;
        this.backendId = backendId;
        this.positions = positions;
    }

    SvcBridgeStatus statusSnapshot() {
        return new SvcBridgeStatus(
            gateway.isConnected(), voicechat != null,
            desiredPlayers.size(), sessions.size(), identities.size(),
            gateway.droppedMediaFrames(), gateway.queuedMediaMessages(), gateway.queuedControlMessages());
    }

    void bindVoicechat(VoicechatServerApi api) {
        this.voicechat = api;
    }

    void unbindVoicechat(VoicechatServerApi api) {
        if (voicechat != api) return;
        closeSessions(api);
        voicechat = null;
    }

    void tickMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Bridge reconciliation must run on the Bukkit main thread");
        }
        positions.refresh();
        VoicechatServerApi api = voicechat;
        if (api == null) return;
        reconcile(api);
    }
    private void reconcile(VoicechatServerApi api) {
        Map<UUID, ManagedPlayer> desiredSnapshot = new HashMap<>(desiredPlayers);
        for (UUID proxyId : new HashSet<>(sessions.keySet())) {
            if (!desiredSnapshot.containsKey(proxyId)) detach(api, proxyId);
        }
        for (ManagedPlayer managed : desiredSnapshot.values()) {
            Player bukkitPlayer = Bukkit.getPlayerExact(managed.playerName());
            if (bukkitPlayer == null || !bukkitPlayer.getName().equalsIgnoreCase(managed.playerName())) {
                detach(api, managed.playerId());
                continue;
            }
            UUID localId = bukkitPlayer.getUniqueId();
            VoicechatConnection connection = api.getConnectionOf(localId);
            if (connection == null || connection.isInstalled()) {
                detach(api, managed.playerId());
                continue;
            }
            ManagedSession existing = sessions.get(managed.playerId());
            if (existing != null && !existing.localPlayerId.equals(localId)) {
                detach(api, managed.playerId());
                existing = null;
            }
            if (existing == null) attach(api, managed.playerId(), localId, connection);
        }
    }

    private void attach(VoicechatServerApi api, UUID proxyId, UUID localId, VoicechatConnection connection) {
        if (!identities.bind(proxyId, localId)) return;

        AudioSender sender = api.createAudioSender(connection);
        if (!api.registerAudioSender(sender)) {
            identities.unbind(proxyId, localId);
            return;
        }
        PlayerAudioListener listener = api.playerAudioListenerBuilder()
            .setPlayer(localId)
            .setPacketListener(packet -> onSvcPacket(proxyId, localId, packet))
            .build();
        if (!api.registerAudioListener(listener)) {
            api.unregisterAudioSender(sender);
            identities.unbind(proxyId, localId);
            return;
        }

        ManagedSession session = new ManagedSession(proxyId, localId, sender, listener);
        ManagedSession raced = sessions.putIfAbsent(proxyId, session);
        if (raced != null) {
            session.close(api);
            identities.unbind(proxyId, localId);
        }
    }
    private void detach(VoicechatServerApi api, UUID proxyId) {
        ManagedSession session = sessions.get(proxyId);
        if (session == null) return;
        session.reset();
        if (sessions.remove(proxyId, session)) {
            identities.unbind(proxyId, session.localPlayerId);
            session.close(api);
        }
    }

    private void closeSessions(VoicechatServerApi api) {
        for (UUID proxyId : new HashSet<>(sessions.keySet())) detach(api, proxyId);
    }

    private void onSvcPacket(UUID proxyRecipientId, UUID localRecipientId, SoundPacket packet) {
        if (!gateway.isConnected()) return;
        UUID senderId = packet.getSender();
        if (identities.isSameManagedPlayer(proxyRecipientId, senderId)) return;

        BridgeStreamReset reset = SvcPacketMapper.mapReset(proxyRecipientId, packet);
        if (reset != null) {
            gateway.publishSvcReset(reset);
            return;
        }

        PositionSnapshot recipientPosition = positions.get(localRecipientId);
        PositionSnapshot entityPosition = null;
        if (packet instanceof de.maxhenkel.voicechat.api.packets.EntitySoundPacket entity) {
            entityPosition = positions.get(entity.getEntityUuid());
        }
        BridgeDelivery delivery = SvcPacketMapper.mapFrame(
            proxyRecipientId, packet, recipientPosition, entityPosition);
        if (delivery != null) gateway.publishSvcDelivery(delivery);
    }

    @Override
    public void onManagedPlayerAttached(ManagedPlayer player) {
        if (!backendId.equals(player.backendId())) return;
        desiredPlayers.put(player.playerId(), player);
    }

    @Override
    public void onManagedPlayerDetached(UUID playerId) {
        desiredPlayers.remove(playerId);
    }

    @Override
    public void onAkashicSpeakerFrame(AkashicSpeakerFrame frame) {
        ManagedSession session = sessions.get(frame.speakerId());
        if (session != null) session.send(frame);
    }

    @Override
    public void onAkashicSpeakerReset(UUID speakerId) {
        ManagedSession session = sessions.get(speakerId);
        if (session != null) session.reset();
    }
    @Override
    public void close() {
        VoicechatServerApi api = voicechat;
        if (api != null) closeSessions(api);
        desiredPlayers.clear();
        identities.clear();
        voicechat = null;
    }

    private static final class ManagedSession {
        private final UUID proxyPlayerId;
        private final UUID localPlayerId;
        private final AudioSender sender;
        private final PlayerAudioListener listener;
        private boolean closed;

        private ManagedSession(UUID proxyPlayerId, UUID localPlayerId,
                               AudioSender sender, PlayerAudioListener listener) {
            this.proxyPlayerId = proxyPlayerId;
            this.localPlayerId = localPlayerId;
            this.sender = sender;
            this.listener = listener;
        }

        synchronized void send(AkashicSpeakerFrame frame) {
            if (closed || !proxyPlayerId.equals(frame.speakerId()) || !sender.canSend()) return;
            sender.whispering(frame.whispering())
                .sequenceNumber(frame.sequence())
                .send(frame.opusUnsafe());
        }

        synchronized void reset() {
            if (!closed && sender.canSend()) sender.reset();
        }

        synchronized void close(VoicechatServerApi api) {
            if (closed) return;
            closed = true;
            api.unregisterAudioListener(listener);
            api.unregisterAudioSender(sender);
        }
    }
}
