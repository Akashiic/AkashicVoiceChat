# Akashic Voice - CLIENT26 sustained soak qualification

Checkpoint: `AV-SUSTAINED-SOAK-20260914-26`.
Candidate: `audit/group-integration-20260913-04/integrated-client26`.
Baseline production: `reviewed-client25-20260914`.
State: automated release-qualification checkpoint; no production-source change and no deployment.

## Scope
CLIENT26 intentionally changes exactly one file relative to CLIENT25:
`platform/gateway-velocity/src/test/java/dev/akashic/voice/platform/velocity/GatewayRuntimeSoakTest.java`.
All production source hashes remain identical to CLIENT25.

The soak now uses session-bound AV authentication rather than the legacy handshake.
It creates 24 authenticated UDP clients and pairs them into 12 cross-server radio channels.
Each repetition performs 10 agent-route cycles and 50 audio frames per client per cycle.
Microphone producers use a 20 ms cadence rather than an instantaneous packet burst.
The test is repeated three times with fresh runtime/session state.

Per repetition this exercises 12,000 encrypted MIC frames, 12,000 radio downlinks,
240 SVC downlinks, 24 session opens/closes, 9 agent route-down/reconnect transitions,
and 24 pair-scoped radio reset datagrams.
Across the three repetitions the gate therefore covers 36,000 MIC frames,
36,000 radio frames, 720 SVC deliveries, 72 authenticated sessions,
27 agent route-down transitions and 72 initial radio hard resets.

## Rate-limit finding
The first 24-client session-bound run intentionally arrived as fast as the loopback could send.
All three repetitions stopped while waiting for a session CHALLENGE.
The Gateway policy allows a 20-HELLO burst per IP and refills at 10 HELLO/s;
the 21st+ immediate loopback bootstrap was correctly rate-limited.

The qualification was corrected, not the production limiter: bootstraps are spaced 60 ms.
The final soak requires `helloRateLimited == 0`, `mediaRateLimited == 0`,
`encryptedRejected == 0`, and `agentMediaDropped == 0`.
The security limit remains unchanged.

## Final soak evidence
Focused sustained soak: PASS, 3/3 repetitions, 37.944 seconds in the focal run.
Full-platform clean build rerun: PASS; final soak XML 3/3 PASS in 37.021 seconds.
Full platform suite: 149 tests in 36 suites, zero failures/errors/skips.
The test count is lower than CLIENT25 because repetitions changed from five short runs to
three substantially heavier sustained runs; traffic volume increased by more than an order of magnitude.
## Build and artifact identity
Root Forge clean build with `--no-configuration-cache`: PASS, 35/35 tasks executed.
Platform clean build with `--no-configuration-cache`: PASS, 38 tasks, 37 executed.

CLIENT26 artifacts are byte-identical to the fully gated CLIENT25 baseline:
- DEV mod: `8d2b04a191ee8415d77c928c6a4e3b9491e7ce632d3167fdea9d7ca592276936`.
- Reobfuscated mod: `e70614764bd2da4def27d3fa5d8122221925979573d6acae4084146b8e83a7ce`.
- addon-api: `6e73a1f96d96251cc631f6a51e4a1e485022257f1cf0a517dc7781e8ff5de347`.
- Gateway: `8dd7a2ee5285ec66b300f257e71e660cc3441ed98837b2cbcb176021cab373ad`.
- Paper: `b5e66814edfd8bfb1b197d786ddb9da727a75e9940dad7ca44da9b92b2c21bdb`.

Because production sources and binaries are byte-identical, CLIENT25's actual JDK8,
DEV-JAR replay, class-major, native-bundle, mutation and distribution inspections apply
unchanged to the production artifact. CLIENT26 adds soak evidence, not a new product binary.

## Boundary
This is a high-volume local loopback/agent qualification with simulated clients.
It is not a live Internet path, human listening test, real Minecraft gameplay session,
physical microphone/OpenAL validation, mobile test, or production deployment.
No active source, production JAR, server, proxy or distribution was replaced.