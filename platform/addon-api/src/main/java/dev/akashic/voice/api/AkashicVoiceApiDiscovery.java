package dev.akashic.voice.api;

import java.util.Optional;

public final class AkashicVoiceApiDiscovery {
    public static final String GATEWAY_PLUGIN_ID = "akashicvoicegateway";

    private AkashicVoiceApiDiscovery() {}

    public static Optional<AkashicVoiceApi> fromPluginInstance(Object pluginInstance) {
        if (!(pluginInstance instanceof AkashicVoiceApiProvider provider)) return Optional.empty();
        return Optional.ofNullable(provider.akashicVoiceApi());
    }

    public static AkashicVoiceApi requireFromPluginInstance(Object pluginInstance) {
        return fromPluginInstance(pluginInstance)
            .orElseThrow(() -> new IllegalStateException("Akashic Voice addon API is unavailable"));
    }
}
