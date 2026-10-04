package dev.akashic.voice.voice.client.playback;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/** Bounded voice-only gain policy. It never writes the shared OpenAL listener. */
final class VoiceGain {

    static final float MAX_GAIN = 3f; // Existing user-configured 0..300% range.
    static final float FLOAT_HEADROOM = 4f; // Power of two keeps S16 conversion exact.
    static final int GAIN_LIMIT_SOFT = 0x200E;
    static final int MONO_FLOAT32 = 0x10010;

    enum Backend {
        EXTENDED_SOURCE,
        FLOAT_HEADROOM,
        PCM16_FALLBACK
    }

    private VoiceGain() {}

    static boolean valid(float gain) {
        return Float.isFinite(gain) && gain >= 0f && gain <= MAX_GAIN;
    }

    static float sourceGain(Backend backend, float gain) {
        if (!valid(gain)) throw new IllegalArgumentException("Voice gain outside 0..3");
        if (backend == Backend.FLOAT_HEADROOM) return gain / FLOAT_HEADROOM;
        return backend == Backend.EXTENDED_SOURCE ? gain : Math.min(1f, gain);
    }

    static float pcmBoost(Backend backend, float gain) {
        return backend == Backend.PCM16_FALLBACK ? Math.max(1f, gain) : 1f;
    }

    static void writeFloat(short[] pcm, FloatBuffer target) {
        target.clear();
        for (short sample : pcm) target.put(sample * (FLOAT_HEADROOM / 32768f));
        target.flip();
    }

    static long writeS16(short[] pcm, float boost, ShortBuffer target) {
        target.clear();
        long clipped = 0;
        if (boost == 1f) target.put(pcm);
        else {
            for (short sample : pcm) {
                int scaled = Math.round(sample * boost);
                if (scaled > Short.MAX_VALUE) {
                    scaled = Short.MAX_VALUE;
                    clipped++;
                } else if (scaled < Short.MIN_VALUE) {
                    scaled = Short.MIN_VALUE;
                    clipped++;
                }
                target.put((short) scaled);
            }
        }
        target.flip();
        return clipped;
    }
}
