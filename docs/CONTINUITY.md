# Akashic Voice - confirmed engineering checkpoint

Checkpoint: AV-2026-09-13-CODEC-COMPLETION-01
Scope: C:\Users\User\Desktop\Codex\Akashic Voice
Status: SOURCE PATCH RECORDED; BUILD/TEST EXECUTION BLOCKED; NOT RELEASE-APPROVED.

## Evidence at entry
- .git/HEAD selects refs/heads/akashic-voice.
- Last observed HEAD reflog entry: 022dc1f922a107eb10f4f6b20d3b550194e8b7ae.
- Existing distribution is homologation-5eb51e1, not the current working tree.
- The preceding inspection recorded 10 modified and 2 untracked files; current git status was not rerun.
- Preserve all existing jitter, gateway, SVC and federated-group work.
- Device status and bounded file reads worked. Starting the Python terminal was security-blocked.
- No alternate execution route was attempted; no build, test, commit or deployment was run.

## Completed writes in this iteration
- LegacyForgeGroupCodec.java originally ended after writeString, without class closure.
- Its referenced write/PacketWriter and resolvedName helpers were missing from the inspected file.
- Appended only the missing helpers and class closure; existing parser/routing code was not rewritten.
- Offline display fallback matches legacy GroupRegistry: first eight UUID characters.
- Added LegacyForgeGroupCodecTest.java with 12 JUnit test methods.
- Tests cover four request types, truncation/trailing bytes, UTF-8, bounds, discriminator separation,
  output field order, offline fallback, empty state and a seeded 10,000-input malformed-packet loop.
- These tests were authored and reread, NOT executed. Do not cite them as passing fuzz results.
- Added AGENTS.md and this checkpoint. No JAR or distribution file was changed.

## Review scope
Static review checked missing symbols, resource closure and matching legacy serialization order.
This does not substitute for javac, JUnit, Forge interoperability or real-client listening tests.

## Full-product progress estimate - judgment, not measured completion
Scope includes legacy client, secure transport, SVC bridge, groups/radio/moderation/API and mobile/live QA.
Weights are a planning rubric. They are not task counts or calendar-time predictions.

| Area | Weight | Estimated earned points |
| --- | ---: | ---: |
| Foundation, runtime separation and secure protocol | 25 | 22 |
| Client audio, UX and measured performance | 25 | 18 |
| Gateway, SVC bridge and transfer lifecycle | 20 | 14 |
| Social functions, moderation, permissions and API | 15 | 4 |
| Real-world compatibility, mobile and release validation | 15 | 3 |
| Total | 100 | 61 |

Communicate approximately 60%, with a 55-65% uncertainty band; do not award progress for documentation volume.
The unexecuted patch above is not credited as an approved delivery or added percentage.
No production-readiness claim or time-to-delivery guarantee follows from this estimate.

## Next authorized execution checkpoint
1. Recheck branch, full git status and diffs; distinguish this patch from pre-existing edits.
2. Confirm no competing build owns these output directories; do not terminate other workers.
3. Run the focused LegacyForgeGroupCodecTest through the platform Gradle build.
4. Run the full platform tests/build, then legacy build and Java 8 compatibility/QA gates.
5. Capture command, source identity, process identity, log path, exit status and test summary.
6. Compare legacy wire bytes against actual Forge encoders/decoders, not only standalone test readers.
7. Only then integrate federated group controls with authority/permission and fail-open tests.

Open review items: total encoded message-size budget; group invitation limits/expiry; persistent group authority;
permissions/moderation parity; group/proximity semantics; native OpenAL lifecycle; live and mobile validation.

## Desktop Commander recovery protocol
This is a persisted procedure, not an installed watchdog or an always-running agent.
Health probe: list_devices -> small .git/HEAD read -> list_sessions when process ownership matters.
A reported online device alone does not prove file/process responsiveness; require the small read too.
Blocked:true on a REPL may mean it is waiting for input, not frozen.

HEALTHY: bounded reads/writes return and expected state can be verified.
TRANSPORT-UNKNOWN: timeout, socket closed or missing response; operation result is unknown.
SECURITY-BLOCKED: permission/tool denial; do not rephrase or switch execution tools to bypass it.

On TRANSPORT-UNKNOWN, once tool control returns:
- Stop the failing strategy; record the last verified operation and its file/line or log offset.
- Recheck device and a tiny known-file read; do not restart the desktop app blindly.
- Before retrying a mutation, read the target to determine whether it already succeeded.
- Before repeating a build, inspect its own PID/start identity, bounded log tail and recorded exit result.
- Reduce the next read to 20-40 lines; increase only after confirmed success.
- Never read an entire large log or kill another project's process.
- For permitted long jobs, start locally with short tool wait and persistent logs, then poll in bounded reads.
- Record ambiguous/stale results as unknown, not failed or passed.
There is no guarantee that a running tool call can be interrupted or a disconnected device can be restored.

## External references consulted
- Velocity messaging/forwarding boundaries: https://docs.papermc.io/velocity/dev/plugin-messaging/
- Velocity 4 runtime requirement: https://docs.papermc.io/velocity/faq/
These references do not certify this source patch; local versioned code and executed tests remain necessary.

Final gate for this checkpoint: PENDING EXECUTION. Preserve the existing distribution unchanged.

## Later executed checkpoint - AV-2026-09-13-MIC-RECOVERY-01
Read `docs/RECOVERY_AUDIT_2026-09-13.md` before continuing.
The prior execution-blocked note above is historical, not the latest test status.
Microphone restart sequence continuity and per-generation resource ownership were repaired.
Actual JDK 8: 114 production/QA source files compiled; 19 standalone executions PASS, including
8 new recovery scenarios. Full legacy build PASS; final stable platform build: 61 JUnit tests PASS.
A local candidate JAR was inspected (all 256 classes major 52) and copied to the audit checkpoint.
The original distribution is unchanged; no release/production approval follows from these results.
Machine-readable receipt: `audit/continuation-20260913-groups/FINAL-RECEIPT.json`.
Aggregate group-control output limits remain an OPEN finding despite the final passing suite.
Concurrent group edits were not overwritten. Preserve existing dirty files and both recovery tests.
Use QA runners with unique labels; inspect their status/locks before restarting an interrupted build.

