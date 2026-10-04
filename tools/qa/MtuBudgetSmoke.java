package dev.akashic.voice.voice.common;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

import com.plasmoverse.opus.OpusMode;
import dev.akashic.voice.voice.common.codec.AudioFormatSpec;
import dev.akashic.voice.voice.common.codec.ConcentusOpusDecoder;
import dev.akashic.voice.voice.common.codec.ConcentusOpusEncoder;
import dev.akashic.voice.voice.common.codec.NativeOpusDecoder;
import dev.akashic.voice.voice.common.codec.NativeOpusEncoder;
import dev.akashic.voice.voice.common.codec.OpusDecoder;
import dev.akashic.voice.voice.common.codec.OpusEncoder;
import dev.akashic.voice.voice.common.crypto.SessionCrypto;
import dev.akashic.voice.voice.common.crypto.SessionKeys;

public final class MtuBudgetSmoke {
    interface Scenario { void run() throws Exception; }
    static int passed;
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void test(String name, Scenario scenario) throws Exception {
        scenario.run(); passed++; System.out.println("PASS " + name);
    }
    public static void main(String[] args) throws Exception {
        test("wire_budget_constants", MtuBudgetSmoke::constants);
        test("mic_budget", MtuBudgetSmoke::micBudget);
        test("v1_budget", MtuBudgetSmoke::v1Budget);
        test("v2_budget", MtuBudgetSmoke::v2Budget);
        test("v3_exactly_1200", MtuBudgetSmoke::v3Exact);
        test("oversize_media_rejected", MtuBudgetSmoke::oversizeMedia);
        test("generic_crypto_allows_larger_non_udp_frames", MtuBudgetSmoke::sealBoundary);
        test("udp_length_predicate_rejects_1201", MtuBudgetSmoke::openBoundary);
        test("native_48k_baseline_packets_unchanged", MtuBudgetSmoke::nativeCompatibility);
        test("java_48k_baseline_packets_unchanged", MtuBudgetSmoke::javaCompatibility);
        test("native_128k_stress_stays_bounded", () -> stress("native"));
        test("java_128k_stress_stays_bounded", () -> stress("java"));
        System.out.println("MTU_BUDGET_OK scenarios=" + passed + " maxOpus=" + AudioFormatSpec.MAX_OPUS_PAYLOAD);
    }

    static void constants() {
        check(WireProtocol.MAX_NORMAL_DATAGRAM_BYTES == 1200, "transport target changed");
        check(WireProtocol.ENCRYPTED_OVERHEAD_BYTES == 28, "legacy AEAD overhead changed");
        check(WireProtocol.MAX_ENCRYPTED_PLAINTEXT_BYTES == 1172, "legacy plaintext budget wrong");
        check(WireProtocol.SESSION_ENCRYPTED_OVERHEAD_BYTES == 44, "session AEAD overhead wrong");
        check(WireProtocol.MAX_SESSION_ENCRYPTED_PLAINTEXT_BYTES == 1156, "session plaintext budget wrong");
        check(AudioFormatSpec.MAX_OPUS_PAYLOAD == 1108, "Opus media budget wrong");
        check(AudioFormatSpec.FRAME_SAMPLES == 960 && AudioFormatSpec.FRAME_DURATION_MS == 20,
            "audio framing changed");
    }

    static byte[] payload() {
        byte[] payload = new byte[AudioFormatSpec.MAX_OPUS_PAYLOAD];
        Arrays.fill(payload, (byte) 11);
        return payload;
    }

    static void micBudget() {
        int datagram = WireProtocol.ENCRYPTED_OVERHEAD_BYTES + MicFramePacket.byteSize(AudioFormatSpec.MAX_OPUS_PAYLOAD);
        check(datagram == 1143 && datagram <= 1200, "mic datagram budget=" + datagram);
    }

    static void v1Budget() {
        SoundFramePacket frame = new SoundFramePacket(new UUID(1L,2L), true, 1F,2F,3F,7,payload());
        int datagram = WireProtocol.ENCRYPTED_OVERHEAD_BYTES + frame.byteSize();
        check(datagram == 1172 && datagram <= 1200, "v1 datagram budget=" + datagram);
    }
    static SoundFrameV2Packet v2(byte[] data) {
        return new SoundFrameV2Packet(new UUID(3L,4L), true, false, 1F,2F,3F,48F,9,data);
    }

