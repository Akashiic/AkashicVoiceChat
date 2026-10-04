# Akashic Voice - CLIENT41 UDP/NAT rebind qualification

Checkpoint: `AV-UDP-NAT-REBIND-20260915-41`.
Production baseline remains `integrated-client39`; this is external QA only.

The real GatewayUdpServer was exercised with session-bound handshakes from changing local UDP ports.
A single rebind test proved that the same bootstrap/session generation can authenticate from a new endpoint,
installs exactly one authoritative connection, immediately removes the old endpoint from uplink/downlink routing,
and continues MIC plus SVC delivery through the new endpoint.

A second stress scenario performed 16 consecutive endpoint rebinds using the same session ID and master secret,
with a fresh client nonce/AEAD state each time. After every rebind `activeSessions == 1`; every previous endpoint
failed to route media, while the newest endpoint continued normally. Final disconnect drained active sessions to zero.
Handshake burst limiting remained zero for this migration load.

Both external JUnit scenarios PASS against CLIENT39 production classes; Gradle build PASS in 21s on the expanded run.
No production source, artifact, active tree or deployment changed.
Planning estimate after this qualification: approximately **93%** (89-96% band).
Remaining release evidence is dominated by client-side recovery behavior, real WAN/two-client audio, physical devices,
mobile Pojav/Amethyst and production deployment/observability.
