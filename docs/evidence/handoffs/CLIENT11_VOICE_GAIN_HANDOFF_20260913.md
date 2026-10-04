# Akashic Voice - CLIENT11 voice-only master gain

Checkpoint: AV-VOICE-GAIN-INTEGRATION-20260913-11.
Latest candidate: audit/group-integration-20260913-04/integrated-client11.
Scope: Akashic Voice only. Active source and prior distribution were not replaced.
Base: complete integrated-client10; 325 files copied with source hash checks.

## Delivered code
- AudioPlayback no longer writes AL_GAIN on the shared listener at init or control updates.
- Each owned channel receives the requested 0..3 voice gain; new channels inherit it.
- Partial channel failures do not block peers and remain pending even after reverting a setting.
- VoiceGain implements three bounded paths: extended source gain, float headroom, core S16 fallback.
- PlaybackChannel reads native gain properties back before acknowledging changes.
- Float uploads use a direct, native-order ByteBuffer plus FloatBuffer view for real LWJGL2.
- Existing 0..300% config range retained; invalid inputs rejected rather than silently clamped.
- Config description updated. No codec, protocol or production dependency change.
Production delta: AudioPlayback.java, PlaybackChannel.java, new VoiceGain.java and config description.

## Driver behavior and honest limits
AL_SOFT_gain_clamp_ex is used only when advertised with a gain limit of at least 3.
Without it, float headroom is selected only for OpenAL Soft with AL_EXT_FLOAT32.
On other drivers, above-unity boost uses saturating S16 with clipping counters and one warning.
Changing that fallback boost discards stale boosted buffers (bounded by the existing queue).
This fallback is NOT lossless amplification for arbitrary peaks; up to 100% uses source gain.
The game master multiplier still affects voices: isolation prevents voice controls from overwriting it.
This patch does not claim total OpenAL-context isolation, device recovery, or mobile compatibility.

## Executed Windows gates (runs-integrated11)
- gain-legacy-final: full Forge build PASS, 27 executed tasks.
- gain-java8-final: actual JDK8 compiled 126 source files; 24 smoke executions PASS.
- gain-platform-final: 124 JUnit tests in 27 suites; zero failures/errors/skipped; 26 build tasks.
- packaged-java8: 24 repeated smokes plus class-origin check PASS against DEV jar.
- DistributionJarSmoke: reobfuscated jar PASS; 267 classes major 52, 5 JNI classes,
  7 native binaries, required notices and 33 license inventory entries.
All final run source-change lists are empty; the build lock was absent at verification.
An earlier real build failed on a non-existent LWJGL2 FloatBuffer overload. The final
ByteBuffer adapter fixed it; retain gain-legacy-build/step-00.log as the failure evidence.

## Isolated Linux evidence, not Windows integration counts
- Local production has the same Java token streams as the three remote gain classes,
  excluding only comments/whitespace (raw source bytes differ due to formatting).
- 25 gain contracts PASS and 25 listening contracts PASS, three final repetitions.
- Baseline gain suite: 3 PASS / 22 FAIL, including overwriting game listener gain 0.37.
- One inherited listening expectation changed from shared-master gain to owned-source gain.
- Partial-failure/revert regression failed before repair and passed afterward.
- Nine deliberately broken variants compiled and were detected by runtime tests.
- Actual OpenAL Soft 1.24.2 ALC_SOFT_loopback: 80 comparisons PASS, maximum sample
  difference 0.0 in measured cases; fixed game multiplier 0.37, two signal amplitudes,
  five volume levels and four distances, for extended and float paths.
- Native loopback validates the gain policy through the C API, not the whole Java mod.
  Only virtual rendering was used (ALSOFT_DRIVERS=null), not physical devices or listening.
Core-only clipping behavior is contract-tested, not acoustically approved.
No universal performance or compatibility claim follows from this finite matrix.

## Artifacts relative to integrated-client11
Mod: build/libs/akashicvoice-0.1.0-alpha.5-akashic-voice.11+022dc1f922-dirty.jar
SHA256: d4df3768792fbf514904b8421a521d946cfa3a29899f1ae0bbe54e6797da7990
Mod size: 3104761 bytes.
Gateway: platform/gateway-velocity/build/libs/gateway-velocity-0.1.0-SNAPSHOT.jar
SHA256: 8f1eb303384fa5419cd1b221d87bc2ea0421afa155f4dd7d03625d28dd8d1a65
Paper: platform/svc-paper/build/libs/svc-paper-0.1.0-SNAPSHOT.jar
SHA256: e5919e987cf006e126b4acbe1caa9b959a5dc9f8b6d34877c197405347b0517a
Gateway/Paper hashes equal CLIENT10; their source was not changed.
Final monitored source verification: 282 active and 308 candidate inputs, no mismatches.

## Blocked work and unresolved gates
Completing VoiceGainContract.java on the desktop was security-blocked. The 54-line
partial file is quarantined as VoiceGainContract11.java.incomplete outside source sets.
The additional verify_gain11_binding.py write was also blocked; ENOENT confirmed it
was not created or executed. No alternate route was used to complete those operations.
The remote gain-contract recorder is an unused draft containing an obsolete overload;
do NOT count it as binding-validated. The final independent Linux recorder uses the
correct direct ByteBuffer layout. Full production compilation uses real LWJGL2.
No QA/native-call fixture is intended for distribution. New gain contracts ran locally;
the actual-JDK8 Windows counts above are the existing full-project regression suite.

Still required: live microphone/ear testing, actual game startup, real backend transfers,
long gameplay/device lifecycle, mobile, remaining product features and controlled release.
The active source tree and dist were not promoted; no commit, tag or deployment was made.
Use CLIENT11 as the latest tested development candidate, not as a stable-release approval.
Primary references: https://openal-soft.org/openal-extensions/SOFT_gain_clamp_ex.txt
and https://legacy.lwjgl.org/javadoc/org/lwjgl/openal/AL10.html.
Specification behavior is not a substitute for runtime validation on the target devices.