    static void v2Budget() {
        int datagram = WireProtocol.ENCRYPTED_OVERHEAD_BYTES + v2(payload()).byteSize();
        check(datagram == 1176 && datagram <= 1200, "v2 datagram budget=" + datagram);
    }

    static void v3Exact() throws Exception {
        SoundFrameV3Packet packet = new SoundFrameV3Packet(1L, v2(payload()));
        check(packet.byteSize() == WireProtocol.MAX_SESSION_ENCRYPTED_PLAINTEXT_BYTES,
            "v3 session plaintext is not exact budget: " + packet.byteSize());
        ByteBuffer plain = ByteBuffer.allocate(1500);
        packet.write(plain);
        check(plain.position() == 1156, "serialized v3 size changed");
        try (CryptoPair pair = new CryptoPair(new UUID(91L, 92L))) {
            ByteBuffer wire = ByteBuffer.allocate(1500);
            check(pair.client.seal(plain, wire), "max v3 did not seal");
            check(wire.position() == 1200, "max v3 wire size=" + wire.position());
            ByteBuffer opened = ByteBuffer.allocate(1500);
            check(pair.server.open(ByteBuffer.wrap(Arrays.copyOf(wire.array(), wire.position())), opened),
                "max v3 did not open");
            check(opened.remaining() == 1156, "opened max v3 size changed");
            check(opened.get() == WirePacketType.SOUND_FRAME_V3, "wrong v3 inner type");
            check(SoundFrameV3Packet.read(opened) != null, "max v3 parser rejected legal packet");
        }
    }