## Latest full-build checkpoint - AV-CLIENT10-FULL-INTEGRATION-20260913
Read `audit/group-integration-20260913-04/CLIENT10_FULL_INTEGRATION_HANDOFF_20260913.md`.
Full Windows build tree: `audit/group-integration-20260913-04/integrated-client10`.
CLIENT09/CLIENT10 are no longer only isolated contracts: this full tree builds with Forge,
compiles 125 files under actual JDK8, passes 24 smokes and 124 platform JUnit tests.
Seven new crypto/client/listening integration scenarios passed; DEV-jar retest also passed.
Active-source promotion was blocked and did NOT occur. Production/dist remain unchanged.
Use the integrated tree as the latest tested candidate; preserve the active dirty source tree.
Existing run receipts and hashes are in `runs-integrated10`; no new root release tag was made.
Live microphone/OpenAL/gameplay/mobile and remaining product gates are still open.

## Latest candidate - AV-VOICE-GAIN-INTEGRATION-20260913-11
Read `audit/group-integration-20260913-04/CLIENT11_VOICE_GAIN_HANDOFF_20260913.md`.
Latest full build tree: `audit/group-integration-20260913-04/integrated-client11`.
Voice volume no longer overwrites shared listener AL_GAIN. Uses per-source extended gain,
OpenAL Soft float headroom, or documented bounded/clipping S16 fallback on other drivers.
Actual JDK8 compiled 126 files; 24 smokes PASS; platform 124 JUnit tests PASS;
DEV-jar 24 repeats + origin check PASS; reobfuscated jar inspected (267 Java8 classes).
New isolated gain/listening contracts: 50 scenarios x3; nine mutations detected.
Real OpenAL Soft 1.24.2 virtual rendering: 80 gain-policy comparisons PASS; not live audio.
Core fallback above 100% can clip and discard stale boosted buffers on gain changes.
Final monitored input hashes: 282 active and 308 candidate entries matched.
Active sources, existing distribution, tags and production deployment were not changed.
Keep the pending live-device/gameplay/mobile/release gates; do not claim stable or 100%.

## Latest built candidate - AV-TRANSMIT-SPEAKER-PANEL-20260913-14
Read `audit/group-integration-20260913-04/CLIENT14_SPEAKER_PANEL_HANDOFF_20260913.md`.
Latest complete tree: `audit/group-integration-20260913-04/integrated-client14`.
CLIENT13 capture/dispatch authorization is consolidated with the new individual-speaker GUI.
The hub now links to search, paging, relative volume, separate mute/reset and session-safe selection.
Actual JDK8 compiled 134 source files; 29 smoke executions PASS; platform 124 JUnit tests PASS.
Five panel mutants were detected; DEV-jar 29 repeats plus origin check PASS; 277 Java8 classes inspected.
Final monitored hashes: 282 active inputs and 317 candidate inputs matched. No active-code promotion.
GUI rendering/clicking, physical audio/gameplay/mobile and remaining product/release gates are OPEN.
An optional label-width refinement was blocked and NOT applied; keep the tested snapshot unchanged.
Evidence, caveats and artifact hashes are recorded in the handoff and runs-integrated14.

## Current development - AV-QUALITY-PRESETS-20260913-15
Candidate source: audit/group-integration-20260913-04/integrated-client15.
Read CLIENT15_QUALITY_HANDOFF_20260913.md and CLIENT15_QA_SUMMARY.json in that audit folder.
Presets/live bitrate and honest high-pass labels implemented; 18 actual Java8 codec/config tests PASS.
Full-source Java8 compilation reached 140 sources, but the new capture-flow test has a fixture failure.
The prepared fixture correction and subsequent builds were security-blocked and were NOT executed.
No CLIENT15 JAR or full Gradle/packaged approval exists. Last full-built reference remains CLIENT14.
Progress review: docs/PROGRESS_REVIEW_20260913.md, approximately 70% with a 65-75% judgment band,
reflecting accumulated validated work through CLIENT14; CLIENT15 full-delivery credit remains held.
Preserve prior candidates, active code and production distribution. No live/mobile/release approval.

## Current candidate - AV-TOUCH-PTT-CORE-20260914-16
CLIENT15 final Windows receipts were rechecked and are PASS; earlier CLIENT15 hold notes above are historical.
Read audit/group-integration-20260913-04/CLIENT16_TOUCH_CORE_HANDOFF_20260914.md and CLIENT16_QA_SUMMARY_20260914.json.
Working tree: audit/group-integration-20260913-04/integrated-client16; active sources were not promoted.
New touch/keyboard input core is built, but its GUI is incomplete outside the source set and NOT linked.
Java8 compiled 142 sources; 31 inherited smokes and 124 platform JUnit tests PASS; JAR structure PASS.
New 35 touch contracts x3 and eight mutations passed in isolated Linux JVM21 --release 8, not on the desktop.
Packaged runtime retest was blocked before startup. No physical input/audio/gameplay/mobile test occurred.
QA runner now uses javac @argfile: original 33,086-character command failed with WinError 206.
Retain the working runner repair; do not repeat the oversized command. No live build lock remains.
Progress remains approximately 71% (65-75% judgment band); new user-facing touch delivery is not credited.
CLIENT15 is the previous feature-complete candidate baseline; neither candidate is production-approved.

## Fully gated baseline - AV-TOUCH-PTT-GUI-20260914-17
Read audit/group-integration-20260913-04/CLIENT17_TOUCH_GUI_HANDOFF_20260914.md
and CLIENT17_QA_SUMMARY_20260914.json. Tree: integrated-client17.
Hold-to-talk GUI is hub-linked; actual JDK8 compiled 144 sources, 32 smoke executions PASS,
Forge/platform gates PASS, DEV-jar 33 executions PASS and 11 touch mutants detected.
Mod SHA256: 6635c5fcec04ca8a02e2582e8241e2de2802166513c9de52f20b4eb9993a1dbc.
No physical touch/audio/gameplay/mobile or production deployment approval follows from those gates.

## Current partial candidate - AV-LEGACY-TELEMETRY-20260914-18
Read CLIENT18_LEGACY_TELEMETRY_HANDOFF_20260914.md and CLIENT18_QA_SUMMARY_20260914.json.
Tree: integrated-client18. Aggregate legacy-server protocol metrics and /avdebug output are implemented.
Core/current targeted Java8 tests plus loopback VoiceServer UDP/crypto integration PASS; static audit 28 checks PASS.
Final accuracy changes cover malformed handshakes and delivery accounting on UDP send failure.
Full Forge/Java8/platform/package gates after that final patch were security-blocked and NOT run.
Keep progress at approximately 72% (67-77% judgment band); CLIENT18 credit is held until full gates pass.
Active source, existing distribution and production remain unchanged.

