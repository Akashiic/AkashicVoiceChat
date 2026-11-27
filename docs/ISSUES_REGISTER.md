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

## Status update — 2026-11-27 (commit `bb2e6aa`, evidence [`evidence/rebuild-20261127/`](evidence/rebuild-20261127/README.md))

All issues stay **open**: the fixes below are AUTHORED + AUTOMATED only and still need the live confirmation named in each row.

| # | Change | Evidence | Still needed to close |
|---:|---|---|---|
| 3 | Binding loss handled as a recovery event in playback, EFX probe and mic capture | AUTOMATED: `OpenAlReloadReproSmoke` with real natives (baseline reproduces the field signature 30×; fixed tree recovers in ~1 s), `OpenAlBindingLossSmoke` 10/10 | Desktop client run with a sound reload (ArchaicFix device checker), then the F1 log scan showing zero AkashicVoice `UnsatisfiedLinkError` headers |
| 4 | Client requests a fresh bootstrap on a backend transfer (new player entity id) | AUTOMATED: `BackendTransferDetectorSmoke` (logic only) | Live transfer through the proxy in direct mode: no 6–11 s voice gap |
| 7 | Recycle pool clamped to the per-source maximum | BUILD + matrix PASS | Field scan without audio-thread OOM |
| 15 | Forge + platform rebuilt from committed source; Java 8 matrix re-run (57 mains) | BUILD + AUTOMATED; Paper bridge and Addon API byte-identical to RC7 | Same procedure on the Windows workstation; class-by-class comparison of the Forge JAR with RC7 |
| 17 | String-keyed ALC queries cached per device; health-poll failures can no longer kill playback | AUTOMATED: `OpenAlBindingLossSmoke` (`health_poll_failure_never_escapes_loop_guard`), real-native reproducer | Long session on an LWJGL 3 device runtime |
| 2 | Observation: the Gradle label reads `-dirty` whenever untracked files exist, even with a clean tracked tree | BUILD | Label design decision |

Not changed: #5 (needs a controlled physical hot-plug test), #6 (needs NecroTempus' `modern_fonts.json` schema before shipping a file), #8, #16, #18 and the gates #9–#14. PR #19 (client addon hooks) was merged without tests or docs; both were added and the hooks hardened (privacy gate) in `bb2e6aa`.

## Hypotheses carried by the issues (all unverified)

- **H1** (#3): LWJGL 2.9.1 `AL.destroy()` resets AL/ALC/EFX native stubs, so calls between destroy and the next `AL.create()` raise `UnsatisfiedLinkError`. *2026-11-27: reproduced with real LWJGL 2.9.4-nightly natives outside Minecraft (AUTOMATED); not yet observed against the field client's 2.9.1.*
- **H2** (#4): the client could rebootstrap on transfer instead of waiting for the timeout.
- **H3** (#17): each health poll leaks LWJGL 3 `MemoryStack` space until exhaustion.

## Suggested triage order

#2 -> #16 -> #15 (identity and reproducibility), then #3, #17, #18, #4, #5, #7, #6, then #8, then the gates in numeric order, with G6 (#14) only after #3 and #5.

## How to maintain

When an issue changes state on GitHub, update this table and cite the evidence file in the same commit as any change to `PROJECT_STATE.md` or `HOMOLOGATION.md`. Do not close a gate issue without a dated folder under `docs/evidence/` holding the primitive observations.
