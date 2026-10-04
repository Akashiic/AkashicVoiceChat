# Active group integration checkpoint

Scope: Akashic Voice only; preserve MIC-RECOVERY-01 and all existing dirty work.
Goal: integrate bounded group control and asynchronous persistence with the Gateway.
Starting HEAD observed: 022dc1f; no build lock at initial inspection.
Candidate inputs: GROUP-HARDENING-02 and GROUP-PERSISTENCE-03.
Neither candidate is treated as already installed or production-approved.

Work gates:
- Compare candidate baselines with current source hashes before changes.
- Authorize group operations using current player/route identity and permissions.
- Preserve native Forbidden bootstrap/control when not managed by the Gateway.
- Keep persistence I/O off media/control paths and block mutations until ready.
- Reject stale connections, malformed requests and unbounded control traffic.
- Test handler/controller lifecycle and run the full platform suite.
- Preserve the original distribution; no automatic deployment.

This checkpoint is an integration work record, not an approval or completion claim.
