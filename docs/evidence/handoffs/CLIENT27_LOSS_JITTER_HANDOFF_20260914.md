# Akashic Voice - CLIENT27 loss/jitter decoder qualification

Checkpoint: `AV-LOSS-JITTER-PLC-20260914-27`.
Candidate: `audit/group-integration-20260913-04/integrated-client27`.
Production baseline: CLIENT25 / CLIENT26 artifacts.
State: automated QA qualification; production source and binaries are unchanged.

## Scope

CLIENT27 changes exactly one QA source relative to CLIENT26:
`tools/qa/LossJitterDecoderSoak.java`.
Production diff: zero files.

The production `OpusJitterBuffer()` constructor is already adaptive, and `DecoderStream`
uses that constructor. CLIENT27 therefore qualifies the existing implementation instead of
introducing another jitter controller.

The soak runs four actual codec matrices:
- native encoder -> native decoder;
- native encoder -> Concentus decoder;
- Concentus encoder -> native decoder;
- Concentus encoder -> Concentus decoder.
## Traffic pattern

Each matrix encodes 4,000 real 48 kHz / mono / 20 ms Opus frames at 48 kbit/s.
Total: 16,000 encoded/decoded frame positions.

Per 4,000-frame matrix the deterministic network model drops 114 packets, including
small three-packet bursts, and reorders 122 adjacent packet pairs. Arrival deltas vary
between 8 and 32 ms around the nominal 20 ms frame cadence.
The final tail is retained so the bounded jitter buffer can drain deterministically.

Acceptance requires zero decode errors, zero jitter resyncs, zero late packets after
reordering, zero duplicates, and a missing-frame count exactly equal to generated loss.
A counting decoder additionally requires one real `decodeMissing()` call per lost frame
and exactly one packet decode for every received frame. Thus PLC execution is measured,
not inferred only from jitter bookkeeping.

## Evidence

Focused Java8 gate: PASS for all four matrices.
Each matrix: 114 losses, 122 reordered pairs, zero assertion failures.
The hardened PLC-counting rerun also passes all four matrices.
Full actual-JDK8 gate: PASS, 156 production/QA sources compiled and 40 runner steps PASS.
The structured receipt reports `candidate_changes=[]` and `active_changes=[]`.
No Forge/platform rebuild is credited to CLIENT27 because no production source changed;
CLIENT25 artifact/build evidence and CLIENT26 sustained Gateway soak remain applicable.

## FEC boundary

This gate qualifies adaptive jitter plus Opus PLC under measured loss/reordering.
It does NOT claim in-band FEC. The installed Concentus API exposes FEC controls, while
`com.plasmoverse:opus-jni-rust:1.0.4` does not expose equivalent encoder/decode-FEC controls.
Enabling FEC only on the Java fallback would create backend-dependent semantics, so no
such production change was made here.

## Boundary

This is deterministic local JVM loss/jitter simulation with real codec implementations.
It is not Internet packet impairment, physical audio listening, Minecraft gameplay,
mobile/ARM validation, or production deployment.

Planning progress after this QA gate: approximately **83%**, judgment band **78-88%**.
No active source, production JAR, server/proxy, release tag or distribution was replaced.