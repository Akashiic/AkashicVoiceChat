# Akashic Voice - CLIENT52 canary evidence chain

Checkpoint: `AV-CANARY-EVIDENCE-CHAIN-20260917-52`.
Release under test remains sealed `release-candidate-20260917-rc5` from CLIENT47.
No production source, JAR, configuration, PSK or deployed process changed in CLIENT52.

## Delivered

CLIENT52 adds a run-scoped, SHA-256 chained wrapper around the existing fail-closed canary evaluator.
`new-canary-session-v3.ps1` requires PASS installation verification, PASS install receipt and verified rollback material before creating a run.
Every run receives a fresh GUID and immutable seed/provenance hashes.
Baseline, Restart and Cleanup are applied in strict order by `apply-session-status-v3.ps1`.
Each stage records the SHA-256 of its input evidence, normalized status JSON and output evidence.
Existing stage outputs are never overwritten.

`prepare-observations-v3.ps1` only opens the manual observation phase after Cleanup exists and its hash matches.
`evaluate-session-v3.ps1` re-hashes the complete stage chain before invoking `evaluate-canary-v3.ps1`.
Objective baseline/restart/cleanup counters, artifact identity, environment, provenance and predeclared duration/tester count cannot be changed during manual observations.
Subjective fields such as listening, restart voice recovery, acoustic/mobile results and stale-voice observation remain writable because they must come from real testers.
## Executed evidence

- Evidence-chain workflow: **15/15 PASS**.
- Additional provenance hardening: **3/3 PASS**.
- Static audit after final hardening: **74/74 PASS**.
- Blank live observations: structured **HOLD**, never inferred as PASS.
- Objective status/evidence tampering: rejected before final report creation.
- Verification receipt missing Paper: rejected before run directory creation.
- Rollback manifest missing Paper: rejected before run directory creation.
- Coherent synthetic QA observations can reach `READY_FOR_STABLE_REVIEW`; they are labeled synthetic and are not release evidence.
- Synthetic config secret was absent from evidence-session files.

CLIENT51 remains the install/rollback safety foundation:
- install contracts 10/10 PASS;
- injected partial-activation recovery 7/7 PASS;
- install static audit 33/33 PASS;
- extracted v3.1 bundle smoke 8/8 PASS.

## Sealed v3.2 operator bundle

`akashic-voice-rc5-homologation-v3.2-20260917.zip`
SHA-256: `ab88d189471784242906497bb759642690c2ee5d6f25b6728c21de240d76b6ac`.
Bundle seal: 41/41 PASS; 8 sealed RC5 release files and 28 homologation files.
An independent extraction smoke passed 12/12: rollback, install, verify, evidence-chain HOLD/READY synthetic paths and restore all worked using only extracted bundle contents.
## Boundary and progress

Planning estimate: **approximately 98%**, judgment band **96-99%**.
This percentage is a planning estimate for the complete product, not a count of tests or a stable-release score.

Remaining completion gates are intentionally not automatable substitutes:
1. real WAN/two-client listening plus production cross-server transfer and controlled backend restart;
2. physical microphone/speaker acoustic and AGC validation;
3. real Pojav and Amethyst thermal/battery/input validation;
4. controlled production canary with operator/player feedback and final cleanup evidence.

`READY_FOR_STABLE_REVIEW` from synthetic QA only proves evaluator plumbing.
No live canary has been performed, no production deployment occurred and RC5 is not yet approved as stable.
Use `PRECANARY_READINESS_V3_2_20260917.json` and `CLIENT52_FINAL_RECEIPT.json` as the current qualification summaries.
