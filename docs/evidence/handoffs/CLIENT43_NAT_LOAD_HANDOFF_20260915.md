# Akashic Voice - CLIENT43 NAT rebind under sustained media

Checkpoint: `AV-NAT-REBIND-MEDIA-QUALIFICATION-20260915-43`.
Baseline production: CLIENT42.
Candidate: `integrated-client43`.
Kind: QA-only qualification; production source is byte-identical to CLIENT42.
State: PASS / not deployed.

## Added qualification
The existing real-UDP NAT rebind suite now includes sustained media during mapping churn:
- 12 successive session-bound UDP endpoints for one authoritative voice session.
- 50 authenticated MIC frames per endpoint at 20 ms cadence (600 MIC frames total).
- Previous endpoint is probed after each rebind and must remain unable to route media.
- One bridge/SVC downlink is sent after each rebind (12 total) to the current endpoint.
- Previous endpoint must remain silent for each downlink.
- `pendingHandshakes` remains exactly one while active and zero after the final disconnect.
- No media rate limit or encrypted rejection is expected for valid traffic.

## Executed evidence
- Focused NAT suite: PASS, 11/11 tasks rerun.
- Full platform clean build: PASS in 3m12s, 38 actionable / 37 executed.
- JUnit: 180 tests / 41 suites, zero failures/errors/skips.
- `GatewayUdpServer.java` SHA256 is identical to CLIENT42:
  `7d88198e48ee453cffe82729e676e86593f6871c57d6ed853109e1b427b455da`.
- Gateway JAR is identical to CLIENT42:
  `73402ddb9fb2193fbca21b0190517389f1df3c730d8690f13316af984c2e90d1`.
- Updated NAT regression test SHA256:
  `8a3b06de6431ad61f5af4d11609726ff5935d7e69b311a1b8e58c2255b1180eb`.

No production feature credit is claimed for this QA-only checkpoint.
Planning estimate increases conservatively to approximately **94%**, judgment band **90-97%**,
because NAT rebinding now has both bounded-state hardening and sustained-media qualification.
Remaining completion gates are dominated by real WAN, physical audio/listening, mobile device and deployment evidence.
