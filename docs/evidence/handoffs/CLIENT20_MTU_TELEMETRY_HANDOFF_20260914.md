# Akashic Voice - CLIENT20 MTU + telemetry consolidation

Checkpoint: AV-MTU-TELEMETRY-CONSOLIDATED-20260914-20.
Candidate: audit/group-integration-20260913-04/integrated-client20.
State: full Forge/Java8/platform/packaged QA PASS; not promoted or deployed.
Source of truth was CLIENT18 telemetry; MTU deltas were selectively merged from concurrent CLIENT19.

## Branch reconciliation
CLIENT19_BASELINE.json proves CLIENT19 branched independently from fully-gated CLIENT17.
Its 145-source Java8 matrix omitted CLIENT18's three telemetry sources/tests; approving it directly would regress telemetry.
Only six CLIENT19 production files differed from its baseline. Five were byte-copied after proving CLIENT18 still matched
the same CLIENT17 base: WireProtocol, AudioFormatSpec, VoiceClient, GatewayUdpServer and GatewayVoiceConnection.
VoiceServer was NOT copied. Its two MTU guards were surgically applied to CLIENT18's telemetry-aware server.
MtuBudgetSmoke was copied; incidental generated quality config directories were not merged.
CLIENT19 was allowed to finish its own platform job and was never overwritten or killed.

## MTU policy
Normal AV UDP datagrams are capped at 1200 bytes. AES-GCM wire overhead is 28 bytes.
SOUND_FRAME_V3 has 48 bytes before Opus, leaving 1124 bytes for the AV-UDP Opus payload.
Legacy client/server and Velocity UDP ingress/egress enforce the 1200-byte boundary.
Gateway SVC/group media is rejected at the final AV-UDP hop when Opus exceeds 1124 bytes.
Generic SessionCrypto remains uncapped by the UDP policy; agent/TCP bridge keeps its 1275-byte Opus contract.
The legacy receive buffers remain 1500 bytes so oversized datagrams can be received and rejected instead of truncated.

## Consolidated automated evidence - runs-integrated20
- mtu-telemetry-focused-02: 12 MTU scenarios + 8 telemetry scenarios + real VoiceServer loopback metrics PASS.
- merge-format: formatter PASS.
- merge-legacy: complete Forge build PASS.
- merge-java8: actual JDK8 compiled 149 sources; 35 smoke-main executions PASS.
- merge-platform: full platform build PASS; 124 JUnit tests in 27 suites, zero failures/errors/skips.
- merge-packaged: 35 smoke replays from DEV JAR plus one class-origin check (23 production classes) PASS.
- merge-mtu-mutations: four compiled MTU defects detected by runtime assertions.
- merge-telemetry-mutations: eight compiled telemetry/instrumentation defects detected by runtime assertions.
- CLIENT20_CONSOLIDATION_AUDIT.json: 16 scope/preservation invariants PASS.
The first focused invocation used the wrong package name for MtuBudgetSmoke after compilation; its failed receipt is retained.

MtuBudgetSmoke validates mic/v1/v2/v3 budgets, exact 1200-byte v3 boundary, 1201 rejection,
non-UDP crypto scope, unchanged 48k native/Java packets and 128k stress under both encoders.
Observed stress maxima in this machine run: native 521-byte Opus packet, Java 461-byte Opus packet.
Those maxima are test observations, not universal codec guarantees; the enforced bound is 1124 bytes.

Telemetry remains present and passes its aggregate/concurrency and real UDP/HMAC/AES-GCM/replay/disconnect tests.
No per-player sensitive metric labels or per-frame log stream was added.

## Artifact identity and integrity
Mod: integrated-client20/build/libs/akashicvoice-0.1.0-alpha.5-akashic-voice.11+022dc1f922-dirty.jar
SHA256: cc6e9418a147b1d7e8510a92e5c9886d876b228c2a26b51aa2b6671bc527e5d5.
DistributionJarSmoke PASS: 285 Java8-major-52 classes, 5 JNI classes, 7 natives,
required notices and 33 license inventory entries.
Gateway SHA256: 84750350457a037e5f78fc1b4d8460be85cd61ccd7bb19eb4738cfd88592c974.
Paper SHA256: e4b891e639a773979315ec26d3fb84ffe5659394cc59b2c6c0cef1d07f548dc4.
Platform artifacts changed because the shared client protocol now carries MTU constants/limits.
Final read-only verification against merge-platform inputs: 282 active and 332 candidate entries, zero mismatches.
No integration20.lock remained. Active root, distribution, tags and production were not changed.

## Boundary / next gate
This closes the <=1200 AV-UDP hardening item in code and automated QA without capping generic agent crypto.
It does not prove arbitrary Internet paths never fragment; live PMTU/network testing remains operational evidence.
Next protocol item is explicit session identity/generation hardening; do not conflate it with AES counter identity.
Planning estimate after this consolidation: approximately 74%, with a 69-79% judgment range.
This percentage is a planning estimate, not release safety, test coverage or remaining calendar time.
