# Evidence archive index

Redacted copies of the engineering record from the original working folder, imported on 2026-10-04. **What is here:** handoff documents, JSON receipts and release-candidate manifests. **What is not:** JARs, ZIPs, candidate source trees, raw client logs and audio captures. File names are unchanged from the original so they can be cross-referenced with `docs/CONTINUITY.md`.

**Redaction applied:** the production host name and port were replaced by placeholders; pattern scans for keys, tokens, PSKs, secrets and passwords found no credential values. Do not add unredacted material here.

**Grade of everything below:** it is a *record of what an earlier engineering process reported*. It is not independently re-verified (see `docs/BUILD_AND_QA.md`).

## Handoffs (`handoffs/`)

52 files, listed alphabetically (CLIENT checkpoint numbers sort naturally within names).

| File | Size (KB) |
|---|---:|
| `CLIENT07_HANDOFF_20260913.md` | 3,6 |
| `CLIENT08_HANDOFF_20260913.md` | 3,8 |
| `CLIENT09_ISOLATED_HANDOFF_20260913.md` | 1,8 |
| `CLIENT10_FULL_INTEGRATION_HANDOFF_20260913.md` | 2,0 |
| `CLIENT10_ISOLATED_HANDOFF_20260913.md` | 1,9 |
| `CLIENT11_VOICE_GAIN_HANDOFF_20260913.md` | 5,8 |
| `CLIENT12_SPEAKER_CONTROLS_HANDOFF_20260913.md` | 2,0 |
| `CLIENT14_SPEAKER_PANEL_HANDOFF_20260913.md` | 6,1 |
| `CLIENT15_QUALITY_HANDOFF_20260913.md` | 5,0 |
| `CLIENT16_TOUCH_CORE_HANDOFF_20260914.md` | 5,6 |
| `CLIENT17_TOUCH_GUI_HANDOFF_20260914.md` | 3,0 |
| `CLIENT18_LEGACY_TELEMETRY_HANDOFF_20260914.md` | 3,8 |
| `CLIENT18_TELEMETRY_HANDOFF_20260914.md` | 4,1 |
| `CLIENT19_MTU_HANDOFF_20260914.md` | 3,6 |
| `CLIENT20_MTU_TELEMETRY_HANDOFF_20260914.md` | 4,5 |
| `CLIENT21_LEGACY_INVITES_HANDOFF_20260914.md` | 2,8 |
| `CLIENT22_SESSION_ID_HANDOFF_20260914.md` | 5,5 |
| `CLIENT26_EXTENDED_SOAK_HANDOFF_20260914.md` | 3,6 |
| `CLIENT26_SOAK_HANDOFF_20260915.md` | 2,3 |
| `CLIENT27_LOSS_JITTER_HANDOFF_20260914.md` | 3,0 |
| `CLIENT28_DECODER_CAP_HANDOFF_20260914.md` | 3,6 |
| `CLIENT30_CLIENT_DIAGNOSTICS_HANDOFF_20260915.md` | 2,5 |
| `CLIENT31_TALK_CACHE_HANDOFF_20260915.md` | 2,9 |
| `CLIENT33_EPOCH_RECLAMATION_HANDOFF_20260915.md` | 3,8 |
| `CLIENT34_SPEAKER_RECLAIM_HANDOFF_20260915.md` | 2,4 |
| `CLIENT36_TRANSFER_HANDOFF_20260915.md` | 1,8 |
| `CLIENT37_MULTIHOP_HANDOFF_20260915.md` | 1,4 |
| `CLIENT39_NAT_HANDSHAKE_HANDOFF_20260915.md` | 2,8 |
| `CLIENT40_PROLONGED_SOAK_HANDOFF_20260915.md` | 2,3 |
| `CLIENT41_NAT_REBIND_HANDOFF_20260915.md` | 1,4 |
| `CLIENT42_CLIENT_RECOVERY_HANDOFF_20260915.md` | 1,5 |
| `CLIENT42_NAT_REBIND_HANDOFF_20260915.md` | 2,6 |
| `CLIENT43_NAT_LOAD_HANDOFF_20260915.md` | 1,9 |
| `CLIENT44_LOGICAL_REBIND_HANDOFF_20260915.md` | 3,1 |
| `CLIENT46_LEGACY_AGENT_HANDOFF_20260916.md` | 3,1 |
| `CLIENT47_AGENT_OBSERVABILITY_HANDOFF_20260917.md` | 2,3 |
| `CLIENT48_CANARY_EVIDENCE_HANDOFF_20260917.md` | 2,5 |
| `CLIENT49_STATUS_PARSER_HANDOFF_20260917.md` | 2,5 |
| `CLIENT52_EVIDENCE_CHAIN_HANDOFF_20260917.md` | 3,5 |
| `CLIENT56_STAGE_RUNTIME_BINDING_HANDOFF_20260917.md` | 2,0 |
| `CLIENT57_PRECANARY_HANDOFF_20260917.md` | 0,8 |
| `CLIENT58_STANDALONE_RECEIPT_BINDING_HANDOFF_20260917.md` | 2,8 |
| `CLIENT59_REAL_CLIENT_RUNTIME_HANDOFF_20260917.md` | 3,0 |
| `CLIENT61_REAL_ROUTE_RECOVERY_HANDOFF_20260917.md` | 2,1 |
| `CLIENT62_CURRENT_RUNTIME_HANDOFF_20260917.md` | 3,3 |
| `CLIENT63_V38_HANDOFF_20260917.md` | 2,7 |
| `RC7_ENGINEERING_STATUS_20260917.md` | 3,5 |
| `REVIEWED15_HANDOFF_20260914.md` | 3,6 |
| `REVIEWED23_MODERATION_HANDOFF_20260914.md` | 4,0 |
| `REVIEWED24_ADDON_API_HANDOFF_20260914.md` | 4,4 |
| `REVIEWED25_RADIO_HANDOFF_20260914.md` | 3,5 |
| `VALIDATION_ONLY.md` | 0,6 |

