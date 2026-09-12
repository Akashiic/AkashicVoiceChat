package dev.akashic.voice.voice.common.crypto;

public final class ReplayWindow {

    public static final int WINDOW_BITS = 64;

    private long highest = -1L;
    private long bitmap;

    public synchronized boolean canAccept(long counter) {
        if (counter < 0L) return false;
        if (highest < 0L || counter > highest) return true;
        long delta = highest - counter;
        if (delta >= WINDOW_BITS) return false;
        long bit = 1L << (int) delta;
        return (bitmap & bit) == 0L;
    }

    public synchronized boolean accept(long counter) {
        if (!canAccept(counter)) return false;
        if (highest < 0L) {
            highest = counter;
            bitmap = 1L;
            return true;
        }
        if (counter > highest) {
            long shift = counter - highest;
            bitmap = shift >= WINDOW_BITS ? 1L : (bitmap << (int) shift) | 1L;
            highest = counter;
            return true;
        }
        int delta = (int) (highest - counter);
        bitmap |= 1L << delta;
        return true;
    }

    public synchronized long highestAccepted() {
        return highest;
    }

    public synchronized void reset() {
        highest = -1L;
        bitmap = 0L;
    }
}
