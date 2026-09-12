# Akashic Voice

High-quality spatial voice for Minecraft 1.7.10, engineered for legacy clients without forcing a legacy server stack.

Akashic Voice is derived from the MIT-licensed ProxyChat project and is being re-architected for the Rede Akashic / The End of The Forbidden environment. The client contract is deliberately conservative: Minecraft 1.7.10, Forge 10.13.4.1614, Java 8 bytecode and LWJGL2-compatible audio. The server may run on a modern JVM such as Java 21.

## Current engineering status

This repository is under active development. The current foundation already includes:

- 48 kHz mono Opus voice at a 48 kbit/s default bitrate, with bundled native libopus on supported desktops and pure-Java Concentus fallback;
- 20 ms / 960-sample audio frames;
- proximity-based OpenAL 3D playback;
- push-to-talk, voice activation and always-on capture modes;
- microphone selection/test UI and voice settings;
- persistent groups with distance-independent group audio;
- versioned AV UDP envelope with authenticated media endpoints;
- HKDF-SHA-256 session derivation and directional AES-128-GCM transport;
- 64-packet replay windows plus handshake and authenticated-media rate limits;
- server-authoritative player position for proximity routing;
- server-authoritative voice identity for offline-mode compatibility;
- one Opus decoder state per remote speaker;
- bounded playback queues and reusable OpenAL scratch buffers;
- per-speaker fixed jitter/reorder rings with duplicate/late handling and Opus PLC;
- a 128-frame preallocated PCM playback pool to avoid 50 Hz array churn;
- backend-agnostic microphone capture SPI with JavaSound and LWJGL2/OpenAL Capture implementations;
- automatic capture fallback plus bounded microphone retry/recovery for device loss and delayed audio readiness;
- selectable `auto` / `native` / `java` Opus backend with bounded native decoder scratch storage.

The Gateway/Simple Voice Chat bridge now passes secure local end-to-end transport QA, including backend-bound bootstrap, agent reconnect, raw-Opus passthrough and proxy UUID ↔ local Bukkit UUID binding. It is **not** claimed production-ready until real Velocity/Paper/SVC clients pass listening, transfer and soak tests. Adaptive jitter tuning and Android/Pojav/Amethyst real-device microphone validation also remain open gates.

## Runtime contract

| Component | Contract |
|---|---|
| Minecraft | 1.7.10 |
| Forge | 10.13.4.1614 |
| Client JVM | Java 8+; produced classes are major version 52 |
| Crucible/server JVM | Java 8+; Java 21 is the supported Rede Akashic target |
| Paper/SVC bridge JVM | Java 21 |
| Velocity Gateway JVM | Java 25 for Velocity 4.0.0 |
| Client graphics/audio API | LWJGL2-compatible OpenAL playback; JavaSound/OpenAL Capture input |
| Codec | Opus 48 kHz mono, 48 kbit/s default; native libopus preferred in `auto`, Concentus fallback |
| Default voice transport | UDP 24454 |
| UniMixins | not required |
| GTNHLib | not required |
| lwjgl3ify | optional environment compatibility, not a dependency |
| Angelica / Hodgepodge / ArchaicFix / CoreTweaks | compatible targets, not dependencies |

The same legacy mod JAR is intended for client and Forge/Crucible server installation. Cross-version routing is isolated under `platform/`: Paper/SVC, agent protocol and Velocity Gateway are separate server-side artifacts so modern dependencies never contaminate the Java 8 client classpath.

Codec backend semantics: `auto` tries bundled native libopus and falls back to Concentus if native loading is unavailable; `native` requires native libopus and fails explicitly; `java` forces Concentus. The bundled native artifact currently targets desktop Windows/Linux/macOS architectures, not Android.

## Java 8 verification

Java 8 support is an audited requirement rather than a build flag. The current source tree has been:

- built with modern Gradle while `enableModernJavaSyntax=false`;
- inspected to confirm class major version 52;
- smoke-tested for Opus encode/decode on JDK 1.8.0_411;
- independently recompiled with the actual JDK 8 `javac` against the resolved Forge compile classpath.

## Offline-mode identity

The original ProxyChat bootstrap could authenticate UDP with a client-local UUID that differed from the UUID used by an offline-mode server. Akashic Voice does not guess UUID mode on the client.

The Minecraft-side bootstrap now returns the authoritative `EntityPlayerMP` UUID together with a 256-bit voice bootstrap secret and UDP port. The client uses exactly that server-issued identity for voice authentication. This design also prepares the protocol for proxy forwarding and future bridge identities.

Live session secrets are credentials. Akashic Voice does not expose them through `/avdebug` or normal logs.

## Network model

The Minecraft connection bootstraps the voice session; audio then flows over UDP. The bootstrap carries a 256-bit secret and the authoritative server UUID. UDP authentication uses fresh client/server nonces and HMAC proofs; HKDF-SHA-256 derives separate client-to-server and server-to-client AES keys.

After authentication, control and media packets are carried only inside AES-128-GCM envelopes with authenticated protocol headers, monotonically increasing directional counters and a bounded replay window. Plaintext media is rejected.

Proximity routing uses the server's tracked world/dimension/position. Client-reported coordinates are not trusted as an authority. Group audio deliberately bypasses distance/dimension checks and is marked non-spatial for playback.

Malformed or oversized Opus payload declarations are rejected before payload allocation. The client UDP socket is connected to the expected voice endpoint and ignores unrelated datagrams. HELLO traffic is globally/per-IP rate-limited before expensive derivation, and authenticated inbound datagrams have per-session packet/byte budgets before AES-GCM decrypt.

Offline-mode remains a weaker identity root: Akashic Voice can secure the UDP session it bootstraps, but it cannot make an unauthenticated Minecraft login intrinsically trustworthy. Live proxy/offline-mode integration remains a separate release gate.

## Building

The build tooling uses JDK 21. This does **not** raise the client runtime requirement; the produced mod targets Java 8 bytecode.

```text
./gradlew clean build
```

Artifacts are written to `build/libs/`. Quality-assurance helpers live under `tools/qa/`.

The bundled native Opus dependency is checksum-pinned: `verifyNativeOpusBundle` validates SHA-256 `e4d67fb9aa3158ed5bd17536b78cea20f85862144e7c1cdb1d6f5972cf031c30` before `shadowJar` may package it.

## Development roadmap

The engineering gates are tracked in `docs/AUDIT.md` and `docs/ROADMAP.md`. Major remaining work includes:

1. adaptive jitter tuning, stream telemetry and live gameplay validation;
2. live in-game allocation/latency/GC profiling and remaining control-path cleanup;
3. real-device OpenAL/Android capture validation for Pojav/Amethyst-class launchers;
4. live deployment validation of the implemented Akashic Voice Gateway and bidirectional Simple Voice Chat bridge;
5. cross-server groups/radio semantics and public addon API.

## Credits and license

Akashic Voice is derived from **ProxyChat** by Julius Meinking, distributed under the MIT License in this repository. The original license notice is preserved.

The bundled Concentus implementation is distributed under its upstream BSD-style license; its notice is included in `META-INF/licenses/Concentus-LICENSE.txt` inside the built JAR.

The optional preferred desktop backend bundles `com.plasmoverse:opus-jni-rust:1.0.4` (MIT) and its native libopus dependency chain. Corresponding third-party notices and checksum/provenance records are included under `META-INF/licenses/native-opus/`.

Simple Voice Chat is used only as an interoperability/behavioral reference where permitted. Akashic Voice must not copy ARR Simple Voice Chat source code; bridge behavior is implemented independently against supported interfaces/protocol behavior.
