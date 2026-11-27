# Changelog

Format follows [Keep a Changelog](https://keepachangelog.com/). No stable release exists; entries describe **candidates and checkpoints**, each with the evidence that backs it. Hashes refer to the Forge JAR (full tables in [`docs/VERSIONING_AND_PROVENANCE.md`](docs/VERSIONING_AND_PROVENANCE.md)).

## [Unreleased]

### Added
- Repository consolidation (2026-10-04): upstream ProxyChat history completed, uncommitted active tree snapshotted (`ee8920f`), RC7 source imported (`26023d4`), evidence archive (`docs/evidence/`), operator tooling (`tools/homologation/rc7-v3.8/`), project documentation, issue register.
- [`docs/FIELD_EVIDENCE_2026-10.md`](docs/FIELD_EVIDENCE_2026-10.md): first analysis of real client logs.
- [`docs/evidence/rebuild-20261127/`](docs/evidence/rebuild-20261127/README.md): first build and QA run of this repository from committed source (BUILD + AUTOMATED), with the issue #3 real-native reproducer results; state documents updated accordingly.
- Client addon hooks (`AkashicVoiceClientHooks`: microphone PCM frame listeners and transmission filters), contributed in PR #19. Documented in [`docs/ADDON_API.md`](docs/ADDON_API.md); regression smoke `tools/qa/ClientAddonHooksSmoke.java`.
- Client-side backend-transfer detection: a new player entity id while staying in a world requests a fresh voice bootstrap immediately instead of waiting for the voice timeout (#4). Smoke `tools/qa/BackendTransferDetectorSmoke.java`.

### Changed
- Client addon hooks hardened after merge: microphone frames reach addons only while the player's own controls authorize transmission (not muted, push-to-talk held); `LinkageError` from an addon is isolated like `RuntimeException`.

### Fixed (AUTHORED + AUTOMATED only; no live confirmation yet)
- #17: the output-health poll can no longer terminate the playback thread. String-keyed ALC queries are resolved once per device instead of twice a second (LWJGL 3 `MemoryStack` pressure), and any failure of the poll disables monitoring for that context instead of killing playback.
- #3: OpenAL native binding loss (`UnsatisfiedLinkError` after a Minecraft sound-system reload) is now handled as a recovery event: playback abandons the vanished context without native deletes and waits for it to return (no extra reload request); a failed EFX probe on unlinked natives is re-probed instead of disabling acoustics for the session; microphone capture ends with one concise log line and never passes a stale capture handle to a re-linked library. Smoke `tools/qa/OpenAlBindingLossSmoke.java`.
- #7: `PlaybackChannel.recycleProcessed` clamps the processed-buffer count and the recycle pool to the per-source maximum.
- Gateway: `GatewayUdpServer.close()` now waits (bounded, 2 s) for its worker threads, so the UDP port is really released before `close()` returns and an in-JVM restart can rebind it (NIO-backed `DatagramSocket` keeps the port until the blocked receiver leaves). Found because `GatewaySessionRestartSoakTest` failed 2–3 of 6 repetitions on Linux.
- QA: `GatewaySessionRestartSoakTest` binds its client before choosing the Gateway port (ephemeral-port collision on Linux); `run_mic_recovery_qa.py --all-smokes` compiles the shared `QualityTestEnvironment` helper and runs only `*Smoke` mains.
- QA: new real-native reproducer `tools/qa/OpenAlReloadReproSmoke.java` performs `AL.destroy()`/`AL.create()` under live voice frames (needs LWJGL 2 natives; `ALSOFT_DRIVERS=null` works without audio hardware).

### Known issues (open, see GitHub issues)
- OpenAL natives unlinked after a Minecraft sound-system reload: mic thread, per-frame playback and EFX probe fail (#3).
- Voice-server timeouts around backend transfers in direct mode (#4); hot-plug recovery events in ordinary sessions (#5).
- Shared version label across RC builds (#2).
- Android device runs: no capture backend could open a microphone (#18); RC7 output-health poll can kill the playback thread on an LWJGL 3 runtime (`MemoryStack` overflow) (#17).

## [RC7] — 2026-09-17 — `AV-PREMIUM-HARDENING-RC7-20260917-58` — `c0f9d86c…`
Installed in the Technic client. Automated gates PASS; live gates open.
### Added
- Acoustic routing policy: environmental EFX only on positional proximity voice; group and radio stay dry.
- OpenAL output-health state machine: `ALC_EXT_disconnect` / default-device change detection, UNAVAILABLE debounce, bound/default-device migration guard, voice-owned retry.
### Changed
- Playback channel and listening-state handling for the above (Forge client only; wire, Gateway, Paper, Addon API unchanged from RC6).

## [RC6] — 2026-09-17 — `AV-PREMIUM-COMPLETION-RC6-20260917-58` — `aaa7294d…`
### Added
- Environmental acoustics (OUTDOOR / ROOM / CAVE / UNDERWATER) with OpenAL EFX and hysteresis.
- Talking nametag suffix aligned with vanilla normal/sneak/sleep nameplates.
- Raw keyboard/mouse PTT path with immediate local indicator (120 ms residual window).
- Speaker/headset recovery through a controlled Minecraft sound reload.

## [RC5] — 2026-09-17 — `AV-LEGACY-AGENT-OBSERVABILITY-RC5-20260917-47` — `6fab0865…`
### Added
- Cumulative Legacy Agent media-drop telemetry across reconnects, exposed through `/avdebug`.
- Canary evidence engine, status parsers, guarded install/rollback tooling (v2.1 → v3.7).
Real-client runtime evidence recorded on this build (CLIENT59–62).

## [RC4] — 2026-09-16 — `AV-LEGACY-AGENT-RC4-20260916-46` — `8e5a6fe5…`
### Added
- Fail-closed Legacy Forge/Crucible Agent mode and operational/debug readiness.

## [RC1–RC3] — 2026-09-15/16 — `e38cfe33…`
Sealed packaging of the CLIENT44 baseline (logical NAT rebind lifecycle, ~95 % planning estimate). Gateway JAR corrected from `2218962a…` (RC1) to `e4329bed…` (RC2+).

## Checkpoints between 022dc1f and RC1 (2026-09-13 → 09-15)
### Added
- Explicit session UUIDs with session-bound AEAD; MTU ≤ 1200 (CLIENT19–22).
- Persistent Gateway moderation `/avvoice`; public Addon API v1; cross-server radio `/avradio` (CLIENT23–25).
- Quality presets and live bitrate; speaker panel; touch/hold-to-talk; per-source voice gain (CLIENT11–17).
- Aggregate protocol telemetry (CLIENT18–20); legacy invite TTL/bounds (CLIENT21).
### Fixed
- Capture restart rewound the media sequence (reproduced: expected 3, received 0) — MIC-RECOVERY-01.
- Retired outbound epoch tombstones eventually blocked new transient speakers in long sessions (CLIENT33).
- Per-IP HELLO burst 20 → 64 so 32 clients behind one NAT can bootstrap together (CLIENT39).
- Decoder streams capped at 256 (CLIENT28); retired default speaker-control entries reclaimed (CLIENT34).

## [0.1.0-alpha.5 … 0.1.0-alpha.1] — 2026-09-11 / 09-12 (tags preserved)
- alpha.5 `60fb120` portable capture backends · alpha.4 `7cdfa6d` native Opus · alpha.3 `378f622` secure transport · alpha.2 `812c24d` jitter + pooled playback · alpha.1 `77e9094` foundation.
- Gate 6 (`fa456ac` → `022dc1f`): SVC adapter, Velocity Gateway, per-backend PSKs, distance/whisper metadata, soak/backpressure/restart recovery, federated group authority.

## ProxyChat upstream (Julius Meinking, MIT) — 2026-05-08 / 05-09
Releases 0.1.0 – 0.3.1 preserved in history up to fork point `a4fa791`.
