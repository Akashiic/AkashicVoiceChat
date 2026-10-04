# Versioning and provenance

**Question this document answers:** "Which build is which, and what exactly am I running?"

Short answer: the version label **`0.1.0-alpha.5-akashic-voice.11+022dc1f922-dirty`** that appears in the client log and in `mcmod.info` identifies a *git state*, not a *build*. At least five different JARs carry it. The JAR installed on the engineering workstation is **RC7**, identified only by its SHA-256.

## 1. Anatomy of the label

The build appears to derive the mod version from the git state (`git describe`-style; inferred from the label format and `git describe` output `0.1.0-alpha.5-11-g022dc1f-dirty`, not from re-reading the build script):

| Part | Meaning |
|---|---|
| `0.1.0-alpha.5` | Nearest tag, commit `60fb120` ("Akashic Voice 0.1.0-alpha.5 portable capture backends") |
| `akashic-voice` | Branch name at build time |
| `.11` | Commits since that tag. Verified: `fa456ac`(+1) `943fddf` `6650fa5` `1cac62e` `e8dd94c` `5eb51e1`(+6) `882a861` `4f72027`(+8) `a590ae2`(+9) `df202d4`(+10) `022dc1f`(+11) |
| `+022dc1f922` | Abbreviated HEAD commit (`022dc1f922a107eb10f4f6b20d3b550194e8b7ae`, "Add federated group authority and managed SVC routing", 2026-09-12) |
| `-dirty` | The working tree had uncommitted changes |

So the label says "HEAD was 022dc1f and something was uncommitted". It does not say *what* was uncommitted. In this repository the uncommitted state of the original checkout is preserved as commit `ee8920f`.

## 2. Builds that share the label

| Build | Where it came from | Forge JAR SHA-256 |
|---|---|---|
| Active-tree dev JAR | `build/libs/…11+022dc1f922-dirty.jar` of the original checkout (built from the dirty tree, 2026-09-13) | `5794d4536020ca189b4e2e1d1af7babf69bf429803386715b17af4bab39430e8` |
| RC1–RC3 | `release-candidate-20260915-rc1`, `…-rc2`, `20260916-rc3` | `e38cfe33d7e722a885425448488ede80807d16a0ea5938631a80f9ddadb47494` |
| RC4 | `release-candidate-20260916-rc4` (legacy Agent mode) | `8e5a6fe5de6a2421ef37422a14204ce48fc43382fb8de944ec5b7462c118215e` |
| RC5 | `release-candidate-20260917-rc5` (Agent observability) | `6fab0865c2d2c5a0e382e193f35e099aa7e0861ab4317b0c78a91796ef8d553e` |
| RC6 | `release-candidate-20260917-rc6` (premium completion) | `aaa7294d6266cabba61fff1c4e3659998944efabdac5157b3f850b4201a36a07` |
| **RC7** | `release-candidate-20260917-rc7` (premium hardening) — **installed** | `c0f9d86cb331598c2e0e7c9d7ebf6091fffee18bc94bbdaf6bbf9cbe6d2eb081` |

Older, differently labelled artifacts: homologation bundle `5eb51e1` (2026-09-12): client/server `d9d1d698…`, Gateway `3b9ab992…`, Paper bridge `65999622…` (full hashes in `docs/homologation/5eb51e1/SHA256SUMS.txt`).

Server-side JARs (identical Gateway/Paper/Addon API from RC2 on, except RC1's Gateway):

| JAR | RC1 | RC2 → RC7 |
|---|---|---|
| Gateway | `2218962a1b7955ba87ec77c22941b92d7b44c9072368fb4fcf191e49c1ac6254` (251,959 B) | `e4329bed4f45db66ef80de66590dc88cc51c35c30e241f4704de2dbe0a2b4ec2` (252,653 B) |
| Paper/SVC | `b5e66814edfd8bfb1b197d786ddb9da727a75e9940dad7ca44da9b92b2c21bdb` | same |
| Addon API | `6e73a1f96d96251cc631f6a51e4a1e485022257f1cf0a517dc7781e8ff5de347` | same |

Full manifests, seal receipts and independent-verification receipts for every RC are archived in `docs/evidence/release-candidates/`.

## 3. Why the repository was behind the installed JAR

Engineering after 2026-09-13 happened in copies of the tree under `audit/group-integration-20260913-04/integrated-clientNN` and `reviewed-clientNN`, each qualified and sealed separately. The handoffs repeat that the **active checkout was never promoted**: "Active source … and production distribution remain unchanged". The `audit/` directory is git-ignored, so none of that work was ever committed.

Consequence: until this repository was assembled, `git log` described a build roughly four weeks of engineering behind the JAR in use. The mapping now is:

| Commit | Content |
|---|---|
| `a4fa791` and earlier | ProxyChat upstream (Julius Meinking, MIT) — 20 commits |
| `77e9094` … `022dc1f` | 16 Akashic Voice commits, alpha.1 → Gate 6 federated groups |
| `ee8920f` | Verbatim snapshot of the dirty active tree (34 paths) |
| `26023d4` | Import of `integrated-client58-premium-hardening-rc7` (RC7 source, 394 files verified byte-identical, CRLF-normalised) |
| later | Documentation and evidence archive |

Existing tags `0.1.0-alpha.1`…`0.1.0-alpha.5` are preserved (annotated tags pointing at `77e9094`, `812c24d`, `378f622`, `7cdfa6d`, `60fb120`). ProxyChat's own tags (`0.1.0`–`0.3.1`) are intentionally not carried over to avoid confusion with this project's versions. They map to upstream commits `18b3a42`, `133395d`, `a71b625`, `5d55c7d`, `ed178c5`, `2701792`, `cd9464c`.

## 4. Open provenance questions

- The candidate trees contain no `.git` directory, yet the JARs built from them carry the label of the original checkout. The handoffs I read do not record how the label was injected. Tracked in issue #2.
- The RC7 JAR was built from the candidate tree, not from commit `26023d4`. A rebuild from this repository will produce a different label (branch and commit count differ) and likely different bytes (timestamps, label). A byte-identical rebuild has **not** been demonstrated.
- RC5 was the JAR observed running in CLIENT59/CLIENT62 (2026-09-17). It was replaced by RC7 later the same day (installed file timestamp 2026-09-17 23:48). All 39 sessions with a preInit line since 2026-09-23 therefore ran RC7 — an inference from the file timestamp, since the log carries only the shared label.

## 5. How to identify a build (do this before reporting a bug or a test result)

1. Compute the JAR hash: `Get-FileHash <jar> -Algorithm SHA256`, and compare with the tables above.
2. Read `mcmod.info` inside the JAR for the label (it will not discriminate between RC builds).
3. In the client log, `Akashic Voice preInit (version …, serverMode=…, voicePort=…)` gives label and mode only.
4. Record: hash, label, mode (`direct`/gateway), JVM, LWJGL, other audio mods (ArchaicFix, Angelica, lwjgl3ify).

## 6. Recommendation

Adopt a build identity that cannot collide:

- version `0.1.0-rc.N+<gitsha>` (or `0.1.0-alpha.N+<gitsha>`), failing the build when the tree is dirty unless explicitly allowed;
- embed `gitsha`, tree hash and build timestamp in `mcmod.info` and the JAR manifest, and print them in the `preInit` log line and `/avdebug`;
- build releases only from a committed tree on `main`, record the JAR SHA-256 in the release notes;
- keep `audit/` evidence out of the source of truth: promote candidates by commit.

Tracked in issue #2 (identity) and #16 (workflow).
