# Akashic Voice - CLIENT39 NAT handshake burst hardening

Checkpoint: `AV-NAT-HANDSHAKE-BURST-20260915-39`.
Baseline: `integrated-client37`.
Candidate: `integrated-client39`.
State: fully gated on the platform; not deployed or promoted to the active tree.

## Finding
The Gateway handshake limiter used a per-IP burst of 20 HELLOs. That is safe for a single user,
but it rejects legitimate synchronized reconnects when many clients share one public/NAT address.
Bulk backend transfer/restart qualification now exercises many session rotations close together.

## Production change
Exactly one production file changes relative to CLIENT37:
`GatewayHandshakeRateLimiter.java`.
The sustained per-IP refill remains 10 HELLO/s. Global refill remains 128/s and global burst 256.
Only the per-IP burst allowance increases from 20 to 64, bounded by the unchanged global cap.
A clock supplier was added for deterministic limiter tests; production uses `System::nanoTime`.

## Evidence
- Focused limiter + transfer-storm tests: PASS.
- Full platform clean/no-configuration-cache: PASS, 38/38 tasks executed.
- Final JUnit count: 176 tests / 40 suites, zero failures/errors/skips.
- Real Gateway NAT burst integration: 32 UDP clients from the loopback IP sent HELLO before reads;
  all 32 received their own session-bound CHALLENGE and `helloRateLimited` remained zero.
- Negative control compiled with the previous burst of 20 fails at client index 20.
- Unit tests still prove the 65th same-IP HELLO is rejected, one token returns only after 100 ms,
  source ports share an IP bucket, different IPs are independent, and aggregate burst remains 256.

## Scope / inheritance
CLIENT37 -> CLIENT39 has four source/test differences: one production limiter change,
two new limiter/NAT tests and the CLIENT38 transfer-storm extension.
All non-platform source and every non-Gateway platform module are hash-identical to CLIENT37.
Therefore the clean CLIENT36 Forge/Java8 production artifact chain remains applicable to those unchanged sources.
Paper SHA256: `b5e66814edfd8bfb1b197d786ddb9da727a75e9940dad7ca44da9b92b2c21bdb`.
Addon API SHA256: `6e73a1f96d96251cc631f6a51e4a1e485022257f1cf0a517dc7781e8ff5de347`.
Gateway SHA256: `2218962a1b7955ba87ec77c22941b92d7b44c9072368fb4fcf191e49c1ac6254`.

## Boundaries
This checkpoint improves legitimate NAT/bulk reconnect capacity; it does not remove abuse controls.
It does not prove real-WAN NAT behavior, physical voice quality, mobile clients, or production deployment.
No active source tree, distribution, tag, or live server was changed.
Planning estimate after this hardening: approximately **91%**, judgment band **87-95%**.
The remaining work is dominated by process-level/live integration, physical audio/mobile validation,
and release deployment evidence rather than missing core protocol features.