## Latest full automated candidate - AV-LEGACY-TELEMETRY-FULL-20260914-18
Read audit/group-integration-20260913-04/CLIENT18_TELEMETRY_HANDOFF_20260914.md and CLIENT18_QA_SUMMARY_20260914.json.
Tree: audit/group-integration-20260913-04/integrated-client18. Active source/dist remain unpromoted.
Legacy VoiceServer aggregate protocol telemetry is implemented without per-player labels or per-frame logs;
`/avdebug` surfaces bounded aggregate snapshots. Real loopback UDP/HMAC/AES-GCM/replay/disconnect metrics PASS.
Actual JDK8 compiled 148 sources; 34 smoke executions PASS; 124 platform JUnit tests PASS.
DEV-JAR replay: 34 smokes plus one origin check PASS. Eight compiled mutations were detected.
Mod SHA256: 0beabd6d0084e4ae68dbfb534405f40de28039df853ff1835de652661ff7aed9.
Final monitored hashes: 282 active and 331 candidate entries, zero mismatches; no build lock remains.
This closes legacy aggregate protocol metrics in code/automated QA, not production dashboards or live monitoring.
Touch UI from CLIENT17 is inherited. GUI/device/audio/gameplay/mobile/deployment gates remain open.
Planning estimate is approximately 73% with a 68-78% judgment band; not a stable-release percentage.

## Latest consolidated candidate - AV-MTU-TELEMETRY-CONSOLIDATED-20260914-20
Read audit/group-integration-20260913-04/CLIENT20_MTU_TELEMETRY_HANDOFF_20260914.md and CLIENT20_QA_SUMMARY_20260914.json.
CLIENT19 independently branched from CLIENT17 and would regress CLIENT18 telemetry if promoted directly.
CLIENT20 was rebuilt from CLIENT18 and selectively merged MTU deltas; telemetry-aware VoiceServer was preserved.
AV UDP is capped at 1200 bytes; v3 leaves 1124 bytes for Opus. Generic agent/TCP crypto remains 1275-capable.
Actual JDK8 compiled 149 sources; 35 smokes PASS; platform 124 JUnit PASS; DEV-JAR 35 replays + origin PASS.
Four MTU mutants and eight telemetry mutants were compiled/detected; consolidation audit passed 16 invariants.
Mod SHA256: cc6e9418a147b1d7e8510a92e5c9886d876b228c2a26b51aa2b6671bc527e5d5.
Final hashes: 282 active and 332 candidate inputs, zero mismatches; no build lock or source promotion.
This closes legacy telemetry plus <=1200 AV-UDP automated gates; live network/PMTU evidence remains separate.
Next protocol work: explicit session identity/generation hardening. Planning estimate ~74% (69-79% band).

## Fully gated baseline - AV-MTU-TELEMETRY-CONSOLIDATED-20260914-20
Read audit/group-integration-20260913-04/CLIENT20_MTU_TELEMETRY_HANDOFF_20260914.md,
CLIENT20_CONSOLIDATION_AUDIT.json and CLIENT20_QA_SUMMARY_20260914.json.
Tree: audit/group-integration-20260913-04/integrated-client20.
This reconciles the CLIENT18 aggregate legacy telemetry with CLIENT19 AV-UDP MTU hardening.
Actual JDK8 compiled 149 sources; 35 smoke mains PASS; platform 124/124 JUnit PASS;
DEV-jar origin + all 35 smoke replays PASS. MTU mutations 4/4 and telemetry mutations 8/8 detected.
Final active/candidate manifests have zero mismatches. Mod SHA256:
cc6e9418a147b1d7e8510a92e5c9886d876b228c2a26b51aa2b6671bc527e5d5.
Normal AV UDP is capped at 1200 bytes / 1124-byte Opus for v3; generic SessionCrypto remains uncapped
and the internal agent/bridge contract remains 1275 bytes. /avdebug exposes aggregate counters only.
Use CLIENT20 as the latest fully gated automated baseline. No active promotion or production deployment.
Planning estimate: approximately 74%, judgment band 69-79%; physical audio/gameplay/mobile/live gates remain open.

## Latest fully gated baseline - AV-EXPLICIT-SESSION-ID-20260914-22
Candidate: `audit/group-integration-20260913-04/integrated-client22`.
Read `CLIENT22_SESSION_ID_HANDOFF_20260914.md` and `CLIENT22_QA_SUMMARY_20260914.json` in the audit folder.
Explicit session UUIDs are now carried by additive AV outer types 5..8 and authenticated in session-bound AES-GCM AAD.
Legacy outer types 1..4 and legacy bootstrap prefix remain supported for migration; legacy crypto vector is unchanged.
Common AV Opus cap is 1108 bytes so session-bound v3 stays exactly within the 1200-byte UDP target.
Final credited Gradle gates used `--no-configuration-cache`: Forge PASS, platform PASS 126/126 JUnit.
Actual JDK8: 155 sources, 38 smoke mains plus one extra backend execution PASS. DEV-JAR replay: 40 PASS.
Eight compiled protocol/bootstrap/MTU mutants were detected. Active/candidate changes during structured final gates: zero.
Planning progress review: `docs/PROGRESS_REVIEW_20260914_CLIENT22.md`, approximately 76% (71-81% band).
No live audio/gameplay/mobile/production approval. Active source and production distribution were not promoted.

## Latest fully gated baseline - AV-GATEWAY-MODERATION-20260914-23
Reviewed tree: `audit/group-integration-20260913-04/reviewed-client23-20260914`.
Read `REVIEWED23_MODERATION_HANDOFF_20260914.md` and `REVIEWED23_QA_SUMMARY_20260914.json`.
Persistent Gateway global mute now filters managed MIC and SVC audio before routing/delivery,
uses persist-before-publish checksummed storage, permission-gated `/avvoice` administration,
and retires active sender epochs/reset playback when a mute is applied.
Reviewed no-cache gates: Forge PASS (38 executed tasks), platform PASS (26), JUnit 131/131.
Actual JDK8 compiled 155 sources; 40 runner commands PASS. DEV-JAR origin/replay: 40 PASS.
Five compiled moderation/reset mutants were detected on the same reviewed snapshot.
Exact CLIENT22->CLIENT23 delta: 7 changed + 3 added production files and 3 added tests, all Gateway-side.
Legacy mod and Paper behavior remain unchanged; moderation is not claimed for standalone legacy VoiceServer mode.
Planning review: `docs/PROGRESS_REVIEW_20260914_CLIENT23.md`, approximately 78% (73-83% band).
No active-tree promotion, production deployment, physical-audio, gameplay or mobile approval occurred.

