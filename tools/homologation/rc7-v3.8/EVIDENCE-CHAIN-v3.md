# Canary evidence chain v3

CLIENT52 adds immutable run identity and SHA-256 chaining around the existing v3 evidence evaluator.
It does not create observations, infer listening quality or turn missing live evidence into PASS.

## 1. Create one run

After RC5 installation and `verify-install.ps1` PASS, run `new-canary-session-v3.ps1` with:
- a new, nonexistent `OutDir`;
- operator/environment labels;
- planned duration and tester count;
- the PASS verify-install receipt;
- the PASS install receipt;
- the verified rollback manifest.

The command validates all three runtime roles, rollback files and release checkpoint before creating anything.
It writes `evidence-00-seeded.json` plus an immutable `RUN-MANIFEST.json` with a unique run ID and hashes.
Never reuse an old run directory for a new canary.
## 2. Apply objective status stages

Capture Gateway `/avgateway status` and legacy `/avdebug`, parse them with `parse-status-v3.ps1`, then apply with:

```powershell
.\apply-session-status-v3.ps1 -RunDir <run> -StatusJson <status.json> -Stage Baseline
.\apply-session-status-v3.ps1 -RunDir <run> -StatusJson <status.json> -Stage Restart
.\apply-session-status-v3.ps1 -RunDir <run> -StatusJson <status.json> -Stage Cleanup
```

Stages are ordered. Each writes a new evidence file, canonical status copy and receipt containing source/status/output hashes.
Reapplying a stage, skipping a predecessor, editing an earlier evidence file or changing canonical status after application is a hard failure.

## 3. Record live observations

After Cleanup passes the chain, run `prepare-observations-v3.ps1 -RunDir <run>`.
Only `evidence-40-observations.json` is intended for manual live observations.
Artifact identity, environment, baseline counters, restart counters, cleanup counters, provenance and predeclared plan are immutable.
## 4. Finalize and evaluate

Run:

```powershell
.\evaluate-session-v3.ps1 -RunDir <run>
```

The wrapper re-hashes the complete Baseline→Restart→Cleanup chain and rejects altered objective status/evidence.
It writes `evidence-50-final.json`, `CANARY-REPORT.json` and `SESSION-FINAL-RECEIPT.json`.
A blank or incomplete observation set remains `HOLD` with exit code 2.
Only coherent evidence satisfying the existing evaluator can produce `READY_FOR_STABLE_REVIEW`.

The automated synthetic PASS in QA exists only to prove the evaluator plumbing; it is never live evidence.
Stable release approval still requires the real WAN/listening/acoustic/mobile/canary observations described in `HOMOLOGATION-v3.md`.

## v3.3 live runtime binding
Before a run is created, the current Forge, Gateway and Paper destinations from the PASS install receipt are re-hashed and must still equal the sealed RC5 hashes. A stale PASS install/verification receipt cannot authorize a run after runtime drift or critical rollback residue. The three current runtime hashes are included in run provenance; no configuration or PSK contents are copied.


## v3.4 path binding
Canary session creation cross-checks each Forge/Gateway/Paper runtime across the PASS install receipt destination, rollback manifest original_path, verification receipt path, sealed release source path and current SHA-256. Same-hash copies at unrelated paths cannot substitute for the runtime that the rollback plan identifies.


## v3.5 stage runtime binding
The current Forge, Gateway and Paper JARs are re-hashed before Baseline, Restart, Cleanup, observation preparation and final evaluation. Each objective stage receipt records the same runtime hash set bound at session creation. Runtime drift, copied install-receipt tampering, previous-stage runtime tampering, or a changed runtime before final review fail closed before the next evidence artifact is created.

## v3.6 standalone receipt binding
`evaluate-canary-v3.ps1` no longer trusts `state=PASS` by itself. It requires the sealed RC5 checkpoint,
exactly one Forge/Gateway/Paper result, verified result flags, sealed SHA-256 values and current matching bytes
at each receipt path. This closes a diagnostic-evaluator bypass reproduced against v3.5.
The evidence-chain workflow remains stronger and remains mandatory for stable review because it additionally
binds runtime destinations, immutable run identity and Baseline/Restart/Cleanup receipts.

