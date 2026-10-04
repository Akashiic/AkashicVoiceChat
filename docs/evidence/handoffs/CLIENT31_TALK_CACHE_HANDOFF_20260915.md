# CLIENT31 — TalkCache bounded lifetime hardening

Checkpoint: `AV-TALK-CACHE-BOUNDS-20260915-31`  
Baseline: `integrated-client30`  
Candidate: `integrated-client31`  
Automated product-progress estimate: **~86%**. This is a +1 point hardening credit, not a live-release credit.

## Finding

`TalkCache` retained every UUID ever passed to `markSpeaking` in a static `ConcurrentHashMap`. The speaking window was only 250 ms, but expired entries were not removed and `clear()` had no production call site. A long-running client could therefore retain historical speaker UUIDs for the lifetime of the classloader.

A pre-patch Java 8 reproduction using the original CLIENT30 production class failed with `AssertionError: stale lookup retained useless entry`.

## Production change

Exactly one production file differs from CLIENT30:

`src/main/java/dev/akashic/voice/voice/client/talk/TalkCache.java`

Final SHA-256: `88cf831d17f1f95302d929b72e956205190563ac232f584222355c4a75a968f9`.

The cache now uses monotonic `System.nanoTime()`, evicts entries when a stale speaker is queried, performs CAS-rate-limited periodic stale pruning, performs a forced stale prune when capacity is pressured, and applies a hard `MAX_ENTRIES = 512` admission bound. Updates for speakers already present remain outside the admission lock. This changes only talk-indicator bookkeeping; it does not reject or alter voice transport/audio frames.

## Regression contract

`TalkCacheBoundedSmoke` covers 8 scenarios: fresh visibility, stale eviction, repeated-speaker stability, 10,000-UUID flood bound, recovery after stale capacity, 8-thread concurrent flood, periodic compaction, and explicit clear.

## Final gates

- Actual JDK 8 matrix: PASS; `161` sources compiled; `44` executions.
- Full legacy ForgeGradle build: PASS; 27/27 actionable tasks.
- Full platform build: PASS; 31/31 actionable tasks; 149 JUnit tests in 36 suites, 0 failures/errors/skips.
- Packaged-class rerun: PASS; 44 executions; production `TalkCache` loaded from the DEV JAR.
- DEV JAR SHA-256: `0b7e6f69510d615ba347c0372e03b8f897e9fc3bfbd495d3fcd23f160b14aab5`.
- Mutation gate: PASS; 5/5 deliberate defects detected.
- Static audit: exactly 1 production delta and 1 QA delta.
- All final runners report `active_sources_modified=false`; the active project tree was not promoted or rewritten.

## Audit history

Two intermediate legacy-build attempts failed only the Spotless gate: first because of one formatter wrapping difference, then because a QA helper converted the candidate file to CRLF. The file was normalized to project LF formatting and the complete build was rerun successfully. These failures are retained in the run directories rather than hidden.

## Not proven by CLIENT31

No live Minecraft deployment was performed. Native microphone/speaker listening, two real clients through Velocity, Paper/SVC live-listener behavior, mobile Pojav/Amethyst, and long live operational soak remain release gates.
