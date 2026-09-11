import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;

import dev.akashic.voice.voice.common.codec.AudioFormatSpec;
import dev.akashic.voice.voice.common.codec.ConcentusOpusDecoder;
import dev.akashic.voice.voice.common.codec.ConcentusOpusEncoder;
import dev.akashic.voice.voice.common.crypto.SessionCrypto;
import dev.akashic.voice.voice.common.crypto.SessionKeys;

public final class PerformanceProbe {
    private static final int OPUS_WARMUP = 500;
    private static final int OPUS_ITERS = 3000;
    private static final int CRYPTO_WARMUP = 5000;
    private static final int CRYPTO_ITERS = 50000;
    private static final int FANOUT_FRAMES = 3000;

    private static final com.sun.management.ThreadMXBean ALLOC = allocationBean();

    public static void main(String[] args) throws Exception {
        System.out.println("runtime=" + System.getProperty("java.version")
            + " vm=" + System.getProperty("java.vm.name"));
        probeOpusEncode();
        probeOpusDecode();
        probeCryptoRoundTrip();
        probeServerFanout(1);
        probeServerFanout(20);
        probeServerFanout(50);
        probeServerFanout(100);
    }

    private static void probeOpusEncode() {
        short[] pcm = speechLikePcm();
        byte[] encoded = new byte[AudioFormatSpec.MAX_OPUS_PAYLOAD];
        ConcentusOpusEncoder encoder = new ConcentusOpusEncoder(48000);
        for (int i = 0; i < OPUS_WARMUP; i++) encoder.encode(pcm, encoded);

        long beforeAlloc = allocatedBytes();
        long start = System.nanoTime();
        int totalBytes = 0;
        for (int i = 0; i < OPUS_ITERS; i++) totalBytes += encoder.encode(pcm, encoded);
        long elapsed = System.nanoTime() - start;
        long allocated = allocatedDelta(beforeAlloc);
        encoder.close();

        double seconds = elapsed / 1_000_000_000.0;
        double fps = OPUS_ITERS / seconds;
        double realtime = (OPUS_ITERS * 0.020) / seconds;
        metric("opus_encode", OPUS_ITERS, elapsed, allocated,
            "fps=" + round(fps) + " realtime_x=" + round(realtime)
                + " avg_payload=" + (totalBytes / OPUS_ITERS));
    }

    private static void probeOpusDecode() {
        short[] pcm = speechLikePcm();
        byte[] encoded = new byte[AudioFormatSpec.MAX_OPUS_PAYLOAD];
        short[] decoded = new short[AudioFormatSpec.FRAME_SAMPLES];
        ConcentusOpusEncoder encoder = new ConcentusOpusEncoder(48000);
        int encodedBytes = encoder.encode(pcm, encoded);
        encoder.close();
        ConcentusOpusDecoder decoder = new ConcentusOpusDecoder();
        for (int i = 0; i < OPUS_WARMUP; i++) decoder.decode(encoded, encodedBytes, decoded);

        long beforeAlloc = allocatedBytes();
        long start = System.nanoTime();
        for (int i = 0; i < OPUS_ITERS; i++) decoder.decode(encoded, encodedBytes, decoded);
        long elapsed = System.nanoTime() - start;
        long allocated = allocatedDelta(beforeAlloc);
        decoder.close();

        double seconds = elapsed / 1_000_000_000.0;
        double fps = OPUS_ITERS / seconds;
        double realtime = (OPUS_ITERS * 0.020) / seconds;
        metric("opus_decode", OPUS_ITERS, elapsed, allocated,
            "fps=" + round(fps) + " realtime_x=" + round(realtime));
    }

    private static void probeCryptoRoundTrip() {
        SessionKeys keys = fixedKeys(1);
        SessionCrypto client = SessionCrypto.forClient(keys);
        SessionCrypto server = SessionCrypto.forServer(keys);
        keys.close();
        ByteBuffer plain = ByteBuffer.allocate(512);
        ByteBuffer wire = ByteBuffer.allocate(768);
        ByteBuffer opened = ByteBuffer.allocate(512);
        for (int i = 0; i < CRYPTO_WARMUP; i++) cryptoRoundTrip(client, server, plain, wire, opened);

        long beforeAlloc = allocatedBytes();
        long start = System.nanoTime();
        for (int i = 0; i < CRYPTO_ITERS; i++) cryptoRoundTrip(client, server, plain, wire, opened);
        long elapsed = System.nanoTime() - start;
        long allocated = allocatedDelta(beforeAlloc);
        client.close();
        server.close();

        double seconds = elapsed / 1_000_000_000.0;
        double ops = CRYPTO_ITERS / seconds;
        metric("aes_gcm_roundtrip", CRYPTO_ITERS, elapsed, allocated,
            "roundtrips_s=" + round(ops));
    }

