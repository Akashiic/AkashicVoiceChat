# Akashic Voice - CLIENT58 standalone receipt binding

Checkpoint: `AV-CANARY-STANDALONE-RECEIPT-BINDING-20260917-58`.
Release under test remains sealed `release-candidate-20260917-rc5` from CLIENT47.
No production source, JAR, configuration, runtime or deployed process changed.

## Defect reproduced

The v3.5 standalone `evaluate-canary-v3.ps1` only required the referenced verification JSON to contain
`state=PASS`. A disposable coherent synthetic evidence file plus a fabricated receipt containing only
`{"state":"PASS"}` returned `READY_FOR_STABLE_REVIEW`.
This did not bypass the stronger v3.5 `evaluate-session-v3.ps1` evidence-chain/runtime binding, but the
standalone evaluator was documented as a valid diagnostic path and therefore required fail-closed hardening.

## v3.6 hardening

The standalone evaluator now requires the sealed RC5 checkpoint, exactly one Forge/Gateway/Paper result,
verified result flags, the sealed SHA-256 values, a present file path, and a fresh SHA-256 match of the
current bytes at every receipt path. Fabricated, stale, incomplete or hash-inconsistent receipts are HOLD.
Documentation now states explicitly that standalone evaluation is diagnostic; stable review must use the
session evaluator because it additionally binds runtime paths, run identity and objective stage receipts.

## Executed evidence

- v3.5 negative control: fabricated minimal PASS receipt was accepted as `READY_FOR_STABLE_REVIEW`.
- v3.6 legitimate current receipt: `artifact_integrity` PASS and coherent synthetic evidence remains READY.
- v3.6 negative matrix: 6/6 invalid receipts returned HOLD: minimal PASS, wrong checkpoint, missing Paper,
  wrong Gateway hash, missing verified path, and a verified path whose bytes changed after the receipt.
- Blank live evidence with a valid current receipt: artifact integrity PASS, overall decision HOLD.
- v3.6 sealed bundle: `akashic-voice-rc5-homologation-v3.6-20260917.zip`.
- Bundle SHA-256: `7bc26aa13991d1e4f2ae9093d38ef0f7fcd84d60a36dcc2a574b6446879f9b0e`.
- Bundle seal: 51/51 PASS.
- Extracted bundle smoke: 21/21 PASS, including rollback, guarded install, verify, evidence chain,
  blank HOLD, coherent synthetic READY, fabricated standalone receipt rejection and verified rollback.
- Fresh final consolidation re-hashed all four RC5 artifacts and they still match `RELEASE-MANIFEST.json`.

Receipts: `CLIENT58_FINAL_RECEIPT.json`, `RC5-HOMOLOGATION-V3.6-BUNDLE-RECEIPT.json`,
`CLIENT58-BUNDLE-SMOKE-RECEIPT.json`, and `PRECANARY_READINESS_V3_6_20260917.json`.

Progress remains approximately **98%** (96-99% judgment band). Remaining completion still requires real
WAN/two-client transfer/restart, physical acoustic/AGC observations, Pojav/Amethyst device evidence and a
controlled production canary/operator-player review. No deployment or stable-release approval occurred.
