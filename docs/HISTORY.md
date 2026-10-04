# History

Chronology reconstructed from git, `docs/CONTINUITY.md`, the CLIENT handoffs and the RC manifests. Dates are those recorded in the sources (file names, commit dates, receipts). Checkpoint ids are stable references into [`evidence/`](evidence/INDEX.md).

## Phase 0 — ProxyChat upstream (2026-05-08 → 2026-05-09)

Julius Meinking's ProxyChat (MIT), 20 commits: Phase 0 skeleton from the GTNH ExampleMod 1.7.10 template → Forge channel + secret handshake → UDP voice server → Concentus Opus + mic capture → 3D OpenAL playback → proximity distribution → PTT/voice activation → settings GUI, hub, groups (Discord-style invites) → upstream releases 0.1.0 … 0.3.1. Fork point: `a4fa791` ("Refresh README, add icon attributions, drop stale planning docs", 2026-05-09).

Upstream defects that motivated the re-architecture (from `docs/AUDIT.md`): a build tied to an absolute macOS JDK path, an unresolvable JitPack Concentus coordinate, a client-local UUID used for UDP auth (breaks offline-mode), one shared decoder for all speakers, and per-frame allocations on the hot path.

## Phase 1 — Foundation and Gate 6 (2026-09-11 → 2026-09-12)

| Commit | Date | Milestone |
|---|---|---|
| `77e9094` | 09-11 | **alpha.1** foundation: Java 8 contract, identity fix |
| `812c24d` | 09-11 | **alpha.2** per-speaker jitter, pooled playback |
| `378f622` | 09-11 | **alpha.3** secure transport: HMAC proofs, HKDF, AES-GCM, replay window, rate limits |
| `7cdfa6d` | 09-11 | **alpha.4** native Opus performance (3.7× encode, 2.3× decode on baseline host) |
| `60fb120` | 09-11 | **alpha.5** portable capture backends (JavaSound / OpenAL Capture) |
| `fa456ac` `943fddf` `6650fa5` | 09-11/12 | Gate 6: SVC adapter, Velocity bootstrap, shared secure client protocol, secure Gateway + SVC bridge |
| `1cac62e` `e8dd94c` | 09-12 | Per-backend agent PSKs; SVC distance/whisper metadata (`SOUND_FRAME_V2`) |
| `5eb51e1` | 09-12 | Bridge homologation diagnostics (first homologation bundle) |
| `882a861` `4f72027` `a590ae2` `df202d4` | 09-12 | Reconnect soak, agent backpressure, Gateway-restart recovery, Paper reconnect QA |
| `022dc1f` | 09-12 | Federated group authority and managed SVC routing — last commit of the original checkout |

## Phase 2 — Candidate engineering outside git (2026-09-13 → 2026-09-17)

All work below happened in copies under `audit/…/integrated-clientNN` / `reviewed-clientNN`; the active checkout stayed at `022dc1f` + dirty. Each checkpoint has a handoff and receipt in the evidence archive.

