package dev.akashic.voice.voice.client.jitter;

import dev.akashic.voice.voice.common.codec.AudioFormatSpec;

public final class OpusJitterBuffer {

    public enum ReadyKind {
        NONE,
        PACKET,
        MISSING
    }

    public static final int DEFAULT_SLOT_COUNT = 8;
    public static final int DEFAULT_TARGET_DELAY_FRAMES = 2;
    public static final long DEFAULT_FLUSH_DELAY_NANOS = 40_000_000L;

    public static final class Slot {

        final byte[] payload = new byte[AudioFormatSpec.MAX_OPUS_PAYLOAD];
        int sequence;
        int length;
        boolean spatial;
        float x;
        float y;
        float z;
        float maxDistance;
        boolean whispering;
        boolean occupied;

        public int sequence() {
            return sequence;
        }

        public int length() {
            return length;
        }

        public boolean isSpatial() {
            return spatial;
        }

        public float x() {
            return x;
        }

        public float y() {
            return y;
        }

        public float z() {
            return z;
        }

        public float maxDistance() {
            return maxDistance;
        }

        public boolean isWhispering() {
            return whispering;
        }

        public byte[] payload() {
            return payload;
        }
    }

    private final Slot[] slots;
    private final int mask;
    private final int targetDelayFrames;
    private final long flushDelayNanos;
    private boolean initialized;
    private int expectedSequence;
    private int highestSequence;
    private long lastOfferNanos;
    private long duplicateCount;
    private long lateCount;
    private long missingCount;
    private long resyncCount;

    public OpusJitterBuffer() {
        this(DEFAULT_SLOT_COUNT, DEFAULT_TARGET_DELAY_FRAMES, DEFAULT_FLUSH_DELAY_NANOS);
    }

    public OpusJitterBuffer(int slotCount, int targetDelayFrames, long flushDelayNanos) {
        if (slotCount < 4 || (slotCount & (slotCount - 1)) != 0) {
            throw new IllegalArgumentException("slotCount must be a power of two >= 4");
        }
        if (targetDelayFrames < 1 || targetDelayFrames >= slotCount) {
            throw new IllegalArgumentException("invalid targetDelayFrames");
        }
        if (flushDelayNanos <= 0L) throw new IllegalArgumentException("flushDelayNanos <= 0");
        this.slots = new Slot[slotCount];
        for (int i = 0; i < slotCount; i++) {
            slots[i] = new Slot();
        }
        this.mask = slotCount - 1;
        this.targetDelayFrames = targetDelayFrames;
        this.flushDelayNanos = flushDelayNanos;
    }

    public synchronized boolean offer(int sequence, boolean spatial, float x, float y, float z, byte[] payload,
        int offset, int length, long nowNanos) {
        return offer(sequence, spatial, x, y, z, Float.NaN, false, payload, offset, length, nowNanos);
    }

    public synchronized boolean offer(int sequence, boolean spatial, float x, float y, float z, float maxDistance,
        boolean whispering, byte[] payload, int offset, int length, long nowNanos) {
        if (length <= 0 || length > AudioFormatSpec.MAX_OPUS_PAYLOAD
            || offset < 0
            || offset + length > payload.length) {
            return false;
        }
        if (!initialized) {
            initialized = true;
            expectedSequence = sequence;
            highestSequence = sequence;
        } else {
            int fromExpected = sequence - expectedSequence;
            if (fromExpected < 0) {
                lateCount++;
                return false;
            }
            if (fromExpected >= slots.length) {
                resyncAround(sequence);
            } else if (sequence - highestSequence > 0) {
                highestSequence = sequence;
            }
        }

        Slot slot = slots[sequence & mask];
        if (slot.occupied) {
            if (slot.sequence == sequence) {
                duplicateCount++;
                return false;
            }
            resyncAround(sequence);
            slot = slots[sequence & mask];
        }

        System.arraycopy(payload, offset, slot.payload, 0, length);
        slot.sequence = sequence;
        slot.length = length;
        slot.spatial = spatial;
        slot.x = x;
        slot.y = y;
        slot.z = z;
        slot.maxDistance = maxDistance;
        slot.whispering = whispering;
        slot.occupied = true;
        if (sequence - highestSequence > 0) highestSequence = sequence;
        lastOfferNanos = nowNanos;
        return true;
    }

    public synchronized ReadyKind next(long nowNanos) {
        if (!initialized) return ReadyKind.NONE;
        int ahead = highestSequence - expectedSequence;
        if (ahead < 0) return ReadyKind.NONE;
        boolean depthReady = ahead >= targetDelayFrames;
        boolean timeoutReady = nowNanos - lastOfferNanos >= flushDelayNanos;
        if (!depthReady && !timeoutReady) return ReadyKind.NONE;

        Slot slot = slots[expectedSequence & mask];
        if (slot.occupied && slot.sequence == expectedSequence) {
            return ReadyKind.PACKET;
        }
        return ReadyKind.MISSING;
    }

    public synchronized Slot expectedSlot() {
        if (!initialized) return null;
        Slot slot = slots[expectedSequence & mask];
        return slot.occupied && slot.sequence == expectedSequence ? slot : null;
    }

    public synchronized void consumeExpected() {
        if (!initialized) return;
        Slot slot = slots[expectedSequence & mask];
        if (slot.occupied && slot.sequence == expectedSequence) {
            slot.occupied = false;
            slot.length = 0;
        } else {
            missingCount++;
        }
        expectedSequence++;
    }

    public synchronized int expectedSequence() {
        return expectedSequence;
    }

    public synchronized long duplicateCount() {
        return duplicateCount;
    }

    public synchronized long lateCount() {
        return lateCount;
    }

    public synchronized long missingCount() {
        return missingCount;
    }

    public synchronized long resyncCount() {
        return resyncCount;
    }

    public synchronized void reset() {
        clearSlots();
        initialized = false;
        expectedSequence = 0;
        highestSequence = 0;
        lastOfferNanos = 0L;
    }

    private void resyncAround(int sequence) {
        clearSlots();
        expectedSequence = sequence - targetDelayFrames;
        highestSequence = sequence;
        resyncCount++;
    }

    private void clearSlots() {
        for (Slot slot : slots) {
            slot.occupied = false;
            slot.length = 0;
        }
    }
}
