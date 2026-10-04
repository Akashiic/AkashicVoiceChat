# Akashic Voice - CLIENT15 consolidation, 2026-09-14

Checkpoint: AV-QUALITY-CONSOLIDATION-20260914-15.
Candidate: audit/group-integration-20260913-04/reviewed-client15-20260914.
State: full Forge build, actual Java8 smoke matrix and platform tests PASS.
This is an audit candidate, not active-source promotion or a stable/production release.

## Recovery and ownership
The initial fixture-fix invocation stopped at its source-shape assertion without writing.
A fresh read showed the correction had appeared concurrently in integrated-client15.
Its successful capture-flow receipt was inspected; the source was then copied into a
separate 328-file, hash-verified snapshot to avoid competing source edits.
No existing candidate, active source file, distribution or other project was overwritten.
A longer snapshot path exceeded the Windows process command-line limit before javac started.
The owned QA runner was repaired to use the Java8 javac @argument-file interface.
The initial ERROR receipt is retained; argument files and their hashes are now recorded.

## Delivered validation
The prior missing capture path now passes in the reviewed snapshot with real Opus,
production MicCapture/VoiceClient and encrypted local UDP; only the microphone is synthetic.
The existing 18 quality/configuration cases pass, including custom numeric configuration,
partial setter failure/retry, 48k packet compatibility and cross-decoding rate changes.
New LiveBitrateConcurrencySmoke adds 8 executed scenarios: nonblocking publication during
encode, orderly close, serialized encoding callers, supplier/encode failures, fatal error
propagation, idempotent failed close and concurrent publication/final-state convergence.
These tests do not start Minecraft or physical audio devices.

## Executed gates - runs-reviewed15-20260914
- initial-quality: ERROR before compilation (Windows command length); no source mutation.
- initial-quality-args: PASS, core quality and capture/network tests after runner repair.
- concurrency: PASS, 8 new scenarios.
- format: PASS, candidate-only formatter.
- full-legacy: PASS, complete Forge build including the quality GUI.
- full-java8: PASS, actual JDK8 compiled 141 sources, 32 smoke-main executions.
- full-platform: PASS, 124 JUnit tests in 27 suites, zero failures/errors/skips.
- mutations: PASS, 8 compiled defective variants detected by runtime assertions.
The final three build/test receipts report zero active/candidate input changes during execution.
The 18 quality, 2 capture-flow and 8 concurrency scenarios are included in the 32 mains;
they are not 28 additional executables. This round does not rerun JAR-based test mains.

Mod: reviewed-client15-20260914/build/libs/akashicvoice-0.1.0-alpha.5-akashic-voice.11+022dc1f922-dirty.jar
SHA256: 6a1f1a401ab167800ddeed42c6d6ad5f3c94d3202b543894daffa206587934dc.
DistributionJarSmoke PASS: 280 classes, all major 52; 5 JNI classes, 7 native binaries,
required license notices and 33 license-inventory entries checked by the existing tool.
The Forge-reobfuscated artifact received structural inspection, not actual game startup.

## Remaining boundaries
Completing a new packaged-test runner was security-blocked. Its partial source was renamed
packagecheck_reviewed15_20260914.py.incomplete and was NOT executed or recreated elsewhere.
A subsequent combined read-only optional inspection was blocked; its intended additional
checks are not claimed. The artifact/hash/DistributionJarSmoke checks above completed earlier.
GUI rendering/clicks, physical microphone/OpenAL, real backend transfers, gameplay/mobile,
other roadmap features and production release approval remain open. No active-source promotion.