## Latest fully gated baseline - AV-PUBLIC-ADDON-API-20260914-24
Reviewed tree: `audit/group-integration-20260913-04/reviewed-client24-20260914`.
Read `REVIEWED24_ADDON_API_HANDOFF_20260914.md` and `REVIEWED24_QA_SUMMARY_20260914.json`.
A standalone addon-api v1 now exposes authenticated session snapshots, moderation queries and low-frequency
session/moderation events without raw Opus, secrets, sockets, crypto internals or per-frame callbacks.
The Gateway embeds the same API classes and implements the public provider contract.
Reviewed no-cache gates: Forge PASS (38 tasks), platform PASS (31), JUnit 140/140 in 34 suites.
Actual JDK8 compiled 155 sources; 40 runner commands PASS; DEV-JAR origin/replay 40 PASS.
Static API audit: 38/38; seven compiled API mutants were detected; external Velocity addon sample compiled.
Exact CLIENT23->CLIENT24 delta: 5 changed + 14 added files, zero removals.
Planning review: `docs/PROGRESS_REVIEW_20260914_CLIENT24.md`, approximately 79% (74-84% band).
Radio/media injection, live two-plugin deployment, physical audio/gameplay/mobile and release approval remain open.
No active-tree promotion, production deployment, release tag or distribution replacement occurred.

## Latest fully gated baseline - AV-CROSS-SERVER-RADIO-20260914-25
Reviewed tree: `audit/group-integration-20260913-04/reviewed-client25-20260914`.
Read `REVIEWED25_RADIO_HANDOFF_20260914.md` and `REVIEWED25_QA_SUMMARY_20260914.json`.
Proxy-authoritative ephemeral radio now provides bounded cross-server direct voice with `/avradio`, permission pruning,
backend-handoff continuity, group/radio deduplication, SVC duplicate suppression and pair-scoped stream resets.
The 20 ms media path reads immutable radio snapshots without radio locks, recipient-set copies or addon callbacks.
Reviewed gates: Forge PASS (35/35 executed), platform PASS (38 tasks), JUnit 151/151 in 36 suites.
Actual JDK8 compiled 155 sources; 40 QA steps PASS; DEV-JAR origin/replay 40/40 PASS.
Distribution structure PASS; static radio audit 22/22; seven compiled radio mutants detected.
CLIENT24->25 audited scope: 6 changed Gateway production files + 2 new production classes + 2 tests + radio docs.
Planning review: `docs/PROGRESS_REVIEW_20260914_CLIENT25.md`, approximately 81% (76-86% band).
No active-source promotion, production deployment, physical-audio/gameplay/mobile or release approval occurred.

## Automated qualification - AV-SUSTAINED-SOAK-20260914-26
Tree: `audit/group-integration-20260913-04/integrated-client26`.
Read `CLIENT26_EXTENDED_SOAK_HANDOFF_20260914.md` and `CLIENT26_QA_SUMMARY_20260914.json`.
CLIENT26 changes only `GatewayRuntimeSoakTest.java`; production source is identical to CLIENT25.
The sustained gate uses 24 session-bound clients, 12 radio pairs, 10 agent cycles,
50 frames/client/cycle at 20 ms cadence, repeated three times with fresh runtimes.
Across the gate: 36,000 MIC frames, 36,000 radio frames, 720 SVC deliveries,
72 sessions and 27 agent route-down transitions; all final limit/drop/rejection counters are zero.
Platform clean no-cache PASS: 149 JUnit / 36 suites. Forge clean no-cache PASS: 35 tasks.
Mod/API/Gateway/Paper artifacts are byte-identical to CLIENT25; no production promotion/deploy.
Planning estimate: approximately 82%, judgment band 77-87%.
Live real clients, physical audio, WAN conditions, mobile and production release gates remain open.
## Latest QA qualification - AV-LOSS-JITTER-PLC-20260914-27
Candidate: `audit/group-integration-20260913-04/integrated-client27`.
Read `CLIENT27_LOSS_JITTER_HANDOFF_20260914.md` and `CLIENT27_QA_SUMMARY_20260914.json`.
Production source/artifacts remain byte-identical to the CLIENT25/26 baseline; CLIENT27 adds one QA source only.
Actual JDK8 compiled 156 sources and all 40 runner steps PASS with zero active/candidate changes.
Four real-codec matrices processed 16,000 frame positions total under deterministic loss, burst loss and reordering.
Each matrix dropped 114 packets and reordered 122 adjacent pairs; decode errors/resyncs/late/duplicates stayed zero.
A counting wrapper proves one actual `decodeMissing()` PLC call per lost packet and one decode per received packet.
Production adaptive jitter was already active before this checkpoint; this gate qualifies it rather than adding it.
In-band FEC remains unclaimed because the installed native JNI API lacks equivalent exposed FEC controls.
Planning estimate: approximately 83% (78-88% band). No production/live/mobile/release approval.
## Qualification checkpoint - AV-EXTENDED-SOAK-20260915-26
Tree: `audit/group-integration-20260913-04/integrated-client26`.
Read `CLIENT26_SOAK_HANDOFF_20260915.md` and `CLIENT26_QA_SUMMARY_20260915.json`.
This checkpoint changes no production source; only `GatewayRuntimeSoakTest` differs from CLIENT25.
Three repetitions of 24 session-bound clients, 10 cycles and 50 frames/client/cycle PASS.
Aggregate evidence: 36,000 MIC frames, 36,000 radio deliveries, 720 SVC downlinks and 27 agent reconnects.
Full platform after the heavier soak: 149 JUnit tests / 36 suites, zero failures/errors/skips.
CLIENT25 artifacts remain the production-code baseline; CLIENT26 only strengthens automated release evidence.
Planning estimate: approximately 82% (77-87% judgment band). Real WAN/audio/gameplay/mobile remain open.

