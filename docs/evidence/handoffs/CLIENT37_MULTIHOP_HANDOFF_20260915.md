# Akashic Voice - CLIENT37 multi-hop backend qualification

Checkpoint: `AV-MULTIHOP-BACKEND-SOAK-20260915-37`.
Baseline: `reviewed-client36-20260915`.
Candidate: `integrated-client37`.
Production source diff: zero.

CLIENT37 extends only `GatewaySessionRestartSoakTest`.
The class now executes 6 restart repetitions, 6 single lobby->forbidden transfer repetitions,
and 3 multi-hop repetitions of 12 hops each across `lobby`, `atm10-normal`, and `forbidden`.
That is 36 chained backend transfers in the new scenario.

At every hop the old voice/session IDs must rotate, the old backend receives DETACH,
`activeUdpSessions` must fall to zero before the fresh handshake, stale encrypted media must not route,
and the new backend must become the only active voice route after authentication.

Focused `GatewaySessionRestartSoakTest`: 15/15 JUnit PASS, zero failures/errors/skips.
Complete platform clean build: PASS in 2m52s, 38 tasks (37 executed, 1 up-to-date).
Complete platform count: 170 tests / 38 suites / zero failures, errors, or skips.
Production hashes are identical to CLIENT36, so Forge/Java8/JAR production gates are inherited
from the clean CLIENT36 artifact baseline rather than re-credited as new product work.

CLIENT37 is a release-qualification checkpoint, not a production feature delta.
No active-source promotion or deployment occurred.
Planning estimate remains approximately 90% (86-95% judgment band).
