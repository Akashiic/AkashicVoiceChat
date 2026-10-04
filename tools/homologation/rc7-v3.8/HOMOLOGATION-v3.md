# Akashic Voice RC7 - canary evidence engine v3

Checkpoint tooling: `AV-PREMIUM-LIVE-ACCEPTANCE-V38-20260917-63`.
V3.8 extends the proven v3.7 evidence chain with RC7-specific premium live-acceptance gates.
Release under test is sealed RC7: `AV-PREMIUM-HARDENING-RC7-20260917-58`.
This kit does not deploy, restart, stop or modify any Minecraft server.
It never requests PSKs, bootstrap secrets, private keys, tokens or raw voice payloads.

## Before the canary

1. Keep the existing RC7 release directory unchanged.
2. Run `verify-install.ps1` against the exact JARs intended for installation.
3. Copy `CANARY-EVIDENCE-v3.json` to a new evidence file for the real run.
4. Fill operator/environment fields and the predeclared canary duration/tester count before testing.
5. Record the SHA-256 of RC7 `RELEASE-MANIFEST.json` and the PASS verification receipt path.
6. Record baseline `/avgateway status` and `/avdebug` numeric values into the structured baseline fields.

A blank or incomplete evidence file is intentionally evaluated as HOLD.
Do not replace missing observations with zero unless the observed value was actually zero.

## During the canary

Use two real clients on the intended network path for listening and cross-server transfer.
Record whether voice resumes with a fresh session and whether duplicate/ghost audio occurs.
For the controlled backend restart, record Agent `connects`, cumulative `dropped`, queue and route state.
`connects` must increase, `dropped` must never regress, and the queue must drain after speech.
For physical audio, test normal speech, whisper, loud speech, silence and background noise on the real devices.
Record clipping, pumping, unwanted echo and intelligibility. Automated codec tests do not substitute for this.

RC7 additionally requires the V38 premium observations for physical output hot-plug, environmental acoustics, nametag suffix alignment and PTT visual timing. Follow `LIVE-ACCEPTANCE-v3.8.md`; leaving any of those blocks blank is intentionally HOLD. The acoustic review must also record an explicit AGC/limiter decision.

For mobile, record at least one real Pojav result and one real Amethyst result, including:
- device and OS;
- sustained duration;
- join/PTT/listening result;
- input/UI issue flag;
- thermal issue flag;
- a battery observation.

The evaluator does not invent temperature or battery thresholds. It requires the real observation and a no-issue judgment.

## Cleanup and evaluation

After canary clients leave, record final active sessions, pending handshakes, managed players and Agent queue.
Final active/pending/managed counts must be no higher than their recorded baseline, and the Agent queue must be zero.
Any stale voice is HOLD.

Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\evaluate-canary-v3.ps1 `
  -EvidenceJson .\my-real-canary.json -OutJson .\my-real-canary-report.json
```

Exit `0` means `READY_FOR_STABLE_REVIEW`; exit `2` means HOLD.
The evaluator derives gates from primitive observations and ignores any manual overall PASS label.
In v3.8 the standalone evaluator also validates the verification receipt checkpoint, required runtime roles,
sealed hashes and the current bytes at every receipt path. A stale or fabricated PASS receipt is HOLD.
Standalone evaluation remains diagnostic: stable review must use `evaluate-session-v3.ps1`, which additionally
binds runtime paths, run identity, objective stage hashes and mid-run runtime hashes.
Stable release approval remains a human review after all gates pass; the evaluator does not deploy or promote RC7.

## CLIENT50 rollback preflight

Before manually replacing runtime JARs, use `prepare-rollback-v3.ps1` to create a fresh hash-verified backup of the currently installed Forge/Gateway/Paper JARs.
Then run `verify-rollback-v3.ps1`; a FAIL is a stop condition.
`restore-rollback-v3.ps1` is dry-run by default and requires `-ExecuteRollback` before it writes anything.
The rollback scripts deliberately exclude configuration/PSK files. See `ROLLBACK-v3.md` for the full procedure.

## CLIENT51 guarded install preflight

After `prepare-rollback-v3.ps1` and `verify-rollback-v3.ps1` pass, run `install-canary-v3.ps1` once without `-ExecuteInstall`.
The dry-run must report `READY_DRY_RUN` and zero destination writes.
The installer refuses to proceed if the installed JARs no longer match the rollback snapshot or if any sealed RC7 source hash differs.
Only an explicit `-ExecuteInstall` activates the three runtime JARs; configuration and PSK files are outside the operation.
If activation fails after one or more replacements, touched artifacts are restored and re-hashed before the script returns failure.
See `INSTALL-v3.md` for the complete procedure and stop conditions.

## CLIENT52 evidence-chain workflow

Create a unique canary run with `new-canary-session-v3.ps1` only after rollback, explicit install and verify-install receipts are PASS.
Use `apply-session-status-v3.ps1` in strict Baseline → Restart → Cleanup order; every stage is hash chained and refuses overwrite.
After Cleanup, run `prepare-observations-v3.ps1`, fill only the live observation fields in `evidence-40-observations.json`, then run `evaluate-session-v3.ps1`.
The finalizer revalidates all objective evidence/status hashes and immutable run identity before invoking the existing evaluator.
See `EVIDENCE-CHAIN-v3.md`; synthetic QA PASS data is not live evidence and must never be reused for release approval.
