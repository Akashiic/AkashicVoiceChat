# Akashic Voice — Engineering Audit

This document records approval gates. A gate is not marked PASS until evidence exists.

## Gate 0 — Upstream reproducibility

Status: **PASS (baseline corrected and rebuilt)**.

Evidence found:
- Upstream `gradle.properties` contained an absolute macOS JDK 25 path. Clean Windows build failed before dependency resolution.
- After removing that path, build reached dependencies but the pinned JitPack Concentus coordinate could not be resolved.
- Replacement candidate: Maven Central `com.plasmoverse:concentus:1.0.0`, Java release 8, BSD-3-Clause.
- Clean build after the two remediations completed successfully (BUILD SUCCESSFUL, 34 tasks).

Acceptance:
- clean checkout/build on this Windows machine using a documented JDK;
- no developer-specific absolute paths;
- every binary dependency reproducibly resolves from a declared repository;
- produced JAR inspected for bundled codec classes and license notice.

## Gate 1 — Java 8 client contract

Status: **PASS**.

Evidence:
- `enableModernJavaSyntax=false`; clean Gradle build succeeds.
- Final JAR inspection reports all Akashic classes at class major 52.
- Concentus encode/decode smoke passes under installed JDK 1.8.0_411.
- Full `dev.akashic.*` source tree recompiles with `javac 1.8.0_411` (`JAVAC8_EXIT=0`).
- No mandatory lwjgl3ify, GTNHLib, UniMixins, Angelica or other mod dependency.

Acceptance:
- source uses Java 8 syntax only (`enableModernJavaSyntax=false`);
- client classes report major version 52;
- codec smoke test executes under installed JDK 8;
- no runtime linkage to Java 9+ APIs in client/common code;
- no mandatory lwjgl3ify, GTNHLib or UniMixins dependency.

## Gate 2 — Identity and offline-mode correctness

Status: **PARTIAL PASS — upstream identity defect fixed; live proxy/offline integration test remains.**

Finding: server secrets are keyed by `EntityPlayerMP.getUniqueID()`, while the current client authenticates UDP
with its local `player.getUniqueID()`. `PacketSecret` does not transmit the server-authoritative UUID.
In offline mode the server can derive `UUID.nameUUIDFromBytes("OfflinePlayer:" + name)`, so identities can differ.

Required fix: bootstrap packet carries the authoritative server UUID and the client uses exactly that UUID for voice auth.
Never repair this by guessing online/offline UUID mode client-side.

Acceptance:
- unit test where client-local UUID != server UUID authenticates successfully with server-issued identity;
- online-mode-style equal UUID case still works;
- reconnect invalidates old secret/session;
- no secrets are logged.

## Gate 3 — Audio stream correctness

Status: **PARTIAL PASS — jitter/reorder/PLC, pooled playback and codec allocation benchmarks pass; live multi-client gameplay remains.**

Findings:
- upstream `VoiceClient` owns one stateful Opus decoder shared by every remote speaker;
- incoming frames are decoded immediately without a true per-stream jitter/reorder stage;
- hot path creates multiple arrays/buffers per 20 ms frame.

Acceptance:
- decoder state is isolated per encoded stream;
- sequence/reorder/PLC tests cover loss, duplicate and out-of-order frames;
- bounded jitter/playback queues cannot grow without limit;
- allocation benchmark exists for capture -> packet and packet -> playback hot paths.

## Gate 4 — UDP security and parser hardening

Status: **PASS — protocol/security implementation and isolated Java 8 QA complete. Live deployment remains an integration test, not a protocol-code blocker.**

Evidence:
- explicit `AV` magic, protocol version and fixed outer envelope types;
- 256-bit bootstrap secret, fresh handshake nonces and distinct server/client HMAC proofs;
- HKDF-SHA-256 checked against RFC 5869 test vector;
- directional AES-128-GCM keys, 96-bit nonces, 128-bit tags and 64-packet replay window;
- unauthenticated HELLO rate limiting plus authenticated packet/byte rate limiting before decrypt;
- concurrent seal test produced counters 0..1999 exactly once;
- fuzz test: 50,000 parser datagrams + 5,000 forged GCM envelopes with no crash/false acceptance;
- full secure-handshake flow smoke: challenge/proofs/encrypted ACK/encrypted media/replay rejection.

Acceptance:
- packet magic/version/type validated before parsing payload;
- datagram size is bounded before allocation/decoding;
- authenticated endpoint/session required before media is accepted;
- AEAD authentication failure drops packet without expensive/log-spam path;
- replay/duplicate window enforced;
- client accepts voice datagrams only from the expected server/gateway endpoint;
- rate limits exist for unauthenticated handshakes and authenticated media abuse; ✓
- malformed packet fuzz tests cannot crash network threads or allocate unbounded memory.

## Gate 5 — Performance

Status: **PARTIAL PASS — desktop Java 8 codec/crypto throughput and allocation are measured and improved; live Minecraft, ARM/Pojav and multi-client server profiling remain.**

Evidence:
- five-JVM integrated Java 8 median at the 48 kbit/s default: native encode ~144.2 us vs Concentus ~537.6 us (~3.73x faster);
- native decode ~31.7 us vs Concentus ~72.4 us (~2.29x faster);
- native encode allocation ~144 B/frame vs ~131.0 KiB/frame; native decode ~1.9 KiB vs ~29.2 KiB/frame;
- AES-GCM and 20-recipient crypto fan-out are orders of magnitude below the 20 ms audio frame budget on the baseline host;
- native/JVM backend matrix, strict native mode, Java fallback, cross-codec interoperability and bounded decoder scratch all pass under JDK 8u411;
- native package relocation was rejected by QA; final packaging preserves JNI names and contains audited third-party notices.

Measure, do not guess. Remaining targets to validate on representative hardware:
- voice networking/DSP never blocks the Minecraft tick/render thread;
- 20 ms capture cadence remains stable under normal client load;
- bounded queue latency under packet jitter; stale audio is dropped instead of accumulated;
- encoded forwarding avoids server-side decode/re-encode for compatible Akashic/SVC Opus streams;
- server routing scales without per-frame world scans or synchronous disk I/O;
- GC allocation profile identifies and removes avoidable 50-Hz hot-path garbage.

## Gate 6 — Cross-version bridge

Status: **PENDING**.

Acceptance:
- Akashic 1.7.10 -> SVC user and SVC -> Akashic user are both audible;
- sender identity, speaking state, position and channel semantics are mapped explicitly;
- proximity never leaks across unrelated backend coordinate spaces;
- Opus passthrough is used only when format/packet semantics are actually compatible;
- bridge failure cannot take down the core local 1.7.10 voice service.

## Critical finding — secret disclosure

Status: **PASS in Akashic Voice / FAIL in upstream**.

`/pcdebug` iterates `SecretRegistry.snapshot()` and prints `player UUID -> live secret UUID` into chat.
A session credential must never be exposed through admin chat, normal logs, metrics labels or exception messages.

Required remediation:
- remove raw-secret enumeration completely;
- debug/status output may expose counts, endpoint state and non-sensitive truncated session IDs only;
- add a source-level test/check that production logging paths do not format session keys.
