package dev.akashic.voice.platform.agent;

import dev.akashic.voice.platform.bridge.AkashicSpeakerFrame;
import dev.akashic.voice.platform.bridge.BridgeDelivery;
import dev.akashic.voice.platform.bridge.BridgeStreamReset;
import dev.akashic.voice.platform.bridge.ManagedPlayer;

import java.util.Objects;
import java.util.UUID;

public sealed interface AgentMessage {
    record Ready() implements AgentMessage {}
    record Attach(ManagedPlayer player) implements AgentMessage {
        public Attach { Objects.requireNonNull(player, "player"); }
    }
    record Detach(UUID playerId) implements AgentMessage {
        public Detach { Objects.requireNonNull(playerId, "playerId"); }
    }
    record SpeakerFrame(AkashicSpeakerFrame frame) implements AgentMessage {
        public SpeakerFrame { Objects.requireNonNull(frame, "frame"); }
    }
    record SpeakerReset(UUID speakerId) implements AgentMessage {
        public SpeakerReset { Objects.requireNonNull(speakerId, "speakerId"); }
    }
    record SvcDelivery(BridgeDelivery delivery) implements AgentMessage {
        public SvcDelivery { Objects.requireNonNull(delivery, "delivery"); }
    }
    record SvcReset(BridgeStreamReset reset) implements AgentMessage {
        public SvcReset { Objects.requireNonNull(reset, "reset"); }
    }
    record Ping(long timestamp) implements AgentMessage {}
    record Pong(long timestamp) implements AgentMessage {}
}