    static void oversizeMedia() {
        byte[] tooLarge = new byte[AudioFormatSpec.MAX_OPUS_PAYLOAD + 1];
        try { v2(tooLarge); throw new AssertionError("oversize v2 accepted"); }
        catch (IllegalArgumentException expected) { }
        try { MicFramePacket.write(ByteBuffer.allocate(1500), 1, tooLarge, tooLarge.length);
            throw new AssertionError("oversize mic accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    static void sealBoundary() throws Exception {
        try (CryptoPair pair = new CryptoPair()) {
            ByteBuffer plain = ByteBuffer.allocate(1400);
            plain.position(1300);
            ByteBuffer wire = ByteBuffer.allocate(1500);
            check(pair.client.seal(plain, wire), "generic AEAD rejected non-UDP frame");
            check(wire.position() == 1328, "generic AEAD size changed");
            ByteBuffer opened = ByteBuffer.allocate(1400);
            check(pair.server.open(ByteBuffer.wrap(Arrays.copyOf(wire.array(), wire.position())), opened),
                "generic AEAD could not open non-UDP frame");
            check(opened.remaining() == 1300, "generic AEAD plaintext changed");
        }
    }

    static void openBoundary() {
        check(WireProtocol.isNormalDatagramLength(1), "minimum UDP datagram rejected");
        check(WireProtocol.isNormalDatagramLength(1200), "1200-byte UDP datagram rejected");
        check(!WireProtocol.isNormalDatagramLength(0), "zero-byte UDP datagram accepted");
        check(!WireProtocol.isNormalDatagramLength(1201), "1201-byte UDP datagram accepted");
    }

    static short[] pcmFrame(int index) {
        short[] pcm = new short[AudioFormatSpec.FRAME_SAMPLES];
        Random random = new Random(0xA11CE + index * 31L);
        for (int i = 0; i < pcm.length; i++) {
            if ((index & 3) == 0) pcm[i] = (short) (12000 * Math.sin(2.0 * Math.PI * 330.0 * i / 48000.0));
            else if ((index & 3) == 1) pcm[i] = (short) ((i & 1) == 0 ? 30000 : -30000);
            else if ((index & 3) == 2) pcm[i] = (short) random.nextInt();
            else pcm[i] = 0;
        }
        return pcm;
    }
    static void nativeCompatibility() throws Exception {
        NativeOpusEncoder capped = new NativeOpusEncoder(48000);
        com.plasmoverse.opus.OpusEncoder baseline = com.plasmoverse.opus.OpusEncoder
            .create(AudioFormatSpec.SAMPLE_RATE, false, 1275, OpusMode.VOIP);
        baseline.setBitrate(48000);
        try {
            byte[] current = new byte[AudioFormatSpec.MAX_OPUS_PAYLOAD];
            for (int frame = 0; frame < 64; frame++) {
                short[] pcm = pcmFrame(frame);
                int count = capped.encode(pcm, current);
                byte[] previous = baseline.encode(pcm);
                check(count == previous.length, "native 48k packet length changed at frame " + frame);
                check(Arrays.equals(Arrays.copyOf(current, count), previous),
                    "native 48k packet bytes changed at frame " + frame);
            }
        } finally {
            capped.close();
            baseline.close();
        }
    }

    static void javaCompatibility() throws Exception {
        ConcentusOpusEncoder capped = new ConcentusOpusEncoder(48000);
        org.concentus.OpusEncoder baseline = new org.concentus.OpusEncoder(
            AudioFormatSpec.SAMPLE_RATE, AudioFormatSpec.CHANNELS, org.concentus.OpusApplication.OPUS_APPLICATION_VOIP);
        baseline.setBitrate(48000);
        byte[] current = new byte[AudioFormatSpec.MAX_OPUS_PAYLOAD];
        byte[] previous = new byte[1275];
        try {
            for (int frame = 0; frame < 64; frame++) {
                short[] pcm = pcmFrame(frame);
                int count = capped.encode(pcm, current);
                int oldCount = baseline.encode(pcm, 0, AudioFormatSpec.FRAME_SAMPLES, previous, 0, previous.length);
                check(count == oldCount, "java 48k packet length changed at frame " + frame);
                check(Arrays.equals(Arrays.copyOf(current, count), Arrays.copyOf(previous, oldCount)),
                    "java 48k packet bytes changed at frame " + frame);
            }
        } finally { capped.close(); }
    }
    static void stress(String backend) throws Exception {
        OpusEncoder encoder = "native".equals(backend) ? new NativeOpusEncoder(128000) : new ConcentusOpusEncoder(128000);
        OpusDecoder decoder = "native".equals(backend) ? new ConcentusOpusDecoder() : new NativeOpusDecoder();
        int max = 0;
        try {
            byte[] packet = new byte[AudioFormatSpec.MAX_OPUS_PAYLOAD];
            short[] decoded = new short[AudioFormatSpec.FRAME_SAMPLES];
            for (int frame = 0; frame < 512; frame++) {
                int count = encoder.encode(pcmFrame(frame), packet);
                check(count > 0 && count <= AudioFormatSpec.MAX_OPUS_PAYLOAD,
                    backend + " produced out-of-budget packet " + count);
                max = Math.max(max, count);
                check(decoder.decode(packet, 0, count, decoded) == AudioFormatSpec.FRAME_SAMPLES,
                    backend + " packet failed cross-backend decode");
            }
        } finally {
            encoder.close();
            decoder.close();
        }
        System.out.println("MTU_STRESS backend=" + backend + " frames=512 maxPacket=" + max);
    }

    static final class CryptoPair implements AutoCloseable {
        final byte[] secret = new byte[SessionKeys.MASTER_SECRET_BYTES];
        final byte[] clientNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        final byte[] serverNonce = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        final SessionCrypto client;
        final SessionCrypto server;
        CryptoPair() { this(null); }
        CryptoPair(UUID sessionId) {
            Arrays.fill(secret, (byte) 17);
            Arrays.fill(clientNonce, (byte) 31);
            Arrays.fill(serverNonce, (byte) 47);
            SessionKeys keys = sessionId == null ? SessionKeys.derive(secret, clientNonce, serverNonce)
                : SessionKeys.derive(secret, clientNonce, serverNonce, sessionId);
            try {
                client = sessionId == null ? SessionCrypto.forClient(keys) : SessionCrypto.forClient(keys, sessionId);
                server = sessionId == null ? SessionCrypto.forServer(keys) : SessionCrypto.forServer(keys, sessionId);
            } finally { keys.close(); }
        }
        public void close() {
            client.close(); server.close();
            Arrays.fill(secret, (byte) 0); Arrays.fill(clientNonce, (byte) 0); Arrays.fill(serverNonce, (byte) 0);
        }
    }
}
