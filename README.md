# Akashic Voice

High-quality spatial voice for Minecraft 1.7.10, engineered for legacy clients without forcing a legacy server stack.

Akashic Voice is derived from the MIT-licensed ProxyChat project and is being re-architected for the Rede Akashic / The End of The Forbidden environment. The client contract is deliberately conservative: Minecraft 1.7.10, Forge 10.13.4.1614, Java 8 bytecode and LWJGL2-compatible audio. The server may run on a modern JVM such as Java 21.

## Current engineering status

This repository is under active development. The current foundation already includes:

- 48 kHz mono Opus voice using bundled pure-Java Concentus;
- 20 ms / 960-sample audio frames;
- proximity-based OpenAL 3D playback;
- push-to-talk, voice activation and always-on capture modes;
- microphone selection/test UI and voice settings;
- persistent groups with distance-independent group audio;
- bounded UDP parsing and authenticated media endpoints;
- server-authoritative player position for proximity routing;
- server-authoritative voice identity for offline-mode compatibility;
- one Opus decoder state per remote speaker;
- bounded playback queues and reusable OpenAL scratch buffers;
- JavaSound capture isolated behind an audio-capture SPI.

The Simple Voice Chat bridge, encrypted media protocol, adaptive jitter buffer and Android/Pojav capture backend are planned work and are **not** claimed complete yet.

## Runtime contract

| Component | Contract |
|---|---|
| Minecraft | 1.7.10 |
| Forge | 10.13.4.1614 |
| Client JVM | Java 8+; produced classes are major version 52 |
| Server JVM | Java 8+; Java 21 is a supported target for Crucible deployments |
| Client graphics/audio API | LWJGL2-compatible OpenAL |
| Codec | Opus / Concentus, 48 kHz mono |
| Default voice transport | UDP 24454 |
| UniMixins | not required |
| GTNHLib | not required |
| lwjgl3ify | optional environment compatibility, not a dependency |
| Angelica / Hodgepodge / ArchaicFix / CoreTweaks | compatible targets, not dependencies |

The same mod JAR is intended for client and Forge/Crucible server installation. Server-side gateway/bridge modules may later be split into dedicated Java 21 artifacts.

## Java 8 verification

Java 8 support is an audited requirement rather than a build flag. The current source tree has been:

- built with modern Gradle while `enableModernJavaSyntax=false`;
- inspected to confirm class major version 52;
- smoke-tested for Opus encode/decode on JDK 1.8.0_411;
- independently recompiled with the actual JDK 8 `javac` against the resolved Forge compile classpath.

## Offline-mode identity

The original ProxyChat bootstrap could authenticate UDP with a client-local UUID that differed from the UUID used by an offline-mode server. Akashic Voice does not guess UUID mode on the client.

The Minecraft-side bootstrap now returns the authoritative `EntityPlayerMP` UUID together with the voice secret and UDP port. The client uses exactly that server-issued identity for voice authentication. This design also prepares the protocol for proxy forwarding and future bridge identities.

Live session secrets are credentials. Akashic Voice does not expose them through `/avdebug` or normal logs.

## Network model

The Minecraft connection bootstraps the voice session; audio then flows over UDP. Media is accepted only after the UDP endpoint authenticates against a server-issued session secret.

Proximity routing uses the server's tracked world/dimension/position. Client-reported coordinates are not trusted as an authority. Group audio deliberately bypasses distance/dimension checks and is marked non-spatial for playback.

Malformed or oversized Opus payload declarations are rejected before payload allocation. The client UDP socket is connected to the expected voice endpoint and ignores unrelated datagrams.

Encryption, replay protection and rate limiting are still roadmap gates and must be completed before production-security approval.

## Building

The build tooling uses JDK 21. This does **not** raise the client runtime requirement; the produced mod targets Java 8 bytecode.

```text
./gradlew clean build
```

Artifacts are written to `build/libs/`. Quality-assurance helpers live under `tools/qa/`.

## Development roadmap

The engineering gates are tracked in `docs/AUDIT.md` and `docs/ROADMAP.md`. Major remaining work includes:

1. per-speaker jitter/reorder/PLC pipeline with bounded pooled storage;
2. authenticated encryption, replay window and UDP abuse controls;
3. low-allocation playback frame pool and performance benchmarks;
4. OpenAL/Android capture backends for Pojav/Amethyst-class launchers;
5. Akashic Voice Gateway and bidirectional Simple Voice Chat interoperability;
6. cross-server groups/radio semantics and public addon API.

## Credits and license

Akashic Voice is derived from **ProxyChat** by Julius Meinking, distributed under the MIT License in this repository. The original license notice is preserved.

The bundled Concentus implementation is distributed under its upstream BSD-style license; its notice is included in `META-INF/licenses/Concentus-LICENSE.txt` inside the built JAR.

Simple Voice Chat is used only as an interoperability/behavioral reference where permitted. Akashic Voice must not copy ARR Simple Voice Chat source code; bridge behavior is implemented independently against supported interfaces/protocol behavior.
