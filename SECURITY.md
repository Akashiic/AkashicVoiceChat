# Security policy

## Reporting

Akashic Voice is pre-release software run by Rede Akashic. Report vulnerabilities privately to the repository owner (GitHub: `Akashiic`) via a private security advisory on this repository. Do not open a public issue for anything that exposes a credential, a bypass of authentication/replay/rate limits, or a way to impersonate a player.

## What counts as a credential

Session bootstrap secrets (256-bit), handshake nonces, derived keys, and the per-backend agent PSKs (`agent-psk.<backend>.base64`, Paper `gateway.psk-base64`) are **credentials**. They must never appear in logs, documentation, checkpoints, test reports, issues or screenshots. `/avdebug` and `/avgateway status` expose aggregate counters only; keep it that way.

Operational rules:

- Generate a distinct 256-bit PSK per backend (`docs/homologation/5eb51e1/Generate-AgentPsk.ps1`); never reuse a client bootstrap secret as an agent PSK.
- Production hostnames and ports are redacted in this repository's evidence archive (`<public-voice-host>`, `<port>`).
- JARs and homologation bundles (which embed JARs) are not committed; only SHA-256 values are.

## Security model in one paragraph

Server-authoritative identity and position; HMAC-proved handshake with fresh nonces; HKDF-SHA-256 directional AES-128-GCM keys; authenticated headers; 64-packet replay window; HELLO and authenticated-media rate limits applied before expensive work; datagrams ≤ 1200 bytes; oversized or malformed Opus declarations rejected before allocation. Offline-mode remains a weaker identity root: the UDP session is secured, the Minecraft login is not made trustworthy by it. This model is covered by automated tests and mutation checks, **not** by an independent external audit.