## Latest production hardening - AV-BOUNDED-DECODER-STREAMS-20260914-28
Candidate: `audit/group-integration-20260913-04/integrated-client28`.
Read `CLIENT28_DECODER_CAP_HANDOFF_20260914.md` and `CLIENT28_QA_SUMMARY_20260914.json`.
Expensive client decoder streams are now capped at 256 while lightweight epoch tombstones remain separately bounded.
Overflow senders allocate no decoder; existing streams remain usable at cap; reset/idle/session cleanup releases slots.
Creation shares the remote-epoch synchronization boundary, preventing concurrent over-cap allocation/session-clear races.
Focused structural and encrypted VoiceClient tests PASS, including 4096 excess UUIDs and 8 concurrent creators.
Actual JDK8 compiled 158 sources; 42 runner steps PASS; Forge/platform gates PASS; 149/149 JUnit PASS.
DEV-JAR origin/replay 42 PASS; distribution inspection PASS; five compiled capacity/reset mutants detected.
Gateway/addon-api/Paper artifacts remain byte-identical to CLIENT25. Active source/distribution remain unchanged.
Planning estimate: approximately 84% (79-89% band). Physical audio/gameplay/mobile/live/deployment gates remain open.
## Latest fully sealed production hardening - AV-OUTBOUND-EPOCH-RECLAMATION-20260915-33
Candidate: `audit/group-integration-20260913-04/integrated-client33`.
Read `CLIENT33_EPOCH_RECLAMATION_HANDOFF_20260915.md` and `CLIENT33_FINAL_RECEIPT.json`.
CLIENT32 (QA-only) reproduced a real Gateway receiver-session leak: retired outbound stream epoch tombstones
remained in a 4096-entry map and could eventually block new transient speakers in a long-lived session.
CLIENT33 changes one production file: successful source-specific/hard retirement removes the active entry,
while the tracker-level epoch counter remains monotonic across reclamation/re-entry.
Focused final: 6/6 tests PASS; six compiled mutants detected. Platform: 155 JUnit / 37 suites, zero failures.
Actual JDK8: 161 sources + 43 smoke executions PASS. DEV-JAR replay: 44 PASS. Gateway packaged probe PASS.
Forge/root and distribution inspection PASS. Mod/Paper remain byte-identical to current CLIENT31; Gateway changes.
An unrelated one-run race in the metrics QA was traced to wait ordering, reproduced as nondeterministic and
fixed in the smoke only; five unchanged-binary reruns plus the full final matrix PASS.
Planning estimate: approximately 87% (82-91% judgment band). Active source/distribution/production untouched.
Physical audio, real two-client Velocity/WAN, mobile, operator soak and production release gates remain open.

## Reviewed baseline - AV-SPEAKER-RECLAIM-BOUNDARY-20260915-34
Use `audit/group-integration-20260913-04/reviewed-client34-20260915` as the latest fully gated candidate.
Read `CLIENT34_FINAL_RECEIPT.json` and `CLIENT34_SPEAKER_RECLAIM_HANDOFF_20260915.md`.
CLIENT34 reclaims retired default speaker-control entries while preserving explicit preferences,
and gives each observed speaker a unique listening identity before decode to avoid a false first-frame boundary.
Forge PASS; actual JDK8 compiled 161 sources; packaged replay 44 PASS;
platform 155 JUnit tests / 37 suites PASS; distribution inspection reports 296 Java8 classes.
Gateway/Paper artifacts are byte-identical to CLIENT33. No active-source promotion or deployment occurred.
Planning estimate: approximately 88%, judgment band 84-91%. Physical/gameplay/mobile/release gates remain open.

## QA qualification - AV-SESSION-RESTART-SPEAKING-SOAK-20260915-35
CLIENT35 is QA-only and uses CLIENT34 production byte-for-byte.
Six repetitions prove session-bound speaking before and after a full Gateway runtime restart on the same UDP port.
Each repetition rejects the stale pre-restart session bootstrap, accepts a fresh session identity,
and requires the authenticated MIC frame to reach the backend Agent before and after restart.
Full platform build PASS: 161 JUnit tests / 38 suites, zero failures/errors/skips.
Gateway and Paper JAR hashes are byte-identical to CLIENT34. No production source or deployment changed.
Planning estimate after this added release evidence: approximately 89%, judgment band 85-92%.

## Reviewed transfer gate - AV-BACKEND-TRANSFER-QUALIFICATION-20260915-36
Reviewed tree: `audit/group-integration-20260913-04/reviewed-client36-20260915`.
Read `CLIENT36_FINAL_RECEIPT.json` and `CLIENT36_TRANSFER_HANDOFF_20260915.md` in the audit folder.
Production source is unchanged from CLIENT35; CLIENT36 adds qualification only.
Six restart cycles plus six lobby -> forbidden transfer cycles pass with stale UDP/session rejection before fresh handshake.
Clean rebuild gates: platform 167/167 PASS, Forge 35/35, Java8 161 sources / 44 commands, DEV JAR replay 44/44.
The clean rebuild also corrected stale historical Gateway/Paper artifacts; use CLIENT36 hashes as the artifact reference.
No active promotion or deployment. Planning estimate: ~90%, judgment band 86-95%.

## Gateway hardening - AV-NAT-HANDSHAKE-BURST-20260915-39
Candidate: `audit/group-integration-20260913-04/integrated-client39`.
Read `CLIENT39_NAT_HANDSHAKE_HANDOFF_20260915.md` and `CLIENT39_FINAL_RECEIPT.json`.
One Gateway production file changes: per-IP HELLO burst increases 20 -> 64 while sustained refill stays 10/s
and global 128/s / burst 256 caps remain unchanged. Deterministic clock injection is test-only behavior.
Real Gateway integration proves 32 clients behind one IP receive session-bound challenges in one burst.
The old burst-20 negative control fails at the 21st client. Full platform: 176 JUnit / 40 suites PASS.
Everything outside Gateway is hash-identical to CLIENT37 and inherits the clean CLIENT36 Java8/Forge baseline.
Planning estimate: approximately 91% (87-95%). No active promotion, live deployment or physical/mobile approval.

