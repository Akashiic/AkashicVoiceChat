package dev.akashic.voice.platform.bridge;

import java.util.UUID;

public interface GatewayLink extends AutoCloseable {
    void start(Listener listener) throws Exception;
    boolean isConnected();
    void publishSvcDelivery(BridgeDelivery delivery);
    void publishSvcReset(BridgeStreamReset reset);

    @Override
    void close();

    interface Listener {
        void onManagedPlayerAttached(ManagedPlayer player);
        void onManagedPlayerDetached(UUID playerId);
        void onAkashicSpeakerFrame(AkashicSpeakerFrame frame);
        void onAkashicSpeakerReset(UUID speakerId);
    }
}
