# Homologation

Homologation here means **controlled pre-production validation of a sealed candidate**. It is not a release, and passing it does not deploy anything. Stable release requires every gate in §5 to close on real evidence and a human decision.

## 1. Candidate under homologation

| Item | Value |
|---|---|
| Candidate | RC7 — checkpoint `AV-PREMIUM-HARDENING-RC7-20260917-58`, state `AUDIT_ONLY_NOT_DEPLOYED` |
| Source | `26023d4` in this repository (imported from `integrated-client58-premium-hardening-rc7`) |
| Forge JAR | `c0f9d86cb331598c2e0e7c9d7ebf6091fffee18bc94bbdaf6bbf9cbe6d2eb081` (installed in the Technic client) |
| Server JARs | Gateway `e4329bed…4ec2`, Paper/SVC `b5e66814…21bdb`, Addon API `6e73a1f9…de347` (unchanged since RC2/RC5) |
| Operator tooling | V3.8 `AV-PREMIUM-LIVE-ACCEPTANCE-V38-20260917-63`; bundle SHA-256 `497273cdd235b33e6678604fcc585b49c04fb966c64f36400d159c99ddca1c2e` (the ZIP embeds the JARs and stays outside git; its 46 text files are in [`tools/homologation/rc7-v3.8/`](../tools/homologation/rc7-v3.8/)) |
| Readiness state | `READY_FOR_RC7_LIVE_ACCEPTANCE_NOT_STABLE` ([receipt](evidence/receipts/PRECANARY_READINESS_V3_8_20260917.json)) |

## 2. Automated gates (all PASS on RC7, as recorded by the sealed receipts)

| Gate | Result |
|---|---|
| Formatting (Spotless) | PASS, no candidate/root drift |
| Forge root build | PASS — 34/34 actionable tasks |
| Actual JDK 8 matrix | PASS — 184 sources, 54 commands |
| Platform regression | PASS — 43 suites / 186 tests / 0 failures / 0 errors / 0 skipped |
| Source-contract mutations | 5/5 detected |
| Compiled behavioural mutations | 4/4 detected |
| JAR-origin replay | PASS — 9 production classes proven from the final JAR, 7 QA replays |
| Seal preflight | PASS — 43/43 |
| Independent sealed verification | PASS — 58/58 |
| V3.8 bundle seal / extracted smoke | 59/59 / 31/31 PASS; 5 premium negative controls HOLD as expected |

Caveats: these ran on loopback or fake audio hardware, prior to this repository's assembly. They were **not re-executed** while assembling the repository (see [`BUILD_AND_QA.md`](BUILD_AND_QA.md)). "Independent" verification is a separate script run by the same engineering process, not an external reviewer.

## 3. Live evidence gathered so far (LIVE-OBSERVED, not approvals)

| Checkpoint | Build | Observation | Does **not** prove |
|---|---|---|---|
| CLIENT59 (2026-09-17) | RC5 | Real Technic client loaded the sealed JAR; FML found `dev.akashic.voice.AkashicVoice`; receive/maintenance/audio/mic threads alive over 7 samples in 124 s; OpenAL capture; native `opus_jni_rust.dll`; secure UDP handshake reached AUTH_ACK | WAN peer attribution, remote media, listening |
| CLIENT60 | RC5 | Clean restart: no voice threads for 116 s while outside a world | — |
| CLIENT61 | RC5 | Archived real multiplayer logs: 3 route changes, each followed by fresh bootstrap and authentication; 6 transport timeouts, all re-authenticated; mic stopped/restarted without crash | Commanded backend restart, two-client listening |
| CLIENT62 | RC5 | Same JAR still installed and running; fresh read-only runtime sample; V3.7 `probe-live-client.ps1` → `PASS_RUNTIME_PREREQUISITES` | Remote media |
| This assembly (2026-10-04) | RC7 (inferred) | 154 desktop client logs, 39 sessions with the mod: 138 authentications, 203 microphone starts, 32 voice-server timeouts, plus the defects in [`FIELD_EVIDENCE_2026-10.md`](FIELD_EVIDENCE_2026-10.md) | Anything about audio content (never read) |
| This assembly — Android | RC7 (hash in managed pack) | 59 real-device QA logs of the Forbidden Mobile Launcher: mod loads, loopback secure session authenticates (10/10), EFX degrades gracefully; **no microphone backend opened (10/10)**; one playback-thread loss | Capture, touch PTT, thermal, battery, WAN |

CLIENT59's own receipt is explicit: `wan_remote_peer_proven: false`, `remote_voice_frames_received_proven: false`, `two_client_listening_proven: false`.

## 4. Decision rules

