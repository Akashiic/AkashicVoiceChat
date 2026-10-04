# Akashic Voice - CLIENT08 spatial playback correction

Checkpoint: AV-SPATIAL-CLIENT-20260913-08. Candidate, not a stable release.
Candidate directory: audit/group-integration-20260913-04/epoch-client-20260913-08.
Scope: Akashic Voice only. CLIENT07, active production sources and existing distribution preserved.

## Reproduced defect
PlaybackChannel cached world coordinates while disabling spatial audio moved the native source to zero.
Re-enabling spatial audio did not restore those coordinates; an unchanged position update was skipped.
The native-call contract test reproduced expected (12,64,-9), observed (0,0,0).
Baseline: 9 failing and 6 passing scenarios, not nine independent root causes.

## Production patch (two files only)
- PlaybackChannel restores cached world coordinates on spatial re-enable.
- While flat, setPosition updates the cache without moving the listener-relative source from zero.
- AudioPlayback forwards position metadata even while global spatial playback is disabled.
- No new production dependency, codec/protocol change, gain change or source recreation on toggles.

## Executed evidence
- spatial-before: 9 FAIL / 6 PASS against production classes compiled with real LWJGL2.
- spatial-after: all 15 scenarios PASS; seeded movement/toggle model checks 10,000 operations.
- Recording facade verified against installed LWJGL2: 13 constants and 13 method signatures match.
- legacy-build: full Gradle build PASS; 34 tasks executed.
- full-java8-matrix: actual JDK 8 compiled 124 sources; 23 smoke executions PASS.
- full-platform: 124 JUnit tests in 27 suites; zero failures/errors/skipped.
- packaged-java8: 23 repeated smokes plus one production class-origin check PASS.
- spatial-packaged: all 15 spatial scenarios PASS using production classes from the DEV jar.
All local run receipts/logs: runs-client08/<gate>/.

## Binary and source integrity
Mod: build/libs/akashicvoice-0.1.0-alpha.5-akashic-voice.11+022dc1f922-dirty.jar
SHA256: 4b2f5d107352416c2c53fc53e69b285b05ed8130aa7d0ca22833bb0b4a515704
DistributionJarSmoke PASS: 264 classes at major 52, 5 JNI classes, 7 native binaries,
required notices and 33 license-inventory entries. No test OpenAL facade or contract classes in artifacts.
Gateway SHA256: 8f1eb303384fa5419cd1b221d87bc2ea0421afa155f4dd7d03625d28dd8d1a65
Paper SHA256: e5919e987cf006e126b4acbe1caa9b959a5dc9f8b6d34877c197405347b0517a
Gateway and Paper artifacts are byte-identical to CLIENT07; their sources were not changed here.
Final verification: 282 active inputs and 302 candidate inputs matched recorded hashes.
Two production source files differ from CLIENT07. client08.lock absent after completion.

## Honest boundaries and blocked optional checks
The command-contract tests use the actual production classes and a test-only AL10 recorder.
They do not perform acoustic rendering, live listening, or microphone capture.
The DEV jar was runtime-tested; the reobfuscated Forge jar received structural inspection, not game startup.
Append operations completing a mutation checker and a native-device probe were security-blocked.
Neither was executed; they are preserved as verify_client08_mutations.py.incomplete and
NativeSpatial08.java.incomplete, outside candidate source sets. No workaround was attempted.
There are NO passing mutation or native-device results from this checkpoint.

## Next gates
Keep this candidate and prior regression coverage; do not label it 100% or production-ready.
Still required: real-client listening, OpenAL/device lifecycle, repeated live backend transfers,
long gameplay sessions, mobile compatibility, remaining product features and controlled integration.
Official binding reference: https://legacy.lwjgl.org/javadoc/org/lwjgl/openal/AL10.html
AL_SOURCE_RELATIVE and AL_POSITION determine coordinate interpretation; code/test results above
are local project evidence, not a claim that official documentation certifies this candidate.
