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
