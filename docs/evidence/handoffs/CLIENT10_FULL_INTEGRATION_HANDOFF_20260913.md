# Akashic Voice - CLIENT10 complete Windows integration
Checkpoint: AV-CLIENT10-FULL-INTEGRATION-20260913.
State: FULL PROJECT BUILT/TESTED, NOT PROMOTED TO ACTIVE SOURCE OR PRODUCTION.
Build tree: audit/group-integration-20260913-04/integrated-client10.
Consolidates CLIENT08 foundation plus cumulative CLIENT09/CLIENT10 changes.
Cumulative patch matched the original source hashes after Git CRLF normalization.

Executed evidence in runs-integrated10:
- integrated-listening: 7 production crypto/client/decoder/playback scenarios PASS.
- legacy-build: PASS, complete Forge build, 34 tasks executed.
- platform-build: PASS, 124 JUnit tests, 27 suites, zero failures/errors/skipped.
- java8-final: actual JDK8 compiled 125 files; 24 smoke executions PASS.
- packaged-java8: 24 repeated smokes plus class-origin test PASS (DEV jar).
- Reobfuscated JAR inspection: 265 classes at major 52, 5 JNI classes, 7 natives,
  required license notices and 33 inventory entries; no QA classes bundled.
New integration cases include deafen during stream/rekey/epoch changes, two speakers,
full control queue, rapid toggles, and rejecting in-flight old PCM.
The previous 52 isolated contracts are not counted as rerun in this Windows matrix.

Mod SHA256: e50c084ec7809c29fdc1c445bd0ec826f610c49ae00fda46bc687dbb73b16f9d.
Mod: integrated-client10/build/libs/akashicvoice-0.1.0-alpha.5-akashic-voice.11+022dc1f922-dirty.jar.
Gateway SHA256: 8f1eb303384fa5419cd1b221d87bc2ea0421afa155f4dd7d03625d28dd8d1a65.
Paper SHA256: e5919e987cf006e126b4acbe1caa9b959a5dc9f8b6d34877c197405347b0517a.
Gateway/Paper binaries match CLIENT08. Existing distribution unchanged.
Final source verification: 282 active and 303 candidate inputs, zero mismatches.
Active promotion script completion was security-blocked; NOT executed, archived incomplete.
The summary-script append was also blocked; no execution or final JSON receipt from it.
No physical audio, game startup, mobile validation or deployment occurred.
Shared master volume and remaining release gates are still OPEN. Build lock absent at finish.
