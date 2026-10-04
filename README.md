# Akashic Voice

High-quality spatial voice chat for **Minecraft 1.7.10 (Forge)**, engineered for legacy clients without forcing a legacy server stack. Derived from the MIT-licensed [ProxyChat](https://github.com/Tony20494/ProxyChat) and re-architected for the Rede Akashic / *The End of The Forbidden* environment.

> **Status (2026-10-04): release candidate RC7, not a stable release.** All automated gates pass; five live-acceptance gates and several field-observed defects remain open. See [`docs/PROJECT_STATE.md`](docs/PROJECT_STATE.md).
>
> Resumo em português: [`docs/PT-BR_RESUMO.md`](docs/PT-BR_RESUMO.md).

## At a glance

| Item | Value |
|---|---|
| Client contract | Minecraft 1.7.10, Forge 10.13.4.1614, Java 8 bytecode (class major 52), LWJGL2-compatible OpenAL |
| Server contract | Forge/Crucible direct mode (Java 8–21), or Velocity 4.0.0 Gateway (Java 25) + Paper 1.21.1 / Simple Voice Chat bridge (Java 21) |
| Codec / transport | Opus 48 kHz mono 20 ms frames, 48 kbit/s default; native libopus preferred, Concentus fallback; UDP 24454; HKDF-SHA-256 + AES-128-GCM |
| Version label in use | `0.1.0-alpha.5-akashic-voice.11+022dc1f922-dirty` (**ambiguous**, see [provenance](docs/VERSIONING_AND_PROVENANCE.md)) |
| Installed JAR identity | RC7 Forge JAR, SHA-256 `c0f9d86cb331598c2e0e7c9d7ebf6091fffee18bc94bbdaf6bbf9cbe6d2eb081` |
| Planning progress | 99 % core stable-v1 scope (RC5, 98–99.5 %); 97 % expanded premium scope (RC7, 95–98.5 %). Judgment estimates, not measurements |
| Release / tags | No stable release, no production deployment approved |

## Documentation map

Read in this order when taking over the project:

1. [`docs/PROJECT_STATE.md`](docs/PROJECT_STATE.md) — single source of truth for what exists, what is proven, what is open.
2. [`docs/HANDOFF.md`](docs/HANDOFF.md) — rules, environment, next actions, how not to break evidence.
3. [`docs/VERSIONING_AND_PROVENANCE.md`](docs/VERSIONING_AND_PROVENANCE.md) — which build is which; hash table RC1–RC7.
4. [`docs/HOMOLOGATION.md`](docs/HOMOLOGATION.md) — acceptance protocol and gate status.
5. [`docs/FIELD_EVIDENCE_2026-10.md`](docs/FIELD_EVIDENCE_2026-10.md) — what 154 real client logs show.
6. [`docs/HISTORY.md`](docs/HISTORY.md) and [`CHANGELOG.md`](CHANGELOG.md) — chronology from ProxyChat to RC7.
7. [`docs/ISSUES_REGISTER.md`](docs/ISSUES_REGISTER.md) — tracked issues (mirrors GitHub Issues).
8. [`docs/BUILD_AND_QA.md`](docs/BUILD_AND_QA.md) — building, JDK matrix, QA runners.
9. [`docs/RELATED_PROJECTS.md`](docs/RELATED_PROJECTS.md) — sibling projects found on the engineering workstation.
10. Engineering contracts: [`ARCHITECTURE`](docs/ARCHITECTURE.md), [`AUDIT`](docs/AUDIT.md), [`ROADMAP_STATUS_20260917`](docs/ROADMAP_STATUS_20260917.md), [`CONTINUITY`](docs/CONTINUITY.md), [`PERFORMANCE`](docs/PERFORMANCE.md), [`ADDON_API`](docs/ADDON_API.md), [`RADIO`](docs/RADIO.md), [`platform/README`](platform/README.md).
11. Evidence archive: [`docs/evidence/INDEX.md`](docs/evidence/INDEX.md) (handoffs CLIENT06–CLIENT63, JSON receipts, RC manifests; production host/port redacted).

## Repository layout

```text
src/                      Forge 1.7.10 mod (client + Forge/Crucible server), Java 8 source
shared/client-protocol/   Java 8 wire/crypto source shared by the mod and the platform
platform/
  addon-api/              Public addon API v1 (no raw audio, no per-frame callbacks)
  bridge-core/            Minecraft-independent bridge/session/group models
  agent-protocol/         Authenticated Paper-agent <-> Gateway protocol (separate key domain)
  svc-paper/              Paper 1.21.1 adapter built only against the public SVC API 2.6.20
  gateway-velocity/       Velocity 4.0.0 Gateway: UDP session authority, routes, groups, radio, moderation
tools/qa/                 Java 8 smokes, JAR inspection, finite QA runners
docs/                     Engineering documentation, evidence archive, homologation templates
```

Git history: 20 upstream ProxyChat commits (2026-05-08/09) → 16 Akashic Voice commits (2026-09-11/12) → one snapshot of the uncommitted active tree → RC7 source import → documentation.

## Runtime contract

| Component | Contract |
|---|---|
| Minecraft | 1.7.10 |
| Forge | 10.13.4.1614 |
| Client JVM | Java 8+; produced classes are major version 52 |
| Crucible/server JVM | Java 8+; Java 21 is the supported Rede Akashic target |
| Paper/SVC bridge JVM | Java 21 |
| Velocity Gateway JVM | Java 25 for Velocity 4.0.0 |
| Client graphics/audio API | LWJGL2-compatible OpenAL playback; JavaSound / OpenAL Capture input |
| Codec | Opus 48 kHz mono, 48 kbit/s default; native libopus preferred in `auto`, Concentus fallback |
| Default voice transport | UDP 24454 |
| UniMixins / GTNHLib | not required |
| lwjgl3ify | optional environment compatibility, not a dependency |
| Angelica / Hodgepodge / ArchaicFix / CoreTweaks | compatible targets, not dependencies |

The same legacy mod JAR is intended for client and Forge/Crucible server installation. Cross-version routing is isolated under `platform/` so modern dependencies never contaminate the Java 8 client classpath.

Codec backend semantics: `auto` tries bundled native libopus and falls back to Concentus; `native` requires native libopus and fails explicitly; `java` forces Concentus. The bundled native artifact targets desktop Windows/Linux/macOS, not Android.

## Feature summary (RC7)

Implemented in code and covered by automated gates (live acceptance still open where noted in the project state):

- Secure voice: authenticated UDP handshake, session-bound AES-GCM, replay window, rate limits, server-authoritative identity and position, explicit session UUIDs, UDP datagrams ≤ 1200 bytes.
- Client audio: per-speaker decoder state, adaptive jitter buffers with Opus PLC, pooled playback, speaker panel (search, per-speaker volume/mute), quality presets with live bitrate, touch/hold-to-talk, per-source voice gain.
- Premium (RC6/RC7): OpenAL EFX environmental acoustics (OUTDOOR/ROOM/CAVE/UNDERWATER), talking nametag suffix, raw PTT path, OpenAL output hot-plug recovery.
- Server side: Velocity Gateway with backend-bound bootstrap, Paper/SVC bidirectional bridge (raw Opus fast path, distance/whisper metadata), legacy Forge/Crucible Agent mode (fail-closed), federated groups, cross-server radio, persistent moderation, public Addon API v1.

Not claimed: in-band Opus FEC, AGC/limiter, Android-native capture, real-device mobile support, production readiness.

## Security notes

Session secrets and agent PSKs are credentials. They must never appear in logs, documentation, checkpoints or test reports. `/avdebug` and `/avgateway status` expose aggregate counters only. See [`SECURITY.md`](SECURITY.md).

Offline-mode remains a weaker identity root: Akashic Voice secures the UDP session it bootstraps but cannot make an unauthenticated Minecraft login intrinsically trustworthy.

## Building

Build tooling uses JDK 21; the produced mod targets Java 8 bytecode. Details, the JDK matrix and QA runners are in [`docs/BUILD_AND_QA.md`](docs/BUILD_AND_QA.md).

```text
./gradlew clean build                       # Forge mod -> build/libs/
./gradlew -p platform --console=plain build # Gateway, Paper bridge, addon API (root wrapper, Gradle 9.3.1)
```

The bundled native Opus dependency is checksum-pinned: `verifyNativeOpusBundle` validates SHA-256 `e4d67fb9aa3158ed5bd17536b78cea20f85862144e7c1cdb1d6f5972cf031c30` before `shadowJar` may package it.

> The commands above are documented from the project's build files and QA receipts. They were **not** re-executed when this repository was assembled; see the verification note in `docs/BUILD_AND_QA.md`.

## Credits and license

Akashic Voice is derived from **ProxyChat** by Julius Meinking (MIT). The original license notice is preserved in `LICENSE`. Upstream history (2026-05-08/09, releases 0.1.0–0.3.1) is preserved in this repository's git history.

The bundled Concentus implementation is distributed under its upstream BSD-style license (`META-INF/licenses/Concentus-LICENSE.txt` in the built JAR). The optional desktop backend bundles `com.plasmoverse:opus-jni-rust:1.0.4` (MIT) and its native libopus chain; notices and checksum/provenance records are under `META-INF/licenses/native-opus/`.

Simple Voice Chat is used only as an interoperability/behavioral reference where permitted. Akashic Voice must not copy ARR Simple Voice Chat source code.
