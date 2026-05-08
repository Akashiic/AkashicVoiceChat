package dev.jm.proxychat.voice.client.talk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TalkCache {

    private static final long SPEAKING_WINDOW_MILLIS = 250L;
    private static final Map<UUID, Long> LAST_FRAME = new ConcurrentHashMap<>();

    private TalkCache() {}

    public static void markSpeaking(UUID playerId) {
        LAST_FRAME.put(playerId, System.currentTimeMillis());
    }

    public static boolean isSpeaking(UUID playerId) {
        Long last = LAST_FRAME.get(playerId);
        return last != null && System.currentTimeMillis() - last <= SPEAKING_WINDOW_MILLIS;
    }

    public static void clear() {
        LAST_FRAME.clear();
    }
}
