# Akashic Voice — Project state

**As of:** 2026-10-04 · **Baseline reviewed:** RC7 (`AV-PREMIUM-HARDENING-RC7-20260917-58`) · **Stable release:** not approved · **Production deployed:** no

This is the single source of truth for continuation decisions. Where it disagrees with an older document (`ROADMAP.md`, `PROGRESS_REVIEW_*`, `CONTINUITY.md`), this file and the evidence receipts under `docs/evidence/` win; the older files remain as history.

## 1. Evidence grading used throughout

| Grade | Meaning |
|---|---|
| AUTHORED | Source written; not compiled by an independent step |
| STATIC | Statically reviewed (symbols, contracts, closure) |
| BUILD | Compiled / built (Gradle, actual JDK 8 `javac`) |
| AUTOMATED | Executed tests, smokes, mutations, soak (loopback / fake hardware) |
| LIVE-OBSERVED | Seen in a real Minecraft client or server log, without a defined acceptance protocol |
| LIVE-APPROVED | Passed a defined acceptance gate with operator/player sign-off |

No part of the product is LIVE-APPROVED. Reviews by one assistant are not independent external review.

## 2. What exists

| Component | Artifact (RC7) | Runtime | SHA-256 |
|---|---|---|---|
| Forge mod (client + Forge/Crucible server) | `akashicvoice-forge-1.7.10.jar` — 3,213,682 B, 321 classes, class major 52 only, 7 native binaries, 5 JNI classes | Java 8+ | `c0f9d86cb331598c2e0e7c9d7ebf6091fffee18bc94bbdaf6bbf9cbe6d2eb081` |
| Velocity Gateway | `akashicvoice-gateway-velocity.jar` — 252,653 B, 146 classes | Velocity 4.0.0, Java 25 (classes 52/65/69) | `e4329bed4f45db66ef80de66590dc88cc51c35c30e241f4704de2dbe0a2b4ec2` |
| Paper / SVC bridge | `akashicvoice-svc-paper.jar` — 128,657 B, 84 classes | Paper 1.21.1, Java 21, SVC API 2.6.20 | `b5e66814edfd8bfb1b197d786ddb9da727a75e9940dad7ca44da9b92b2c21bdb` |
| Addon API v1 | `akashicvoice-addon-api.jar` — 7,581 B, 9 classes | Java 21 (class 65) | `6e73a1f96d96251cc631f6a51e4a1e485022257f1cf0a517dc7781e8ff5de347` |

JARs are not stored in git. Gateway, Paper and Addon API are byte-identical from RC5 through RC7; only the Forge JAR changed between RC5 and RC7.

**Where the RC7 Forge JAR is installed (verified 2026-10-04):** the Technic *Forbidden Series* client `mods/` folder (SHA-256 matches RC7) and three AkashicCinematicMenu QA instances. No Gateway or Paper JAR was found installed on the engineering workstation; the receipts state production deployment has not happened.

**How the field client runs:** all 39 client sessions since 2026-09-23 log `serverMode=direct, voicePort=24454` — the Forge/Crucible `VoiceServer` path, **not** the Velocity Gateway. The Gateway/Paper bridge is built and automated-tested but has no live deployment evidence in this repository (CLIENT61 observed the client using a configured public endpoint with session-bound bootstrap; see HOMOLOGATION).

## 3. Capability matrix (RC7)

Status words follow `docs/ROADMAP_STATUS_20260917.md`: DONE (implemented and gated) · QUALIFIED (DONE plus load/fault/recovery evidence) · LIVE-GATE (needs real environment) · CONDITIONAL (only if live data proves need) · DEFERRED-VNEXT.

