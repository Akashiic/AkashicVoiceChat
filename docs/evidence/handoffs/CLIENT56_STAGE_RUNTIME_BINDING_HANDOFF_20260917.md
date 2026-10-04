# Akashic Voice - CLIENT56 stage runtime binding

Checkpoint: `AV-CANARY-STAGE-RUNTIME-BINDING-20260917-56`.
Release under test remains sealed `release-candidate-20260917-rc5` from CLIENT47.
No production source, JAR, configuration or deployed process changed.

## Problem reproduced

The v3.4 operator kit revalidated Forge/Gateway/Paper when opening a canary session, but objective stages did not re-hash them.
A controlled negative test changed the installed Gateway after session creation; v3.4 still accepted Baseline.
Receipt: `CLIENT56-MIDRUN-DRIFT-V34-REPRO.json`.

## v3.5 hardening

`assert-runtime-v3.ps1` verifies the copied install receipt hash and re-hashes all three installed runtime JARs.
Baseline, Restart and Cleanup call it before accepting status evidence.
Each stage receipt records `AV-CANARY-STAGE-RUNTIME-BINDING-20260917-56` plus the runtime hash set.
Restart/Cleanup require the previous stage receipt to carry the same bound hashes.
Observation preparation and final evaluation re-run the same runtime guard.
Final evaluation also requires all three objective stage receipts to match the session runtime hash set.

## Executed evidence

- CLIENT56 focused contracts: **15/15 PASS**.
- Copied install-receipt tamper rejected before Baseline.
- Runtime drift rejected before Baseline, Restart, Cleanup, observation preparation and final evaluation.
- Previous-stage runtime-hash tamper rejected.
- Restored valid runtime preserves the intended HOLD path for blank subjective observations.
- v3.5 sealed bundle: `akashic-voice-rc5-homologation-v3.5-20260917.zip`.
- SHA-256: `fba17150a6586cccb8f27e4571aa4222a9be27e4d99379b3276abf4849aa2e90`.
- Bundle seal: **49/49 PASS**.
- Extracted bundle smoke: **20/20 PASS**.
- Final CLIENT56 consolidation: **12/12 PASS**.

Progress remains approximately **98%**, judgment band **96-99%**.
This closes an operator-evidence race; it does not substitute for real WAN/audio/mobile/canary observations.
Remaining completion is live evidence only. No deployment or stable-release approval occurred.
