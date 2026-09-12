package dev.akashic.voice.platform.bridge;

public final class DisconnectedGatewayLink implements GatewayLink {
    @Override
    public void start(Listener listener) {
        // Intentionally disconnected until a transport implementation is configured.
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public void publishSvcDelivery(BridgeDelivery delivery) {
        // Drop while disconnected. Never queue unbounded audio.
    }

    @Override
    public void publishSvcReset(BridgeStreamReset reset) {
        // Drop while disconnected.
    }

    @Override
    public void close() {
    }
}