| Area | State | Highest evidence |
|---|---|---|
| Secure transport (handshake, HKDF, AES-GCM, replay, rate limits, explicit session UUIDs, MTU ≤ 1200) | QUALIFIED | AUTOMATED (+ LIVE-OBSERVED authentication) |
| Codec (native libopus + Concentus, strict/auto/java) | DONE | AUTOMATED |
| Capture (JavaSound / OpenAL Capture, recovery, retry) | QUALIFIED | AUTOMATED + LIVE-OBSERVED (OpenAL Capture, 203 mic starts) |
| Jitter / PLC / reorder | QUALIFIED | AUTOMATED (16,000 frame positions under loss/reorder) |
| In-band Opus FEC | DEFERRED-VNEXT | not claimed |
| AGC / limiter | CONDITIONAL | intentionally absent until acoustic measurements exist |
| Speaker panel, quality presets, live bitrate, touch PTT | DONE | AUTOMATED (touch needs real device) |
| Premium acoustics (EFX OUTDOOR/ROOM/CAVE/UNDERWATER) | DONE (automated) | AUTOMATED; **field logs show EFX probe failures after sound reload** (issue register #3) |
| Talking nametag suffix, raw PTT path | DONE (automated) | AUTOMATED |
| Output hot-plug recovery (ALC disconnect, sound-system reload) | DONE (automated) | AUTOMATED; **field logs show OpenAL natives unlinked during recovery** |
| Gateway, Paper/SVC bridge, raw Opus fast path, whisper/distance metadata | QUALIFIED | AUTOMATED (loopback soak up to 302,400 MIC frames) |
| Federated groups, cross-server radio, persistent moderation, Addon API v1 | DONE / QUALIFIED | AUTOMATED |
| Legacy Agent mode (fail-closed) + cumulative drop telemetry | DONE | AUTOMATED |
| Backend transfer / restart / NAT rebind | QUALIFIED | AUTOMATED + partial LIVE-OBSERVED (route-change rebootstrap, CLIENT61) |
| Android / Pojav / Amethyst | LIVE-GATE | LIVE-OBSERVED (partial, negative): on real-device QA runs the mod loads and authenticates a loopback session, but **no capture backend could open a microphone (10/10 sessions)** and one run lost its playback thread to an LWJGL 3 `MemoryStack` overflow in the RC7 output-health poll (issues #17, #18) |

## 4. Progress estimates (judgment, not measurement)

| Scope | Estimate | Band | Source |
|---|---:|---|---|
| Core stable-v1 (RC5 baseline) | 99 % | 98–99.5 % | CLIENT61/62 (2026-09-17) |
| Expanded scope including premium features (RC7) | 97 % | 95–98.5 % | RC7 manifest / V3.8 readiness (2026-09-17) |

Rules inherited from the engineering record: do not raise the number for more synthetic tests; do not convert any live gate to PASS from smokes, thread dumps, soaks or documentation.

**Reviewer's note (2026-10-04, this repository assembly):** the field evidence in [`FIELD_EVIDENCE_2026-10.md`](FIELD_EVIDENCE_2026-10.md) — OpenAL natives becoming unlinked after Minecraft sound-system reloads (desktop), the RC7 output-health poll terminating the playback thread on an LWJGL 3 runtime (device), and no microphone capture on device — was not visible to the automated gates, which run against fake or loopback audio. Treat the 97 % premium-scope figure as an **upper bound** until issues #3–#5 and #17–#18 in the register are triaged, and note that the mobile gate (G4) is not "no evidence" but "evidence of failure on the tested configuration". This is a judgment, not a re-measurement.

## 5. Open gates and findings

Live gates (each has a tracking issue):

1. Controlled backend **process** restart with recovery proof (CLIENT60/61 prove lifecycle and route recovery, not a commanded restart).
2. Two real clients over WAN with audible bidirectional speech, including a cross-backend route.
3. Physical acoustic review (clipping, echo, pumping, intelligibility) and an explicit AGC/limiter decision.
4. Real Pojav + Amethyst device pass: capture, playback, touch PTT, focus/background, thermal, battery.
5. Controlled production canary with rollback ready and operator/player acceptance.
6. Premium extras: AirPods/headset unplug-replug during live voice; outdoor/room/cave/underwater listening; nametag and PTT visual acceptance.

Findings from this assembly (new): see `ISSUES_REGISTER.md` #2–#7 and #17–#18 — version-label ambiguity, OpenAL unlinked natives after sound reload, handshake timeouts after transfers, hot-plug event frequency, missing `modern_fonts.json`, audio-thread OOM signature, RC7 health poll killing the playback thread on LWJGL 3, no microphone capture on device.

Carried-over items from `CONTINUITY.md` that should be re-verified rather than assumed closed: total encoded group-control message-size budget; invitation limits/expiry; persistent group authority; permission/moderation parity; native OpenAL lifecycle.

## 6. Not claimed

Stable release; production readiness; zero defects; audible two-client speech; acoustic quality; mobile support; FEC; AGC; guaranteed performance; independent external review.

## 7. How to keep this file true

Update it whenever a gate changes state. A gate moves only on evidence recorded in `docs/evidence/` (command, source identity, process identity, log path, exit status, summary). The newest verified source/runtime evidence outranks any older summary.
