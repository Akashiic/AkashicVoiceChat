# Akashic Voice — Delivery Roadmap

> **Status note (2026-09-17):** this file is the historical delivery plan. For current evidence-backed status, use `docs/ROADMAP_STATUS_20260917.md`. Do not treat older PARTIAL/PENDING wording below as the current implementation state.

The project advances by evidence gates, not by feature count.

## Phase A — Foundation / 0.1.x

1. Make upstream build reproducible.
2. Rebrand package, assets, config and commands to Akashic Voice while preserving MIT attribution.
3. Enforce Java 8 source + bytecode for the Forge/client artifact.
4. Fix offline-mode identity by sending the server-authoritative UUID in bootstrap.
5. Split capture/playback behind small SPIs so JavaSound/OpenAL/Android can vary independently.
6. Isolate Opus decoder state per remote stream.
7. Add deterministic codec/protocol unit tests and Java 8 runtime smoke tests.
8. Produce first alpha JAR only after Gates 0-3 pass.

## Phase B — Protocol v1 / 0.2.x

1. **DONE:** explicit AV wire magic and protocol version; capability negotiation remains.
2. **PARTIAL:** directional counters and bounded replay windows are done; explicit session IDs remain.
3. **DONE:** AES-128-GCM media/control protection with deterministic unique nonces.
4. **DONE:** client UDP receive path is bound to the expected endpoint.
5. **DONE:** parser bounds checks, unauthenticated/authenticated rate limiting and fuzz tests.
6. **PARTIAL:** payload caps are enforced; <=1200-byte transport target remains for MTU hardening.
7. **PENDING:** protocol metrics without per-frame log spam.

## Phase C — Audio quality / 0.3.x

1. **PARTIAL:** fixed bounded per-speaker jitter/reorder is implemented; adaptive target-delay tuning remains.
2. **PARTIAL:** Opus PLC is implemented; in-band FEC still requires measured loss scenarios.
3. Player volume/mute/deafen controls.
4. Whisper/proximity semantic flags and non-spatial group playback.
5. Replace misleading basic 'noise suppression' label with explicit DSP stages.

6. Add limiter/AGC only after objective clipping/noise tests and listening tests.
7. **PARTIAL:** the default Akashic HD baseline is now 48 kbit/s Opus; the full named preset matrix remains.

## Phase D — Gateway and SVC bridge / 0.4.x

1. **PARTIAL:** Velocity 4.0.0 Gateway, secure UDP session authority and backend-bound bootstrap are implemented; live deployment remains.
2. **PARTIAL:** Paper 1.21.1 SVC adapter is implemented against public API 2.6.20; real-client listening test remains.
3. **PARTIAL:** bidirectional raw-Opus transport passes local end-to-end tests; exact SVC distance/whisper semantics still need a negotiated wire extension.
4. **PENDING:** cross-server groups/radio channels; never fake physical proximity between unrelated backend coordinate spaces.
5. **IMPLEMENTED FAST PATH:** compatible 48 kHz / 960-sample / 20 ms Opus is passed through without transcoding; fallback transcoder is not yet implemented.
6. **PENDING:** central moderation, permissions and production metrics/session dashboards.
7. **PARTIAL:** fail-open backend routing and agent reconnect isolation are implemented; live outage/restart soak testing remains.

## Phase E — Mobile / 0.5.x

1. **PENDING:** run the Java 8 client under Pojav legacy and Amethyst real-device test matrices.
2. **PARTIAL:** JavaSound is isolated, hardened and fallback-safe; Android enumeration/capture remains a real-device test.
3. **IMPLEMENTED / NEEDS DEVICE VALIDATION:** LWJGL2 OpenAL Capture backend, linkage-safe probing, bounded buffers and runtime retry/recovery.
4. **CONDITIONAL:** if JavaSound/OpenAL are unreliable on Android, implement the smallest possible AudioRecord/Oboe adapter.
5. Add touch-friendly PTT/toggle UX without requiring a physical keyboard.
6. Profile thermal load, battery impact, encode complexity and jitter on ARM devices.

## Release gate

No release is called stable until clean build, Java 8 runtime, offline-mode auth, packet-loss/jitter,
malformed-packet, reconnect, multi-speaker decoder isolation and prolonged-session tests pass.
Performance conclusions require captured measurements; visual/audio quality conclusions require explicit test cases.
