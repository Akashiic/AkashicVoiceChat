# CLIENT26 extended gateway soak qualification

Checkpoint: `AV-EXTENDED-SOAK-20260915-26`.
State: **PASS qualification-only checkpoint**.
Tree: `audit/group-integration-20260913-04/integrated-client26`.
Baseline production: reviewed CLIENT25; production-source diff is exactly zero.
Only `GatewayRuntimeSoakTest.java` differs from CLIENT25.

## Extended scenario

Each of three repetitions runs 24 authenticated session-bound UDP clients.
Clients are paired into 12 radio channels and use the explicit session UUID handshake.
Each repetition performs 10 media cycles with 50 MIC frames/client/cycle at a 20 ms sender cadence.
The Paper-agent socket is replaced between cycles, for 9 reconnects per repetition.
Each cycle also exercises one SVC downlink per client.

Totals across the three repetitions:
- 72 authenticated client sessions opened and drained.
- 36,000 encrypted MIC frames accepted.
- 36,000 non-spatial radio deliveries validated.
- 720 SVC v2 downlinks validated.
- 27 live agent reconnects while UDP sessions remain established.
- 72 pair-scoped radio reset deliveries during initial tuning.
## Assertions

Every repetition asserts exact session open/close counts, MIC acceptance, radio delivery count,
zero group deliveries, zero rate limiting, zero encrypted rejection, zero agent media drops,
radio directory drained to zero after cleanup, and no runtime listener errors.
The route agent is reattached after each reconnect and all client sessions remain present.

## Executed evidence

Focused soak: PASS, 3/3 repetitions.
Recorded testcase times: 12.217 s, 12.153 s, 12.235 s; combined 36.613 s.
Full platform after the change: PASS, 30/30 tasks executed.
JUnit after changing only the repeated soak count: 149 tests / 36 suites, zero failures/errors/skips.
The count is lower than CLIENT25's 151 because this soak intentionally changed from 5 short repetitions
to 3 substantially heavier repetitions; no production test was removed.

Artifact/source identity check:
- production source diff CLIENT25 -> CLIENT26: 0 files;
- total source diff: exactly 1 test file;
- legacy DEV JAR SHA remains `8d2b04a191ee8415d77c928c6a4e3b9491e7ce632d3167fdea9d7ca592276936`;
- addon-api SHA remains `6e73a1f96d96251cc631f6a51e4a1e485022257f1cf0a517dc7781e8ff5de347`.

This is local loopback/load evidence, not real WAN, physical-audio or production-player soak.