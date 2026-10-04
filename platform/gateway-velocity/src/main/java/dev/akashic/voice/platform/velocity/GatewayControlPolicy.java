package dev.akashic.voice.platform.velocity;

/** Pure forwarding policy. The adapter MUST derive currentConnection from actual Velocity objects. */
final class GatewayControlPolicy {
    enum Direction { CLIENT, BACKEND, OTHER }
    enum Action { IGNORE, DROP, NATIVE, BOOTSTRAP, GROUP }
    private GatewayControlPolicy() {}

    static Action classify(boolean matchingChannel, boolean alreadyHandled, boolean currentConnection,
                           Direction direction, int tag, int length, boolean backendReady, boolean bound) {
        if (!matchingChannel || alreadyHandled) return Action.IGNORE;
        if (!currentConnection || length <= 0 || direction == Direction.OTHER) return Action.DROP;
        if (direction == Direction.CLIENT) {
            if (tag == 0 && length == 1) return backendReady ? Action.BOOTSTRAP : Action.NATIVE;
            if (tag >= 2 && tag <= 5) {
                if (bound) return Action.GROUP;
                if (!backendReady && length <= 259) return Action.NATIVE;
            }
            return Action.DROP;
        }
        boolean serverControl = tag == 1 || tag == 9 || tag >= 6 && tag <= 8;
        return serverControl && !backendReady && !bound ? Action.NATIVE : Action.DROP;
    }
}
