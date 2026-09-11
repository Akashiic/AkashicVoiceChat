import java.lang.management.ManagementFactory;

import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.common.codec.AudioFormatSpec;
import dev.akashic.voice.voice.common.codec.OpusCodecFactory;
import dev.akashic.voice.voice.common.codec.OpusDecoder;
import dev.akashic.voice.voice.common.codec.OpusEncoder;

public final class CodecBackendPerformanceProbe {
    private static final int WARMUP = 1000;
    private static final int ITERS = 10000;
    private static final com.sun.management.ThreadMXBean ALLOC = allocationBean();

    public static void main(String[] args) {
        run("auto");
        run("java");
    }

    private static void run(String mode) {
        AkashicVoiceConfig.setOpusBackend(mode);
        OpusCodecFactory.resetBackendProbeForTests();
        AkashicVoiceConfig.setOpusBackend(mode);
        short[] pcm = speechLikePcm();
        byte[] encoded = new byte[AudioFormatSpec.MAX_OPUS_PAYLOAD];
        short[] decoded = new short[AudioFormatSpec.FRAME_SAMPLES];
        OpusEncoder encoder = OpusCodecFactory.createEncoder();
        OpusDecoder decoder = OpusCodecFactory.createDecoder();
        for (int i = 0; i < WARMUP; i++) encoder.encode(pcm, encoded);
        long before = allocated();
        long start = System.nanoTime();
        int lastLen = 0;
        for (int i = 0; i < ITERS; i++) lastLen = encoder.encode(pcm, encoded);
        long encodeNs = System.nanoTime() - start;
        long encodeAlloc = allocated() - before;

        for (int i = 0; i < WARMUP; i++) decoder.decode(encoded, 0, lastLen, decoded);
        before = allocated();
        start = System.nanoTime();
        for (int i = 0; i < ITERS; i++) decoder.decode(encoded, 0, lastLen, decoded);
        long decodeNs = System.nanoTime() - start;
        long decodeAlloc = allocated() - before;

        String status = OpusCodecFactory.backendStatus();
        System.out.printf(
            "codec_backend mode=%s status=%s encode_ns=%.2f encode_alloc_B=%.2f decode_ns=%.2f decode_alloc_B=%.2f payload=%d%n",
            mode, status, encodeNs / (double) ITERS, encodeAlloc / (double) ITERS,
            decodeNs / (double) ITERS, decodeAlloc / (double) ITERS, lastLen);
        encoder.close();
        decoder.close();
    }

    private static short[] speechLikePcm() {
        short[] pcm = new short[AudioFormatSpec.FRAME_SAMPLES];
        for (int i = 0; i < pcm.length; i++) {
            double t = i / 48000.0;
            pcm[i] = (short) Math.round((Math.sin(2 * Math.PI * 180 * t) * 6000)
                + (Math.sin(2 * Math.PI * 730 * t) * 1800));
        }
        return pcm;
    }
    private static com.sun.management.ThreadMXBean allocationBean() {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean)) return null;
        com.sun.management.ThreadMXBean sun = (com.sun.management.ThreadMXBean) bean;
        if (sun.isThreadAllocatedMemorySupported() && !sun.isThreadAllocatedMemoryEnabled()) {
            sun.setThreadAllocatedMemoryEnabled(true);
        }
        return sun.isThreadAllocatedMemorySupported() ? sun : null;
    }

    private static long allocated() {
        return ALLOC == null ? 0L : ALLOC.getThreadAllocatedBytes(Thread.currentThread().getId());
    }
}
