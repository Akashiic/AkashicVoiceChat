# Akashic Voice - microphone recovery audit

Checkpoint: **AV-2026-09-13-MIC-RECOVERY-01**.
Scope: Akashic Voice only. Immersive Petroleum and other projects were not modified.
Approval: deterministic recovery tests and builds PASS; real-client/mobile/production approval remains OPEN.
Base HEAD: `022dc1f922a107eb10f4f6b20d3b550194e8b7ae`; pre-existing dirty changes were preserved.

## Reproduced defect and fix
`MicCapture` restarted its local sequence at zero when a microphone was reopened.
The live `VoiceClient` forwarded that number unchanged to a receiver whose jitter sequence continued.
A real encrypted UDP loopback test failed: **expected 3, received 0** after capture restart.
The media sequence now belongs to the VoiceClient transport session, under its existing send lock.
Capture restarts no longer rewind it. Wire layout, bitrate and AEAD counters are unchanged.

`MicCaptureSession` isolates each generation's device and encoder.
Stop closes its device but leaves an in-flight encoder to the capture worker's finally block.
Old-generation cleanup cannot dispose replacement resources. EOF, runtime read/encode failure and
interruption clean up the worker. Normal encoder-start failures are caught by existing recovery logic.
Empty reads wait briefly instead of spinning. High-pass arithmetic is unchanged; state is per generation.

## New executed regression coverage
`MicSequenceRecoverySmoke`: capture restarts, unauthenticated suppression, signed sequence wrap.
`MicCaptureSessionSmoke`: stop during encode, encoding failure, zero-read/EOF, generation isolation,
and cleanup failure/idempotence. These are eight scenarios using actual production paths with
loopback UDP/fake hardware, not physical microphones or live Minecraft clients.

An initial concurrency-test watchdog expired during a delayed run and falsely resembled disposal.
It was replaced by an explicit release latch; the disposal assertions were retained.
The outer finite QA process timeout and harness cleanup still bound test execution.

## Executed evidence
- `audit/qa-runs/mic-recovery-baseline-20260913`: FAIL, reproduces sequence rewind before the patch.
- `audit/qa-runs/mic-legacy-build-20260913`: PASS, full Gradle legacy build, 27 executed tasks.
- `audit/qa-runs/legacy-full-javac8-final-20260913`: PASS, actual JDK 8u411 recompilation of
  114 production/QA sources and 19 standalone test executions, including both new smoke classes.
- Legacy matrix includes native/Java codec interoperability, native-required fallback policy,
  jitter, capture selection, replay/crypto/handshake and rate-limit tests.
- Fuzz smoke: 50,000 parser inputs and 5,000 forged crypto envelopes; concurrent seal: 2,000 counters.
- `audit/qa-runs/platform-build-recovery3-20260913`: PASS, full platform build, 61 JUnit tests,
  zero failures/errors. Includes 12 inherited group-codec tests executed in this continuation.
- Distribution inspection: all 256 classes major 52, 5 JNI classes, 7 native binaries,
  required notices present and 33 inventory license entries verified by the existing smoke tool.

## Concurrent work and approval boundary
Other writes to the group codec/tests were observed. Those files were not overwritten by this patch.
An earlier platform run saw two failing aggregate-size budget tests plus a new staging file.
The final passing 61-test suite does NOT include those temporary budget tests, and the codec still
has count/string limits rather than an aggregate output-size budget. This remains an explicit finding,
not a resolved defect. Implement/test aggregate limits before exposing federated controls in production.
Group control wiring, bounded invitations/persistence/permissions and live SVC/mobile gates remain open.
A passing build/test snapshot is not proof of audible speech, real network soak or modpack compatibility.

## Verified local candidate (not a release)
`build/libs/akashicvoice-0.1.0-alpha.5-akashic-voice.11+022dc1f922-dirty.jar`
SHA-256: `5794d4536020ca189b4e2e1d1af7babf69bf429803386715b17af4bab39430e8`.
A verified copy is under `audit/continuation-20260913-groups/verified-artifacts/`.
`dist/homologation-5eb51e1` was not replaced. No production deployment or release tag was made.

## Continuity and recovery tooling
`tools/qa/run_platform_qa.py`: finite Gradle jobs, local logs, heartbeat, PID/exit receipts,
exclusive build lock and source fingerprint checks. An unexpected input change marks a run STALE.
`tools/qa/run_mic_recovery_qa.py --all-smokes`: actual Java 8 source/test snapshots and finite tests.
Both runners keep outputs in unique `audit/qa-runs/<label>` directories instead of streaming large logs.
After lost transport, read status and a bounded log tail; do not start a duplicate job or clear a live lock.
A preflight bug selecting the `.gradle` directory was corrected; its confirmed dead-runner lock was
removed after process verification. Blocked/failed/stale historical runs remain recorded, not hidden.
These tools do not control or guarantee Desktop Commander reconnection and are not a permanent daemon.

Initial verified backup: `audit/continuation-20260913-groups/baseline/` (288 files).
Final machine-readable receipt: `audit/continuation-20260913-groups/FINAL-RECEIPT.json`.
Source hashes were rechecked against the final Java 8 test snapshot before recording approval.
Review here means implementation review and executed tests by this assistant, not independent reviewers.
Progress estimate remains approximately 60% of the full product (55-65% judgment band), not a release metric.
Next work: coordinate ownership of group controls, preserve this recovery regression suite, and close
aggregate bounds/authority/identity/control integration before full live deployment validation.
