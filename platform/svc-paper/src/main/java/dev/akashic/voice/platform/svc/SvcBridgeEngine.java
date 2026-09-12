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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class SvcBridgeEngine implements GatewayLink.Listener, AutoCloseable {
    private final GatewayLink gateway;
    private final String backendId;
    private final BukkitPositionSnapshots positions;
    private final Set<UUID> desiredPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ManagedSession> sessions = new ConcurrentHashMap<>();
    private volatile VoicechatServerApi voicechat;

    SvcBridgeEngine(GatewayLink gateway, String backendId, BukkitPositionSnapshots positions) {
        this.gateway = gateway;
        this.backendId = backendId;
        this.positions = positions;
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
        Set<UUID> desiredSnapshot = new HashSet<>(desiredPlayers);
        for (UUID playerId : new HashSet<>(sessions.keySet())) {
            if (!desiredSnapshot.contains(playerId)) {
                detach(api, playerId);
            }
        }
        for (UUID playerId : desiredSnapshot) {
            VoicechatConnection connection = api.getConnectionOf(playerId);
            if (connection == null || connection.isInstalled()) {
                detach(api, playerId);
                continue;
            }
            if (!sessions.containsKey(playerId)) {
                attach(api, connection);
            }
        }
    }

    private void attach(VoicechatServerApi api, VoicechatConnection connection) {
        UUID playerId = connection.getPlayer().getUuid();
        AudioSender sender = api.createAudioSender(connection);
        if (!api.registerAudioSender(sender)) return;

        PlayerAudioListener listener = api.playerAudioListenerBuilder()
            .setPlayer(playerId)
            .setPacketListener(packet -> onSvcPacket(playerId, packet))
            .build();
        if (!api.registerAudioListener(listener)) {
            api.unregisterAudioSender(sender);
            return;
        }
        ManagedSession session = new ManagedSession(playerId, sender, listener);
        ManagedSession raced = sessions.putIfAbsent(playerId, session);
        if (raced != null) {
            session.close(api);
        }
    }

    private void detach(VoicechatServerApi api, UUID playerId) {
        ManagedSession session = sessions.get(playerId);
        if (session == null) return;
        session.reset();
        if (sessions.remove(playerId, session)) {
            session.close(api);
        }
    }

    private void closeSessions(VoicechatServerApi api) {
        for (UUID playerId : new HashSet<>(sessions.keySet())) {
            detach(api, playerId);
        }
    }

    private void onSvcPacket(UUID recipientId, SoundPacket packet) {
        if (!gateway.isConnected()) return;
        UUID senderId = packet.getSender();
        if (sessions.containsKey(senderId)) return;

        BridgeStreamReset reset = SvcPacketMapper.mapReset(recipientId, packet);
        if (reset != null) {
            gateway.publishSvcReset(reset);
            return;
        }

        PositionSnapshot recipientPosition = positions.get(recipientId);
        PositionSnapshot entityPosition = null;
        if (packet instanceof de.maxhenkel.voicechat.api.packets.EntitySoundPacket entity) {
            entityPosition = positions.get(entity.getEntityUuid());
        }
        BridgeDelivery delivery = SvcPacketMapper.mapFrame(
            recipientId, packet, recipientPosition, entityPosition);
        if (delivery != null) gateway.publishSvcDelivery(delivery);
    }

    @Override
    public void onManagedPlayerAttached(ManagedPlayer player) {
        if (!backendId.equals(player.backendId())) return;
        desiredPlayers.add(player.playerId());
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
        voicechat = null;
    }

    private static final class ManagedSession {
        private final UUID playerId;
        private final AudioSender sender;
        private final PlayerAudioListener listener;
        private boolean closed;

        private ManagedSession(UUID playerId, AudioSender sender, PlayerAudioListener listener) {
            this.playerId = playerId;
            this.sender = sender;
            this.listener = listener;
        }

        synchronized void send(AkashicSpeakerFrame frame) {
            if (closed || !playerId.equals(frame.speakerId()) || !sender.canSend()) return;
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
