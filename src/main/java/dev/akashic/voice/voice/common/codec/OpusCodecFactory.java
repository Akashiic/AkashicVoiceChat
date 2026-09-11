package dev.akashic.voice.voice.common.codec;

import dev.akashic.voice.config.AkashicVoiceConfig;

public final class OpusCodecFactory {

    private OpusCodecFactory() {}

    public static OpusEncoder createEncoder() {
        // Native opus4j slot: try native first, fall back to Concentus. For now, Concentus only.
        return new ConcentusOpusEncoder(AkashicVoiceConfig.opusBitrate());
    }

    public static OpusDecoder createDecoder() {
        return new ConcentusOpusDecoder();
    }
}
