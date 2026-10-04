# Akashic Voice - engineering entry point

Scope: this repository only. Do not modify Immersive Petroleum or other projects.
Read README.md, docs/AUDIT.md, docs/ROADMAP.md and docs/CONTINUITY.md before work.
The newest verified source/runtime evidence outranks an older summary or artifact.

## Runtime and product contracts
- Legacy client/common: Minecraft 1.7.10, Forge 10.13.4.1614, Java 8 source/bytecode.
- Crucible target: Java 21; Paper bridge: Java 21; current Velocity gateway: Java 25.
- UniMixins, GTNHLib, Hodgepodge and Angelica are compatibility targets, not mandatory dependencies.
- Preserve native Forbidden voice and bidirectional Akashic/SVC interoperability.
- Never model unrelated backend coordinates as physical proximity.
- Keep session credentials out of logs, documentation, checkpoints and test reports.

## Evidence and release discipline
- Preserve pre-existing local modifications; never reset/clean/stash another worker's changes.
- Distinguish authored, statically reviewed, build-tested, integration-tested and live-approved.
- Inspect each patch separately from its implementation; log findings and explicit approval gates.
- Review passes by one assistant are not independent external reviewers.
- Add regression tests for repairs; historical passing reports do not approve a changed tree.
- No production deployment, release tag or distribution replacement without required evidence.
- Report uncertainty and blocked validation; do not claim zero defects or guaranteed performance.

## Remote continuity
- Use device status + a small known-file read as health probes; inspect sessions by ownership.
- A waiting REPL is not evidence of a hang. Never kill another chat's process.
- Keep reads bounded and writes small. Record confirmed writes before the next operation.
- After transport failure, stop that strategy, revalidate state and resume smaller; never replay writes blindly.
- Do not bypass security/tool denials or change global MCP settings to obtain access.

## Candidate-copy / Gradle cache rule
- Never trust `.gradle` or configuration-cache state copied from another audit candidate: Gradle may retain absolute source paths.
- Prefer excluding `.gradle` from candidate snapshots. For copied candidates, final Gradle evidence MUST use `--no-configuration-cache`.
- A green Gradle result is insufficient if the packaged JAR does not prove class origin from the current candidate.
- Keep a packaged-origin/runtime replay gate for protocol changes; stale artifacts must invalidate cached build evidence.
