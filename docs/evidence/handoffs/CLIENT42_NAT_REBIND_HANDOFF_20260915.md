# Akashic Voice - CLIENT42 NAT rebind receipt reclamation

Checkpoint: `AV-NAT-REBIND-RECEIPT-RECLAMATION-20260915-42`.
Baseline production: `integrated-client39` (CLIENT40/41 were QA-only qualifications).
Candidate: `integrated-client42`.
State: full platform gated / not deployed.

## Reproduced defect
CLIENT41 proved endpoint authority moved correctly on NAT rebind, but a stronger assertion exposed
that each activated AUTH receipt remained in `pendingHandshakes` until the 10-second sweep.
The baseline failed `pendingHandshakes == 1` during repeated same-session rebinding.
This was not dual media authority, but transient handshake-state accumulation.

## Production fix
Only `GatewayUdpServer.java` changes in production.
The current endpoint keeps its activated receipt so retransmitted AUTH can receive another AUTH_ACK.
When a connection is displaced by rebind, its activated receipt is removed immediately.
When an active connection is actually removed/disconnected, its activated receipt is removed too.
Cleanup verifies endpoint, proxy player and voice identity before removal.
The media crypto/session wire format, rate limiter and Agent TCP channel are unchanged.

## Executed evidence
- Baseline negative control: targeted CLIENT41 test failed after adding the pending-receipt bound.
- Focused CLIENT42: 3 NAT tests PASS with all tasks rerun.
- Session-bound test: 32 consecutive rebinds, one active session, one pending receipt, old endpoint rejected.
- Legacy handshake rebind: same bound and old-endpoint rejection.
- Both handshake modes retransmit identical AUTH_RESPONSE and receive a second AUTH_ACK without opening a new session.
- Final disconnect leaves zero active sessions and zero pending receipts.
- Full platform clean build: PASS in 2m58s, 38 tasks / 37 executed.
- JUnit: 179 tests / 41 suites, zero failures/errors/skips.
- Mutation sensitivity: PASS 4/4 after correcting the mutation harness to copy shared sources.
  Mutants independently removed legacy cleanup, session cleanup, disconnect cleanup, or current-receipt retention.

Gateway JAR SHA256: `73402ddb9fb2193fbca21b0190517389f1df3c730d8690f13316af984c2e90d1`.
GatewayUdpServer SHA256: `7d88198e48ee453cffe82729e676e86593f6871c57d6ed853109e1b427b455da`.
Regression test SHA256: `f7f7d44fa013b055e00b59d8fe44ce8f2505c288819fec59ac87e0a8133174c3`.
Source diff versus CLIENT39: exactly one production Java file and one test Java file.

No active-tree promotion, production deployment, physical audio, real WAN, or mobile-device test occurred.
Planning estimate after this production hardening: approximately **93%**, judgment band roughly **89-97%**.
