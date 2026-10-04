# Akashic Voice — RC5 engineering status (2026-09-17)

This document supersedes stale status labels in `ROADMAP.md` for continuation decisions. The roadmap remains historical; evidence receipts and `CONTINUITY.md` remain authoritative.

Current sealed production-code candidate: `AV-LEGACY-AGENT-OBSERVABILITY-RC5-20260917-47`.
Planning completion estimate: **~96%**, judgment band **92-98%**. This is engineering completion, not stable-release approval.
No production deployment, release tag, active-tree promotion or 100% declaration has occurred.

## Protocol / security

- AV magic/version, authenticated handshake, HKDF, directional AES-GCM, replay window and rate limits: DONE / gated.
- Explicit session identity with backward-compatible bootstrap and session-bound UDP envelope: DONE (CLIENT22).
- UDP MTU hardening <=1200 bytes while keeping internal Agent/TCP payload contract separate: DONE (CLIENT19/20).
- Aggregate protocol/session metrics without per-frame log spam: DONE (CLIENT18/20 and later observability).
- NAT handshake burst and logical NAT rebind lifecycle: DONE / gated (CLIENT39/44).
- Malformed/fuzz/replay/session restart qualification: DONE in automated gates; live network still required for release.

## Client audio / controls

- Per-stream decoder state, bounded decoder creation, bounded jitter/playback queues and stale-stream reclamation: DONE / gated (through CLIENT28/33/34).
- Adaptive jitter target behavior: IMPLEMENTED and qualified by loss/jitter/PLC QA (CLIENT27); old roadmap PARTIAL label is stale.
- Opus PLC: DONE / measured in automated loss scenarios.
- In-band Opus FEC: **NOT CLAIMED**. Installed native JNI API does not expose equivalent FEC controls; do not enable only on the Java backend.
- Per-speaker volume, mute, global deafen, output-gain isolation: DONE / gated.
- Named Opus quality presets and live bitrate control: DONE / gated.
- Touch-friendly hold-to-talk/PTT: DONE / gated; real mobile input validation remains.
- High-pass option is labelled honestly; it is not RNNoise/noise cancellation.
- Limiter/AGC: **INTENTIONALLY NOT RELEASE-APPROVED** until objective clipping/noise measurements plus real listening evidence exist.

## Gateway / bridge / social

- Velocity Gateway, secure session authority, Legacy Agent and Paper/SVC bridge: DONE in code and local/integration QA; live deployment/listening remains.
- Bidirectional raw compatible Opus fast path: DONE / gated.
- Distance/whisper metadata extension: IMPLEMENTED; physical/live listening semantics still require validation.
- Federated groups, persistent group authority and legacy invite TTL/bounds: DONE / gated.
- Cross-server radio with bounded directory, no group/radio duplicate delivery and pair-scoped resets: DONE / gated (CLIENT25).
- Persistent central moderation and hard reset of muted streams: DONE / gated (CLIENT23).
- Public addon API v1, isolated from the audio-frame hot path: DONE / gated (CLIENT24).
- Production observability for Gateway and Legacy Agent including cumulative drop/reconnect counters: DONE / gated through RC5.

## Reliability qualification

- Sustained multi-client session-bound soak, Agent reconnects, backend transfers and radio fan-out: PASS in automated qualification (CLIENT26/35/36/40).
- Gateway process restart while logical clients continue speaking: PASS in automated qualification; real running deployment restart remains a live gate.
- Packet loss, jitter, duplicate, reordering and PLC qualification: PASS (CLIENT27).
- Bounded receiver/decoder/speaker/epoch state and reclamation: DONE / gated through CLIENT34.
- RC5 Forge/JDK8/platform/JAR/seal verification: PASS. See `CLIENT47_FINAL_RECEIPT.json`.
- Canary evaluator/status parser v2.1: PASS; operator tooling only, no production-code change.

## Mobile / physical hardware

- JavaSound/OpenAL capture abstraction, recovery and non-capturing enumeration: DONE in code / automated QA.
- Engineering desktop non-invasive capture enumeration: PASS, 5 list entries under Java 8; microphone was not opened.
- Pojav legacy + Amethyst real device microphone/playback: **LIVE GATE OPEN**.
- Android permission lifecycle, suspend/resume, Bluetooth/wired hotplug: **LIVE GATE OPEN**.
- ARM thermal/battery/cadence profiling: **LIVE GATE OPEN**.
- AudioRecord/Oboe fallback: CONDITIONAL ONLY; implement only if the real-device JavaSound/OpenAL matrix demonstrates a need.

## Stable-release blockers

The remaining work is evidence-dominated, not missing core architecture:

1. Real WAN/two-client audible speech and real production cross-server transfer + controlled restart.
2. Physical microphone/speaker/headset acoustic pass, including clipping, echo, pumping, intelligibility and any AGC decision.
3. Real Pojav/Amethyst device pass including thermal and battery observations.
4. Controlled production canary plus operator/player feedback with zero contradictory counters.

Use `PRECANARY_READINESS_V2_1_20260917.json` and the sealed `akashic-voice-rc5-homologation-v2.1-20260917.zip` for the next live step. A tool returning `READY_FOR_STABLE_REVIEW` still requires human review; it does not itself declare stable release.
