# Akashic Voice - CLIENT22 explicit session identity

Checkpoint: AV-EXPLICIT-SESSION-ID-20260914-22.
Candidate: audit/group-integration-20260913-04/integrated-client22.
State: full Forge, actual Java8, platform, packaged-runtime and mutation gates PASS.
This is an audit baseline candidate, not active-source promotion or production deployment.

## Protocol result
The existing AV VERSION=1 and legacy outer types 1..4 remain supported.
New additive outer types 5..8 carry session-bound HELLO, CHALLENGE, AUTH_RESPONSE and ENCRYPTED traffic.
Session-bound encrypted packets carry a 16-byte UUID in clear authenticated AAD before the counter.
The session UUID is not a secret; tampering is rejected before accepting replay state.
SessionKeys has a v2 derivation domain and incorporates the session UUID into HKDF salt.
Legacy SessionKeys/SessionCrypto factories preserve the prior wire behavior and exact legacy crypto vector.
SessionCrypto remains transport-generic; the internal agent TCP protocol was not session-wrapped.

## MTU consequence
Session-bound AEAD overhead is 44 bytes, versus 28 for legacy AV.
The common AV Opus cap is now 1108 bytes so v3 media remains exactly <=1200 bytes:
1108 Opus + 48 v3 metadata + 44 session-bound envelope = 1200.
The internal agent/SVC bridge remains at 1275 bytes; Gateway rejects >1108 only at AV UDP egress.

## Bootstrap migration
Forge 1.7.10 SimpleIndexedCodec source was inspected locally: IMessage.fromBytes receives payload.slice()
and the codec does not reject unread trailing bytes after fromBytes returns.
PacketSecret therefore preserves the exact legacy 52-byte body and optionally appends a 16-byte session UUID.
The Gateway Forge response likewise preserves the exact 53-byte legacy frame and appends 16 bytes (69 total).
Old clients ignore the suffix and continue the legacy handshake; new clients use session-bound AV when present.
A new client receiving an old bootstrap without a session ID falls back to the legacy protocol.
The new decoder accepts only 0 or exactly 16 trailing bootstrap bytes; truncated or oversized suffixes fail closed.

SecretRegistry stores secret + session ID in one entry and exposes an atomic defensive Material snapshot.
Invalidation rotates both. VoiceServer uses that atomic material for session-bound HELLO to avoid secret/session TOCTOU.
Gateway BootstrapSessionRegistry likewise binds voice identity, secret, backend route and explicit session ID.

## Executed focused evidence
SessionIdentitySmoke: 6 PASS - handshake framing, HKDF separation, bound AEAD, tamper/replay,
legacy vector identity and exact 1200-byte session v3.
SessionBootstrapSmoke: 5 PASS after exact-suffix hardening.
SessionVoiceServerIntegrationSmoke: real loopback UDP session handshake/auth/MIC/replay/session-tamper/
disconnect/rotation PASS; microphone/game startup not involved.
MtuBudgetSmoke: 12 PASS; native and Concentus 128 kbit/s stress stayed within 1108 in tested frames.
GatewaySessionIdentityIntegrationTest: real Gateway UDP session-bound handshake/MIC/tamper/downlink/disconnect PASS.

## Final gates and cache incident
A copied Gradle configuration cache retained absolute paths to CLIENT21. Early CLIENT22 Gradle builds were green
but produced a stale DEV JAR without SessionHandshakePackets. The packaged origin check detected this.
Those cache-contaminated Gradle results are NOT credited.
All final Gradle evidence was rerun with --no-configuration-cache.
Structured receipts: runs-integrated22/legacy-final, java8-final and platform-final.
legacy-final PASS; java8-final PASS with actual JDK 1.8.0_411 compiling 155 sources;
platform-final PASS with 126 JUnit tests in 28 suites, zero failures/errors/skips.
Each structured final gate reports zero active_changes and zero candidate_changes.
Packaged final replay: runs-client22-packaged-final-04 PASS, 40 executions against DEV JAR production classes.
Mutation final-02: 8 compiled defective variants, 8 detected after recompiling the target smoke with each mutant.

Reobfuscated mod structural inspection PASS: 295 Java8 classes, 5 JNI classes, 7 native binaries,
required notices and 33 license-inventory entries.
Final reobfuscated mod SHA256: e70614764bd2da4def27d3fa5d8122221925979573d6acae4084146b8e83a7ce.
Final DEV JAR SHA256 tested: 8d2b04a191ee8415d77c928c6a4e3b9491e7ce632d3167fdea9d7ca592276936.
Gateway SHA256: 3793120913b2ec0fb36841a9463be4d9d3bfc3e7acd33e9e76570cd25410fd16.
Paper SHA256: b5e66814edfd8bfb1b197d786ddb9da727a75e9940dad7ca44da9b92b2c21bdb.
No integration22 lock remains.

## Scope and boundaries
Compared with CLIENT21: 20 production files and 7 test files differ in the inspected source scopes.
The active source tree was not promoted or overwritten. Production distribution was not replaced.
Legacy clients remain allowed to use the old handshake for migration compatibility; explicit session ID is
therefore enforced for session-aware clients, not a forced cutover of every historical client.
No physical microphone, headphone listening, live Minecraft multi-client session, real proxy/backend transfer,
Pojav/Amethyst device matrix or production soak was performed by CLIENT22.

## Mandatory continuation rule
Audit candidate copies must exclude .gradle/configuration-cache state OR all Gradle validation must use
--no-configuration-cache. Never credit a copied candidate's cached Gradle result without proving artifact origin.
The packaged origin check is a required regression gate for future protocol candidates.

Resume from integrated-client22 as the newest fully gated technical baseline.
Do not replace active source/distribution until live/release gates are explicitly approved.
