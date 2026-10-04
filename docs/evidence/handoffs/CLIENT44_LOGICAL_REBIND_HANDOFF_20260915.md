# Akashic Voice - CLIENT44 logical NAT rebind lifecycle

Checkpoint: `AV-NAT-REBIND-LOGICAL-LIFECYCLE-20260915-44`.
Candidate: `integrated-client44`.
State: fully gated audit candidate; active source/distribution/production were not replaced.
Baseline: CLIENT43 (~94%), which already qualified NAT rebind under sustained media.

## Problem closed
A NAT rebinding of an already authenticated voice grant necessarily opens a new UDP transport and closes the displaced transport. The transport counters must observe that change, but runtime/addon/backend logical lifecycle must not see a fake logout/login pair.

CLIENT44 separates these meanings. `sessionsOpened` and `sessionsClosed` still count every transport authority change. `Listener.onConnected/onDisconnected` are suppressed only when the displaced and replacement connections identify the same logical grant.

For session-bound AV, logical identity now includes proxy UUID, backend, voice identity **and authenticated session ID**. Legacy mode has `sessionId=null` on both sides and retains the pre-session-ID identity tuple. A new grant/backend transfer is therefore not misclassified as a NAT rebind.

`BootstrapSessionRegistry.grantFor` was audited: an unchanged player/name/backend/UDP target returns the existing grant; changing the logical grant allocates fresh voice/session identities. The explicit session-ID comparison additionally prevents lifecycle suppression even if a voice-identity value were ever reused.

## Executed evidence
- Focused Gateway tests: `GatewayLogicalSessionIdentityTest`, `GatewayNatRebindIntegrationTest`, and `GatewaySessionRestartSoakTest` PASS.
- Full platform clean build with `--no-configuration-cache`: PASS, 38/38 tasks executed.
- Platform JUnit: 185 tests in 42 suites, zero failures/errors/skips.
- Root Forge clean build with `--no-configuration-cache`: PASS, 35/35 tasks executed.
- Forge distribution inspection: PASS, 296 classes, class major 52 only, 5 JNI classes, 7 native binaries, required notices and 33 license-inventory entries.
- Mutation sensitivity: 5/5 deliberate defects detected. Four exercise logical callback/metric semantics; the fifth removes session-ID matching and is rejected by the new identity test.
- Production diff versus CLIENT43: exactly `GatewayUdpServer.java` and `GatewayVoiceConnection.java`.

## Artifact identity
- Forge: `e38cfe33d7e722a885425448488ede80807d16a0ea5938631a80f9ddadb47494` (byte-identical to RC1).
- Gateway: `e4329bed4f45db66ef80de66590dc88cc51c35c30e241f4704de2dbe0a2b4ec2` (146 classes; majors 52/65/69).
- Paper: `b5e66814edfd8bfb1b197d786ddb9da727a75e9940dad7ca44da9b92b2c21bdb` (unchanged).
- Addon API: `6e73a1f96d96251cc631f6a51e4a1e485022257f1cf0a517dc7781e8ff5de347` (unchanged).

## Remaining release boundaries
This closes automated logical-lifecycle correctness for NAT rebinding; it does not replace real WAN/two-client listening, physical microphone/speaker quality, real Pojav/Amethyst thermal/battery validation, or production canary/operator observation.
Planning estimate after this checkpoint: approximately **95%**, judgment band **91-98%**. Do not interpret this as 95% production safety or as elapsed-time remaining.
