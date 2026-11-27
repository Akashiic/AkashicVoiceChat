# Akashic Voice Addon API v1

The server-side addon API is intentionally low frequency. It exposes authenticated session snapshots,
Gateway moderation state, session lifecycle events and moderation-change events. It does **not** expose
bootstrap secrets, sockets, transport crypto, raw Opus buffers or per-frame callbacks.

Artifact: `dev.akashic.voice:addon-api:0.1.0-SNAPSHOT` from `platform/addon-api`.
Current host: Akashic Voice Gateway on Velocity 4.0.0.
API version: `AkashicVoiceApi.API_VERSION == 1`.

An addon should declare a required Velocity dependency on plugin id `akashicvoicegateway`, compile against
the addon-api artifact without shading/relocating its API package, and discover the loaded plugin instance:

```java
PluginContainer container = proxy.getPluginManager()
    .getPlugin(AkashicVoiceApiDiscovery.GATEWAY_PLUGIN_ID)
    .orElseThrow();
Object gateway = container.getInstance().orElseThrow();
AkashicVoiceApi voice = AkashicVoiceApiDiscovery.requireFromPluginInstance(gateway);
```

`@Dependency(id = "akashicvoicegateway")` establishes load ordering on Velocity.
Velocity 4.0.0's current plugin loader resolves missing classes from other loaded plugin classloaders; addons
must therefore keep addon-api compile-only and revalidate this integration if the proxy version is changed.

`players()` returns immutable snapshots of currently authenticated Gateway voice sessions. `player(UUID)`
and `isGloballyMuted(UUID)` are read-only queries. `listen(...)` receives only session and moderation events.
Listener registrations are idempotently closeable; a failing addon listener is isolated from other listeners.
The API is deactivated and clears retained listeners/sessions when Gateway initialization fails or shuts down.

API v1 deliberately omits raw frame callbacks and arbitrary media injection. Cross-server radio/media injection
requires a separate authority/backpressure contract and should be added as a future compatible extension rather
than making the existing 50 Hz media path invoke arbitrary third-party code.

## Client-side hooks (Forge 1.7.10 mod) — `AkashicVoiceClientHooks`, API version 1

Contributed in PR #19 (external contributor), hardened afterwards. These hooks live in the Forge mod
(`dev.akashic.voice.api`, Java 8) and are separate from the Gateway API above. Unlike the Gateway API they
*do* run per frame (about 50 Hz) on the microphone capture thread, so the rules below are strict.

| Entry point | Purpose |
|---|---|
| `addMicFrameListener(ClientMicFrameListener)` | Receives a defensive copy of each processed 20 ms mono S16 frame (960 samples, 48 kHz), its RMS level and whether it will be transmitted |
| `addTransmissionFilter(ClientMicTransmissionFilter)` | Returning `false` suppresses transmission of the current frame (for example a roleplay "silenced" state) |
| `isMuted()`, `isDeafened()`, `isPttEngaged()`, `lastTransmitMillis()`, `isPlayerSpeaking(UUID)`, `computeRms(short[])` | Read-only state queries |

Both `add…` methods return an idempotently closeable handle.

Contract:

- **Privacy.** Frames reach listeners only while the player's own controls authorize transmission: not muted
  and, in push-to-talk mode, the key held. Audio captured while muted or with push-to-talk released is never
  handed to addons. In voice-activation mode, frames below the threshold are delivered with
  `transmitting == false`.
- **Non-blocking.** Callbacks run on the capture thread; copy or enqueue work to your own thread.
- **Failure isolation.** `RuntimeException` and `LinkageError` thrown by a callback are logged (throttled) and
  ignored; a failing filter fails *open* so a broken addon cannot permanently mute the player.
  `VirtualMachineError`s are not swallowed.
- **No injection.** The hooks cannot inject or replace audio and expose no transport, session or crypto state.

Evidence: AUTOMATED — `tools/qa/ClientAddonHooksSmoke.java` (privacy gate, isolation, defensive copy,
registration lifecycle). No addon has been exercised in a live client.
