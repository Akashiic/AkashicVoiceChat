# Akashic Voice Platform

Server-side platform for cross-backend and cross-protocol Akashic Voice routing.

The platform is intentionally isolated from the Forge 1.7.10 client artifact. The client/common wire remains Java 8; Paper/SVC modules target Java 21; the Velocity 4.0.0 gateway targets Java 25.

## Modules

- `client-protocol`: the exact Java 8 AV wire/crypto source shared with the legacy client.
- `bridge-core`: Minecraft-independent bridge/session models.
- `agent-protocol`: authenticated Paper-agent ↔ Gateway protocol with a dedicated key domain.
- `svc-paper`: Paper 1.21.1 adapter built only against the public Simple Voice Chat API 2.6.20.
- `gateway-velocity`: Velocity 4.0.0 bootstrap, UDP session authority, backend routes and agent hub.

## Gate 6 status

The local integration path is implemented and QA-tested, but is not yet approved as a live deployment.

Implemented/tested locally:
- Forge legacy `AkashicVoice` bootstrap framing and proxy-triggered rebootstrap on backend transfer;
- backend-bound voice identities and 256-bit bootstrap secrets;
- client ↔ Gateway UDP HELLO/CHALLENGE/AUTH_RESPONSE/AES-GCM using the same Java 8 source as the mod;
- authenticated PSK agent channel with HKDF domain separation, AES-GCM, replay protection and reconnect handling;
- raw Opus passthrough through SVC `AudioSender` / `PlayerAudioListener` adapter paths;
- proxy UUID ↔ local Bukkit UUID binding by exact player name for offline-mode/proxy deployments;
- end-to-end local test: Akashic UDP MIC → Gateway → agent and SVC delivery/reset → Gateway → Akashic UDP;
- additive `SOUND_FRAME_V2` preserves per-frame SVC max-distance and whisper metadata without changing Forbidden's v1 packet contract.
- bounded agent backpressure keeps control responsive, drops stale media instead of blocking producers and preserves reset ordering;
- permanent soak QA covers five independent runs of 20 concurrent Akashic UDP clients, concurrent MIC bursts and repeated agent reconnects;
- SocketGatewayLink loopback integration covers both media/control directions plus disconnect cleanup and automatic agent reconnect;
- Gateway restart QA rejects stale voice identities and requires a fresh bootstrap before a new secure session can authenticate.

Remaining live gates:
- deploy the Velocity and Paper artifacts against the actual Rede Akashic proxy/lobby;
- verify audible Akashic ↔ SVC speech with two real clients and backend transfers;
- verify SVC group/category behavior and long-session reconnects;
- validate live SVC attenuation/whisper behavior carried by additive `SOUND_FRAME_V2`; wire v1 remains unchanged for Forbidden-local voice;
- prove cross-server group/radio semantics without treating unrelated backend coordinates as physical proximity.

The Gateway remains fail-open for backends without a healthy authenticated agent: it does not steal the legacy Forge bootstrap request from those servers.

## Agent PSKs

The Gateway stores a distinct 256-bit PSK for every allowed backend. For example:

```properties
allowed-backends=lobby,forbidden-1
agent-psk.lobby.base64=<lobby-only-secret>
agent-psk.forbidden-1.base64=<forbidden-only-secret>
```

The Paper bridge for `backend-id: lobby` must receive only the value from `agent-psk.lobby.base64` in its `gateway.psk-base64` setting. A backend PSK must never be copied to another backend. Legacy single-key configs are migrated automatically only when exactly one backend is allowed; a legacy global key with multiple backends is rejected.
## Homologation diagnostics

- Velocity: `/avgateway status` (permission `akashicvoice.gateway.status`). Shows runtime state, UDP/agent ports, backend route readiness, active/pending sessions, accepted MIC/downlink/reset counters and aggregate reject counters. It never prints player identities, bootstrap secrets, nonces or PSKs.
- Paper lobby: `/avbridge status` (permission `akashicvoice.bridge.status`, default OP). Shows Gateway connectivity, SVC API binding, desired managed players, active bridge sessions and identity-binding count.

Use both commands together during live tests: if the Gateway route is `UP` but the Paper bridge reports `DISCONNECTED`, investigate the agent link/PSK; if both are healthy but `micIn` does not rise, investigate client bootstrap/UDP; if `micIn` rises but active Paper sessions stay at zero, investigate local player/SVC binding.
