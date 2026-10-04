package dev.akashic.voice.platform.velocity;

record GatewayUdpSnapshot(
    int activeSessions,
    int pendingHandshakes,
    long helloRateLimited,
    long mediaRateLimited,
    long encryptedRejected,
    long micFramesAccepted,
    long soundFramesSent,
    long groupFramesSent,
    long radioFramesSent,
    long soundResetsSent,
    long sessionsOpened,
    long sessionsClosed
) {}
