# Akashic Voice - CLIENT19 AV UDP MTU hardening

Checkpoint: AV-UDP-MTU-HARDENING-20260914-19.
Candidate: audit/group-integration-20260913-04/integrated-client19.
State: full automated Forge/Java8/platform/DEV-jar gates PASS; not live/production approved.
This branch started from fully-gated CLIENT17; partial CLIENT18 telemetry was intentionally not merged.

## Final transport design
Normal AV UDP datagrams are capped at 1200 bytes at UDP boundaries, not inside generic SessionCrypto.
AEAD overhead is 28 bytes, giving 1172 bytes maximum encrypted AV plaintext.
SoundFrameV3 is the largest media envelope: 48 bytes metadata/type + Opus, so AV Opus is capped at 1124 bytes.
Worst-case legal V3 therefore seals to exactly 1200 bytes. MIC/V1/V2 are smaller.
Client, legacy server, GatewayUdpServer and GatewayVoiceConnection enforce the UDP boundary.
SessionCrypto remains transport-agnostic because it also secures the agent TCP channel.
The agent/bridge internal Opus limit remains 1275 bytes; bridge media above 1124 is rejected cleanly before AV UDP.
No AV wire layout, protocol version, nonce construction, cipher, frame duration or sample rate changed.

## Defect found by platform suite
An initial implementation placed the 1200-byte limit in SessionCrypto. Two AgentSecureChannelBackpressureTest
cases failed because the same AEAD is used by larger framed TCP agent messages. No agent code was modified.
The cap was moved to UDP boundaries and the focused backpressure test returned PASS.
This failure is retained as evidence that the platform suite detected an incorrect architectural boundary.

## Executed evidence
MtuBudgetSmoke: 12 scenarios PASS. V3 max seals/open/parses at exactly 1200 bytes.
Native 128k stress: 512 frames, observed maximum Opus packet 521 bytes; Java: 512 frames, maximum 461 bytes.
Both 48k encoders were byte-identical to legacy 1275-output-buffer baselines across 64 frames each.
mtu-legacy-final-03: complete Forge build PASS, 27 tasks executed.
mtu-java8-final-03: actual JDK8 compiled 145 sources; 33 smoke-main executions PASS.
mtu-platform-final-04: platform JUnit PASS, 124 tests / 27 suites, zero failures/errors/skips.
mtu-packaged-final: 34 DEV-jar executions PASS, including origin check for 20 production classes.
DistributionJarSmoke: PASS; 283 Java8 classes, 5 JNI classes, 7 natives and 33 license inventory entries.
mtu-mutations: four compiled regressions all detected (1125 Opus, 1201 UDP, exclusive 1200 boundary,
and incorrectly moving the UDP cap back into generic SessionCrypto).
CLIENT19_MTU_STATIC_AUDIT.json: 14 transport-scope invariants PASS.
An added GatewayUdpServer regression proves an internal 1125-byte bridge payload is rejected before AV UDP
without exception or soundFramesSent inflation; the full platform suite remained PASS afterward.
No production source changed after the final Java8 matrix; the only later source delta was that gateway test.

Mod SHA256: 08f7b69df1a732a569ae65d676b921e105210fb4036bb4a4683a2444eff813a8.
Gateway SHA256: 84750350457a037e5f78fc1b4d8460be85cd61ccd7bb19eb4738cfd88592c974.
Paper SHA256: e4b891e639a773979315ec26d3fb84ffe5659394cc59b2c6c0cef1d07f548dc4.
Current candidate matches the final platform input manifest; active inputs also have zero mismatches.
No integration19.lock remains. Root HEAD is still 022dc1f; no tag, active promotion or deployment occurred.

## Remaining boundaries
No physical network path-MTU measurement was performed; 1200 is a protocol budget, not a measured ISP MTU.
Physical audio/gameplay/mobile/live gateway deployment and remaining roadmap work stay open.
CLIENT18 telemetry remains a separate partial branch and is not included in CLIENT19.
