# Akashic Voice — CLIENT46 Legacy Agent / RC4

Checkpoint: `AV-LEGACY-AGENT-RC4-20260916-46`.
Candidate: `integrated-client46-feedback`.
Release candidate: `release-candidate-20260916-rc4`.
State: fully gated audit/release candidate; NOT deployed, tagged, or promoted into the active dirty tree.
Planning estimate: **96%**, judgment band **92-98%**.

## Delivered

The Forge/Crucible 1.7.10 backend can now run in `serverMode=agent` and connect outbound to the Velocity Gateway Agent port instead of exposing/starting the backend voice UDP server.
Agent mode is fail-closed: invalid/missing PSK does not fall back to direct UDP, and local secret/bootstrap requests return no backend bootstrap authority.
Direct mode remains the compatibility path and explicitly stops any Agent before starting the legacy UDP server.

The Java 8 Agent uses backend-scoped PSK authentication, HKDF-derived session material, HMAC proof, AES-GCM transport, bounded queues, control-before-media scheduling, heartbeat/reconnect and server-authoritative position data.
## Final gates

- Forge/root gate: PASS on post-format source hashes.
- Actual Java 8: PASS, 167 production/QA sources compiled, 46 command records.
- Platform: PASS; 186 JUnit tests / 43 suites / zero failures, errors, or skips.
- DEV-JAR replay: PASS, 46 executions; Agent/config/lifecycle classes required to originate from the packaged JAR.
- Reobfuscated distribution inspection: 309 Java 8 classes, 5 JNI classes, 7 native files, notices/license inventory PASS.
- Mutation sensitivity: 5/5 compiled Agent defects detected.
- Fail-closed/lifecycle static audit: 20/20.
- RC4 independent seal verification: 567/567.

The first packaged-origin run failed only because the QA verifier used the wrong FQCN for `CommonEvents`; the preserved second run corrected the verifier without altering product sources and passed 46/46.
Gateway, Paper/SVC and addon-api artifacts are byte-identical to RC3; only the Forge artifact changed for the Legacy Agent readiness work.
## RC4 artifact identity

- Forge 1.7.10: `8e5a6fe5de6a2421ef37422a14204ce48fc43382fb8de944ec5b7462c118215e` — 309 classes, major 52.
- Gateway Velocity: `e4329bed4f45db66ef80de66590dc88cc51c35c30e241f4704de2dbe0a2b4ec2`.
- SVC Paper: `b5e66814edfd8bfb1b197d786ddb9da727a75e9940dad7ca44da9b92b2c21bdb`.
- Addon API: `6e73a1f96d96251cc631f6a51e4a1e485022257f1cf0a517dc7781e8ff5de347`.

`SEAL-RECEIPT.json` records 560 pre-write checks. The packaging command printed 564 after four additional post-seal artifact re-hashes. `RC4-INDEPENDENT-VERIFY.json` removes that accounting ambiguity by recomputing the sealed package independently and passes 567/567 checks.

## Remaining gates to 100%

The remaining work is deliberately not credited as automated completion: real WAN/two-client listening and transfer/restart observation, physical microphone/speaker/acoustic quality, real Pojav/Amethyst device/thermal/battery validation, and a production canary with operator/player feedback.
Do not claim stable release or 100% before those gates are executed against the sealed candidate or a later equivalently gated RC.
