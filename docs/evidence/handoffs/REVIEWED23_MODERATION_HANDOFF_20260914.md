# Akashic Voice - CLIENT23 moderation review

Checkpoint: AV-GATEWAY-MODERATION-20260914-23.
Reviewed tree: audit/group-integration-20260913-04/reviewed-client23-20260914.
Base: fully gated CLIENT22 explicit-session baseline.
State: full Forge, Java8, platform, packaged replay and mutation gates PASS.
This remains an audit candidate; active sources and production were not promoted.

## Delivered scope
Gateway moderation adds persistent global voice mute by player UUID.
`/avvoice mute|unmute|check|status` requires `akashicvoice.moderation.admin`.
Mute state is persisted in a bounded checksummed binary store (max 4096 UUIDs).
Writes use temp file + fsync + atomic replace; state is published only after persistence succeeds.
Missing store means empty state; corrupt/invalid store fails closed instead of silently clearing mutes.

The Gateway filters both audio ingress paths before routing:
- managed Akashic MIC frames, before group/agent routing;
- SVC deliveries, before UDP delivery.
Control/reset traffic remains permitted so stale playback can be retired.
Muting performs a best-effort hard reset for current listeners; future frames remain blocked regardless.
Stream-epoch clients retire the current sender epoch once; repeated hard reset is idempotent.
Legacy listeners receive the existing reset packet.

## Final reviewed gates
- Root Forge build: PASS with configuration cache disabled; 38 tasks executed.
- Platform build: PASS with configuration cache disabled; 26 tasks executed.
- Actual JDK8: PASS; 155 source files compiled, 40 runner commands including 38 smoke mains.
- Platform JUnit: 131 tests in 31 suites; zero failures, errors or skips.
- DEV-JAR replay: 40 executions PASS; production class origin checked from the packaged JAR.
- Mutation review: 5 compiled mutants / 5 detected on the reviewed snapshot.
- DistributionJarSmoke: PASS; 295 Java8 classes, 5 JNI classes, 7 native binaries, notices/licenses present.
- Java8 runner recorded zero active-source and zero candidate-input changes during its gate.
- No reviewed23 build lock remains.

Mutation coverage explicitly detects:
1. publishing mute state before persistence;
2. bypassing the managed MIC moderation filter;
3. bypassing the SVC moderation filter;
4. skipping persistence checksum validation;
5. allowing repeated hard-reset emission for an already-retired epoch.

Exact source delta from CLIENT22: 7 changed production files, 3 added production files and 3 added tests,
all under platform/gateway-velocity. No legacy/client/Paper production source was changed by CLIENT23.
The temporary quality-config fixture created by Java8 QA was moved into the run evidence directory.

## Artifact identity
Legacy mod reobfuscated JAR SHA256:
`e70614764bd2da4def27d3fa5d8122221925979573d6acae4084146b8e83a7ce`.
DEV JAR SHA256:
`8d2b04a191ee8415d77c928c6a4e3b9491e7ce632d3167fdea9d7ca592276936`.
Gateway JAR SHA256 from the final reviewed no-cache build:
`547f8bcf0615a073519972cb6929bc118b2e9638077642d03a55896af7889f0b`.
Paper bridge SHA256:
`b5e66814edfd8bfb1b197d786ddb9da727a75e9940dad7ca44da9b92b2c21bdb`.
The mod and Paper artifacts remain functionally unchanged from CLIENT22; moderation is Gateway-only.

## Boundaries
This is persistent Gateway moderation, not moderation for a standalone legacy VoiceServer without Gateway.
There is no web dashboard in this checkpoint; administration is command/permission based.
No physical microphone/OpenAL, live multi-player server deployment, prolonged gameplay or mobile-device test ran here.
No production deployment, release tag, active-tree promotion or old distribution replacement occurred.
Real deployment restart/persistence and operator UX remain homologation gates despite automated persistence tests.

Planning estimate after this checkpoint: approximately 78% of the complete product,
with a 73-83% judgment band. This is a planning estimate, not elapsed-time or test-count arithmetic.
Suggested weighted points: 24/25 foundation, 22/25 client audio/UX, 18/20 gateway/transfer,
10/15 social/moderation/API, 4/15 real compatibility/mobile/release = 78/100.
