package dev.akashic.voice.platform.svc;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;

public final class AkashicSvcPlugin implements VoicechatPlugin {
    private final SvcBridgeEngine engine;

    AkashicSvcPlugin(SvcBridgeEngine engine) {
        this.engine = engine;
    }

    @Override
    public String getPluginId() {
        return "akashic_voice_bridge";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class,
            event -> engine.bindVoicechat(event.getVoicechat()));
        registration.registerEvent(VoicechatServerStoppedEvent.class,
            event -> engine.unbindVoicechat(event.getVoicechat()));
    }
}
