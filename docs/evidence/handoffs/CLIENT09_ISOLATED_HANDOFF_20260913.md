# Akashic Voice - CLIENT09 isolated deafen candidate

Checkpoint: AV-DEAFEN-ISOLATION-CANDIDATE-20260913-09.
Status: sandbox-only source candidate; NOT applied, integrated, or production-approved.
Base: six SHA-256-verified CLIENT08 files. Active code/distribution unchanged.
Creation of prepare_client09.py was security-blocked; ENOENT confirmed.
No alternate route was used to create that script or a remote CLIENT09 copy.

## Candidate
AudioPlayback publishes listening state outside the lossy task queue.
Only voice-owned sources are retired; old acquired/queued PCM has an identity token.
Rapid deafen/undeafen rejects stale PCM; wire-epoch tombstones remain.
The audio worker handles at most 64 general tasks per cycle before checking voice state.
PlaybackFrame carries the acquisition token; bootstrap calls setDeafened separately.
Master voice volume still uses the shared listener; its full isolation remains OPEN.

## Executed in the isolated Linux workspace, not this desktop
OpenJDK 21.0.11 with --release 8; five production sources compiled using test fixtures.
25 contract scenarios: base 8 PASS/17 FAIL; final candidate 25 PASS x3 repetitions.
8 mutations detected: 7 runtime contracts, 1 static bootstrap-callsite check.
Patch applied in a disposable local copy: all six outputs match supplied candidate.
Bootstrap is statically checked, NOT compiled/executed with Forge.
No actual JDK8 runtime, full Gradle/platform build, native audio, game startup or JAR.

Deliverable attached in the conversation:
AkashicVoice_CLIENT09_Deafen_Candidate_2026-09-13.zip (89,425 bytes, 76 files).
ZIP SHA256: 2515c0a420d15e0f89ce7643560a691a31289395c389768c7a58145b3d8ad0e1
Next gate: authorized full-project integration, actual Java8/Forge regressions and live audio.
Do not count this candidate as integrated progress or supersede the CLIENT08 runtime evidence.
