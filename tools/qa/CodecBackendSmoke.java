import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.common.codec.AudioFormatSpec;
import dev.akashic.voice.voice.common.codec.OpusCodecFactory;
import dev.akashic.voice.voice.common.codec.OpusDecoder;
import dev.akashic.voice.voice.common.codec.OpusEncoder;

public final class CodecBackendSmoke {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "auto";
        String expected = args.length > 1 ? args[1] : "native-libopus";
        AkashicVoiceConfig.setOpusBackend(mode);
        OpusCodecFactory.resetBackendProbeForTests();

        short[] pcm = new short[AudioFormatSpec.FRAME_SAMPLES];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) Math.round(Math.sin(2.0 * Math.PI * 330.0 * i / 48000.0) * 9000.0);
        }
        byte[] encoded = new byte[AudioFormatSpec.MAX_OPUS_PAYLOAD];
        short[] decoded = new short[AudioFormatSpec.FRAME_SAMPLES];
        OpusEncoder encoder = OpusCodecFactory.createEncoder();
        OpusDecoder decoder = OpusCodecFactory.createDecoder();
        int len = encoder.encode(pcm, encoded);
        int samples = decoder.decode(encoded, len, decoded);
        int plc = decoder.decodeMissing(decoded);
        encoder.close();
        decoder.close();

        String status = OpusCodecFactory.backendStatus();
        require(len > 0 && len <= encoded.length, "invalid encoded length");
        require(samples == AudioFormatSpec.FRAME_SAMPLES, "invalid decoded frame");
        require(plc == AudioFormatSpec.FRAME_SAMPLES, "invalid PLC frame");
        require(status.contains("encoder=" + expected), "unexpected encoder backend: " + status);
        require(status.contains("decoder=" + expected), "unexpected decoder backend: " + status);
        System.out.println("codec-backend-smoke-ok mode=" + mode + " status=" + status + " bytes=" + len);
    }
}
