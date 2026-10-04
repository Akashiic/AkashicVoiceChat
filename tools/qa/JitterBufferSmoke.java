import dev.akashic.voice.voice.client.jitter.OpusJitterBuffer;
import dev.akashic.voice.voice.client.jitter.OpusJitterBuffer.ReadyKind;

public final class JitterBufferSmoke {
    private static final byte[] P = {1, 2, 3};

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static boolean offer(OpusJitterBuffer b, int seq, long t) {
        return b.offer(seq, true, seq, 2, 3, P, 0, P.length, t);
    }

    public static void main(String[] args) {
        long t = 1_000_000_000L;
        OpusJitterBuffer b = new OpusJitterBuffer(8, 2, 40_000_000L);
        require(offer(b, 10, t), "offer 10");
        require(offer(b, 12, t + 1), "offer 12");
        require(offer(b, 11, t + 2), "out-of-order 11");
        require(b.next(t + 2) == ReadyKind.PACKET, "10 must become ready");
        require(b.expectedSlot().sequence() == 10, "expected 10");
        b.consumeExpected();
        require(b.next(t + 2) == ReadyKind.NONE, "maintain target depth");
        require(offer(b, 13, t + 3), "offer 13");
        require(b.next(t + 3) == ReadyKind.PACKET, "11 ready after depth refill");
        require(b.expectedSlot().sequence() == 11, "reordered 11");
        b.consumeExpected();

        OpusJitterBuffer loss = new OpusJitterBuffer(8, 2, 40_000_000L);
        require(offer(loss, 1, t), "loss offer 1");
        require(offer(loss, 2, t + 1), "loss offer 2");
        require(offer(loss, 4, t + 2), "loss offer 4");
        require(loss.next(t + 2) == ReadyKind.PACKET, "loss packet 1");
        loss.consumeExpected();
        require(loss.next(t + 2) == ReadyKind.PACKET, "loss packet 2");
        loss.consumeExpected();
        require(loss.next(t + 2) == ReadyKind.NONE, "wait before declaring 3 lost");
        require(offer(loss, 5, t + 3), "loss offer 5");
        require(loss.next(t + 3) == ReadyKind.MISSING, "missing 3 detected");
        loss.consumeExpected();
        require(loss.missingCount() == 1, "PLC count");

        require(!offer(loss, 5, t + 4), "duplicate rejected");
        require(loss.duplicateCount() == 1, "duplicate count");

        OpusJitterBuffer flush = new OpusJitterBuffer(8, 2, 40_000_000L);
        require(offer(flush, 7, t), "flush offer");
        require(flush.next(t + 39_000_000L) == ReadyKind.NONE, "flush too early");
        require(flush.next(t + 41_000_000L) == ReadyKind.PACKET, "tail flush");
        flush.consumeExpected();

        OpusJitterBuffer wrap = new OpusJitterBuffer(8, 2, 40_000_000L);
        require(offer(wrap, Integer.MAX_VALUE - 1, t), "wrap a");
        require(offer(wrap, Integer.MAX_VALUE, t + 1), "wrap b");
        require(offer(wrap, Integer.MIN_VALUE, t + 2), "wrap c");
        require(wrap.next(t + 2) == ReadyKind.PACKET, "wrap depth");
        require(wrap.expectedSlot().sequence() == Integer.MAX_VALUE - 1, "wrap ordering");

        OpusJitterBuffer resync = new OpusJitterBuffer(8, 2, 40_000_000L);
        require(offer(resync, 1, t), "resync first");
        require(offer(resync, 40, t + 1), "resync far packet");
        require(resync.resyncCount() == 1, "resync count");

        OpusJitterBuffer metadata = new OpusJitterBuffer(8, 2, 40_000_000L);
        require(metadata.offer(100, true, 1F, 2F, 3F, 17.5F, true,
            P, 0, P.length, t), "metadata offer");
        require(metadata.next(t + 41_000_000L) == ReadyKind.PACKET, "metadata flush");
        OpusJitterBuffer.Slot metadataSlot = metadata.expectedSlot();
        require(metadataSlot != null, "metadata slot");
        require(metadataSlot.maxDistance() == 17.5F, "metadata distance");
        require(metadataSlot.isWhispering(), "metadata whisper");

        OpusJitterBuffer adaptive = new OpusJitterBuffer(256, 2, 40_000_000L, true);
        long adaptiveTime = t;
        require(adaptive.offer(1000, true, 0F, 0F, 0F, P, 0, P.length, adaptiveTime), "adaptive first");
        for (int i = 1; i <= 4; i++) {
            adaptiveTime += 50_000_000L;
            require(adaptive.offer(1000 + i, true, 0F, 0F, 0F, P, 0, P.length, adaptiveTime),
                "adaptive jitter offer " + i);
        }
        require(adaptive.targetDelayFrames() == 4, "adaptive target must rise to 4");
        require(adaptive.jitterEstimateNanos() >= 18_000_000L, "adaptive jitter estimate must rise");
        for (int i = 1; i <= 120; i++) {
            adaptiveTime += 20_000_000L;
            require(adaptive.offer(1004 + i, true, 0F, 0F, 0F, P, 0, P.length, adaptiveTime),
                "adaptive stable offer " + i);
        }
        require(adaptive.targetDelayFrames() == 2, "adaptive target must decay back to 2");

        OpusJitterBuffer silence = new OpusJitterBuffer(16, 2, 40_000_000L, true);
        require(silence.offer(1, true, 0F, 0F, 0F, P, 0, P.length, t), "silence first");
        require(silence.offer(2, true, 0F, 0F, 0F, P, 0, P.length, t + 500_000_000L), "silence gap");
        require(silence.offer(3, true, 0F, 0F, 0F, P, 0, P.length, t + 520_000_000L), "silence resume");
        require(silence.targetDelayFrames() == 2, "silence must not inflate target");
        require(silence.jitterEstimateNanos() == 0L, "silence must not inflate jitter estimate");

        OpusJitterBuffer lateAdaptive = new OpusJitterBuffer(8, 2, 40_000_000L, true);
        require(lateAdaptive.offer(10, true, 0F, 0F, 0F, P, 0, P.length, t), "late 10");
        require(lateAdaptive.offer(11, true, 0F, 0F, 0F, P, 0, P.length, t + 20_000_000L), "late 11");
        require(lateAdaptive.offer(12, true, 0F, 0F, 0F, P, 0, P.length, t + 40_000_000L), "late 12");
        require(lateAdaptive.next(t + 40_000_000L) == ReadyKind.PACKET, "late ready 10");
        lateAdaptive.consumeExpected();
        require(lateAdaptive.offer(13, true, 0F, 0F, 0F, P, 0, P.length, t + 60_000_000L), "late 13");
        require(lateAdaptive.next(t + 60_000_000L) == ReadyKind.PACKET, "late ready 11");
        lateAdaptive.consumeExpected();
        require(!lateAdaptive.offer(10, true, 0F, 0F, 0F, P, 0, P.length, t + 61_000_000L),
            "late old packet rejected");
        require(lateAdaptive.targetDelayFrames() == 3, "late packet must raise target");

        OpusJitterBuffer fixedFour = new OpusJitterBuffer(8, 4, 40_000_000L);
        require(fixedFour.offer(7, true, 0F, 0F, 0F, P, 0, P.length, t), "fixed4 offer");
        require(fixedFour.next(t + 79_000_000L) == ReadyKind.NONE, "fixed4 flush too early");
        require(fixedFour.next(t + 81_000_000L) == ReadyKind.PACKET, "fixed4 flush at 80ms");

        System.out.println("jitter-buffer-smoke-ok");
    }
}