## QA qualification - AV-PROLONGED-SESSION-SOAK-20260915-40
Production baseline remains `audit/group-integration-20260913-04/integrated-client39` byte-for-byte.
Read `CLIENT40_PROLONGED_SOAK_HANDOFF_20260915.md` and `CLIENT40_QA_SUMMARY_20260915.json`.
An external test source extended the Gateway soak to 350 frames/client/cycle at 20 ms cadence.
Five long scenarios PASS in 317.841 s of JUnit time: 302,400 authenticated MIC frames,
302,400 radio deliveries, 864 SVC downlinks, 27 Agent reconnects and 144 client backend transfers.
The first long run exposed a QA-only heartbeat assumption; the harness now handles Ping/Pong like the real Paper link.
The same load then passed; Gateway JAR remains CLIENT39 SHA256 `2218962a1b7955ba87ec77c22941b92d7b44c9072368fb4fcf191e49c1ac6254`.
No production source, artifact, active tree or deployment changed. Planning estimate ~92% (88-96% band).
Live WAN, physical listening/microphone, real mobile devices and production deployment remain open.

## Latest gated baseline - AV-NAT-REBIND-LOGICAL-LIFECYCLE-20260915-44
Read `audit/group-integration-20260913-04/CLIENT44_LOGICAL_REBIND_HANDOFF_20260915.md` and `CLIENT44_FINAL_RECEIPT.json`.
Latest gated tree: `audit/group-integration-20260913-04/integrated-client44`.
NAT rebinding now preserves transport open/close metrics while suppressing fake logical disconnect/connect lifecycle for the same grant. Session-bound identity additionally requires matching authenticated session ID; legacy mode preserves null/null compatibility.
Full platform no-cache build: 185 JUnit tests / 42 suites PASS. Root Forge no-cache build PASS. Five deliberate lifecycle/identity regressions detected. Production delta versus CLIENT43 is exactly two Gateway files.
Planning estimate: approximately 95%, judgment band 91-98%. Active sources and production deployment remain unchanged. Live WAN/audio/mobile/canary gates remain open.

## Latest sealed candidate — AV-LEGACY-AGENT-RC4-20260916-46
Read `audit/group-integration-20260913-04/CLIENT46_LEGACY_AGENT_HANDOFF_20260916.md` and `CLIENT46_FINAL_RECEIPT.json`.
Latest release package: `audit/group-integration-20260913-04/release-candidate-20260916-rc4`.
CLIENT46 adds fail-closed Legacy Forge/Crucible Agent mode and operational/debug readiness while preserving direct mode compatibility.
Final automated gates: Forge PASS; actual JDK8 167 sources / 46 command records PASS; platform 186 JUnit in 43 suites PASS; DEV-JAR replay 46/46 PASS; Agent mutations 5/5; static fail-closed audit 20/20; independent RC4 seal verification 567/567.
RC4 Forge SHA256: `8e5a6fe5de6a2421ef37422a14204ce48fc43382fb8de944ec5b7462c118215e` (309 Java8 classes). Gateway/Paper/addon-api are byte-identical to RC3.
Planning estimate: approximately 96%, judgment band 92-98%. Remaining gates are real WAN/listening+transfer/restart, physical acoustic quality, real Pojav/Amethyst thermal/battery, and production canary/operator-player feedback.
No production deployment, git tag, active-tree promotion, or 100% claim has occurred.

## Latest sealed candidate — AV-LEGACY-AGENT-OBSERVABILITY-RC5-20260917-47
Read `audit/group-integration-20260913-04/CLIENT47_AGENT_OBSERVABILITY_HANDOFF_20260917.md` and `CLIENT47_FINAL_RECEIPT.json`.
Latest release package: `audit/group-integration-20260913-04/release-candidate-20260917-rc5`.
CLIENT47 makes Legacy Agent media-drop telemetry cumulative across reconnects/disconnected state and exposes it through the existing `/avdebug` Agent status.
Final gates: focused 2-generation drop smoke PASS (88 cumulative drops); Forge PASS; JDK8 168 sources / 47 commands PASS; platform 186 JUnit / 43 suites PASS; DEV-JAR origin/replay PASS; mutations 2/2; independent RC5 verify 567/567.
RC5 Forge SHA256: `6fab0865c2d2c5a0e382e193f35e099aa7e0861ab4317b0c78a91796ef8d553e`; Gateway/Paper/addon-api remain byte-identical to RC4.
Planning estimate remains approximately 96%, judgment band 92-98%, because this observability improvement does not close any live gate.
Remaining gates: real WAN/listening plus production transfer/restart, physical acoustic/AGC validation, real Pojav/Amethyst thermal/battery, and production canary/operator-player feedback. No production deployment or 100% claim.

## RC5 live-canary tooling qualification - 2026-09-17 v2.1
Production baseline remains `AV-LEGACY-AGENT-OBSERVABILITY-RC5-20260917-47`; no JAR, active source, configuration, tag or deployment changed.
New sealed operator bundle: `audit/group-integration-20260913-04/akashic-voice-rc5-homologation-v2.1-20260917.zip`.
SHA256: `8194d0c49390c62a0f00a03733da644726c46ea094b4a2249351fdc89d79dd40`.
The original RC5 homologation bundle remains byte-identical at `8e26c606cadd06930e50001be08a680cb0b35754908216a70e0c779bda65d6b9`.
`evaluate-canary-v2.ps1` is fail-closed on missing/non-numeric counters and requires live PASS gates plus consistent baseline/final state, zero normal-canary Agent drop delta, bounded Agent queue, monotonic drops and a reconnect-count increase after controlled restart.
Self-tests: empty template => HOLD; coherent synthetic evidence => READY_FOR_STABLE_REVIEW; manually PASS-marked evidence with a drop/reconnect contradiction => HOLD.
`parse-canary-status-v2.ps1` parses the exact current `/avgateway status` and legacy `/avdebug` formats; UP/DOWN is preserved and incomplete input is rejected rather than defaulted.
Non-invasive capture preflight reran under installed Java 8 and enumerated 5 capture-list entries. `CaptureBackendSmoke` does not open a physical microphone; this is not acoustic approval.
Read `PRECANARY_READINESS_V2_1_20260917.json` and `RC5-HOMOLOGATION-V2.1-BUNDLE-RECEIPT.json`.
Planning estimate stays approximately **96%** (92-98% judgment band): this tooling reduces canary/operator risk but closes none of the four required live gates.

