# Akashic Voice — Homologation 5eb51e1

This bundle is for controlled pre-production validation. It is not a stable release.

## Artifacts

- `akashicvoice-client-server-1.7.10.jar`: Forge 1.7.10 client/server artifact, Java 8 bytecode.
- `akashicvoice-gateway-velocity-4.0.0.jar`: Velocity 4.0.0 Gateway.
- `akashicvoice-svc-bridge-paper-1.21.1.jar`: Paper 1.21.1 / Simple Voice Chat bridge.

## Runtime matrix

- Client 1.7.10: Java 8 baseline; Java 17/21 compatibility is also supported.
- Crucible 1.7.10: Java 21 is supported.
- Paper lobby 1.21.1: Java 21.
- Velocity 4.0.0 Gateway: Java 25 runtime.

## Security setup

1. Run `Generate-AgentPsk.ps1` locally.
2. Put that Base64 value in the Velocity key `agent-psk.lobby.base64`.
3. Put the same value in the Paper bridge key `gateway.psk-base64`.
4. Never put a client voice bootstrap secret in either setting.
5. Do not reuse the lobby PSK for a different backend.

## Installation order

1. Velocity: install `akashicvoice-gateway-velocity-4.0.0.jar` and start once to generate its data folder/config.
2. Stop Velocity and apply the values from `gateway-config.properties.template`.
3. Paper lobby: install Simple Voice Chat and `akashicvoice-svc-bridge-paper-1.21.1.jar`.
4. Start Paper once, stop it, then apply `paper-bridge-config.yml.template`.
5. Install `akashicvoice-client-server-1.7.10.jar` on the test 1.7.10 client and on Crucible for native Forbidden voice.
6. Start Paper first, then Velocity, then the test clients.

## Health gate before audio

Velocity console/player with permission:
`/avgateway status`

Expected:
- Runtime = ONLINE
- `Routes: lobby=UP`
- no continuously rising reject counters while idle

Paper console/OP:
`/avbridge status`

Expected:
- Gateway = CONNECTED
- SVC API = BOUND
- before Akashic players join, desired/active/bindings may all be 0

## Test 1 — Akashic client establishes Lobby session

1. Join the Lobby using the 1.7.10 Akashic Voice client through the normal Rede Akashic route.
2. Run `/avgateway status`.
3. Run `/avbridge status`.

PASS:
- Gateway active session count increases.
- `micIn` rises only while the Akashic client is transmitting.
- Paper `desired`, `active`, and `bindings` become 1 for one managed Akashic player.
- No secret/PSK/nonce appears in either log.

FAIL diagnosis:
- route DOWN: agent/PSK/config problem;
- route UP but no active UDP session: bootstrap/client/Gateway UDP problem;
- active UDP session but Paper active=0: player-name/local UUID/SVC binding problem.

## Test 2 — Akashic → SVC audio

1. Place one Akashic 1.7.10 user and one normal SVC 1.21.1 user near each other in the Lobby.
2. Akashic user speaks continuously for several seconds.

PASS:
- SVC user hears intelligible audio without obvious double playback or feedback loop.
- Gateway `micIn` rises.
- Paper bridge remains active and connected.
- No decode/re-encode should occur in the bridge fast path.

## Test 3 — SVC → Akashic audio

1. Reverse the previous test: only the SVC user speaks.
2. Move through several distances while speaking.
3. Exercise SVC whisper once.

PASS:
- Akashic user hears the SVC speaker spatially.
- Gateway `svcOut` rises.
- distance behavior changes with SVC per-frame metadata carried by `SOUND_FRAME_V2`.
- whisper delivery is received without turning into global/non-spatial audio.
- `resets` rises when the SVC stream resets/stops where applicable.

## Test 4 — Backend handoff

Repeat at least 10 cycles:
`Lobby -> forbidden-1 -> Lobby`

PASS:
- client receives rebootstrap without Minecraft relog;
- Lobby uses the Gateway session;
- Forbidden uses its native Forge/Crucible voice path;
- returning to Lobby creates a fresh Gateway-bound session;
- old UDP session does not keep routing audio to the previous backend;
- no duplicate voices or stuck speaking indicators.

## Test 5 — Agent reconnect / failure isolation

1. With a healthy Akashic Lobby voice session, stop only the Paper bridge or break its agent connection.
2. Observe `/avgateway status` until `lobby=DOWN`.
3. Restore the Paper bridge with the same PSK.

PASS:
- Velocity itself remains healthy;
- existing UDP state does not crash the Gateway;
- route returns to `lobby=UP` after agent reconnect;
- managed players are re-attached without Minecraft relog;
- native Forbidden voice remains unaffected.

## Test 6 — Negative PSK

Temporarily put a wrong PSK in the Paper bridge.

PASS:
- agent never reaches `lobby=UP`;
- no fallback authentication succeeds accidentally;
- after restoring the correct backend-specific PSK, the route recovers normally.

## Soak gate

After functional tests pass, run a multi-hour session with repeated talking, silence, movement and backend transfers. Record both status commands periodically. Reject the build if sessions/pending handshakes continually grow, reject counters rise under normal traffic, memory grows without settling, or audio latency accumulates over time.
