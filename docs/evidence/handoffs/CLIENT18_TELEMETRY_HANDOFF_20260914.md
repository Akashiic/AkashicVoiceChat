# Akashic Voice - CLIENT18 aggregate protocol telemetry

Checkpoint: AV-LEGACY-TELEMETRY-FULL-20260914-18.
Candidate: audit/group-integration-20260913-04/integrated-client18.
State: full Forge/Java8/platform/packaged QA PASS; not promoted or deployed.
CLIENT17 and active sources/distribution were preserved.

## Delivered
VoiceServer now owns aggregate counters only: UDP datagrams/bytes in/out, HELLO/media rate limits,
encrypted/handshake rejects, accepted mic frames/Opus bytes, proximity/group/self-echo deliveries,
and session opened/closed/timeout counts. Snapshot includes only primitive aggregate values.
No UUID, player name, address, key, bootstrap secret or session secret is stored in metrics.
`/avdebug` remains permission level 2 and prints bounded snapshots only when invoked; no per-frame logging was added.
A stopped socket now falls back to the configured UDP port instead of publishing getLocalPort() == -1.
Only successful socket sends increment transmitted-datagram/byte counters.

## Real integration coverage
VoiceServerMetricsIntegrationSmoke starts the production VoiceServer on loopback UDP, sends malformed data,
performs HELLO/CHALLENGE/HMAC authentication, opens AES-GCM media, submits a mic frame, replays it,
sends orphan/unknown handshake traffic and performs encrypted DISCONNECT.
It verifies the corresponding aggregate metrics and verifies failed sends after stop do not inflate TX counters.
No Minecraft game, player client or physical audio device is started by this smoke.

## Executed evidence - runs-integrated18
- telemetry-initial: Java8 compile + aggregate-counter smoke PASS.
- telemetry-integration-03: production VoiceServer loopback/crypto integration PASS after fixing closed-port fallback.
- telemetry-format-final: formatter PASS.
- telemetry-legacy-final: complete Forge build PASS.
- telemetry-java8-final: actual JDK8 compiled 148 sources; 34 smoke executions PASS.
- telemetry-platform-final: full platform build PASS; 124 JUnit tests in 27 suites, zero failures/errors/skips.
- telemetry-packaged-final: 34 smoke replays from the DEV JAR plus one class-origin check PASS.
- telemetry-mutations-final-02: 8 compiled defective variants detected by runtime assertions.
The first integration invocation used a wrong smoke-class name after successful compilation; its failed receipt is retained.
A later integration run exposed the closed-socket `-1` port defect; the final integration-03 run is the approved result.
The first mutation runner detected 7 cases then stopped on an ambiguous locator; final-02 uses a replay-specific locator.
These historical failures are not hidden and did not mutate production sources unexpectedly.

Final monitored inputs from telemetry-platform-final: 282 active and 331 candidate entries, zero mismatches.
No integration18.lock remained after completion.

## Artifact identity
Mod: integrated-client18/build/libs/akashicvoice-0.1.0-alpha.5-akashic-voice.11+022dc1f922-dirty.jar
SHA256: 0beabd6d0084e4ae68dbfb534405f40de28039df853ff1835de652661ff7aed9.
DistributionJarSmoke PASS: 285 Java8-major-52 classes, 5 JNI classes, 7 natives,
required notices and 33 license inventory entries.
Gateway SHA256: 8f1eb303384fa5419cd1b221d87bc2ea0421afa155f4dd7d03625d28dd8d1a65.
Paper SHA256: e5919e987cf006e126b4acbe1caa9b959a5dc9f8b6d34877c197405347b0517a.
Gateway/Paper remain byte-identical to prior candidates.

## Boundaries
Counters are lifetime-since-process-start totals, not durable time-series monitoring.
They do not expose per-player labels or a production dashboard. Gateway already has its own aggregate status surface.
This checkpoint closes the legacy-server aggregate protocol-metrics implementation/gate in code and automated QA,
not live production observability, Prometheus export, gameplay validation or operational alerting.
No production deployment, release tag, active-source promotion, physical audio or mobile test occurred.

Planning progress estimate after CLIENT17 touch GUI + CLIENT18 telemetry: approximately 73%,
with a 68-78% judgment range. This is a weighted planning estimate, not a test-count percentage.
Use CLIENT18 as the latest fully automated candidate while continuing protocol hardening and remaining live gates.
