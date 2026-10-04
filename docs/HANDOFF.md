# Handoff

**Written:** 2026-10-04, while consolidating the project into this repository.
**Audience:** the next engineer or assistant continuing Akashic Voice (and Gabriel, the owner, `Akashiic` on GitHub).
**Read first:** [`PROJECT_STATE.md`](PROJECT_STATE.md). This file adds the working rules, the environment and a concrete next-action list.

## 1. One-paragraph situation

Akashic Voice is a spatial Opus voice chat for Minecraft 1.7.10 / Forge. The engineering record ends at **RC7** (2026-09-17): every automated gate passes, nothing is live-approved, and there is no stable release. Between 2026-09-23 and 2026-10-03 the RC7 JAR ran in a real Technic client (desktop) and in Android launcher QA instances; the logs show defects the automated gates could not see (OpenAL natives unlinked after a sound reload; voice timeouts around transfers; on Android, no microphone backend and a playback thread killed by the output-health poll). The next work is therefore **triage of those field defects, then the live gates**, not new features.

## 2. Non-negotiable rules (inherited from `AGENTS.md`)

1. No release tag, production deployment or distribution replacement without the evidence the gate requires. This repository has **no** release tags beyond the preserved `0.1.0-alpha.1…5`.
2. Distinguish AUTHORED / STATIC / BUILD / AUTOMATED / LIVE-OBSERVED / LIVE-APPROVED. A synthetic result never substitutes for a live observation.
3. Never put credentials in logs, docs, checkpoints, commits or issues: session secrets, agent PSKs, production host/port, player identifiers. The evidence archive is redacted; keep it that way (see [`evidence/INDEX.md`](evidence/INDEX.md)).
4. Preserve other workers' local changes. Never `reset`, `clean` or `stash` something you did not create.
5. Review passes by one assistant are not independent external review. Do not describe them as such.
6. The newest verified source/runtime evidence outranks an older summary.
7. Keep Java 8 compatibility in `src/` and `shared/client-protocol/` (class major 52); modern dependencies live only under `platform/`.
8. Candidate-copy rule: never trust `.gradle` or configuration-cache state copied between trees; final Gradle evidence from a copied tree uses `--no-configuration-cache`, and a green build is insufficient unless the packaged JAR proves class origin from the current tree.

## 3. What was done in this consolidation (and what was not)

Done:

- Rebuilt the git history: 20 ProxyChat upstream commits, 16 Akashic Voice commits, one snapshot commit (`ee8920f`) that preserves the previously uncommitted active tree, one RC7 source import (`26023d4`), then documentation, evidence and tooling.
- Imported 216 redacted evidence files, the V3.8 operator tooling and the 5eb51e1 homologation plan.
- Analysed 154 desktop and 59 Android client logs ([`FIELD_EVIDENCE_2026-10.md`](FIELD_EVIDENCE_2026-10.md)).
- Reconstructed build identity ([`VERSIONING_AND_PROVENANCE.md`](VERSIONING_AND_PROVENANCE.md)).
- Opened the issue set in [`ISSUES_REGISTER.md`](ISSUES_REGISTER.md).

Not done:

- **No build or test was re-executed.** All "AUTOMATED / BUILD" statements are transcribed from earlier receipts. See [`BUILD_AND_QA.md`](BUILD_AND_QA.md).
- No JAR is stored in git; hashes are recorded instead.
- The original local working folder was **not** modified. It remains the only place where the large `audit/` candidate trees (`integrated-clientNN`, `reviewed-clientNN`) and `dist/` live; see [`FRAGMENTATION_MAP.md`](FRAGMENTATION_MAP.md).
- Audio content in logs or captures was never read or analysed.

## 4. Where things are

| Need | Location |
|---|---|
| Current truth | `docs/PROJECT_STATE.md` |
| Which JAR is which | `docs/VERSIONING_AND_PROVENANCE.md` |
| Acceptance procedure and gate table | `docs/HOMOLOGATION.md`, `docs/homologation/5eb51e1/`, `tools/homologation/rc7-v3.8/` |
| Field defects | `docs/FIELD_EVIDENCE_2026-10.md` |
| Per-checkpoint history | `docs/CONTINUITY.md` (CLIENT checkpoints), `docs/HISTORY.md` (digest) |
| Handoffs and receipts | `docs/evidence/handoffs/`, `docs/evidence/receipts/`, `docs/evidence/release-candidates/` |
| Architecture | `docs/ARCHITECTURE.md`, `platform/README.md` |
| Siblings on the workstation | `docs/RELATED_PROJECTS.md` |
| Portuguese summary | `docs/PT-BR_RESUMO.md` |

## 5. Environment notes (engineering workstation, 2026-10-04)

- Windows 10; build tooling needs JDK 21 (Temurin 21.0.11 and 25.0.3 found under `~/.jdks`); the QA scripts expect a real JDK 8 `javac 1.8.x` (found at `C:\Program Files\Java\jdk-1.8`; earlier documents cite 8u411 — re-check the exact build before citing it).
- Gradle 9.3.1 wrapper at the root; the platform build is `-p platform`.
- QA runners: `tools/qa/run_platform_qa.py`, `tools/qa/run_mic_recovery_qa.py`.
- The field client is the Technic *Forbidden Series* install; the Forge JAR in its `mods/` folder matches the RC7 hash.
- Android runs come from the Forbidden Mobile Launcher engineering QA instances (see `RELATED_PROJECTS.md`).

## 6. Recommended next actions, in order

1. **Settle build identity (#2, #16, #15).** Rebuild from a committed tree on `main`, record JAR SHA-256 and the reproducibility result; make the label unique per build. Everything else is hard to attribute without this.
2. **Triage the defects the gates missed (#3, #4, #5, #7, #17, #18).** For #3 test hypothesis H1 (LWJGL 2.9.1 `AL.destroy()` leaves native stubs unlinked; the voice mod then calls into a dead binding) with a reproducer that reloads the sound system in a dev client. For #4 test H2 (client could re-bootstrap on transfer instead of waiting for a timeout). For #17 test H3 (an LWJGL 3 `MemoryStack` leak in the output-health poll) with a short soak on an LWJGL 3 runtime. For #18 find out why no capture backend opens on device (permission, backend selection, or Pojav/Amethyst audio routing).
3. **Re-verify carried-over items (#8):** group-control message-size budget, invitation limits/expiry, persistent group authority, permission/moderation parity, native OpenAL lifecycle.
4. **Only then** run the live gates G1–G6 (#9–#14) using the V3.8 procedure. Running G6 (premium listening) before #3/#5 are triaged would test a path known to misbehave.
5. After any gate changes state, update `PROJECT_STATE.md`, `HOMOLOGATION.md` and `ISSUES_REGISTER.md` in the same commit, citing the evidence file.

## 7. Pitfalls already paid for

- A waiting REPL or a quiet thread dump is not a hang and not evidence of health.
- Copying a candidate tree carries stale Gradle configuration with absolute paths.
- The version label looks like a build identifier and is not; do not quote it as one.
- The Gateway and Paper bridge have no live deployment evidence; the field client runs **direct mode** (`serverMode=direct`, UDP 24454). Do not generalise field findings to the Gateway or vice versa.
- Offline-mode login is a weaker identity root; voice security cannot repair that.
- JARs and QA captures were deliberately left out of git (size, provenance). If a gate needs them, restore from the original working folder and record hashes.

## 8. How to update this file

Add a dated section at the top of §6 when priorities change, and move completed items to [`HISTORY.md`](HISTORY.md). Do not rewrite earlier handoffs under `docs/evidence/handoffs/`; they are historical records.