    private static void probeServerFanout(int fanout) {
        SessionCrypto[] sessions = new SessionCrypto[fanout];
        for (int i = 0; i < fanout; i++) {
            SessionKeys keys = fixedKeys(10 + i);
            sessions[i] = SessionCrypto.forServer(keys);
            keys.close();
        }
        ByteBuffer plain = ByteBuffer.allocate(512);
        ByteBuffer wire = ByteBuffer.allocate(768);
        fillPlain(plain);
        for (int frame = 0; frame < 200; frame++) sealFanout(sessions, plain, wire);
        long beforeAlloc = allocatedBytes();
        long start = System.nanoTime();
        for (int frame = 0; frame < FANOUT_FRAMES; frame++) sealFanout(sessions, plain, wire);
        long elapsed = System.nanoTime() - start;
        long allocated = allocatedDelta(beforeAlloc);
        for (SessionCrypto session : sessions) session.close();

        double seconds = elapsed / 1_000_000_000.0;
        double sourceFps = FANOUT_FRAMES / seconds;
        double sealsPerSecond = (FANOUT_FRAMES * fanout) / seconds;
        metric("server_fanout_" + fanout, FANOUT_FRAMES, elapsed, allocated,
            "source_fps=" + round(sourceFps) + " seals_s=" + round(sealsPerSecond));
    }

    private static void cryptoRoundTrip(SessionCrypto client, SessionCrypto server,
        ByteBuffer plain, ByteBuffer wire, ByteBuffer opened) {
        fillPlain(plain);
        if (!client.seal(plain, wire)) throw new AssertionError("seal failed");
        ByteBuffer view = ByteBuffer.wrap(wire.array(), 0, wire.position());
        if (!server.open(view, opened)) throw new AssertionError("open failed");
    }

    private static void sealFanout(SessionCrypto[] sessions, ByteBuffer plain, ByteBuffer wire) {
        for (SessionCrypto session : sessions) {
            plain.position(256);
            if (!session.seal(plain, wire)) throw new AssertionError("fanout seal failed");
        }
    }
    private static void fillPlain(ByteBuffer plain) {
        plain.clear();
        for (int i = 0; i < 256; i++) plain.put((byte) (i * 31 + 7));
    }

    private static short[] speechLikePcm() {
        short[] pcm = new short[AudioFormatSpec.FRAME_SAMPLES];
        long state = 0x1234ABCDL;
        for (int i = 0; i < pcm.length; i++) {
            double t = i / (double) AudioFormatSpec.SAMPLE_RATE;
            double signal = Math.sin(2.0 * Math.PI * 180.0 * t) * 6200.0
                + Math.sin(2.0 * Math.PI * 420.0 * t) * 2400.0;
            state = state * 1664525L + 1013904223L;
            signal += (((state >>> 16) & 0xFF) - 128) * 9.0;
            pcm[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(signal)));
        }
        return pcm;
    }

    private static SessionKeys fixedKeys(int seed) {
        byte[] master = new byte[SessionKeys.MASTER_SECRET_BYTES];
        byte[] cn = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        byte[] sn = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        for (int i = 0; i < master.length; i++) master[i] = (byte) (seed * 17 + i);
        for (int i = 0; i < cn.length; i++) cn[i] = (byte) (seed * 29 + i);
        for (int i = 0; i < sn.length; i++) sn[i] = (byte) (seed * 43 + i);
        return SessionKeys.derive(master, cn, sn);
    }
    private static com.sun.management.ThreadMXBean allocationBean() {
        try {
            java.lang.management.ThreadMXBean base = ManagementFactory.getThreadMXBean();
            if (!(base instanceof com.sun.management.ThreadMXBean)) return null;
            com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) base;
            if (bean.isThreadAllocatedMemorySupported() && !bean.isThreadAllocatedMemoryEnabled()) {
                bean.setThreadAllocatedMemoryEnabled(true);
            }
            return bean.isThreadAllocatedMemoryEnabled() ? bean : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static long allocatedBytes() {
        if (ALLOC == null) return -1L;
        return ALLOC.getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    private static long allocatedDelta(long before) {
        if (before < 0L) return -1L;
        long after = allocatedBytes();
        return after >= before ? after - before : -1L;
    }
    private static void metric(String name, int operations, long elapsedNanos, long allocated, String extra) {
        double nsPerOp = elapsedNanos / (double) operations;
        String allocation = allocated >= 0L
            ? " alloc_B_op=" + round(allocated / (double) operations)
            : " alloc_B_op=unsupported";
        System.out.println(name + " ns_op=" + round(nsPerOp) + allocation + " " + extra);
    }

    private static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}

// Fan-out scaling is intentionally kept in the same probe so all measurements use
// identical SessionCrypto/JVM conditions. Future revisions should compare medians,
// not single-run timings, because this is a desktop development host.
