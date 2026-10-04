# Akashic Voice - CLIENT15 quality presets candidate

Checkpoint: AV-QUALITY-PRESETS-20260913-15.
Source candidate: audit/group-integration-20260913-04/integrated-client15.
State: actual Java8 sources compiled; 18 codec/configuration scenarios PASS;
new capture-flow fixture FAIL; full Gradle/packaging gates NOT RUN. Not a release.
Latest full-built reference remains integrated-client14. Active code/dist were preserved.

## Implemented source
VoiceQualityPreset maps Economy 24000, Balanced 32000, HD 48000 and Detail 64000 bit/s.
These are project choices, not standardized acoustic quality ratings.
The existing voice.opusBitrate is the single stored value; valid custom values are preserved.
Default 48000, accepted 6000..128000 range, 48kHz mono and 20ms frames are unchanged.
LiveBitrateEncoder applies requested bitrate between frames on the encoding caller.
Native and Concentus setters are exposed by their installed APIs; public javap confirmed both.
The factory wires both backends through this control without recreating a codec or microphone.
Confirmation occurs only after setter return. A partial failure invalidates the cache so a
subsequent revert is reapplied rather than falsely assumed current. Close is idempotent.
No backend fallback, network wire format, capture gate or frame size was changed.

GuiVoiceQuality is connected to the hub, with four presets, current/custom target display,
Back/Escape, compact-window fallback and the existing high-pass toggle.
English/Portuguese quality strings were added; existing English/German filter labels corrected.
The legacy noiseSuppression key and actual high-pass algorithm were retained.
Configuration description now distinguishes high-pass filtering from noise gating/RNNoise.
The GUI is source-compiled only, NOT rendered or clicked in-game.

## Executed evidence: runs-integrated15
quality-core-01: 13 scenarios passed, then Forge Configuration failed because the
standalone test JVM lacked the launcher's game-directory input. Test environment was corrected.
quality-core-02: PASS; actual JDK8 compiled 138 source files and executed 18 scenarios.
The real Forge Configuration roundtrip used a test-only package fixture and isolated directory;
no Minecraft launch or existing player config was touched. The fixture is under tools/qa only.
Two real-codec streams total 480 frames across eight targets, with one decoder per stream.
Native->Concentus and Concentus->native decoding remained 960 samples per frame.
Default 48k output matched direct baseline encoders byte-for-byte for 16 frames per backend.
This is not acoustic listening, a traffic-rate benchmark, or physical capture validation.
quality-flow-01: actual JDK8 compiled 140 sources, including GUI and new flow test;
the following capture/network test FAILED at the reused fixture's fixed four-byte payload check.
It is not approved. No passing result exists for its two planned scenarios.
CLIENT15_UI_STATIC_CHECKS.json: 14 quality strings per language, placeholders and hub/back links PASS;
no native OpenAL calls, capture startup or server messages originate directly in the new GUI.

## Blocked continuation, not executed
A command combining fix_quality15_network_fixture.py, extend_quality15_payload_check.py and the
subsequent QA/build stages was security-blocked. It was NOT retried by a different route.
The fixture still calls network.receiveFrame(); the prepared variable-payload replacement is not applied.
No quality-flow-02 run, full Gradle build, packaging run, new JAR, or new mutation result exists.
The prepared payload-response check also did not run; do not claim measured bitrate response.
Final read-only check: 282 active inputs and 323 candidate inputs matched their recorded hashes.
No integration15.lock or new candidate build/libs directory remained.
Do not promote this candidate while the integration fixture/build gates are pending.

## Progress and next acceptance
Read docs/PROGRESS_REVIEW_20260913.md: current weighted estimate is approximately 70%,
with a 65-75% judgment band. This updates accumulated CLIENT11-14 engineering, not this
partially approved feature's contribution. Weights remain 25/25/20/15/15; points 22/20/16/8/4.
Current CLIENT15 is not a passing full-project snapshot. Preserve its failed test and pending scripts.
Do not replace the last built CLIENT14 artifact, active source tree or production distribution.
Required continuation: permitted fixture correction, actual capture/network success, complete
Forge/Java8/platform/packaged regressions, then real UI/audio/gameplay/mobile and remaining scope.
No source promotion, release tag or server deployment was performed.

Primary API reference checked in this continuation:
https://www.opus-codec.org/docs/opus_api-1.3.1/group__opus__encoder.html
It documents stateful encoders and bitrate CTLs during a stream. Installed native and Concentus
public setters were verified on the resolved classpath; local tests, not the API manual, support
this candidate's behavior. Preset labels/rates are project policy, not external quality certification.
