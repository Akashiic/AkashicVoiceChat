# Akashic Voice - CLIENT28 decoder-stream capacity hardening

Checkpoint: `AV-BOUNDED-DECODER-STREAMS-20260914-28`.
Candidate: `audit/group-integration-20260913-04/integrated-client28`.
State: **VERIFIED automated candidate**; not promoted to active sources or production.

## Finding and remediation

`StreamEpochTracker` already bounded lightweight anti-resurrection state to 4096 senders, but
`VoiceClient.decoderStreams` had no explicit cardinality cap. Every live entry owns a stateful
Opus decoder plus an 8-slot Opus jitter payload ring, so cycling authenticated sender UUIDs
could retain materially more state than epoch tombstones during the 30-second idle window.

CLIENT28 caps expensive concurrent decoder streams at 256. Existing senders are served before
the capacity check. An overflow sender is rejected without allocating a decoder and increments
`decoderStreamCapacityDrops`. V1, V2 and V3 paths stop before jitter/TalkCache work if allocation
is rejected. Reset/idle/session cleanup continues to release slots normally.

Creation is serialized under the existing `remoteEpochs` monitor, the same boundary used for
session/epoch transitions. This prevents concurrent creators from exceeding the cap or crossing
a transport-session clear.
## New contracts

`DecoderStreamCapacitySmoke` validates 256 independent decoder identities, rejects a 257th,
keeps an existing stream usable while full, floods 4096 extra UUIDs without cardinality growth,
verifies exact drop accounting, reset/reuse with fresh decoder state, full clear, and eight
concurrent creators (8 x 128 attempts) without exceeding the cap.

`DecoderStreamNetworkCapacitySmoke` uses the real encrypted `VoiceClient`/epoch path. It fills
all 256 V3 sender slots, rejects sender 257 while keeping the secure session alive, applies a
matching V3 reset to release a slot, then accepts that sender in the same session.

Five compiled mutants are detected: cap 257, cap 255, missing overflow accounting, rejecting an
existing sender while full, and V3 reset failing to remove the decoder. The first mutation run
intentionally remains as evidence: its reset mutant targeted the legacy reset and survived;
the corrected final run targets V3 and passes 5/5.

## Final gates

- Forge root build: PASS on the formatted candidate.
- Actual JDK8: PASS, 158 sources compiled, 42 runner steps, zero candidate/active changes.
- Platform: PASS, 31 executed tasks; JUnit 149 tests / 36 suites, zero failures/errors/skips.
- DEV-JAR replay/origin: PASS, 42 executions.
- Distribution inspection: 295 Java8 classes, 5 JNI classes, 7 native binaries, required notices/inventory PASS.
- Final mutation sensitivity: 5/5 compiled mutants detected.
- Validation lock absent.
## Artifact identity and unchanged platform

DEV JAR SHA256: `649cc11f1971c8ba5690d46699b3dbfa41261ab15817354cc49fe6382e751ffd`.
Reobfuscated mod SHA256: `1a80fe46dc5a374d09532a76d816a621f42a2a9a6b26cfa37055977de123f9f7`.
Gateway, addon-api and Paper JARs are byte-identical to CLIENT25:
- addon-api `6e73a1f96d96251cc631f6a51e4a1e485022257f1cf0a517dc7781e8ff5de347`;
- Gateway `8dd7a2ee5285ec66b300f257e71e660cc3441ed98837b2cbcb176021cab373ad`;
- Paper `b5e66814edfd8bfb1b197d786ddb9da727a75e9940dad7ca44da9b92b2c21bdb`.

Exact CLIENT27 -> CLIENT28 audited delta: one production file (`VoiceClient.java`) and two QA files.
No Gateway/Paper/API production change accompanies this client-side hardening.

Planning progress after this gate: approximately **84%**, judgment band **79-89%**.
This does not substitute for physical audio, live multi-client gameplay, mobile/ARM validation,
real-network soak, or deployment approval. Active source and production distribution are unchanged.