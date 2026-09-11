# Akashic Voice — Architecture Contract

Status: design baseline for the 1.7.10 client/server and future cross-version gateway.

## Non-negotiable runtime contract

- Minecraft client: 1.7.10 / Forge 10.13.4.1614.
- Client bytecode: Java 8 (`class` major version 52).
- Client source baseline: Java 8 syntax; no Jabel/JVM-downgraded APIs required.
- Client renderer/audio integration: LWJGL2-compatible surface; lwjgl3ify is supported, never required.
- Dedicated Forbidden backend: Crucible 1.7.10, Java 21 permitted and expected in production.
- Voice codec: Opus, 48 kHz, mono, 20 ms frames (960 samples/frame).
- Default codec implementation: pure-Java Concentus compiled for Java 8.
- Network media transport: UDP; Minecraft TCP/plugin channel is bootstrap/control only.
- Future Android target: PojavLauncher legacy + Amethyst-compatible architecture.

## Layering

`client-1.7.10` owns Minecraft UI, keybinds, capture selection and OpenAL playback.
`protocol-core` owns wire format, crypto, sequence/replay logic and capabilities.
`codec-core` owns Opus interfaces and Concentus implementation.
`server-1.7.10` owns authoritative positions, sessions and local proximity routing.
`gateway` (Java 21) owns cross-server routing, policy, metrics and protocol adapters.
`bridge-svc` (Java 21) owns Simple Voice Chat interoperability through its public API.

The Java 8 client must never depend on gateway, Paper, Velocity, Simple Voice Chat,
GTNHLib, Angelica, Hodgepodge, UniMixins, GeckoLib or other modpack-specific libraries.

## Hot-path rules

- No blocking work on the Minecraft client/render thread or the server tick thread.
- No per-frame logger calls in normal operation.
- Reuse capture, network and decode buffers wherever ownership permits.
- One stateful Opus decoder per remote encoded stream/speaker; never share decoder state across speakers.
- Bound every queue. Latency is more important than preserving stale voice frames.
- Keep normal UDP datagrams at or below 1200 bytes to avoid common-path IP fragmentation.
- Server routing forwards encoded Opus whenever formats are compatible; transcoding is a fallback, not the default.
- Positions used for proximity decisions are server-authoritative.
- Cross-backend proximity is forbidden: different servers communicate only through explicit groups/radio/channels.

## Audio pipeline

Capture -> DC/high-pass -> optional gate/denoise/AGC -> limiter -> Opus encoder -> encrypted UDP.
Encrypted UDP -> authenticate/replay check -> per-speaker jitter buffer -> Opus decoder/PLC -> OpenAL spatial playback.

The first implementation keeps DSP deliberately small and measurable. A filter is not accepted merely because
it sounds sophisticated: it must have an objective CPU budget and an A/B listening justification.

## Audio backends

Capture is behind an `AudioCapture` SPI. Desktop JavaSound is the first backend.
OpenAL capture is the preferred portable fallback if runtime support is verified.
An Android-native AudioRecord/Oboe bridge is a future fallback only if Pojav/Amethyst cannot expose a reliable Java path.

Playback is behind an `AudioOutput` SPI. LWJGL2 OpenAL is the 1.7.10 implementation.
The core protocol and codec layers must not import `javax.sound`, LWJGL, Forge or Minecraft classes.

## Session and security model

1. Minecraft control channel requests voice bootstrap data.
2. Server responds with its authoritative player UUID, protocol version/capabilities,
   voice endpoint, random session identifier and a fresh session key.
3. Client authenticates the UDP path using that server-issued identity; it never invents/recomputes the UUID.
4. Media packets carry bounded sequence numbers and are authenticated/encrypted with an AEAD construction.
5. Server keeps a replay window and endpoint binding per session.
6. Logout/reconnect invalidates the old session and key immediately.

Initial AEAD target is AES-128-GCM because it is available in Java 8 and avoids dependence on modern-JDK ciphers.
The packet header is authenticated as AAD. Nonces must be unique for each key and direction; nonce construction
is part of the protocol specification and cannot be ad-hoc random-per-frame state.

Offline-mode note: when Minecraft login/control traffic itself is not authenticated/encrypted, the bootstrap
channel cannot by itself prove server identity against an active MITM. Akashic Voice must not claim otherwise.
A future pinned server public-key/TOFU layer may strengthen this without requiring online-mode.

## Compatibility policy

- Wire versions are explicit and capability-negotiated.
- Unknown optional capabilities are ignored; unknown mandatory protocol versions are rejected cleanly.
- Bridge adapters translate semantic events (proximity/group/whisper), not implementation classes.
- Simple Voice Chat integration uses its public API/protocol contracts; ARR implementation code is not copied.
- The original ProxyChat MIT work remains attributed in NOTICE/LICENSE history where code is derived.
