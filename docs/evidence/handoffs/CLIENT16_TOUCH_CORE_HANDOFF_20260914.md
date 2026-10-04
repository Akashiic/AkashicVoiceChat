# Akashic Voice - CLIENT16 touch PTT core and Windows QA runner

Checkpoint: AV-TOUCH-PTT-CORE-20260914-16.
Candidate tree: audit/group-integration-20260913-04/integrated-client16.
State: full candidate builds and inherited QA PASS; new touch GUI NOT delivered.
Active source tree, previous candidates and existing distribution were preserved.
CLIENT15 final receipts were rechecked: quality-legacy/java8/platform-20260914 and packaged QA PASS.

## Implemented source (core, not a user-accessible touch feature)
TouchPttLease is an identity for one explicit press, renewed by a UI controller.
Its one-second monotonic lease expires without GUI work; late renewal cannot revive it.
TouchTalkSession requires non-null route identity, authentication and focus to begin;
continuation additionally requires the same route, held pointer and a live lease.
Touch and physical keyboard state are independent; physical PTT may remain active after touch ends.
MicGate includes touch in the PTT authorization, without changing ALWAYS/VOICE/deafen semantics.
Mute and mode changes revoke touch; VoiceClientHolder.stop, stopMicCapture and crypto replacement
also invalidate transient touch input. Already-dispatched datagrams cannot be recalled.
No per-audio-frame allocation was added by the lease/controller code.
Production delta: MicGate.java, VoiceClient.java, VoiceClientHolder.java and two new control classes.
No protocol, codec, dependency, server module or saved configuration was changed.

## GUI/test restrictions
Writing TouchPttSmoke.java was security-blocked; ENOENT confirmed. It was not created elsewhere on this desktop.
GuiTouchTalk.java was only partly written before a blocked append; it was moved outside the source set to
GuiTouchTalk16.java.incomplete. The hub was NOT linked to a touch button; the GUI is NOT compiled/delivered.
The packaged-test preparation/execution command was blocked; verify_integrated16_packaged.py is absent.
No packaged runtime retest or new touch behavior test executed on the desktop.

## Executed Windows gates (runs-integrated16)
The initial inherited-QA command failed before javac with WinError 206: command line too long.
The generated command was 33,086 characters. The scoped QA runner now writes a quoted ASCII
javac argument file, with 151 arguments and a hash receipt, retaining all compiler flags/sources.
This uses the documented Java8 @argfile interface; no command security restriction was bypassed.
A first patch locator failed on CRLF; no change occurred then. Newline normalization fixed the locator.
- touch-existing-regressions-20260914: existing 12 capture and 23 dispatch scenarios PASS.
- touch-legacy-20260914: full Forge build PASS, 34 executed tasks.
- touch-java8-20260914: actual JDK8 compiled 142 sources; 31 inherited smoke executions PASS.
- touch-platform-20260914: full platform build PASS, 26 tasks; 124 JUnit tests in 27 suites,
  zero failures/errors/skipped. These are inherited tests, not new touch scenarios.
- DistributionJarSmoke: structural PASS; 282 classes at major 52, 5 JNI classes, 7 natives,
  required notices and 33 license inventory entries. No game/native audio was started.

## Isolated new contracts, not desktop runtime tests
Four copied production files were SHA256 matched to the formatted desktop candidate.
Linux OpenJDK21 --release 8 executed 35 distinct control contracts three times; all PASS.
Only the config threshold accessor was a fixture; the gate/controller/lease and format constants were real copies.
Eight compiled mutations were caught by runtime assertions (expiry, revival, focus, route,
stale close, authorization identity, mute retirement, and ignoring touch input).
These do not prove the GUI, OS mouse events, physical microphone, crypto replacement hook or mobile behavior.
Initial local compilation attempts stopped at option/intentional close lint warnings; final runs passed.
The local package contains the contract code and receipts, not a desktop installation or a new blocked test file.
The user-facing touch feature remains on HOLD until its UI and integrated-specific tests are completed.

## Artifact and integrity
Mod: integrated-client16/build/libs/akashicvoice-0.1.0-alpha.5-akashic-voice.11+022dc1f922-dirty.jar
SHA256: a6b63b2dabd680a8f8d2bd2d4db6ee7e7a7fa9cb5fc2dec68177a547a5139f5f.
Gateway: 8f1eb303384fa5419cd1b221d87bc2ea0421afa155f4dd7d03625d28dd8d1a65.
Paper: e5919e987cf006e126b4acbe1caa9b959a5dc9f8b6d34877c197405347b0517a.
Platform artifacts match CLIENT15. Final monitored inputs: 282 active, 325 candidate; zero mismatches.
No integration16.lock remained. Root HEAD remains 022dc1f; no commit, tag, promotion or deployment.

## Resume/progress
Keep approximately 71% (planning range 65-75%) unchanged: no completed user-facing touch delivery credit.
CLIENT15 remains the previous fully tested user-feature baseline; CLIENT16 is the built experimental core.
Preserve the argument-file runner repair. Do not resurrect the failed oversized javac command.
Read runs-integrated16 receipts and this handoff; complete the UI and new integrated touch tests when permitted.
Live mouse/touch input, physical audio, gameplay, mobile and the remaining release scope are not approved.
The one-second lease is a code policy with injected-clock contract evidence, not measured microphone latency.

Primary references consulted:
https://docs.oracle.com/javase/8/docs/technotes/tools/windows/javac.html (argument files)
https://docs.oracle.com/javase/specs/jls/se8/html/jls-17.html (publication/synchronization)
https://legacy.lwjgl.org/javadoc/org/lwjgl/input/Mouse.html (input state queries)
https://legacy.lwjgl.org/javadoc/org/lwjgl/opengl/Display.html (focus queries)
Local source/tests, not those API pages, are the evidence for implementation results.
