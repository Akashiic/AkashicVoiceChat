package dev.akashic.voice.voice.server;

import java.util.concurrent.atomic.AtomicLong;

/** Lock-free aggregate counters for diagnostics; no per-player labels or high-cardinality state. */
final class VoiceServerTelemetry {

    private final AtomicLong datagramsReceived = new AtomicLong();
    private final AtomicLong bytesReceived = new AtomicLong();
    private final AtomicLong datagramsSent = new AtomicLong();
    private final AtomicLong bytesSent = new AtomicLong();
    private final AtomicLong helloRateLimited = new AtomicLong();
    private final AtomicLong mediaRateLimited = new AtomicLong();
    private final AtomicLong encryptedRejected = new AtomicLong();
    private final AtomicLong handshakeRejected = new AtomicLong();
    private final AtomicLong micFramesAccepted = new AtomicLong();
    private final AtomicLong micBytesAccepted = new AtomicLong();
    private final AtomicLong spatialDeliveries = new AtomicLong();
    private final AtomicLong groupDeliveries = new AtomicLong();
    private final AtomicLong selfEchoDeliveries = new AtomicLong();
    private final AtomicLong sessionsOpened = new AtomicLong();
    private final AtomicLong sessionsClosed = new AtomicLong();
    private final AtomicLong sessionsTimedOut = new AtomicLong();

    void received(int bytes) {
        int valid = validBytes(bytes);
        datagramsReceived.incrementAndGet();
        bytesReceived.addAndGet(valid);
    }

    void sent(int bytes) {
        int valid = validBytes(bytes);
        datagramsSent.incrementAndGet();
        bytesSent.addAndGet(valid);
    }

    void helloRateLimited() {
        helloRateLimited.incrementAndGet();
    }

    void mediaRateLimited() {
        mediaRateLimited.incrementAndGet();
    }

    void encryptedRejected() {
        encryptedRejected.incrementAndGet();
    }

    void handshakeRejected() {
        handshakeRejected.incrementAndGet();
    }

    void micAccepted(int opusBytes) {
        int valid = validBytes(opusBytes);
        micFramesAccepted.incrementAndGet();
        micBytesAccepted.addAndGet(valid);
    }

    void spatialDelivery() {
        spatialDeliveries.incrementAndGet();
    }

    void groupDelivery() {
        groupDeliveries.incrementAndGet();
    }

    void selfEchoDelivery() {
        selfEchoDeliveries.incrementAndGet();
    }

    void sessionOpened() {
        sessionsOpened.incrementAndGet();
    }

    void sessionClosed() {
        sessionsClosed.incrementAndGet();
    }

    void sessionTimedOut() {
        sessionsTimedOut.incrementAndGet();
    }

    private static int validBytes(int bytes) {
        if (bytes < 0) throw new IllegalArgumentException("negative byte count");
        return bytes;
    }

    VoiceServerMetricsSnapshot snapshot(boolean running, int port, int active, int pending) {
        return new VoiceServerMetricsSnapshot(
            running,
            port,
            active,
            pending,
            datagramsReceived.get(),
            bytesReceived.get(),
            datagramsSent.get(),
            bytesSent.get(),
            helloRateLimited.get(),
            mediaRateLimited.get(),
            encryptedRejected.get(),
            handshakeRejected.get(),
            micFramesAccepted.get(),
            micBytesAccepted.get(),
            spatialDeliveries.get(),
            groupDeliveries.get(),
            selfEchoDeliveries.get(),
            sessionsOpened.get(),
            sessionsClosed.get(),
            sessionsTimedOut.get());
    }
}
