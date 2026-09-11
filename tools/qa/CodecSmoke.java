import dev.akashic.voice.voice.common.codec.AudioFormatSpec;
import dev.akashic.voice.voice.common.codec.ConcentusOpusDecoder;
import dev.akashic.voice.voice.common.codec.ConcentusOpusEncoder;

public final class CodecSmoke {
    public static void main(String[] args) {
        short[] pcm = new short[AudioFormatSpec.FRAME_SAMPLES];
        for (int i = 0; i < pcm.length; i++) {
            double phase = 2.0 * Math.PI * 440.0 * i / AudioFormatSpec.SAMPLE_RATE;
            pcm[i] = (short) Math.round(Math.sin(phase) * 8000.0);
        }

        byte[] encoded = new byte[AudioFormatSpec.MAX_OPUS_PAYLOAD];
        short[] decoded = new short[AudioFormatSpec.FRAME_SAMPLES];
        ConcentusOpusEncoder encoder = new ConcentusOpusEncoder(48000);
        ConcentusOpusDecoder decoder = new ConcentusOpusDecoder();
        int encodedBytes = encoder.encode(pcm, encoded);
        int decodedSamples = decoder.decode(encoded, encodedBytes, decoded);
        encoder.close();
        decoder.close();

        if (encodedBytes <= 0 || encodedBytes > encoded.length) throw new AssertionError("bad encoded length");
        if (decodedSamples != AudioFormatSpec.FRAME_SAMPLES) throw new AssertionError("bad decoded sample count");
        System.out.println("codec-smoke-ok encodedBytes=" + encodedBytes + " decodedSamples=" + decodedSamples);
    }
}
