# Akashic Voice - CLIENT36 backend-transfer qualification

Checkpoint: `AV-BACKEND-TRANSFER-QUALIFICATION-20260915-36`.
Reviewed tree: `reviewed-client36-20260915`.
CLIENT36 changes no production source relative to CLIENT35; only `GatewaySessionRestartSoakTest` changed.

The test now executes six proxy/runtime restart cycles plus six lobby -> forbidden backend-transfer cycles.
Each transfer requires the old bootstrap/UDP generation to be invalidated before a fresh handshake.
Old authenticated traffic is rejected, the new handshake succeeds, and agent session state returns to zero.

A first 12-cycle run hit Gradle test-result infrastructure loss (`output.bin` disappeared) without an assertion failure.
A clean reviewed copy without build/cache state reran the same test successfully: 12/12 PASS.
The complete clean platform suite then passed 167 tests in 38 suites with zero failures/errors/skips.

Forge root also passed from the clean snapshot: 35/35 tasks executed.
The real Java 8 matrix compiled 161 sources and passed 44 commands with zero active/candidate hash changes.
The freshly rebuilt DEV JAR then passed 44/44 packaged executions with production-class origin checks.
The reobfuscated mod contains 296 Java 8 classes, 5 JNI classes and 7 natives, with license inventory PASS and no QA classes.

The clean platform rebuild exposed that older CLIENT34/35 Gateway/Paper JAR hashes were stale relative to their unchanged production sources.
CLIENT36 therefore establishes a corrected artifact-chain reference rather than pretending the historical hashes remain authoritative.
No production source edit was required for that correction.

No active-source promotion, server deployment, physical microphone/listening test, or real mobile-device test occurred.
Planning estimate after this qualification: about 90%, judgment band 86-95%.
