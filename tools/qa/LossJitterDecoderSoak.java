package dev.akashic.voice.voice.client.jitter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import dev.akashic.voice.voice.common.SoundFrameV2Packet;
import dev.akashic.voice.voice.common.codec.AudioFormatSpec;
import dev.akashic.voice.voice.common.codec.ConcentusOpusDecoder;
import dev.akashic.voice.voice.common.codec.ConcentusOpusEncoder;
import dev.akashic.voice.voice.common.codec.NativeOpusDecoder;
import dev.akashic.voice.voice.common.codec.NativeOpusEncoder;
import dev.akashic.voice.voice.common.codec.OpusDecoder;
import dev.akashic.voice.voice.common.codec.OpusEncoder;

/** Decoder/jitter/PLC soak. It opens no Minecraft window and no audio device. */
public final class LossJitterDecoderSoak {
    private static final int FRAMES = 4000;
    private static final UUID SENDER = new UUID(0x12345678L, 0x90abcdefL);
    private static final long[] ARRIVAL_DELTAS = {
        20_000_000L, 25_000_000L, 15_000_000L, 32_000_000L,
        8_000_000L, 20_000_000L, 21_000_000L, 19_000_000L
    };

    private LossJitterDecoderSoak() {}

    public static void main(String[] args) {
        run("native-native", new NativeOpusEncoder(48_000), new NativeOpusDecoder());
        run("native-java", new NativeOpusEncoder(48_000), new ConcentusOpusDecoder());
        run("java-native", new ConcentusOpusEncoder(48_000), new NativeOpusDecoder());
        run("java-java", new ConcentusOpusEncoder(48_000), new ConcentusOpusDecoder());
        System.out.println("LOSS_JITTER_DECODER_SOAK_OK matrices=4 frames=" + (FRAMES * 4));
    }

    private static void run(String name, OpusEncoder encoder, OpusDecoder decoder) {
        List<Packet> packets = new ArrayList<Packet>(FRAMES);
        short[] pcm = new short[AudioFormatSpec.FRAME_SAMPLES];
        byte[] encoded = new byte[AudioFormatSpec.MAX_OPUS_PAYLOAD];
        try {
            for (int sequence = 0; sequence < FRAMES; sequence++) {
                fillPcm(pcm, sequence);
                int length = encoder.encode(pcm, encoded);
                check(length > 0 && length <= encoded.length, name + " invalid encoded length");
                packets.add(new Packet(sequence, Arrays.copyOf(encoded, length)));
            }
        } finally {
            encoder.close();
        }

        CountingDecoder counting = new CountingDecoder(decoder);
        DecoderStream stream = new DecoderStream(SENDER, counting, new OpusJitterBuffer());
        int dropped = 0;
        int reordered = 0;
        long now = 1_000_000_000L;
        try {
            for (int sequence = 0; sequence < FRAMES;) {
                if (drop(sequence)) {
                    dropped++;
                    sequence++;
                    continue;
                }
                if (shouldSwap(sequence) && sequence + 1 < FRAMES && !drop(sequence + 1)) {
                    now += ARRIVAL_DELTAS[sequence & (ARRIVAL_DELTAS.length - 1)];
                    offer(stream, packets.get(sequence + 1), now);
                    now += ARRIVAL_DELTAS[(sequence + 1) & (ARRIVAL_DELTAS.length - 1)];
                    offer(stream, packets.get(sequence), now);
                    reordered++;
                    sequence += 2;
                    continue;
                }
                now += ARRIVAL_DELTAS[sequence & (ARRIVAL_DELTAS.length - 1)];
                offer(stream, packets.get(sequence), now);
                sequence++;
            }
            for (int i = 0; i < 4; i++) stream.drain(now + 1_000_000_000L, null);

            check(stream.decodeErrorCount() == 0L, name + " decoder errors=" + stream.decodeErrorCount());
            check(stream.resyncCount() == 0L, name + " jitter resync=" + stream.resyncCount());
            check(stream.lateCount() == 0L, name + " late packets=" + stream.lateCount());
            check(stream.duplicateCount() == 0L, name + " duplicates=" + stream.duplicateCount());
            check(stream.missingCount() == dropped,
                name + " PLC mismatch expected=" + dropped + " actual=" + stream.missingCount());
            check(counting.missingCalls == dropped, name + " PLC decoder calls=" + counting.missingCalls);
            check(counting.packetCalls == FRAMES - dropped, name + " packet decoder calls=" + counting.packetCalls);
            System.out.println("PASS " + name + " dropped=" + dropped + " reorderedPairs=" + reordered);
        } finally {
            stream.close();
        }
    }

    private static void offer(DecoderStream stream, Packet packet, long now) {
        SoundFrameV2Packet frame = new SoundFrameV2Packet(
            SENDER, true, false, 12F, 64F, -9F, 24F, packet.sequence, packet.opus);
        stream.offer(frame, now, null);
    }

    private static boolean drop(int sequence) {
        if (sequence >= FRAMES - 16) return false;
        int burst = sequence % 401;
        return sequence % 47 == 13 || (burst >= 200 && burst <= 202);
    }

    private static boolean shouldSwap(int sequence) {
        return sequence < FRAMES - 16 && sequence % 31 == 8;
    }

    private static void fillPcm(short[] pcm, int frame) {
        double frequency = 220.0 + (frame % 9) * 37.0;
        double phaseBase = frame * AudioFormatSpec.FRAME_SAMPLES;
        for (int i = 0; i < pcm.length; i++) {
            double phase = 2.0 * Math.PI * frequency * (phaseBase + i) / AudioFormatSpec.SAMPLE_RATE;
            pcm[i] = (short) (Math.sin(phase) * 9000.0);
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static final class CountingDecoder implements OpusDecoder {
        private final OpusDecoder delegate;
        int packetCalls;
        int missingCalls;
        CountingDecoder(OpusDecoder delegate) { this.delegate = delegate; }
        public int decode(byte[] opus, int offset, int length, short[] pcm) {
            packetCalls++;
            return delegate.decode(opus, offset, length, pcm);
        }
        public int decodeMissing(short[] pcm) {
            missingCalls++;
            return delegate.decodeMissing(pcm);
        }
        public void close() { delegate.close(); }
    }

    private static final class Packet {
        final int sequence;
        final byte[] opus;
        Packet(int sequence, byte[] opus) { this.sequence = sequence; this.opus = opus; }
    }
}
