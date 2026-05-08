package dev.jm.proxychat.voice.common.codec;

import dev.jm.proxychat.config.ProxyChatConfig;

public final class OpusCodecFactory {

    private OpusCodecFactory() {}

    public static OpusEncoder createEncoder() {
        // Native opus4j slot: try native first, fall back to Concentus. For now, Concentus only.
        return new ConcentusOpusEncoder(ProxyChatConfig.opusBitrate());
    }

    public static OpusDecoder createDecoder() {
        return new ConcentusOpusDecoder();
    }
}
