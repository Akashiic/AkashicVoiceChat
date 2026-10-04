# Akashic Voice - CLIENT40 prolonged-session qualification

Checkpoint: `AV-PROLONGED-SESSION-SOAK-20260915-40`.
Baseline production: `integrated-client39`.
Kind: external QA qualification only; no candidate production tree was created.
State: PASS / not deployed.

The attempt to create an `integrated-client40` snapshot was security-blocked before any write.
The qualification therefore injects one external test source into CLIENT39's Gateway test source set
through `client40-long-soak.init.gradle`; production sources and artifacts remain unchanged.

## Load
- 24 session-bound UDP clients in 12 radio pairs.
- 10 Agent cycles with 350 MIC frames/client/cycle at 20 ms cadence.
- Main soak repeated 3 times; 27 Agent reconnects total.
- Transfer storm uses 12 clients, 6 backend targets and 350 frames/client/target; repeated twice.
- 144 per-client backend transfers total across lobby, ATM10 normal and Forbidden routes.
## Aggregate evidence
- 302,400 authenticated MIC frames accepted.
- 302,400 non-spatial radio deliveries checked.
- 864 Agent->Gateway SVC downlinks checked.
- 240 authenticated UDP session openings across the repeated scenarios.
- Final session counts drained to zero in every repetition.
- Agent media drops, HELLO/media rate limits and encrypted rejections remained zero where asserted.
- Test class result: 5 tests, 0 failures/errors/skips, 317.841 s of JUnit test time.
- Gradle invocation: PASS in 5m37s; only external test compilation/execution ran.

The first run exposed a QA-only issue: long duration crossed the Agent heartbeat interval and the
old short-soak assertion mistook a valid `AgentMessage.Ping` for a media frame. The harness was
fixed to mirror the real Paper link: reply with Pong and continue awaiting the functional message.
The same 350-frame load then passed; load was not reduced.

Gateway JAR SHA256 remains `2218962a1b7955ba87ec77c22941b92d7b44c9072368fb4fcf191e49c1ac6254`.
Baseline soak source SHA256: `bbe9b1d0aabdc95062766371fe99d4d6383356f6fcc2f1842f709d92a30fa11b`.
External long-soak source SHA256: `860ea093118bb754a72e89b5581b73ebdc5698098d9af67ebd894c813cc34fcd`.
No active-source promotion, production deployment, WAN test, physical audio or mobile validation occurred.
Planning estimate after this automated release qualification: approximately **92%** (88-96% band).
