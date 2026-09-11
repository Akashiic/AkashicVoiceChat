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

        System.out.println("jitter-buffer-smoke-ok");
    }
}