1. A gate is PASS only with recorded primitive observations (command, source identity, process identity, log path, exit status). The V3 evaluators derive PASS/HOLD from observations and **fail closed** on missing or non-numeric counters, contradictory baseline/final state, or any Agent drop delta in a normal canary.
2. `READY_FOR_STABLE_REVIEW` from an evaluator still requires a human review; it is not a deployment approval.
3. A synthetic result never substitutes for a live observation.
4. Defects found in the field reopen the affected gates. As of this document, issues #3–#5 (OpenAL natives after sound reload; handshake timeouts after transfer; hot-plug event frequency) should be triaged **before** the live premium acceptance in §5 is attempted, otherwise the acceptance would test a path that is known to misbehave.

## 5. Gate status

| # | Gate | Required evidence | State | Issue |
|---|---|---|---|---|
| G1 | Controlled backend process restart | Real restart of the authorised backend/Agent, voice recovers, no stale session or leak, status receipts before/after | OPEN | #9 |
| G2 | Two-client remote listening | Two real clients, bidirectional audible speech, preferably cross-backend, session-bound counters rising on both sides | OPEN | #10 |
| G3 | Acoustic review + AGC/limiter decision | Physical mic/speaker/headset pass (clipping, echo, pumping, intelligibility) and an explicit "AGC not needed" or implement-and-qualify decision | OPEN | #11 |
| G4 | Real mobile acceptance | Pojav (legacy) + Amethyst on real devices: capture, playback, touch PTT, focus/background, thermal, battery, prolonged session | OPEN — failing on the tested device runs (no microphone backend; playback thread loss) | #12, #17, #18 |
| G5 | Controlled production canary | Operator/player feedback, rollback ready, stable telemetry, explicit acceptance | OPEN | #13 |
| G6 | Premium live acceptance | Headset unplug/replug during remote voice; outdoor/room/cave/underwater listening; nametag + PTT visual acceptance | OPEN | #14 |

## 6. Live procedure (V3.8 order)

1. Prepare and verify rollback material (`prepare-rollback-v3.ps1`, `verify-rollback-v3.ps1`).
2. Dry-run the RC7 canary installer (`install-canary-v3.ps1`).
3. At an explicit maintenance boundary, perform the controlled install.
4. Verify installed hashes (`verify-install.ps1`) before collecting evidence.
5. Create a unique session binding runtime paths and hashes (`new-canary-session-v3.ps1`).
6. Capture the Baseline (`parse-status-v3.ps1`, `apply-status-v3.ps1`).
7. Run real two-client speech, backend transfer and controlled backend restart.
8. Physical output hot-plug while remote voice is audible.
9. Outdoor/room/cave/underwater listening and general acoustic review; record the AGC/limiter decision.
10. Review normal/sneaking/sleeping nametag suffix and local/remote PTT visuals.
11. Real Pojav and Amethyst acceptance.
12. Capture Cleanup and finalise the evidence chain (`evaluate-session-v3.ps1`, `evaluate-canary-v3.ps1`).
13. Only after every gate passes may the result enter stable review.

Tooling docs: [`HOMOLOGATION-v3.md`](../tools/homologation/rc7-v3.8/HOMOLOGATION-v3.md), [`LIVE-ACCEPTANCE-v3.8.md`](../tools/homologation/rc7-v3.8/LIVE-ACCEPTANCE-v3.8.md), [`INSTALL-v3.md`](../tools/homologation/rc7-v3.8/INSTALL-v3.md), [`ROLLBACK-v3.md`](../tools/homologation/rc7-v3.8/ROLLBACK-v3.md), [`EVIDENCE-CHAIN-v3.md`](../tools/homologation/rc7-v3.8/EVIDENCE-CHAIN-v3.md).

## 7. Gateway/Paper functional plan

The Gateway + Paper/SVC bridge live test plan (six tests: session establishment, Akashic→SVC audio, SVC→Akashic audio, 10-cycle backend handoff, Agent reconnect, negative PSK, then a multi-hour soak) is preserved in [`docs/homologation/5eb51e1/LIVE_TEST_PLAN.md`](homologation/5eb51e1/LIVE_TEST_PLAN.md) with its config templates and PSK generator. It was written for the 2026-09-12 bundle; artifact names have changed (use the RC JAR names from [`VERSIONING_AND_PROVENANCE.md`](VERSIONING_AND_PROVENANCE.md)) but the procedure and pass criteria still apply. Generate a distinct 256-bit PSK per backend; never put a client bootstrap secret in either config.

## 8. Evidence handling

- Never store session secrets, PSKs, nonces or audio content. The evidence archive in this repository had one production hostname and port redacted (`<public-voice-host>`, `<port>`); no credential values were found by pattern scan (private keys, tokens, 43-char Base64 secrets, IPv4 addresses, e-mail addresses).
- Record every live run in `docs/evidence/` with a unique label; keep failed and stale runs, do not hide them.
- Large artifacts (JARs, bundle ZIPs) stay outside git; record SHA-256 only.
