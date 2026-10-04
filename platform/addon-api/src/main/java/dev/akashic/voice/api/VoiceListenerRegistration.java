package dev.akashic.voice.api;

@FunctionalInterface
public interface VoiceListenerRegistration extends AutoCloseable {
    @Override
    void close();
}
