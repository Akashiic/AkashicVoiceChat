# Akashic Voice - CLIENT49 Canary status parser

Checkpoint: `AV-CANARY-STATUS-PARSER-20260917-49`.
Base release remains sealed RC5 / CLIENT47.
State: canary evidence ingestion tooling fully tested; **no live canary or deployment performed**.

## What changed

CLIENT49 adds no production code and changes no RC5 artifact.
It extends the CLIENT48 homologation-v3 kit with:
- `parse-status-v3.ps1`: parses actual `/avgateway status` and Agent-mode `/avdebug` console text into normalized numeric JSON;
- `apply-status-v3.ps1`: applies normalized observations to Baseline, Restart or Cleanup fields while requiring a new output file.

The parser accepts normal console timestamp/prefix text because it searches the stable command payload within the captured text.
It extracts Gateway runtime/routes/session counts/Agent queues and Legacy Agent connection/managed/queue/dropped/connect counters.
Raw console text is not embedded into the normalized status JSON.

## Executed evidence

- End-to-end parser/apply/evaluator pipeline: 8/8 PASS.
- Timestamp-prefixed Gateway and Legacy console text parsed successfully.
- Baseline, restart and cleanup fields populated correctly.
- Source evidence SHA-256 remained unchanged across all apply stages.
- A DOWN route remains `required_routes_up=false`.
- Missing Legacy Agent counter line is rejected.
- In-place evidence overwrite is rejected.
- Final synthetic parser-to-evaluator evidence reaches `READY_FOR_STABLE_REVIEW` only when all manual/live observations are also positive.

## Static audit

- 23/23 checks PASS.
- Four sealed RC5 artifact hashes remain unchanged.
- Parser markers were compared against the actual CLIENT47 `GatewayStatusCommand` and `AvDebugCommand` source strings.
- Normalized output contains only schema/gateway/legacy structures.
- Parser/apply scripts contain no PSK/secret/password/private-key/access-token field handling.
- No JAR is included in the CLIENT49 homologation tooling directory.

## Boundary

CLIENT49 reduces transcription error; it does not infer subjective listening/acoustic/mobile observations and never fabricates a PASS.
Those observations must still be entered from real testers/devices before the CLIENT48 evaluator can return stable-review readiness.
No active source, release artifact, server configuration or production deployment changed.

Planning estimate remains **approximately 96%**, judgment band **92-98%**.
The remaining percentage is dominated by real WAN/audio/mobile/canary evidence, not missing automated parser code.
