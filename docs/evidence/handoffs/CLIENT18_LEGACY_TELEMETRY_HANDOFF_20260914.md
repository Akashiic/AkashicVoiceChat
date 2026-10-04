# Akashic Voice - CLIENT18 legacy server telemetry

Checkpoint: AV-LEGACY-TELEMETRY-20260914-18.
Candidate: audit/group-integration-20260913-04/integrated-client18.
State: targeted/current Java8 tests and static audit PASS; full gates after final accuracy patch are blocked.
CLIENT17 remains the last candidate with complete automated Forge/Java8/platform/JAR gates.
Active source tree, existing distribution and production were not changed.

## Implemented scope
The legacy 1.7.10 VoiceServer now owns aggregate AtomicLong diagnostics only.
Snapshot fields contain primitive counters/booleans; no player UUIDs, names, addresses, keys or secrets.
Counters cover UDP datagrams/bytes RX+TX, HELLO/media rate limiting, encrypted/handshake rejection,
accepted microphone frames/Opus bytes, spatial/group/self-echo deliveries, opened/closed/timed-out sessions.
metricsSnapshot also exposes current active sessions and pending handshakes under the existing lock.
/avdebug permission level remains 2 and prints only aggregate values plus existing bootstrap ticket count.
No metric creates a per-player series and no new frame-level logging was introduced.

## Defects found while integrating
The first live snapshot after server.stop() failed because a closed DatagramSocket can remain bound
while getLocalPort() returns -1. metricsSnapshot now falls back to the configured port when closed/invalid.
A later audit found delivery counts could report success after AES-GCM seal even when UDP send failed.
sendRaw now returns actual send success; sendEncrypted propagates that result before delivery counters change.
Malformed HELLO/AUTH packets and AUTH responses with no pending handshake are now counted as handshake rejects.
These are diagnostic correctness fixes; wire formats, crypto primitives and routing policy are unchanged.

## Executed evidence
telemetry-integration-03: PASS on actual JDK8; 148 sources compiled at that stage.
VoiceServerTelemetrySmoke: 8 scenarios PASS, including 200,000 concurrent updates and invalid-input atomicity.
VoiceServerMetricsIntegrationSmoke used real VoiceServer + loopback UDP, HMAC handshake, AES-GCM,
valid MIC_FRAME, replay rejection, explicit disconnect and post-stop snapshot behavior.
After the final accuracy patch, telemetry-accuracy-targeted-02 recompiles the current VoiceServer,
telemetry/snapshot and both tests over the last full Java8 classpath; both tests PASS.
The extended integration test covers truncated HELLO/AUTH, orphan AUTH, closed raw send and
closed encrypted send returning false without increasing tx counters.
CLIENT18_STATIC_AUDIT.json: 28 source invariants PASS after the final patch.
The audit verifies primitive-only snapshot fields, no logger in telemetry, permission level 2,
actual UDP result propagation and ordering of tx counting after DatagramSocket.send().

## Tool restrictions
The normal run_integrated18_qa.py invocations for formatter and full Java8 matrix were security-blocked
before execution. They were not replayed through an alternate command or configuration change.
A telemetry mutation harness write was also security-blocked before file creation; no mutation result is claimed.
The targeted current-delta runner is narrower than a complete Forge/platform build and does not replace it.
Therefore do NOT call CLIENT18 fully built, packaged or production-approved yet.

## Resume
Preserve integrated-client17 as the fully gated baseline and integrated-client18 as the partial telemetry candidate.
Next required gates: permitted formatter/full Forge build, complete actual-Java8 smoke matrix, platform JUnit,
reobfuscated JAR inspection and packaged runtime replay, followed by telemetry mutation sensitivity if permitted.
Only after those pass should roadmap protocol metrics receive completed-delivery credit or progress increase.
Physical audio/gameplay/mobile/live deployment remain independent open release gates.
