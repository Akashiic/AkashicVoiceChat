import dev.akashic.voice.config.AkashicVoiceConfig;
import dev.akashic.voice.voice.common.codec.OpusCodecFactory;
import dev.akashic.voice.voice.common.codec.OpusEncoder;

public final class CodecBackendStrictSmoke {
    public static void main(String[] args) {
        AkashicVoiceConfig.setOpusBackend("auto");
        OpusCodecFactory.resetBackendProbeForTests();
        OpusEncoder fallback = OpusCodecFactory.createEncoder();
        fallback.close();
        if (!OpusCodecFactory.backendStatus().contains("encoder=java-concentus")) {
            throw new AssertionError("auto mode did not fall back before strict transition");
        }

        AkashicVoiceConfig.setOpusBackend("native");
        try {
            OpusCodecFactory.createEncoder();
            throw new AssertionError("native-required mode unexpectedly fell back");
        } catch (IllegalStateException expected) {
            String message = expected.getMessage();
            if (message == null || !message.contains("required but unavailable")) throw expected;
        }
        System.out.println("codec-backend-strict-smoke-ok");
    }
}
