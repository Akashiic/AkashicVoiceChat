package dev.akashic.voice.voice.server;

/** Immutable aggregate diagnostics. Contains no player IDs, addresses, keys or session secrets. */
public final class VoiceServerMetricsSnapshot {

    private final boolean running;
    private final int udpPort;
    private final int activeSessions;
    private final int pendingHandshakes;
    private final long datagramsReceived;
    private final long bytesReceived;
    private final long datagramsSent;
    private final long bytesSent;
    private final long helloRateLimited;
    private final long mediaRateLimited;
    private final long encryptedRejected;
    private final long handshakeRejected;
    private final long micFramesAccepted;
    private final long micBytesAccepted;
    private final long spatialDeliveries;
    private final long groupDeliveries;
    private final long selfEchoDeliveries;
    private final long sessionsOpened;
    private final long sessionsClosed;
    private final long sessionsTimedOut;

    VoiceServerMetricsSnapshot(boolean running, int udpPort, int activeSessions, int pendingHandshakes,
        long datagramsReceived, long bytesReceived, long datagramsSent, long bytesSent, long helloRateLimited,
        long mediaRateLimited, long encryptedRejected, long handshakeRejected, long micFramesAccepted,
        long micBytesAccepted, long spatialDeliveries, long groupDeliveries, long selfEchoDeliveries,
        long sessionsOpened, long sessionsClosed, long sessionsTimedOut) {
        if (udpPort < 0 || udpPort > 65535 || activeSessions < 0 || pendingHandshakes < 0) {
            throw new IllegalArgumentException("invalid live metrics");
        }
        this.running = running;
        this.udpPort = udpPort;
        this.activeSessions = activeSessions;
        this.pendingHandshakes = pendingHandshakes;
        this.datagramsReceived = nonNegative(datagramsReceived);
        this.bytesReceived = nonNegative(bytesReceived);
        this.datagramsSent = nonNegative(datagramsSent);
        this.bytesSent = nonNegative(bytesSent);
        this.helloRateLimited = nonNegative(helloRateLimited);
        this.mediaRateLimited = nonNegative(mediaRateLimited);
        this.encryptedRejected = nonNegative(encryptedRejected);
        this.handshakeRejected = nonNegative(handshakeRejected);
        this.micFramesAccepted = nonNegative(micFramesAccepted);
        this.micBytesAccepted = nonNegative(micBytesAccepted);
        this.spatialDeliveries = nonNegative(spatialDeliveries);
        this.groupDeliveries = nonNegative(groupDeliveries);
        this.selfEchoDeliveries = nonNegative(selfEchoDeliveries);
        this.sessionsOpened = nonNegative(sessionsOpened);
        this.sessionsClosed = nonNegative(sessionsClosed);
        this.sessionsTimedOut = nonNegative(sessionsTimedOut);
    }

    private static long nonNegative(long value) {
        if (value < 0L) throw new IllegalArgumentException("negative counter");
        return value;
    }

    public boolean running() {
        return running;
    }

    public int udpPort() {
        return udpPort;
    }

    public int activeSessions() {
        return activeSessions;
    }

    public int pendingHandshakes() {
        return pendingHandshakes;
    }

    public long datagramsReceived() {
        return datagramsReceived;
    }

    public long bytesReceived() {
        return bytesReceived;
    }

    public long datagramsSent() {
        return datagramsSent;
    }

    public long bytesSent() {
        return bytesSent;
    }

    public long helloRateLimited() {
        return helloRateLimited;
    }

    public long mediaRateLimited() {
        return mediaRateLimited;
    }

    public long encryptedRejected() {
        return encryptedRejected;
    }

    public long handshakeRejected() {
        return handshakeRejected;
    }

    public long micFramesAccepted() {
        return micFramesAccepted;
    }

    public long micBytesAccepted() {
        return micBytesAccepted;
    }

    public long spatialDeliveries() {
        return spatialDeliveries;
    }

    public long groupDeliveries() {
        return groupDeliveries;
    }

    public long selfEchoDeliveries() {
        return selfEchoDeliveries;
    }

    public long sessionsOpened() {
        return sessionsOpened;
    }

    public long sessionsClosed() {
        return sessionsClosed;
    }

    public long sessionsTimedOut() {
        return sessionsTimedOut;
    }

    public long deliveries() {
        return spatialDeliveries + groupDeliveries + selfEchoDeliveries;
    }
}
