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
- end-to-end local test: Akashic UDP MIC → Gateway → agent and SVC delivery/reset → Gateway → Akashic UDP.

Remaining live gates:
- deploy the Velocity and Paper artifacts against the actual Rede Akashic proxy/lobby;
- verify audible Akashic ↔ SVC speech with two real clients and backend transfers;
- verify SVC group/category behavior and long-session reconnects;
- add/negotiated protocol support for exact per-frame SVC maximum distance/whisper attenuation (wire v1 currently preserves position but not per-frame distance);
- prove cross-server group/radio semantics without treating unrelated backend coordinates as physical proximity.

The Gateway remains fail-open for backends without a healthy authenticated agent: it does not steal the legacy Forge bootstrap request from those servers.
