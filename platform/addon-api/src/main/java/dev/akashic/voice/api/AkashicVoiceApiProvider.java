package dev.akashic.voice.api;

/** Implemented by a host plugin that exposes an Akashic Voice addon API instance. */
public interface AkashicVoiceApiProvider {
    AkashicVoiceApi akashicVoiceApi();
}