| Checkpoint | Date | Headline |
|---|---|---|
| MIC-RECOVERY-01 | 09-13 | Reproduced a real defect (capture restart rewound the media sequence: expected 3, received 0); fixed; 61 platform tests |
| CLIENT06–10 | 09-13 | Client foundation/playback/spatial patches and isolated contracts; first full integration tree (CLIENT10: 125 files on JDK 8, 24 smokes, 124 platform tests) |
| CLIENT11 | 09-13 | Per-source voice gain; no longer overwrites the shared listener `AL_GAIN` |
| CLIENT12–14 | 09-13 | Speaker controls and individual-speaker panel (search, paging, volume, mute) |
| CLIENT15 | 09-13 | Quality presets 24/32/48/64 kbit/s, live bitrate |
| CLIENT16–17 | 09-14 | Touch/hold-to-talk core and GUI |
| CLIENT18–20 | 09-14 | Aggregate legacy telemetry; UDP MTU ≤ 1200; consolidated baseline |
| CLIENT21 | 09-14 | Legacy invite TTL/bounds |
| CLIENT22 | 09-14 | Explicit session UUIDs (AV outer types 5..8, session-bound AAD) |
| CLIENT23 | 09-14 | Persistent Gateway moderation (`/avvoice`), persist-before-publish |
| CLIENT24 | 09-14 | Public Addon API v1 |
| CLIENT25 | 09-14 | Proxy-authoritative cross-server radio (`/avradio`) |
| CLIENT26 / 40 | 09-14/15 | Sustained soaks (36,000 → 302,400 MIC frames, 144 backend transfers) |
| CLIENT27 | 09-14 | Loss/jitter/PLC qualification (16,000 frame positions) |
| CLIENT28 | 09-14 | Decoder streams capped at 256 |
| CLIENT30–34 | 09-15 | Client diagnostics, talk-cache bound, epoch reclamation (found and fixed a real receiver-session leak), speaker reclaim |
| CLIENT35–37 | 09-15 | Gateway restart/transfer/multihop qualification |
| CLIENT39, 41–44 | 09-15 | NAT handshake burst (HELLO burst 20→64), NAT rebind, logical rebind lifecycle |
| RC1 → RC3 | 09-15/16 | First sealed packages (`AV-RELEASE-CANDIDATE-PACKAGING-…`) |
| CLIENT46 / RC4 | 09-16 | Fail-closed Legacy Forge/Crucible Agent mode |
| CLIENT47 / **RC5** | 09-17 | Cumulative Agent drop telemetry; Forge SHA-256 `6fab0865…` |
| CLIENT48–58 (tooling) | 09-17 | Canary evaluator, status parser, evidence chain, guarded install/rollback, runtime/path/receipt binding; homologation kits v2 → v3.7 |
| CLIENT59–62 | 09-17 | Real-client runtime evidence on RC5 (see HOMOLOGATION §3) |
| CLIENT57–58 / **RC6** | 09-17 | Premium completion: environmental EFX acoustics, talking nametag suffix, raw PTT path, output-device recovery; Forge SHA-256 `aaa7294d…` |
| CLIENT58 / **RC7** | 09-17 | Premium hardening: acoustic routing policy (EFX only on positional proximity voice; group/radio stay dry), output-health state machine with UNAVAILABLE debounce and default-device migration guard; Forge SHA-256 `c0f9d86c…` |
| CLIENT63 / V3.8 | 09-17/18 | RC7 live-acceptance tooling; last recorded engineering entry |

## Phase 3 — Field use and repository consolidation (2026-09-23 → 2026-10-04)

- 2026-09-23 → 10-03: RC7 JAR runs in the Technic client in `direct` mode (39 sessions with the preInit line). No new engineering checkpoint was recorded between 2026-09-18 and this assembly.
- 2026-10-04: this repository assembled — history completed with upstream, dirty tree snapshotted (`ee8920f`), RC7 source imported (`26023d4`), evidence archived, operator tooling committed, field logs analysed ([`FIELD_EVIDENCE_2026-10.md`](FIELD_EVIDENCE_2026-10.md)), issues opened.

## Dead ends and corrections worth remembering

- **Native package relocation** was rejected by QA; JNI names must stay intact.
- **FEC** was deliberately not shipped: the native JNI wrapper lacks symmetric FEC controls, and enabling it on the Java backend only would make semantics backend-dependent.
- **CLIENT19 vs CLIENT18:** CLIENT19 branched from CLIENT17 and would have regressed CLIENT18 telemetry; CLIENT20 was rebuilt from CLIENT18 and selectively merged.
- **Heartbeat assumption (CLIENT40):** a long soak exposed a QA-only assumption about Ping/Pong; the harness was fixed, not the production code.
- **RC1–RC3 Gateway JAR** differed from RC2+ (`2218962a…` vs `e4329bed…`); the clean rebuild at CLIENT36 also corrected stale historical artifacts.
- **Progress estimates:** the planning figure rose from ~60 % (2026-09-13) to 99 % (2026-09-17) largely on added automated evidence; the project's own rule now forbids raising it for more synthetic tests.
