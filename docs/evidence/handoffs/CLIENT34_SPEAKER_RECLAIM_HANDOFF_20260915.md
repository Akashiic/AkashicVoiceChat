# Akashic Voice - CLIENT34 speaker reclamation / boundary identity

Checkpoint: `AV-SPEAKER-RECLAIM-BOUNDARY-20260915-34`.
Reviewed candidate: `reviewed-client34-20260915`.
State: full candidate gated; NOT promoted to active source or production.
Baseline: CLIENT33.

## Production delta
Only three production classes changed:
- `SpeakerControls`: per-speaker default state receives a unique identity and default entries can be reclaimed.
- `AudioPlayback`: observes speaker identity before first decode and reclaims default state on channel/epoch retirement.
- `DecoderStream`: uses the playback-owned speaker identity at the decode boundary.

Customized volume/mute entries are retained. Only uncustomized 100%/unmuted state is reclaimable.
The change prevents default retired speakers from permanently consuming the bounded speaker directory.
The unique identity also prevents the first frame of a new speaker generation from reusing a shared default identity.

## Causal evidence
Before the patch, `SpeakerControlsSmoke` failed with:
`retired default speaker kept capacity`.
After the patch it passes 29 scenarios, including encrypted speaker controls, epoch changes,
rapid mute/resume and positive-volume buffered Opus behavior.
Session-bound UDP regression also passes (`mic=2 stale=2 replay=1`).
Gateway and Paper artifacts are byte-identical to CLIENT33, matching the client-only production delta.
## Final gates
- Legacy/Forge: PASS.
- Actual JDK8: 161 sources, PASS.
- Platform: PASS; 155 JUnit tests / 37 suites, zero failures/errors/skips.
- Packaged replay: 44 executions PASS.
- Distribution inspection: 296 Java8 classes, 5 JNI classes, 7 natives, required notices/inventory PASS.
- Final mod SHA256: `e38cfe33d7e722a885425448488ede80807d16a0ea5938631a80f9ddadb47494`.
- No CLIENT34 lock remains.

QA-only `quality15-config-*` residue created by the test matrix was moved outside the reviewed candidate.
Relative to CLIENT33, the meaningful diff is three production classes plus `SpeakerControlsSmoke`.
Two missing `quality15-config-*` files belonged to old QA residue and are not product removals.

## Remaining boundary
This checkpoint improves bounded long-session identity/control reclamation; it does not substitute for
physical audio, real gameplay/mobile or production deployment validation. No release claim is made.
Planning estimate after this gated delta: approximately 88%, judgment band 84-91%.