## Receipts (`receipts/`)

143 JSON/text receipts. Grouped by leading checkpoint token:

| Group | Count | Example |
|---|---:|---|
| CLIENT06 | 3 | `CLIENT06_FOUNDATION.json` |
| CLIENT07 | 1 | `CLIENT07_CLEANUP_PATCH.json` |
| CLIENT08 | 1 | `CLIENT08_SPATIAL_PATCH.json` |
| CLIENT10 | 1 | `CLIENT10_INTEGRATION_APPLIED.json` |
| CLIENT11 | 4 | `CLIENT11_CONFIG_DESCRIPTION.json` |
| CLIENT12 | 2 | `CLIENT12_ENCODED_BOUNDARY.json` |
| CLIENT13 | 1 | `CLIENT13_TRANSMIT_PATCH.json` |
| CLIENT14 | 2 | `CLIENT14_FINAL_QA_SUMMARY.json` |
| CLIENT15 | 3 | `CLIENT15_QA_SUMMARY.json` |
| CLIENT16 | 3 | `CLIENT16_QA_SUMMARY_20260914.json` |
| CLIENT17 | 2 | `CLIENT17_QA_SUMMARY_20260914.json` |
| CLIENT18 | 4 | `CLIENT18_ACCURACY_PATCH.json` |
| CLIENT19 | 5 | `CLIENT19_MTU_PATCH.json` |
| CLIENT20 | 2 | `CLIENT20_CONSOLIDATION_AUDIT.json` |
| CLIENT21 | 1 | `CLIENT21_QA_SUMMARY_20260914.json` |
| CLIENT22 | 1 | `CLIENT22_QA_SUMMARY_20260914.json` |
| CLIENT24 | 2 | `CLIENT24_ADDON_API_STATIC_AUDIT.json` |
| CLIENT25 | 1 | `CLIENT25_RADIO_STATIC_AUDIT.json` |
| CLIENT26 | 2 | `CLIENT26_QA_SUMMARY_20260914.json` |
| CLIENT27 | 1 | `CLIENT27_QA_SUMMARY_20260914.json` |
| CLIENT28 | 1 | `CLIENT28_QA_SUMMARY_20260914.json` |
| CLIENT30 | 1 | `CLIENT30_STATIC_AUDIT.json` |
| CLIENT31 | 2 | `CLIENT31_FINAL_RECEIPT.json` |
| CLIENT33 | 1 | `CLIENT33_FINAL_RECEIPT.json` |
| CLIENT34 | 3 | `CLIENT34_BOUNDARY_IDENTITY_PATCH.json` |
| CLIENT35 | 1 | `CLIENT35_RESTART_SOAK_RECEIPT.json` |
| CLIENT36 | 1 | `CLIENT36_FINAL_RECEIPT.json` |
| CLIENT37 | 1 | `CLIENT37_FINAL_RECEIPT.json` |
| CLIENT39 | 1 | `CLIENT39_FINAL_RECEIPT.json` |
| CLIENT40 | 1 | `CLIENT40_QA_SUMMARY_20260915.json` |
| CLIENT42 | 1 | `CLIENT42_FINAL_RECEIPT.json` |
| CLIENT44 | 1 | `CLIENT44_FINAL_RECEIPT.json` |
| CLIENT45 | 1 | `CLIENT45_RC2_PREFLIGHT.json` |
| CLIENT46 | 2 | `CLIENT46_FINAL_RECEIPT.json` |
| CLIENT47 | 2 | `CLIENT47_DROP_COUNTER_PATCH.json` |
| CLIENT48 | 2 | `CLIENT48_FINAL_RECEIPT.json` |
| CLIENT49 | 2 | `CLIENT49_FINAL_RECEIPT.json` |
| CLIENT50 | 1 | `CLIENT50_FINAL_RECEIPT.json` |
| CLIENT51 | 2 | `CLIENT51_FINAL_RECEIPT.json` |
| CLIENT52 | 2 | `CLIENT52_FINAL_RECEIPT.json` |
| CLIENT53 | 1 | `CLIENT53-CRITICAL-ROLLBACK-RECEIPT.json` |
| CLIENT54 | 2 | `CLIENT54-BUNDLE-SMOKE-RECEIPT.json` |
| CLIENT55 | 4 | `CLIENT55_FINAL_RECEIPT.json` |
| CLIENT56 | 4 | `CLIENT56_FINAL_RECEIPT.json` |
| CLIENT57 | 1 | `CLIENT57_PRECANARY_REVALIDATION_20260917.json` |
| CLIENT58 | 15 | `CLIENT58_COMPILED_MUTATION_FINAL_RECEIPT.json` |
| CLIENT59 | 7 | `CLIENT59_ENVIRONMENT_ATTRIBUTION.json` |
| CLIENT60 | 1 | `CLIENT60_REAL_RESTART_LIFECYCLE_RECEIPT.json` |
| CLIENT61 | 1 | `CLIENT61_REAL_ROUTE_RECOVERY_RECEIPT.json` |
| CLIENT62 | 5 | `CLIENT62_CURRENT_RUNTIME_RECEIPT.json` |
| CLIENT63 | 1 | `CLIENT63-V38-BUNDLE-SMOKE-RECEIPT.json` |
| RC5 | 10 | `RC5-HOMOLOGATION-BUNDLE-RECEIPT.json` |
| RC7 | 1 | `RC7-HOMOLOGATION-V3.8-BUNDLE-RECEIPT.json` |
| REVIEWED | 8 | `REVIEWED15_QA_SUMMARY_20260914.json` |
| PRECANARY | 7 | `PRECANARY_READINESS_20260917.json` |
| EPOCH | 1 | `EPOCH_CLIENT_RECOVERY.json` |
| FINAL | 1 | `FINAL-RECEIPT.json` |
| LIVE | 2 | `LIVE_READINESS_CLIENT59_20260917.json` |

