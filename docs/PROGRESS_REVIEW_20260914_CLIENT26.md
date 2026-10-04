# Akashic Voice progress review - CLIENT26

Checkpoint: `AV-SUSTAINED-SOAK-20260914-26`.
Qualification tree: `audit/group-integration-20260913-04/integrated-client26`.
Production baseline remains byte-identical to `reviewed-client25-20260914`.

Planning estimate: **approximately 82% complete**, judgment band roughly **77-87%**.
This is a one-point evidence gain, not a new feature count.

CLIENT26 adds sustained automated Gateway qualification without changing production code:
24 session-bound clients, 12 radio pairs, 10 agent cycles, 50 frames/client/cycle,
20 ms producer cadence and three fresh-runtime repetitions.

Total exercised traffic: 36,000 encrypted MIC frames, 36,000 radio direct frames,
720 SVC deliveries, 72 authenticated sessions and 27 agent route-down transitions.
Final assertions require zero HELLO/media rate-limit, encrypted rejection and agent media drops.

Full platform no-cache build PASS; 149 JUnit tests / 36 suites PASS.
Root Forge no-cache build PASS, 35 tasks executed.
All production artifacts are byte-identical to CLIENT25, so its JDK8/JAR replay,
mutation, structural and native-bundle evidence remains directly applicable.

The initial unpaced 24-client attempt correctly crossed the per-IP HELLO burst policy
(20-token burst) and timed out waiting for a challenge. Production security limits were
not relaxed; the final soak paces bootstrap at 60 ms and explicitly requires zero rate-limited HELLOs.

This remains local loopback/agent automation. It does not satisfy live real-client speech,
physical microphone/OpenAL, real WAN loss/jitter, Pojav/Amethyst, thermal/battery,
or production deployment/restart listening gates.

The remaining project weight is now dominated by external/live evidence rather than basic
protocol feature implementation. FEC also remains deliberately open: the current native
Opus wrapper lacks the symmetric FEC controls needed to ship one consistent backend policy.