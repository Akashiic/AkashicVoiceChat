# Akashic Voice - CLIENT10 isolated output-control candidate

Checkpoint: AV-OUTPUT-CONTROLS-CANDIDATE-20260913-10.
Scope: Akashic Voice only. Status: ISOLATED, NOT INTEGRATED OR RELEASE-APPROVED.
Full-product progress estimate remains about 60%, judgment band 55-65%.
Active HEAD rechecked: 022dc1f. CLIENT09 remote candidate remains absent.
CLIENT08 AudioPlayback/PlaybackChannel hashes still match the preserved references.

Based on the attached CLIENT09 ZIP (SHA256 2515c0a420d15e0f89ce7643560a691a31289395c389768c7a58145b3d8ad0e1).
Changes: AudioPlayback and PlaybackChannel only relative to CLIENT09.
Latest volume/spatial/pose state no longer relies on the lossy general task queue.
The audio worker applies controls with no new PCM; partial binding-call failures retry.
Channel position/spatial/distance caches acknowledge calls only after return.
Master gain STILL uses the shared listener; complete volume isolation remains OPEN.

Isolated Linux verification: OpenJDK 21.0.11, --release 8; NOT actual JDK8 runtime.
Five core production sources compiled with TEST-ONLY OpenAL/logging fixtures.
Base: 8 PASS / 19 FAIL across 27 new control scenarios.
Final: 27 new + 25 previous deafen scenarios PASS, three repetitions (156 executions).
Eight compiled runtime mutations detected. Both patches applied/verified in disposable local copies.
Bootstrap unchanged from CLIENT09; not compiled with Forge. No full build, native audio or new JAR.
No desktop source/distribution code was written in this continuation.

Conversation artifact: AkashicVoice_CLIENT10_Controles_Candidato_2026-09-13.zip
129013 bytes, 109 files; SHA256 cbb2cece8c907f6866ab9787f8aef3f9fc989b25dfa2ae484c37b6fcdf3ebd6d.
Contains candidate/base sources, CLIENT09 and CLIENT08 patches, test fixtures and evidence.
Next: authorized integration, complete Forge/Java8/platform regressions, native listening and live/mobile gates.
