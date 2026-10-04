# Akashic Voice - CLIENT48 Canary evidence engine

Checkpoint: `AV-CANARY-EVIDENCE-ENGINE-20260917-48`.
Release under evaluation remains sealed RC5 / CLIENT47.
State: homologation tooling fully tested; **no live canary or deployment performed**.

## Scope

CLIENT48 changes zero production source files and zero release artifacts.
It adds a parallel homologation-v3 kit that derives canary decisions from primitive observations instead of trusting a manually typed overall PASS.
The sealed RC5 directory and the previous homologation kit remain byte-identical to their receipts.

The v3 evaluator checks:
- exact RC5 checkpoint and current sealed release-manifest SHA-256;
- PASS JSON artifact-verification receipt;
- Gateway/route/Legacy-Agent baseline health;
- two-client bidirectional listening and expected spatial semantics;
- cross-server transfer recovery with fresh voice session;
- backend restart recovery, increasing Agent connects and monotonic dropped-media counter;
- queue drain and clean session/managed-player cleanup;
- physical acoustic observations;
- real Pojav and Amethyst observations;
- planned-vs-observed canary duration/tester count, crashes, disconnects and feedback.

## Executed evidence

- Evaluator synthetic matrix: 10/10 PASS.
- Positive complete evidence: exit 0 / `READY_FOR_STABLE_REVIEW`.
- Nine negative scenarios: exit 2 / `HOLD` (hash mismatch, drop regression, missing reconnect, stuck queue, session leak, ghost audio, clipping, missing Amethyst, crash).
- Blank/incomplete template: structured HOLD exit 2 with explicit schema errors, no stack-trace-only failure.
- Static/immutability audit: 16/16 PASS.
- Old homologation kit hashes: 5/5 unchanged.
- RC5 artifact hashes: 4/4 unchanged.
- `verify-install.ps1` copy hash matches the previously sealed script.
- v3 evidence schema has no PSK/secret/password/private-key/access-token field.

## Boundaries

CLIENT48 does not manufacture live evidence and cannot close the four remaining real-world gates by itself.
Physical microphone/speaker quality, WAN listening/transfer/restart, real Pojav/Amethyst thermal/battery and the production canary still require human/device observations.
The evaluator deliberately returns HOLD for missing evidence and does not deploy, restart, promote, tag or roll back any server automatically.

Planning estimate therefore remains **approximately 96%**, judgment band **92-98%**.
This checkpoint improves canary decision integrity and operator repeatability; it does not receive artificial product-completion credit.
