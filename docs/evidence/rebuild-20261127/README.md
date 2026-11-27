# Rebuild and QA receipt — 2026-11-27

**Grade:** BUILD + AUTOMATED. Nothing here is LIVE-OBSERVED or LIVE-APPROVED. Executed by one assistant in a
Linux cloud container; this is not an independent external review.

## Source identity

| Item | Value |
|---|---|
| Code commit built | `bb2e6aa` (branch `claude/friendly-goodall-vpdlyp`, parent `9743fba` = `main` after PR #19) |
| Tracked tree at build time | clean (`git status --porcelain --untracked-files=no` empty); this evidence folder was untracked, which is why the Gradle label still says `-dirty` (see issue #2) |
| Baseline for the reproducer comparison | `9743fba` (pre-fix), compiled with JDK 8 `javac` from a detached worktree |

## Toolchain

| Piece | Version |
|---|---|
| OS | Linux (Ubuntu 24.04 container) |
| Gradle | 9.3.1 wrapper, launcher JVM OpenJDK 21.0.13.1 |
| Toolchains | OpenJDK 25.0.5.1 (Ubuntu), OpenJDK 8u514 (Ubuntu), Azul Zulu 8 (auto-provisioned by Gradle: the build requires vendor Azul for `compileApiJava`) |
| JDK 8 for the QA matrix | `javac 1.8.0_514` (a real JDK 8, as the runner requires) |
| OpenAL for real-native smokes | LWJGL 2 natives from `run/natives/lwjgl2` (extracted by the Forge build), `ALSOFT_DRIVERS=null` (no audio hardware) |

Container-only adjustments (not in the repository): Maven Central reached through Google's mirror via a
session-local Gradle init script (the default host returned HTTP 429); the Ubuntu JDK 8 accessibility bridge
(`assistive_technologies`) disabled, because `org.lwjgl.Sys` loads the AWT toolkit and the missing ATK
wrapper aborted it.

## Commands and results

| Step | Command (abridged) | Result |
|---|---|---|
| Forge build | `./gradlew --no-configuration-cache clean build` | PASS — BUILD SUCCESSFUL, 35 actionable tasks (includes Spotless check) |
| Platform build + tests | `./gradlew -p platform --no-configuration-cache clean build` | PASS — 43 suites / 186 tests / 0 failures / 0 errors / 0 skipped (same counts as the RC7 receipt) |
| Java 8 smoke matrix | `JAVA8_HOME=… ALSOFT_DRIVERS=null LD_LIBRARY_PATH=run/natives/lwjgl2 python3 tools/qa/run_mic_recovery_qa.py fix-20261127-f --all-smokes` | PASS — 189 sources compiled, 57 test mains, exit 0 ([`java8-matrix-status.json`](java8-matrix-status.json), [`java8-matrix-summary.txt`](java8-matrix-summary.txt)) |
| JAR inspection | `python3 tools/qa/inspect_jar.py <forge jar>`; `python3 tools/qa/DistributionJarSmoke.py <forge jar>` | PASS — all 326 classes major 52 (321 Akashic), Concentus + licence present, 5 JNI classes, 7 natives, 5 notices |

Platform first run (before the fix in `bb2e6aa`): `GatewaySessionRestartSoakTest` failed 3/6, then 2/6
repetitions with `BindException: Address already in use` — first from a test-side ephemeral-port collision,
then on the in-JVM Gateway restart itself (`GatewayUdpServer.close()` returned before the NIO socket released
the port). Both fixed in `bb2e6aa`; the 0-failure result above is after the fix.

## Artifacts (not committed; hashes only)

| Artifact | Bytes | SHA-256 | vs RC7 |
|---|---:|---|---|
| `akashicvoice-bb2e6aa-…-dirty.jar` (Forge) | 3,221,398 | `e72f5b172c5ea0b925c6a8257e71213a795feaa42627a258d08d4cebbb7f5eb9` | differs (expected: code changed) |
| `gateway-velocity-0.1.0-SNAPSHOT.jar` | 252,846 | `a40601d9528f91c3daafec68773f0703c1c58940e8d5c24dd283533250d72acc` | differs (expected: `GatewayUdpServer` changed) |
| `svc-paper-0.1.0-SNAPSHOT.jar` | 128,657 | `b5e66814edfd8bfb1b197d786ddb9da727a75e9940dad7ca44da9b92b2c21bdb` | **byte-identical to RC7** |
| `addon-api-0.1.0-SNAPSHOT.jar` | 7,581 | `6e73a1f96d96251cc631f6a51e4a1e485022257f1cf0a517dc7781e8ff5de347` | **byte-identical to RC7** |
| `agent-protocol-0.1.0-SNAPSHOT.jar` | — | `a2ab65cf0481387b7dd816286a2ad7f7596228bc1eb0dd147d2c89218402b402` | not in the RC7 table |
| `bridge-core-0.1.0-SNAPSHOT.jar` | — | `5247cf9c8a6b212c11e169e5dd10f2a8d690283def67341f6f6345c4be9f13f2` | not in the RC7 table |
| `client-protocol-0.1.0-SNAPSHOT.jar` | — | `c2fd040b67ff7289dcfa364bf6aa553de7465e10a259593d1758009b2345d0e7` | not in the RC7 table |

The two byte-identical modules show that the committed platform source reproduces the RC7 artifacts for code
that did not change (issue #15). The Forge JAR was not compared class-by-class with RC7 here because the RC7
JAR is not in this container.

## Issue #3 reproducer with real OpenAL natives

`tools/qa/OpenAlReloadReproSmoke.java` plays Minecraft's part: it creates the OpenAL context, lets Akashic Voice
share it (`ownsOpenAL=false`, as in every field session), then calls `AL.destroy()`, keeps submitting voice
frames for 600 ms, and calls `AL.create()`.

| Tree | Outcome | Log |
|---|---|---|
| Baseline `9743fba` | **FAIL** — 30× `Audio frame failed` with `UnsatisfiedLinkError: org.lwjgl.openal.AL11.nalSource3i(IIIII)V` (the field signature of F1), and no recovery within 15 s | [`openal-reload-repro-BASELINE-head60.log`](openal-reload-repro-BASELINE-head60.log) |
| Fixed `bb2e6aa` | **PASS** — one `Voice output lost its OpenAL native binding` warning, `Shared OpenAL output recovered after Minecraft sound reload` within about 1 s, EFX re-enabled, 0 frames dropped after recovery | [`openal-reload-repro-FIXED.log`](openal-reload-repro-FIXED.log) |

This supports hypothesis H1 (LWJGL 2 `AL.destroy()` unlinks the native stubs; calls made before the next
`AL.create()` raise `UnsatisfiedLinkError`) with LWJGL 2.9.4-nightly natives. It is still not a Minecraft run:
ArchaicFix's device checker, paulscode's SoundSystem and the real 2.9.1 natives of the field client were not
exercised. Gate G6 remains the place to confirm it live.

## What was not run

- No Minecraft client or server; no live voice; no Android device; no Windows workstation.
- The V3.8 homologation scripts (`tools/homologation/rc7-v3.8/*.ps1`) — PowerShell, live-environment only.
- Source/behavioural mutation gates and the seal preflight from the RC7 receipts (their tooling is not in this repository).
- Microphone capture on real OpenAL (the null driver has no capture device); the capture-side fix is covered only by `OpenAlBindingLossSmoke` (unlinked natives, fake device).
