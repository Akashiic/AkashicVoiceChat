# Issues register

Mirror of the GitHub Issues of this repository as created on 2026-10-04 (milestone `stable-v1`). GitHub is the live system; this file records the initial set and the reasoning, so the register survives outside GitHub. **Pinned:** #1, #2, #3.

Legend - Evidence: LO = LIVE-OBSERVED, ST = STATIC, AU = AUTOMATED, none = no evidence yet.

| # | Title | Type | Sev. | Evidence | Source | Depends on / blocks |
|---:|---|---|---|---|---|---|
| 1 | Stable-v1 homologation tracker (pinned) | tracking | - | - | `PROJECT_STATE.md` | umbrella |
| 2 | Version label is not a build identity (pinned) | provenance | medium | ST + LO | `VERSIONING_AND_PROVENANCE.md` | blocks attribution of all field evidence |
| 3 | OpenAL natives unlinked after sound-system reload (pinned) | defect | high | LO (desktop) | F1 | blocks #14; related #5, #17 |
| 4 | Voice-server timeouts around backend transfers (direct mode) | defect | medium | LO | F2 (H2) | related #9, #10 |
| 5 | Output-recovery events in ordinary sessions | defect | medium | LO | F3 | related #3, #14 |
| 6 | Missing `modern_fonts.json` ERROR every launch | defect | low | LO | F4 | - |
| 7 | Audio-thread OOM in `PlaybackChannel.recycleProcessed` | defect | low | LO (once) | F5 | related #3 |
| 8 | Re-verify carried-over items from CONTINUITY | verification | - | ST | `PROJECT_STATE.md` section 5 | overlaps #3 |
| 9 | G1 Controlled backend process restart | gate | - | none live | `HOMOLOGATION.md` | needs #2, #4 |
| 10 | G2 Two-client WAN listening | gate | - | none live | `HOMOLOGATION.md` | needs #2, #3, #4 |
| 11 | G3 Acoustic review + AGC/limiter decision | gate | - | none | `HOMOLOGATION.md` | needs #10 |
| 12 | G4 Real mobile acceptance (failing on tested runs) | gate | - | LO (negative) | F6, F7 | needs #17, #18 |
| 13 | G5 Controlled production canary | gate | - | none | `HOMOLOGATION.md` | needs G1-G4 triage |
| 14 | G6 Premium live acceptance | gate | - | none live | `HOMOLOGATION.md` | needs #3, #5 |
| 15 | Re-execute builds and QA from committed source | build | - | none | `BUILD_AND_QA.md` | related #2, #16 |
| 16 | Build and promotion workflow | workflow | - | ST | `FRAGMENTATION_MAP.md` | related #2, #15 |
| 17 | RC7 output-health poll kills playback thread on LWJGL 3 | defect | high | LO (device, once) | F7 (H3) | blocks #12 |
| 18 | No audio capture backend opens a microphone on device | defect | high | LO (device, 10/10) | F6 | blocks #12 |

## Hypotheses carried by the issues (all unverified)

- **H1** (#3): LWJGL 2.9.1 `AL.destroy()` resets AL/ALC/EFX native stubs, so calls between destroy and the next `AL.create()` raise `UnsatisfiedLinkError`.
- **H2** (#4): the client could rebootstrap on transfer instead of waiting for the timeout.
- **H3** (#17): each health poll leaks LWJGL 3 `MemoryStack` space until exhaustion.

## Suggested triage order

#2 -> #16 -> #15 (identity and reproducibility), then #3, #17, #18, #4, #5, #7, #6, then #8, then the gates in numeric order, with G6 (#14) only after #3 and #5.

## How to maintain

When an issue changes state on GitHub, update this table and cite the evidence file in the same commit as any change to `PROJECT_STATE.md` or `HOMOLOGATION.md`. Do not close a gate issue without a dated folder under `docs/evidence/` holding the primitive observations.
