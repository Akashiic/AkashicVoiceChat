package dev.akashic.voice.api;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import dev.akashic.voice.AkashicVoice;
import dev.akashic.voice.voice.client.MicGate;
import dev.akashic.voice.voice.client.talk.TalkCache;

/**
 * Minimal, zero-dependency client hook registry for external 1.7.10 addons.
 *
 * <p>
 * Fast-paths with zero allocations when no listeners or filters are registered.
 * All addon callbacks are failure-isolated so a misbehaving addon cannot interrupt voice capture.
 */
public final class AkashicVoiceClientHooks {

    public static final int API_VERSION = 1;

    private static final long FAILURE_LOG_INTERVAL_NANOS = 5_000_000_000L;

    private static final CopyOnWriteArrayList<ClientMicFrameListener> MIC_FRAME_LISTENERS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<ClientMicTransmissionFilter> TRANSMISSION_FILTERS = new CopyOnWriteArrayList<>();
    private static final AtomicLong LAST_FAILURE_LOG_NANOS = new AtomicLong();

    private AkashicVoiceClientHooks() {}

    /**
     * Registers a non-blocking read-only microphone PCM frame listener.
     *
     * @return an idempotently closeable registration handle
     */
    public static AutoCloseable addMicFrameListener(ClientMicFrameListener listener) {
        Objects.requireNonNull(listener, "listener");
        MIC_FRAME_LISTENERS.add(listener);
        final AtomicBoolean closed = new AtomicBoolean(false);
        return () -> {
            if (closed.compareAndSet(false, true)) {
                MIC_FRAME_LISTENERS.remove(listener);
            }
        };
    }

    /**
     * Registers a non-blocking transmission gate filter.
     *
     * @return an idempotently closeable registration handle
     */
    public static AutoCloseable addTransmissionFilter(ClientMicTransmissionFilter filter) {
        Objects.requireNonNull(filter, "filter");
        TRANSMISSION_FILTERS.add(filter);
        final AtomicBoolean closed = new AtomicBoolean(false);
        return () -> {
            if (closed.compareAndSet(false, true)) {
                TRANSMISSION_FILTERS.remove(filter);
            }
        };
    }

    public static boolean hasMicFrameListeners() {
        return !MIC_FRAME_LISTENERS.isEmpty();
    }

    public static boolean hasTransmissionFilters() {
        return !TRANSMISSION_FILTERS.isEmpty();
    }

    /**
     * Evaluates registered transmission filters. Returns {@code true} immediately when none exist.
     * Failing filters fail-open so a broken addon cannot permanently mute the player.
     */
    public static boolean isTransmissionAllowed() {
        if (TRANSMISSION_FILTERS.isEmpty()) return true;
        for (ClientMicTransmissionFilter filter : TRANSMISSION_FILTERS) {
            try {
                if (!filter.allowTransmission()) return false;
            } catch (RuntimeException failure) {
                logFailureThrottled("ClientMicTransmissionFilter failed; ignoring filter", failure);
            }
        }
        return true;
    }

    /**
     * Dispatches a processed PCM frame to registered listeners. Zero allocation when no listeners exist.
     */
    public static void dispatchMicFrame(short[] pcm, int sampleRate, boolean transmitting) {
        if (MIC_FRAME_LISTENERS.isEmpty() || pcm == null) return;
        double rms = MicGate.computeRms(pcm);
        for (ClientMicFrameListener listener : MIC_FRAME_LISTENERS) {
            try {
                listener.onMicFrame(Arrays.copyOf(pcm, pcm.length), sampleRate, rms, transmitting);
            } catch (RuntimeException failure) {
                logFailureThrottled("ClientMicFrameListener failed; isolating listener error", failure);
            }
        }
    }

    public static boolean isMuted() {
        return MicGate.isMuted();
    }

    public static boolean isDeafened() {
        return MicGate.isDeafened();
    }

    public static boolean isPttEngaged() {
        return MicGate.isPttEngaged();
    }

    public static long lastTransmitMillis() {
        return MicGate.lastTransmitMillis();
    }

    public static boolean isPlayerSpeaking(UUID playerId) {
        return playerId != null && TalkCache.isSpeaking(playerId);
    }

    public static double computeRms(short[] pcm) {
        return pcm == null ? 0.0 : MicGate.computeRms(pcm);
    }

    private static void logFailureThrottled(String message, RuntimeException failure) {
        long now = System.nanoTime();
        long prev = LAST_FAILURE_LOG_NANOS.get();
        if (prev == 0L || now - prev >= FAILURE_LOG_INTERVAL_NANOS) {
            if (LAST_FAILURE_LOG_NANOS.compareAndSet(prev, now)) {
                AkashicVoice.LOG.warn(message, failure);
            }
        }
    }
}
