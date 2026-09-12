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

## Gate 5A - Capture portability / mobile readiness

Status: **PARTIAL PASS - JavaSound/OpenAL Capture abstraction and recovery QA pass on Java 8; real Android/Pojav/Amethyst microphone validation remains.**

Evidence:
- capture UI, setup wizard and mic test use backend-neutral device IDs instead of `Mixer.Info`;
- `auto`, `javasound` and `openal` routing is configurable while legacy raw JavaSound mixer names are migrated;
- LWJGL2 ALC capture compiles against the exact Forge 1.7.10 LWJGL API and does not create/destroy the global OpenAL context;
- OpenAL probing is linkage-safe: missing `lwjgl64` does not crash device enumeration or the GUI;
- fixed direct scratch buffers bound OpenAL polling/copy memory; no per-poll buffer allocation;
- microphone startup and runtime failure recovery retry outside the Minecraft tick/render thread with log-spam suppression;
- full Akashic source recompiles with Oracle JDK 8u411 after the capture changes;
- `CaptureBackendSmoke` passes without opening a physical microphone.

Remaining acceptance:
- capture real microphone audio under Pojav legacy and Amethyst on ARM64;
- verify Android permission lifecycle, suspend/resume and Bluetooth/wired hotplug;
- measure 20 ms cadence, thermals and battery cost;
- add Android-native AudioRecord/Oboe fallback only if JavaSound/OpenAL prove unreliable.

## Gate 6 — Cross-version bridge

Status: **PARTIAL PASS — secure Gateway/agent/SVC transport passes local end-to-end QA; live Rede Akashic deployment and listening tests remain.**

Evidence:
- `client-protocol` compiles the exact Java 8 AV wire/crypto sources used by both the legacy mod and Gateway;
- Velocity 4.0.0 Gateway compiles/tests on a Java 25 toolchain; Paper/SVC adapter compiles/tests on Java 21;
- Forge legacy bootstrap request/response framing is byte-tested, plus discriminator-9 `REBOOTSTRAP` for backend handoff;
- Gateway creates backend-bound random voice identities and 256-bit secrets instead of trusting proxy/offline UUID equivalence;
- Paper agent uses a separate 256-bit PSK/HKDF domain and AES-GCM channel; wrong PSK is rejected;
- agent reconnect replaces the old route without a false route-down race;
- proxy UUID and local Bukkit/SVC UUID may differ and are bound explicitly by exact player name; duplicate local claims are rejected;
- Gateway UDP integration test covers encrypted auth, MIC, replay rejection, SOUND and disconnect;
- full runtime integration test covers Akashic UDP MIC -> Gateway -> agent and agent SVC delivery/reset -> Gateway -> Akashic UDP;
- local reconnect/load soak keeps 8 authenticated UDP clients alive across 4 agent reconnect cycles, preserving 256 MIC uplinks and 32 SVC v2 downlinks with no leaked sessions;
- normal EOF/socket closure during agent reconnect is classified as route lifecycle, not a false operational error.
- SVC adapter maps through public `AudioSender` / `PlayerAudioListener` contracts and preserves compatible Opus bytes without decode/re-encode;
- agent transport uses separate bounded control/media queues; media producer threads do not block on a stalled TCP peer and oldest media is dropped under pressure while stream reset ordering is preserved;
- 20-client Gateway soak passes with 4 agent reconnect cycles, 640 encrypted MIC frames, 80 SVC downlinks, zero leaked UDP sessions and zero unexpected media/replay rejection;
- `/avgateway status` and `/avbridge status` expose non-sensitive queue/drop/session diagnostics for homologation.
- backends without a healthy authenticated agent remain fail-open to their native Forge bootstrap path.

Remaining acceptance:
- run two real clients through Velocity + Paper lobby + Simple Voice Chat and prove audible bidirectional speech;
- prove lobby <-> Forbidden backend transfer repeatedly without stale voice sessions;
- live-test offline-mode UUID divergence with the actual proxy/Paper configuration;
- live-listen and validate SVC per-frame distance/whisper attenuation; additive SOUND_FRAME_V2 now carries max-distance + whisper metadata while legacy v1 remains unchanged;
- validate group/category semantics and cross-server radio without leaking physical proximity across unrelated backends;
- extend the passing local reconnect/load soak to Gateway process restart and prolonged real-network sessions under load.

## Critical finding — secret disclosure

Status: **PASS in Akashic Voice / FAIL in upstream**.

`/pcdebug` iterates `SecretRegistry.snapshot()` and prints `player UUID -> live secret UUID` into chat.
A session credential must never be exposed through admin chat, normal logs, metrics labels or exception messages.

Required remediation:
- remove raw-secret enumeration completely;
- debug/status output may expose counts, endpoint state and non-sensitive truncated session IDs only;
- add a source-level test/check that production logging paths do not format session keys.
