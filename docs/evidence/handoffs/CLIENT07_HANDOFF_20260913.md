# Akashic Voice - CLIENT07 verified candidate

Checkpoint: AV-EPOCH-CLIENT-20260913-07. Not a production release.
Candidate: audit/group-integration-20260913-04/epoch-client-20260913-07.
Active source files and dist/homologation-5eb51e1 were not replaced.

## Delivered in this continuation
Recovered CLIENT06 results and preserved a 213-file candidate snapshot.
Added PlaybackCleanupSmoke and reproduced four failures before the patch.
Patched AudioPlayback in CLIENT07 only: close failures do not escape retirement,
old-channel close failure does not drop a successor frame, and stopped playback
rejects submissions under the same lock used during shutdown.
RuntimeException/LinkageError are isolated at channel cleanup; fatal VM errors
are not swallowed by that cleanup helper. This is not a claim about all error paths.

## Executed gates
- cleanup-before: 4 FAIL, 1 PASS (baseline reproduction).
- cleanup-after: all 5 cleanup scenarios and all 13 client-flow scenarios PASS.
- legacy-package: full legacy Gradle build PASS, 34 tasks executed.
- full-java8-matrix: actual JDK 8 compiled 124 source files; 23 smoke executions PASS.
- full-platform: 124 JUnit tests in 27 suites; zero failures/errors/skipped.
- packaged-java8: 24 executions PASS, including 23 repeated smokes and class-origin
  verification against the DEV jar, without production loose classes on the classpath.
  Strict-native-unavailable smoke uses a deliberately stripped QA-only copy.
- DistributionJarSmoke: reobfuscated jar PASS; 264 Java-8 classes, 5 JNI classes,
  7 native binaries and required license notices, including 33 inventory entries.
All run logs/status/source manifests are in runs-client07/<gate>/.
No physical microphone, real Minecraft client, production server or mobile device was started.

## Artifact locations and SHA-256
All paths below are relative to the CLIENT07 candidate directory.
Mod: build/libs/akashicvoice-0.1.0-alpha.5-akashic-voice.11+022dc1f922-dirty.jar
2db006fb8386cfd337dfc0a06afe04d463d0b9d85474cc953ff66d8de431bcb5
Gateway: platform/gateway-velocity/build/libs/gateway-velocity-0.1.0-SNAPSHOT.jar
8f1eb303384fa5419cd1b221d87bc2ea0421afa155f4dd7d03625d28dd8d1a65
Paper: platform/svc-paper/build/libs/svc-paper-0.1.0-SNAPSHOT.jar
e5919e987cf006e126b4acbe1caa9b959a5dc9f8b6d34877c197405347b0517a
Class majors: mod 52; gateway 52/65/69; Paper 52/65.
Final read-only verification: 282 active inputs and 299 candidate inputs matched their
recorded hashes. These are monitored inputs, not an assertion about every desktop file.
The packaged-only runner tracks artifact hashes; source hashes were checked independently.
The client07 build lock was absent after all jobs completed.

## Restrictions and next gates
Creation of package_client07.py was denied by the tool security layer; ENOENT confirmed
that no script was created. No retry or alternate route was used to create that packager.
The compiled artifacts already exist, but a consolidated delivery ZIP was not produced.
Keep the active source tree, existing distribution and production deployment unchanged.
Next required evidence: real-client audible speech, repeated Lobby/Forbidden transfers,
OpenAL/native-device lifecycle, performance under gameplay, mobile and remaining product scope.
A source review also flags spatial off/on cached-position restoration in PlaybackChannel
for a dedicated native-call contract regression test; it is not repaired by this patch.
Do not claim all product features complete or increase production approval from test counts.
Official references: Oracle Java SE 8 LinkageError/VirtualMachineError API documentation,
and JVMS SE25 section 4.1 for class version mapping. Tests remain the evidence for this code.
