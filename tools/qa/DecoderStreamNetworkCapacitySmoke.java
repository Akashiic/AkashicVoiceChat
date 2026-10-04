package dev.akashic.voice.voice.client.playback;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.client.VoiceClient;
import dev.akashic.voice.voice.client.jitter.DecoderStream;
import dev.akashic.voice.voice.client.playback.EpochClientFlowSmoke.Fixture;
import dev.akashic.voice.voice.common.SoundFrameV2Packet;
import dev.akashic.voice.voice.common.SoundFrameV3Packet;

/** Encrypted VoiceClient path at decoder capacity. Physical output remains a test sink. */
public final class DecoderStreamNetworkCapacitySmoke {
    private DecoderStreamNetworkCapacitySmoke() {}

    public static void main(String[] args) throws Exception {
        String backend = AkashicVoiceConfig.opusBackend();
        AkashicVoiceConfig.setOpusBackend("java");
        try (Fixture f = new Fixture()) {
            f.offer();
            SoundFrameV2Packet template = f.frame(new UUID(7L, 7L), 0);
            byte[] opus = Arrays.copyOfRange(template.opusData, template.opusOffset,
                template.opusOffset + template.opusLength);
            UUID first = null;
            for (int i = 0; i < 256; i++) {
                UUID sender = new UUID(20L, i + 1L);
                if (i == 0) first = sender;
                f.deliver(frame(f, sender, 1L, 0, opus));
            }
            check(f.streams().size() == 256, "encrypted path did not fill cap");
            check(f.client.decoderStreamCapacityDropCount() == 0L, "false capacity drops");

            UUID overflow = new UUID(21L, 1L);
            f.deliver(frame(f, overflow, 1L, 0, opus));
            check(f.streams().size() == 256, "overflow encrypted sender allocated decoder");
            check(!f.streams().containsKey(overflow), "overflow sender installed decoder");
            check(f.client.decoderStreamCapacityDropCount() == 1L, "overflow not counted");

            f.deliver(f.sealReset(first, 1L));
            check(f.streams().size() == 255 && !f.streams().containsKey(first), "reset did not release slot");
            f.deliver(frame(f, overflow, 1L, 1, opus));
            check(f.streams().size() == 256 && f.streams().containsKey(overflow),
                "same epoch did not recover after slot release");
            check(f.client.decoderStreamCapacityDropCount() == 1L, "recovery counted as another drop");
        } finally {
            AkashicVoiceConfig.setOpusBackend(backend);
        }
        System.out.println("DECODER_STREAM_NETWORK_CAPACITY_OK cap=256");
    }

    private static byte[] frame(Fixture f, UUID sender, long epoch, int sequence, byte[] opus) {
        SoundFrameV2Packet inner = new SoundFrameV2Packet(
            sender, true, false, 1F, 2F, 3F, 16F, sequence, opus);
        ByteBuffer plain = ByteBuffer.allocate(1500);
        new SoundFrameV3Packet(epoch, inner).write(plain);
        return f.seal(plain);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
