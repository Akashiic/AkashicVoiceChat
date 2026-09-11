# Akashic Voice — Delivery Roadmap

The project advances by evidence gates, not by feature count.

## Phase A — Foundation / 0.1.x

1. Make upstream build reproducible.
2. Rebrand package, assets, config and commands to Akashic Voice while preserving MIT attribution.
3. Enforce Java 8 source + bytecode for the Forge/client artifact.
4. Fix offline-mode identity by sending the server-authoritative UUID in bootstrap.
5. Split capture/playback behind small SPIs so JavaSound/OpenAL/Android can vary independently.
6. Isolate Opus decoder state per remote stream.
7. Add deterministic codec/protocol unit tests and Java 8 runtime smoke tests.
8. Produce first alpha JAR only after Gates 0-3 pass.

## Phase B — Protocol v1 / 0.2.x

1. **DONE:** explicit AV wire magic and protocol version; capability negotiation remains.
2. **PARTIAL:** directional counters and bounded replay windows are done; explicit session IDs remain.
3. **DONE:** AES-128-GCM media/control protection with deterministic unique nonces.
4. **DONE:** client UDP receive path is bound to the expected endpoint.
5. **DONE:** parser bounds checks, unauthenticated/authenticated rate limiting and fuzz tests.
6. **PARTIAL:** payload caps are enforced; <=1200-byte transport target remains for MTU hardening.
7. **PENDING:** protocol metrics without per-frame log spam.

## Phase C — Audio quality / 0.3.x

1. Per-speaker jitter/reorder buffers with adaptive but bounded target delay.
2. Opus PLC for missing frames; evaluate in-band FEC with measured loss scenarios.
3. Player volume/mute/deafen controls.
4. Whisper/proximity semantic flags and non-spatial group playback.
5. Replace misleading basic 'noise suppression' label with explicit DSP stages.

6. Add limiter/AGC only after objective clipping/noise tests and listening tests.
7. Define quality presets (Economy/Balanced/High/Akashic HD) as measurable codec/DSP settings.

## Phase D — Gateway and SVC bridge / 0.4.x

1. Java 21 Akashic Voice Gateway with protocol-adapter interfaces.
2. Simple Voice Chat adapter through supported public API contracts.
3. Bidirectional voice, speaking state and positional semantics in the same backend.
4. Cross-server groups/radio channels; never fake cross-server physical proximity.
5. Opus passthrough fast path where negotiated format permits it; transcoder fallback otherwise.
6. Central moderation, permissions, metrics and session routing.
7. Failure isolation: SVC/Velocity/Paper adapter outage must not break Forbidden-local voice.

## Phase E — Mobile / 0.5.x

1. Run Java 8 client under Pojav legacy and Amethyst test matrices.
2. Validate JavaSound microphone enumeration/capture; do not assume it works.
3. Validate OpenAL capture as portable fallback.
4. If both are unreliable, implement the smallest possible Android-native AudioRecord/Oboe adapter.
5. Add touch-friendly PTT/toggle UX without requiring a physical keyboard.
6. Profile thermal load, battery impact, encode complexity and jitter on ARM devices.

## Release gate

No release is called stable until clean build, Java 8 runtime, offline-mode auth, packet-loss/jitter,
malformed-packet, reconnect, multi-speaker decoder isolation and prolonged-session tests pass.
Performance conclusions require captured measurements; visual/audio quality conclusions require explicit test cases.
