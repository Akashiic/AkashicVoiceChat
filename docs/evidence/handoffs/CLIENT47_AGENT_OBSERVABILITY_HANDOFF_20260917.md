# Akashic Voice - CLIENT47 Agent observability / RC5

Checkpoint: `AV-LEGACY-AGENT-OBSERVABILITY-RC5-20260917-47`.
Candidate: `integrated-client47-agent-observability`.
Release bundle: `release-candidate-20260917-rc5`.
State: fully gated release candidate; **not deployed**.

## Production delta

Only two production files differ from CLIENT46:
- `LegacyAgentChannel.java`
- `LegacyGatewayAgent.java`

One QA file was added: `LegacyAgentDropCounterSmoke.java`.
There is no wire/protocol change and no Gateway/Paper/addon API code change.
The Forge Agent now owns one cumulative `AtomicLong` for media drops and injects it into every channel generation.
Therefore `droppedMediaFrames()` remains meaningful across reconnects and while the Agent is disconnected.
The channel still bounds its media queue at 256 and drops the oldest queued media under pressure.
## Executed evidence

- Focused Java 8 smoke: PASS; two channel generations; 88 cumulative drops.
- Final formatter: PASS.
- Final Forge/root build: PASS.
- Actual JDK 8 matrix: PASS; 168 compiled sources; 47 commands.
- Platform: PASS; 186 JUnit tests / 43 suites; zero failures/errors/skips.
- DEV-JAR origin/replay: PASS; both altered production classes loaded from the DEV JAR.
- Mutation sensitivity: 2/2 compiled defects detected (per-connection counter; hidden disconnected counter).
- Reobfuscated Forge JAR structural inspection: PASS; 309 Java 8 classes, 5 JNI, 7 natives.
- RC5 seal preflight: PASS.
- RC5 independent verification: PASS 567/567.

RC5 Forge SHA-256: `6fab0865c2d2c5a0e382e193f35e099aa7e0861ab4317b0c78a91796ef8d553e`.
Gateway and Paper hashes are byte-identical to RC4; addon-api is also unchanged.
The active source tree and existing RC4 bundle were not overwritten.
## Progress boundary

Planning estimate remains **96%**, judgment band **92-98%**.
CLIENT47 improves production observability but does not close a live validation gate, so no percentage was added merely for more tests.
Open live gates remain:
1. Real WAN/two-client listening plus production cross-server transfer/restart.
2. Physical microphone/speaker quality and acoustic/AGC validation.
3. Real Pojav/Amethyst devices with thermal/battery observation.
4. Production canary deployment and operator/player feedback.

RC5 is suitable for the next controlled homologation step; it is not labeled stable or production-approved by this receipt.
