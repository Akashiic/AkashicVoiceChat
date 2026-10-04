# Fragmentation map

**Question this answers:** the engineering record of Akashic Voice was spread over a git checkout, an `audit/` forest of candidate trees, a `dist/` folder, an operator tooling pack and an installed JAR. Where did each fragment go, and what is *only* in the original folder?

**Scan:** 2026-10-04, read-only, of `Desktop\Codex\Akashic Voice` (the "original"). At the end of the scan the original was still at `HEAD 022dc1f`, branch `akashic-voice`, 34 uncommitted status entries, 0 stashes — i.e. unchanged by this assembly. The model for this map is the `FRAGMENTATION-MAP.md` of the sibling project `Akashic-Player-Backport-GitHub`.

## 1. Inventory of the original

| Area | Size at scan | Role | Disposition |
|---|---|---|---|
| `src/`, `shared/`, `platform/`, `tools/qa`, `gradle*`, build files | 155 + 15 + 339 + 26 files, ≈ 3 MB | Source of truth for the product | **In git.** Committed history up to `022dc1f`; the uncommitted state on top of it is commit `ee8920f` |
| Uncommitted working tree (34 entries) | — | The active, never-committed work (client/platform changes, new docs) | **Snapshot commit `ee8920f`** (message states it is a snapshot, not a reviewed change) |
| `docs/` (15 files) | 0.1 MB | Engineering contracts, progress reviews, `CONTINUITY.md` | **In git** (and indexed in `docs/README.md`) |
| `dist/` (`homologation-5eb51e1`) | 3.1 MB, 9 files | Earlier homologation plan and templates | **Imported** to `docs/homologation/5eb51e1/` |
| `build/` | 77.5 MB, 7,396 files | Gradle outputs, dev JARs (`build/libs/`) | **Not imported** (build output; hashes recorded in `docs/VERSIONING_AND_PROVENANCE.md`) |
| `.gradle/`, `run/`, `logs/` | 19.6 + 24.0 MB, small logs | Caches, dev client run directory | **Not imported.** `run/` and `logs/` are git-ignored by the project |
| `audit/group-integration-20260913-04/homologation-rc7-v3.8-20260917/` (46 operator files) | small | RC7 / V3.8 operator tooling (PowerShell, templates) | **Imported** to `tools/homologation/rc7-v3.8/` |
| `audit/` | **5.15 GB, 348,381 files** | The candidate forest and QA evidence (git-ignored in the original) | See §2 |

## 2. `audit/` — what it holds and what was taken

`audit/` is git-ignored in the original and is **not** in this repository. It contains 54 top-level folders; two dominate:

| Folder | Size | What it is |
|---|---|---|
| `group-integration-20260913-04/` | 5.01 GB, 341,969 files, 173 directories + 656 loose files | The working forest: 39 `integrated-client*` candidate trees (one per CLIENT checkpoint, incl. `integrated-client58-premium-hardening-rc7`), 6 `reviewed-client*` trees, 7 sealed `release-candidate-*` folders (RC1–RC7, 3.4 MB each, JARs inside), `runs-*` QA output, `homologation-rc*` bundles, `bundle-smoke-*`, the handoff and receipt files |
| `crate-license-inspect/` | 106 MB, 2,923 files | Licence inspection material for the bundled native Opus crate (judged from the folder name and the licence notices shipped in the JAR; contents not reviewed) |

The remaining folders are small, named by purpose: `javac8-*` / `jdk8-compile*` (Java 8 compile matrices per alpha), `alpha4-*` / `alpha5-*` / `gate6-*` QA runs, `qa-runs` (463 files), `continuation-20260913-groups`, `gateway-control-20260913-01`, `process-lab-client40`, `thirdparty`, `license-upstream`, and Python caches.

**Taken into this repository (redacted):**

- 52 handoff documents → `docs/evidence/handoffs/`
- 143 JSON receipts → `docs/evidence/receipts/`
- 21 RC manifest/verification files (no JARs) → `docs/evidence/release-candidates/`
- The RC7 candidate **source** → commit `26023d4` ("Import RC7 candidate source")
- Index: [`evidence/INDEX.md`](evidence/INDEX.md)

**Deliberately left behind** (still only in the original folder):

- All JARs and ZIPs, including the sealed RC1–RC7 artifacts (recoverable by SHA-256 from the original; see provenance tables).
- The other 38 `integrated-client*` and all `reviewed-client*` candidate trees. Per the engineering record they were **never promoted**: they are intermediate states between checkpoints, not releases. Their effect is summarised by the CLIENT checkpoint table in [`HISTORY.md`](HISTORY.md) and `CONTINUITY.md`.
- `runs-*`, `qa-runs`, javac matrices and other raw QA output (bulk, regenerable, may contain machine paths).
- `crate-license-inspect` (third-party material); the resulting licence notices ship inside the built JAR (`META-INF/licenses/`).

## 3. Outside the original folder

| Fragment | Where | Handling |
|---|---|---|
| Installed RC7 JAR | Technic *Forbidden Series* `mods/` (hash matches) and three AkashicCinematicMenu QA instances | Referenced by hash; not copied |
| Desktop client logs (154) and Android QA logs (59) | Technic install; Forbidden Mobile Launcher `qa/device` | Analysed in [`FIELD_EVIDENCE_2026-10.md`](FIELD_EVIDENCE_2026-10.md); raw logs not imported (player and host data) |
| Sibling projects | `Desktop\Codex\*` | Listed in [`RELATED_PROJECTS.md`](RELATED_PROJECTS.md); not imported |

## 4. Rules for the original folder

1. **Do not delete `audit/`** until a decision is recorded here: it is the only copy of the sealed JARs and of the intermediate candidate trees.
2. Treat this repository as the canonical working copy from 2026-10-04. Do not develop in two places; if the original is touched, record it as a new fragment.
3. If the original's 34 uncommitted entries change, they are no longer represented by `ee8920f`; take a new dated snapshot rather than rewriting history.
4. Candidate trees carry no `.git`; never rely on them for provenance (see issue #2).

## 5. Open questions

- Whether the unpromoted `integrated-client*` trees are worth keeping once the sealed JARs are archived elsewhere (owner decision; they account for most of the 5 GB).
- Where to archive the sealed RC JARs long-term (a private GitHub Release asset is possible, but a release or tag must not imply approval — see `AGENTS.md`).
