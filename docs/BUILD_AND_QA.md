# Build and QA

> **Verification note (2026-10-04).** The commands and results below are taken from the project's build files, `AGENTS.md` and the RC7 receipts under `docs/evidence/`. **None of them was re-executed while this repository was assembled.** Treat every "PASS" here as a *recorded* result about the RC7 candidate tree, not about this checkout. Re-running them is task #15 in [`ISSUES_REGISTER.md`](ISSUES_REGISTER.md).

## 1. Toolchain

| Piece | Requirement | Seen on the engineering workstation |
|---|---|---|
| Gradle | Wrapper, Gradle 9.3.1 (`gradle/wrapper/gradle-wrapper.properties`) | wrapper at repo root |
| JDK for Gradle | 21 | Temurin 21.0.11, Temurin 25.0.3 (`~/.jdks`) |
| JDK for Java 8 matrix | a real JDK 8 `javac 1.8.x` (QA fails otherwise) | `C:\Program Files\Java\jdk-1.8`; earlier documents cite 8u411 — confirm before quoting |
| Python | for `tools/qa/*.py` | not recorded |
| Minecraft / Forge | 1.7.10 / 10.13.4.1614 | Technic *Forbidden Series* client |

The mod produces **Java 8 bytecode (class major 52)**. The Gateway needs Java 25 (Velocity 4.0.0), the Paper bridge and Addon API Java 21.

## 2. Build commands (documented, not re-run)

```text
./gradlew clean build                        # Forge mod  -> build/libs/
./gradlew -p platform --console=plain build  # Gateway, Paper bridge, bridge-core, agent-protocol, addon-api
```

- `verifyNativeOpusBundle` (root `build.gradle.kts`) validates the SHA-256 of the bundled native Opus artifact (`e4d67fb9…1c30`) before `shadowJar` may package it.
- Copied trees: use `--no-configuration-cache` and prove class origin from the packaged JAR (see `AGENTS.md`, candidate-copy rule).
- Expected output name of the Forge JAR: `akashicvoice-forge-1.7.10.jar`. The label embedded in `mcmod.info` will **differ** from the RC7 label when built here (branch and commit count differ) — see [`VERSIONING_AND_PROVENANCE.md`](VERSIONING_AND_PROVENANCE.md).

## 3. QA assets in this repository

`tools/qa/` holds 61 files — Java 8 smoke, fuzz, soak and probe programs plus Python runners:

| Runner / helper | Purpose |
|---|---|
| `run_platform_qa.py` | Platform regression matrix (suites/tests across `platform/`) |
| `run_mic_recovery_qa.py` | Capture-restart and sequence-recovery scenarios (MIC-RECOVERY-01) |
| `inspect_jar.py`, `DistributionJarSmoke.py` | JAR content, class-major, native and JNI inspection |
| `source_fingerprint.py` | Source-tree fingerprint used by origin/replay gates |
| `*Smoke.java` | Handshake, crypto, replay, epoch, MTU, jitter, decoder capacity, speaker panel, EFX, output health, legacy Agent, touch PTT, wire fuzz, and more |
| `LossJitterDecoderSoak`, `CodecBackendPerformanceProbe`, `PerformanceProbe` | Soak and performance probes |

Gap that matters: `OpenAlOutputHealthSmoke` and the EFX smokes run against fake or loopback audio. They do **not** exercise real LWJGL 2.9.1 native-stub behavior after `AL.destroy()` (issue #3) nor an LWJGL 3 `MemoryStack` (issue #17). New regression tests for those defects must close that gap.

## 4. Recorded RC7 results (source: `docs/evidence/handoffs/RC7_ENGINEERING_STATUS_20260917.md`, `receipts/CLIENT58_RC7_FINAL_RECEIPT.json`)

| Gate | Recorded result |
|---|---|
| Formatting (Spotless) | PASS, no drift |
| Forge full build | PASS, 34/34 actionable tasks |
| Java 8 full matrix (real JDK 8) | PASS, 184 sources, 54 commands |
| Platform regression | PASS, 43 suites / 186 tests / 0 failures / 0 errors / 0 skipped |
| Source-contract mutations | PASS, 5/5 detected |
| Compiled behavioral mutations | PASS, 4/4 detected |
| JAR-origin replay | PASS, 9 production classes from the final JAR, 7 QA replays |
| Seal preflight | PASS, 43/43 |
| Independent sealed verification | PASS, 58/58 (a script run by the same engineering process, not an external reviewer) |
| Production implementation-gap markers | 0 |
| Real output probe | default output detected; OpenAL EFX supported; physical disconnect/reconnect **not** proven |

## 5. How to re-verify in this repository (suggested procedure)

1. Use a clean clone of `main` (no `.gradle` copied from elsewhere).
2. Record: `git rev-parse HEAD`, `git status --porcelain`, `java -version` for each JDK, `./gradlew --version`.
3. Run the two Gradle builds above with `--no-configuration-cache`; save the console log under `docs/evidence/rebuild-YYYYMMDD/` with the exit status.
4. Run `tools/qa/run_platform_qa.py` and `run_mic_recovery_qa.py`; save output and exit status.
5. Hash the produced JARs and compare with RC7: identical bytes are **not expected**; compare class lists and `inspect_jar.py` output instead, and record the result in issue #15.
6. Update `PROJECT_STATE.md` only for what was actually observed.

## 6. What this document does not claim

That the repository builds as-is today; that the recorded gate results hold for any tree other than the RC7 candidate; or that a green build implies any live gate. Live gates are tracked in [`HOMOLOGATION.md`](HOMOLOGATION.md).