Key receipts for the current baseline:

- `receipts/CLIENT58_RC7_FINAL_RECEIPT.json` - RC7 automated gate summary, artifact hashes, open live gates.
- `receipts/CLIENT58_RC7_JAR_ORIGIN_RECEIPT.json` - class origin proven from the final RC7 JAR.
- `receipts/CLIENT58_RC7_SOURCE_MUTATION_RECEIPT.json`, `CLIENT58_RC7_COMPILED_MUTATION_RECEIPT.json` - mutation detection (5/5, 4/4).
- `receipts/RC7-HOMOLOGATION-V3.8-BUNDLE-RECEIPT.json` - operator pack bundle.

## Release-candidate manifests (`release-candidates/`)

- `release-candidate-20260915-rc1` - 2 files
- `release-candidate-20260915-rc2` - 2 files
- `release-candidate-20260916-rc3` - 3 files
- `release-candidate-20260916-rc4` - 4 files
- `release-candidate-20260917-rc5` - 4 files
- `release-candidate-20260917-rc6` - 3 files
- `release-candidate-20260917-rc7` - 3 files

Each directory holds manifests, SHA-256 lists and verification receipts for one sealed candidate; the JARs themselves were not imported. Hash tables for RC1-RC7 are consolidated in `docs/VERSIONING_AND_PROVENANCE.md`.

## How to use this archive

1. Start from `docs/PROJECT_STATE.md`; follow its pointers here only when you need the primary record.
2. A checkpoint named `CLIENTnn` is described in `docs/CONTINUITY.md`; its handoff and receipt share the number.
3. If a handoff disagrees with a later one, the later verified one wins (`AGENTS.md`).
4. Never edit these files. Add new evidence under a new dated folder, with command, source identity, process identity, log path and exit status.
