# Akashic Voice - CLIENT33 outbound epoch reclamation

Checkpoint: `AV-OUTBOUND-EPOCH-RECLAMATION-20260915-33`.
Baseline: `integrated-client31`; diagnostic precursor: `integrated-client32`; verified candidate: `integrated-client33`.
State: automated gates PASS. This is an audit baseline, not active-source promotion or production deployment.

## Bug reproduced
CLIENT32 added only `OutboundStreamEpochReclamationTest`; production was byte-identical to CLIENT31.
The four focused tests failed because `OutboundStreamEpochs.retire(...)` marked a stream retired but retained
its entry inside the receiver-session map. A long-lived receiver that heard many transient speakers could
therefore consume the hard 4096-entry capacity even after every stream was reset.

## Fix
CLIENT33 changes one production file: `OutboundStreamEpochs.java` in the Velocity Gateway.
Successful source-specific and hard retirement now return the active epoch and remove the map entry.
The tracker-level `nextEpoch` remains monotonic, so a reclaimed sender re-entering the session receives a
strictly newer epoch. The obsolete `retired` flag was removed: an entry is either active in the map or absent.
All tracker use remains under the synchronized `GatewayVoiceConnection` boundary.

## Contracts
The final focused class has six tests: full-capacity reclaim/re-entry; source-specific matching; hard reset;
transient churn while an active survivor remains; true active-capacity rejection until one stream retires;
and group/non-group route mismatch isolation. Six compiled mutants were detected: both reclaim omissions,
source guard removal, group guard removal, capacity off-by-one and non-monotonic epoch assignment.

## Final evidence
- Forge/root legacy build: PASS through the hash/lock runner.
- Actual JDK8: 161 sources compiled; 43 smoke executions PASS; zero active/candidate changes during the passing run.
- Platform: clean build with configuration cache disabled, 38/38 actionable tasks executed.
- Platform JUnit: 155 tests / 37 suites; zero failures, errors or skips.
- DEV-JAR replay: 44 executions PASS; SHA256 `0b7e6f69510d615ba347c0372e03b8f897e9fc3bfbd495d3fcd23f160b14aab5`.
- Gateway JAR probe: capacity/reclaim/re-entry/hard-retire PASS using `OutboundStreamEpochs` loaded from the final Java25 JAR.
- Mutations: 6 compiled / 6 detected.
- DistributionJarSmoke: PASS, 296 Java8 classes, 5 JNI classes, 7 native binaries, required notices and 33 inventory entries.

Artifact hashes:
- mod reobf: `507e6942a2d0f76a091182e3ba2a6d2752a45129ddd8e5fafe9cf74130bee57f`.
- gateway: `83b387332295b2191a3a432e0de13c6a6d6ce9b8a7f22c896e12d6d637a51f4c`.
- Paper: `b5e66814edfd8bfb1b197d786ddb9da727a75e9940dad7ca44da9b92b2c21bdb`.
DEV JAR, reobf mod and Paper are byte-identical to the current CLIENT31 baseline; the Gateway JAR changes.
The current source-scope diff CLIENT31->CLIENT33 contains exactly one production file, one new Gateway test,
and the deterministic-wait correction in `VoiceServerMetricsIntegrationSmoke`.

## QA race found and fixed
The first full JDK8 run failed once in `VoiceServerMetricsIntegrationSmoke`. Production removes a disconnecting
connection from the active map before incrementing `sessionsClosed`, so the test's old wait for only
`activeSessions == 0` had a legitimate scheduling window. The unchanged binary passed five immediate reruns.
The smoke was hardened to wait for both active-session removal and the close metric; the entire JDK8 matrix
then passed. No server-production code changed for that finding.

## Remaining boundaries
No real two-client Velocity session, WAN impairment, physical microphone/OpenAL listening, mobile device,
operator soak or production deployment was executed by this checkpoint. Planning estimate: about 87%,
judgment band 82-91%. Do not translate that number into remaining elapsed time or release readiness.
