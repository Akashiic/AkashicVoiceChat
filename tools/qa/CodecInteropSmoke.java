import dev.akashic.voice.voice.common.codec.AudioFormatSpec;
import dev.akashic.voice.voice.common.codec.ConcentusOpusDecoder;
import dev.akashic.voice.voice.common.codec.ConcentusOpusEncoder;
import dev.akashic.voice.voice.common.codec.NativeOpusDecoder;
import dev.akashic.voice.voice.common.codec.NativeOpusEncoder;

public final class CodecInteropSmoke {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        short[] pcm = new short[AudioFormatSpec.FRAME_SAMPLES];
        for (int i = 0; i < pcm.length; i++) {
            double t = i / (double) AudioFormatSpec.SAMPLE_RATE;
            pcm[i] = (short) Math.round(Math.sin(2.0 * Math.PI * 330.0 * t) * 9000.0);
        }
        byte[] packet = new byte[AudioFormatSpec.MAX_OPUS_PAYLOAD];
        short[] decoded = new short[AudioFormatSpec.FRAME_SAMPLES];

        NativeOpusEncoder nativeEncoder = new NativeOpusEncoder(48000);
        ConcentusOpusDecoder javaDecoder = new ConcentusOpusDecoder();
        int nativeBytes = nativeEncoder.encode(pcm, packet);
        int javaSamples = javaDecoder.decode(packet, nativeBytes, decoded);
        require(javaSamples == AudioFormatSpec.FRAME_SAMPLES, "Concentus failed to decode native Opus");
        nativeEncoder.close();
        javaDecoder.close();

        ConcentusOpusEncoder javaEncoder = new ConcentusOpusEncoder(48000);
        NativeOpusDecoder nativeDecoder = new NativeOpusDecoder();
        int javaBytes = javaEncoder.encode(pcm, packet);
        int nativeSamples = nativeDecoder.decode(packet, 0, javaBytes, decoded);
        int plcSamples = nativeDecoder.decodeMissing(decoded);
        require(nativeSamples == AudioFormatSpec.FRAME_SAMPLES, "native failed to decode Concentus Opus");
        require(plcSamples == AudioFormatSpec.FRAME_SAMPLES, "native PLC frame size");
        javaEncoder.close();
        nativeDecoder.close();

        System.out.println("codec-interop-smoke-ok nativeBytes=" + nativeBytes
            + " javaBytes=" + javaBytes + " plcSamples=" + plcSamples);
    }
}