## Canary tooling checkpoint - AV-CANARY-EVIDENCE-ENGINE-20260917-48
Release under test remains sealed `release-candidate-20260917-rc5`; production source/artifacts are unchanged.
Read `audit/group-integration-20260913-04/CLIENT48_CANARY_EVIDENCE_HANDOFF_20260917.md`,
`CLIENT48_FINAL_RECEIPT.json` and `CLIENT48_PACKAGE_RECEIPT.json`.
Homologation-v3 derives PASS/HOLD from primitive observations instead of trusting a manually typed overall PASS.
Synthetic evaluator matrix: 10/10; blank template is structured HOLD exit 2; static/immutability audit 16/16.
Old homologation kit 5/5 hashes and sealed RC5 artifact 4/4 hashes remain unchanged.
Operator kit: `akashic-voice-rc5-canary-v3-client48-20260917.zip`, SHA256
`973037900bb3ce6ae0730e79c1cf398b4dec677ea1421e9b5fc11b5f848be8f4`; nine files, no JARs/secrets.
Planning estimate remains approximately 96% (92-98% band): this improves live-canary decision integrity but does not replace real WAN/acoustic/mobile/canary evidence.

## Canary ingestion checkpoint - AV-CANARY-STATUS-PARSER-20260917-49
Release under test remains sealed `release-candidate-20260917-rc5`; production source/artifacts remain unchanged.
Read `audit/group-integration-20260913-04/CLIENT49_STATUS_PARSER_HANDOFF_20260917.md`,
`CLIENT49_FINAL_RECEIPT.json` and `CLIENT49_PACKAGE_RECEIPT.json`.
`parse-status-v3.ps1` normalizes real `/avgateway status` and Agent-mode `/avdebug` text;
`apply-status-v3.ps1` applies Baseline/Restart/Cleanup metrics only into a new evidence file.
End-to-end pipeline 8/8 PASS; static/source-format audit 23/23 PASS; DOWN routes and missing Agent counters fail closed.
Operator kit: `akashic-voice-rc5-canary-v3-client49-20260917.zip`, SHA256
`c7805e83246b849fb77c04cd18970ebbd10ee49a32457d12fe53fe283abcc281`; 12 files, no JARs.
Planning estimate remains ~96% (92-98% band). Remaining completion gates require real WAN/listening/restart, physical acoustic observations, real Pojav/Amethyst devices and a controlled production canary/operator review.

## Pre-canary evidence-chain qualification - CLIENT52 / 2026-09-17
Production baseline remains sealed `AV-LEGACY-AGENT-OBSERVABILITY-RC5-20260917-47`; no production JAR/config/deployment changed.
Current tooling checkpoint: `AV-CANARY-EVIDENCE-CHAIN-20260917-52`.
Read `audit/group-integration-20260913-04/CLIENT52_EVIDENCE_CHAIN_HANDOFF_20260917.md`, `CLIENT52_FINAL_RECEIPT.json` and `PRECANARY_READINESS_V3_2_20260917.json`.
Evidence-chain tests: 15/15 PASS; provenance hardening: 3/3 PASS; static audit after hardening: 74/74 PASS.
CLIENT51 guarded install/rollback remains qualified: 10/10 install, 7/7 partial-activation recovery, 33/33 static audit.
Sealed operator bundle: `akashic-voice-rc5-homologation-v3.2-20260917.zip`, SHA256 `ab88d189471784242906497bb759642690c2ee5d6f25b6728c21de240d76b6ac`.
Bundle seal 41/41 PASS; extracted bundle smoke 12/12 PASS including rollback→install→verify→evidence chain→restore.
Planning estimate is approximately **98%** (96-99% judgment band).
Remaining completion is live evidence only: real WAN/two-client+transfer/restart, physical acoustic/AGC, real Pojav+Amethyst thermal/battery/input, and controlled production canary/operator-player feedback.
No live canary, production deployment or stable-release approval has occurred; synthetic READY_FOR_STABLE_REVIEW is evaluator QA only.

## Latest pre-canary hardening - AV-CANARY-STAGE-RUNTIME-BINDING-20260917-56
Release under test remains sealed `release-candidate-20260917-rc5`; production artifacts/configuration remain unchanged.
Read `audit/group-integration-20260913-04/CLIENT56_STAGE_RUNTIME_BINDING_HANDOFF_20260917.md`,
`CLIENT56_FINAL_RECEIPT.json` and `PRECANARY_READINESS_V3_5_20260917.json`.
CLIENT54 first bound session creation to the live installed Forge/Gateway/Paper hashes.
CLIENT55 reproduced and fixed same-hash path substitution by cross-binding install destination,
rollback `original_path`, verification path and sealed release source.
CLIENT56 reproduced v3.4 accepting runtime drift after session creation, then added stage/final runtime revalidation.
Focused CLIENT56 contracts: 15/15 PASS; sealed v3.5 bundle 49/49; extracted bundle smoke 20/20;
final consolidation 12/12. v3.5 bundle SHA256:
`fba17150a6586cccb8f27e4571aa4222a9be27e4d99379b3276abf4849aa2e90`.
Planning estimate remains approximately 98% (96-99% band). Remaining completion requires real WAN/two-client transfer/restart,
physical acoustic/AGC observations, real Pojav/Amethyst thermal/battery/input, and controlled production canary feedback.
No deployment or stable-release approval has occurred.

## Latest pre-canary revalidation - AV-PRECANARY-REVALIDATION-20260917-57
RC5 and homologation v3.5 were freshly re-hashed on 2026-09-17; all sealed hashes still match.
Blank live evidence was re-evaluated and correctly returned HOLD/exit 2, confirming fail-closed behavior.
Read `audit/group-integration-20260913-04/CLIENT57_PRECANARY_HANDOFF_20260917.md` and
`CLIENT57_PRECANARY_REVALIDATION_20260917.json` for the fresh receipt.
Progress remains approximately 98% (96-99% judgment band). Automated pre-canary engineering is exhausted;
remaining completion requires real WAN/two-client, physical acoustic, Pojav/Amethyst and production-canary evidence.
No deployment, stable-release approval, source promotion or artifact replacement occurred.

