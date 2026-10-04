# Akashic Voice - CLIENT24 public addon API review

Checkpoint: AV-PUBLIC-ADDON-API-20260914-24.
Reviewed tree: audit/group-integration-20260913-04/reviewed-client24-20260914.
Base: fully gated CLIENT23 moderation baseline.
State: full Forge, platform, Java8, packaged replay, external-addon and mutation gates PASS.
This is an audit candidate; active sources and production were not promoted.

## Delivered API v1
A standalone `platform/addon-api` artifact now defines a stable low-frequency server-side surface.
API version is `AkashicVoiceApi.API_VERSION == 1`.
The surface provides authenticated session snapshots, one-player lookup, global-mute lookup,
moderation revision, session lifecycle events and moderation-change events.
It exposes no bootstrap secret, socket, SessionCrypto, ByteBuffer, raw Opus buffer or per-frame callback.

The Gateway embeds the same API classes and implements `AkashicVoiceApiProvider`.
Addons compile against addon-api as compile-only and declare a required dependency on plugin id
`akashicvoicegateway`; discovery uses the loaded Gateway plugin instance and the public provider contract.
A failing addon listener is isolated from other listeners; fatal VM/thread errors are not swallowed.
Listener registrations close idempotently. Gateway shutdown/init failure deactivates the API and clears sessions/listeners.

## Runtime integration
GatewayRuntime publishes connect/disconnect only; MIC and SVC media hot paths contain no addon callback.
`/avvoice mute|unmute` publishes moderation events only after GatewayModerationService commits persistence.
Synthetic persistence failure is tested: no state publication and no addon event occurs.
Dynamic mute/revision queries reflect the current moderation service rather than stale snapshots.
Returned player lists are immutable, deterministically sorted snapshots.

`docs/ADDON_API.md` documents the compile-only artifact and Velocity discovery pattern.
An external sample addon was compiled through an isolated Gradle project against only addon-api plus
`com.velocitypowered:velocity-api:4.0.0` and its normal transitive dependencies, using the Java25 toolchain.
`jdeps -s addon-api.jar` reports only `java.base` for the standalone API artifact.

## Final reviewed gates
- Root Forge build: PASS, configuration cache disabled, 38 tasks executed.
- Platform build: PASS, configuration cache disabled, 31 tasks executed.
- Platform JUnit: 140 tests in 34 suites; zero failures/errors/skips.
- Actual JDK8 legacy matrix: PASS; 155 sources compiled, 40 runner commands.
- DEV-JAR origin/replay: 40 executions PASS.
- Public API static audit: 38/38 invariants PASS.
- API mutation review: 7 compiled mutants / 7 detected on the reviewed snapshot.
- External addon sample Gradle compile: PASS with reviewed addon-api artifact.

## Artifact identity
Standalone addon-api SHA256:
`1bbd94c947bc61d18abfd0279ae05607736ee52230456162893f1d6e7b1d0052`.
Gateway SHA256:
`6cdfa148c8d967df00ec37b7129e4a3e0b98ccbd9bf28a3a895117cffab85c53`.
Legacy mod SHA256 remains CLIENT23-identical:
`e70614764bd2da4def27d3fa5d8122221925979573d6acae4084146b8e83a7ce`.
DEV JAR SHA256 remains:
`8d2b04a191ee8415d77c928c6a4e3b9491e7ce632d3167fdea9d7ca592276936`.
Paper bridge SHA256 remains:
`b5e66814edfd8bfb1b197d786ddb9da727a75e9940dad7ca44da9b92b2c21bdb`.
DistributionJarSmoke remains PASS: 295 Java8 classes, 5 JNI classes, 7 native binaries and notices/licenses.

Exact CLIENT23 -> CLIENT24 reviewed delta: 5 changed files + 14 added files, zero removals.
The added scope is addon-api, its tests/documentation, GatewayAddonApi and Gateway API integration tests.
No legacy client/server or Paper production source is changed by CLIENT24.

## Boundaries
API v1 deliberately does not expose raw frames or arbitrary media injection.
Cross-server radio/media injection remains a separate roadmap item requiring authority/backpressure semantics.
The addon sample proves compile-time consumption, not a live two-plugin Velocity deployment.
Physical audio, real multi-player deployment, prolonged gameplay and mobile validation remain open.
No active-tree promotion, distribution replacement, release tag or production deployment occurred.

Planning estimate after this checkpoint: approximately 79% of the complete product, judgment band 74-84%.
Suggested weighted points: 24/25 foundation, 22/25 client audio/UX, 18/20 gateway/transfer,
11/15 social/moderation/API, 4/15 real compatibility/mobile/release = 79/100.
