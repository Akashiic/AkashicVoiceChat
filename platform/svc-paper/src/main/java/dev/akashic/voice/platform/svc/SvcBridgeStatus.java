package dev.akashic.voice.platform.svc;

record SvcBridgeStatus(
    boolean gatewayConnected,
    boolean voicechatBound,
    int desiredPlayers,
    int activeSessions,
    int identityBindings
) {}
