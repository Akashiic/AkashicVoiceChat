# Documentation index

Start with [`PROJECT_STATE.md`](PROJECT_STATE.md). Everything else is either a deeper dive or a historical record.

## Current-state documents (maintained)

| Document | Question it answers |
|---|---|
| [`PROJECT_STATE.md`](PROJECT_STATE.md) | What exists, what is proven, what is open? |
| [`HANDOFF.md`](HANDOFF.md) | I am taking over: rules, environment, next actions? |
| [`HOMOLOGATION.md`](HOMOLOGATION.md) | How is acceptance decided; which gates are open? |
| [`VERSIONING_AND_PROVENANCE.md`](VERSIONING_AND_PROVENANCE.md) | Which build is which? What is the version label? |
| [`FIELD_EVIDENCE_2026-10.md`](FIELD_EVIDENCE_2026-10.md) | What do real client logs (desktop + Android) show? |
| [`ISSUES_REGISTER.md`](ISSUES_REGISTER.md) | Which issues are tracked (mirror of GitHub Issues)? |
| [`BUILD_AND_QA.md`](BUILD_AND_QA.md) | How to build; what QA exists; what was recorded (not re-run)? |
| [`RELATED_PROJECTS.md`](RELATED_PROJECTS.md) | Which sibling projects exist on the workstation? |
| [`FRAGMENTATION_MAP.md`](FRAGMENTATION_MAP.md) | Where did each fragment of the original folder go? |
| [`HISTORY.md`](HISTORY.md) | Chronology from ProxyChat to RC7, dead ends |
| [`PT-BR_RESUMO.md`](PT-BR_RESUMO.md) | Resumo em português |

## Engineering contracts (authored by the project)

[`ARCHITECTURE.md`](ARCHITECTURE.md) · [`AUDIT.md`](AUDIT.md) · [`ROADMAP.md`](ROADMAP.md) · [`ROADMAP_STATUS_20260917.md`](ROADMAP_STATUS_20260917.md) · [`CONTINUITY.md`](CONTINUITY.md) · [`PERFORMANCE.md`](PERFORMANCE.md) · [`ADDON_API.md`](ADDON_API.md) · [`RADIO.md`](RADIO.md)

## Historical records (not maintained; kept for traceability)

[`PROGRESS_REVIEW_20260913.md`](PROGRESS_REVIEW_20260913.md), `PROGRESS_REVIEW_20260914_CLIENT22…26.md`, [`RECOVERY_AUDIT_2026-09-13.md`](RECOVERY_AUDIT_2026-09-13.md), [`ACTIVE_GROUP_INTEGRATION_2026-09-13.md`](ACTIVE_GROUP_INTEGRATION_2026-09-13.md), [`RC5_ENGINEERING_STATUS_20260917.md`](RC5_ENGINEERING_STATUS_20260917.md). Where they disagree with `PROJECT_STATE.md`, `PROJECT_STATE.md` and the receipts win.

## Evidence and procedures

- [`evidence/INDEX.md`](evidence/INDEX.md) — redacted handoffs (CLIENT06–63), JSON receipts, RC manifests.
- [`homologation/5eb51e1/`](homologation/5eb51e1/) — the original homologation plan and templates.
- [`../tools/homologation/rc7-v3.8/`](../tools/homologation/rc7-v3.8/) — RC7 / V3.8 operator tooling (PowerShell): rollback preparation, canary install, session binding, evaluators.

## Conventions

- English for repository documents; Portuguese only in `PT-BR_RESUMO.md`.
- Dates ISO (`YYYY-MM-DD`). Hashes SHA-256, full length where identity matters.
- Every claim carries an evidence grade; estimates are labelled "judgment".
- Do not edit files under `evidence/`; add new evidence in a new dated folder.