## Latest pre-canary hardening - AV-CANARY-STANDALONE-RECEIPT-BINDING-20260917-58
Release under test remains sealed `release-candidate-20260917-rc5`; all four release artifact hashes still match.
v3.5 standalone evaluation was reproduced accepting a fabricated `{"state":"PASS"}` verification receipt
when all other synthetic observations were coherent. The evidence-chain/session evaluator was not bypassed.
v3.6 now validates receipt checkpoint, exact Forge/Gateway/Paper roles, sealed hashes, verified flags and
fresh current-file SHA-256 before `artifact_integrity` can PASS. Six invalid receipt variants are HOLD.
Read `audit/group-integration-20260913-04/CLIENT58_STANDALONE_RECEIPT_BINDING_HANDOFF_20260917.md`,
`CLIENT58_FINAL_RECEIPT.json` and `PRECANARY_READINESS_V3_6_20260917.json`.
v3.6 bundle SHA256: `7bc26aa13991d1e4f2ae9093d38ef0f7fcd84d60a36dcc2a574b6446879f9b0e`;
bundle seal 51/51 PASS and extracted bundle smoke 21/21 PASS.
Progress remains approximately 98% (96-99% band). Remaining completion is live evidence only; no deployment,
stable-release approval, source promotion or production artifact replacement occurred.

## CLIENT59 - real Forge 1.7.10 client runtime evidence (2026-09-17)
Checkpoint: `AV-REAL-CLIENT-RUNTIME-EVIDENCE-20260917-59`.
The sealed RC5 Forge JAR is installed in a real running Technic Forbidden Series client and its SHA-256 matches the
CLIENT47 release exactly. FML discovers `dev.akashic.voice.AkashicVoice`; no current Akashic Voice ERROR/FATAL match.
A read-only JDK8 thread dump observed receive, maintenance, playback and OpenAL microphone-capture production threads.
Sealed source only starts MicCapture while the VoiceClient is running and authenticated; the runtime therefore reached
an authenticated voice session. The receive thread is blocked in a real `DatagramSocket.receive()` and the Minecraft
process owns a UDP endpoint. `opus_jni_rust.dll` is loaded while client config uses Opus auto / 48000 bit/s.
Seven samples over 124 seconds retained all four voice threads with no matching timeout/capture/bootstrap-reset warning.
No audio contents were read, no GUI input was synthesized, and no production artifact/server was changed.
This is a real-client load/auth/local-transport PASS, not proof of remote voice reception or acoustic quality.
Machine-readable receipts: `CLIENT59_FINAL_RECEIPT.json`, `CLIENT59_SUSTAINED_RUNTIME_RECEIPT.json`,
`CLIENT59_REAL_CLIENT_TRANSPORT_RECEIPT.json`, and `CLIENT59_NATIVE_CODEC_RECEIPT.json`.
Progress remains approximately 98% (96-99% band). Remaining completion: two-client WAN/listening, transfer/restart,
physical acoustic/AGC evidence, real Pojav/Amethyst device evidence, and controlled production canary/operator review.

## CLIENT60/61 - real restart and route recovery (2026-09-17)
CLIENT60 `AV-REAL-CLIENT-RESTART-LIFECYCLE-20260917-60`: a fresh real Forbidden Series client loaded RC5 and remained
without Akashic Voice threads for 116 seconds outside a world; microphone/UDP voice did not auto-start in the menu.
Receipt: `CLIENT60_REAL_RESTART_LIFECYCLE_RECEIPT.json`.

CLIENT61 `AV-REAL-ROUTE-RECOVERY-EVIDENCE-20260917-61`: archived real multiplayer logs show the public configured voice
endpoint, session-bound bootstrap + secure authentication, three route-change/rebootstrap cycles that all authenticated,
and six timeout/recovery cycles that all eventually reauthenticated. Microphone capture was torn down/restarted across
recovery without client crash. Gateway RC5 source ties rebootstrap to backend post-connect or route-up recovery.
Receipts: `CLIENT61_REAL_ROUTE_RECOVERY_RECEIPT.json`, `CLIENT61_REAL_ROUTE_RECOVERY_HANDOFF_20260917.md`, and
`LIVE_READINESS_CLIENT61_20260917.json`.

Progress estimate is now approximately 99% (98-99.5% band). Remaining completion is deliberately limited to final live
acceptance: controlled backend process restart, remote media/listening with two clients, physical acoustic/AGC review,
real Pojav/Amethyst devices, and production canary/operator-player acceptance. Stable release remains false.

## CLIENT62 - current RC5 runtime freshness + homologation v3.7 (2026-09-17)
Checkpoint: `AV-CURRENT-RC5-RUNTIME-FRESHNESS-20260917-62` / tooling `AV-LIVE-CLIENT-PROBE-V37-20260917-62`.
The same sealed RC5 Forge JAR remains installed in the currently running real Technic Forge 1.7.10 client;
SHA-256 still equals `6fab0865c2d2c5a0e382e193f35e099aa7e0861ab4317b0c78a91796ef8d553e`.
A fresh read-only runtime sample again observed VoiceClient receive/maintenance/audio/microphone threads,
`DatagramSocket.receive`, and OpenAL capture, with zero current timeout/microphone/packet/socket-error matches.
A live heap-class sample contained one VoiceClient but no active DecoderStream at that instant; remote media is NOT claimed.
The RC5 source has no unresolved production TODO/FIXME/not-implemented markers; remaining completion is external acceptance.

Homologation v3.7 adds a read-only `probe-live-client.ps1` that binds installed Forge SHA + process + jstack lifecycle
without reading command lines, audio payloads or secrets. Real client probe: PASS_RUNTIME_PREREQUISITES.
Negative QA: wrong artifact -> HOLD, incomplete runtime -> HOLD, deterministic jstack timeout -> HOLD,
complete synthetic runtime -> PASS; sentinel secret copy count is zero.
v3.7 inherits all 38 v3.6 tooling files byte-identically. Bundle seal: 55/55 PASS; extracted bundle smoke PASS.
Bundle SHA-256: `2a771baf1feb7b78fb0ebc04a79940622f785ffe8f13837eae9173d0301a331f`.
Read `CLIENT62_CURRENT_RUNTIME_RECEIPT.json`, `CLIENT62_CURRENT_RUNTIME_HANDOFF_20260917.md`,
`CLIENT62_LIVE_PROBE_V37_RETRY.json`, `CLIENT62-BUNDLE-SMOKE-RECEIPT.json`, and `PRECANARY_READINESS_V3_7_20260917.json`.
Progress remains approximately 99% (98-99.5% band); stable release remains false.
Remaining gates: controlled backend restart, two-client remote listening, acoustic/AGC, real mobile, production canary acceptance.
