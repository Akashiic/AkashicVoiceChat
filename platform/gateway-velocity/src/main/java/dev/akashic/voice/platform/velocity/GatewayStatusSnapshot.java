package dev.akashic.voice.platform.velocity;

import java.util.Map;

record GatewayStatusSnapshot(
    boolean running,
    int udpPort,
    int agentPort,
    int activeUdpSessions,
    int pendingHandshakes,
    Map<String, Boolean> backendRoutes,
    long helloRateLimited,
    long mediaRateLimited,
    long encryptedRejected,
    long micFramesAccepted,
    long soundFramesSent,
    long soundResetsSent,
    long agentMediaDropped,
    int agentMediaQueued,
    int agentControlQueued,
    long sessionsOpened,
    long sessionsClosed
) {
    GatewayStatusSnapshot {
        backendRoutes = Map.copyOf(backendRoutes);
    }
}
