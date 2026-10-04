# Akashic Voice - CLIENT13 transmission and CLIENT14 speaker panel

Checkpoint: AV-TRANSMIT-SPEAKER-PANEL-20260913-14.
Latest built candidate: audit/group-integration-20260913-04/integrated-client14.
State: complete candidate built/tested; NOT promoted to active source or production.
Scope: Akashic Voice only. Prior candidates and the active dirty tree were preserved.

## Transmission checkpoint completed
CLIENT13 baseline CaptureAuthorizationSmoke: 4 PASS / 8 FAIL in 12 scenarios.
MicGate now publishes immutable authorization identities for meaningful mute/mode/PTT changes.
MicCapture carries the acquisition identity across read/encode and forwards it to the real sink.
VoiceClient validates capture generation, crypto session and authorization before/inside its send lock.
Cancelled callbacks do not consume the media sequence; UI control writers do not hold the send lock.
The final check is a dispatch boundary, not a guarantee of recalling already-dispatched datagrams.
The existing deafen/listen-only transmission semantics were preserved.
CLIENT13 executed 12 capture and 23 encrypted-transport boundary scenarios successfully.
Its complete Forge/Java8/platform gates passed before the CLIENT14 snapshot was created.

## Individual speaker panel delivered in code
GuiSpeakerControls is linked from the Voice Chat hub by the Player voices button.
The screen includes name/UUID search, pagination, +/-10 percentage-point controls, separate mute,
reset to 100%/unmuted, Back/Escape navigation and a small-window fallback.
SpeakerPanelModel accepts only UUIDs already registered in this playback session's SpeakerControls.
Names are sanitized display hints, never authority. The selected full UUID is displayed.
Selection survives sorting by UUID, but is cleared when hidden or when the playback session changes.
Stale-session edits are rejected. Changing volume preserves mute, and muting preserves the level.
English and Brazilian Portuguese strings were added for the new panel; this is not full-mod localization.
Preferences are session-local, not saved across servers. No server/native calls originate in the panel.

## Executed CLIENT14 evidence (runs-integrated14)
- panel-java8-initial: compiled the actual Forge-facing GUI and passed 25 panel-model scenarios.
- panel-legacy-final: complete Forge build PASS, 34 tasks executed.
- panel-java8-final: actual JDK8 compiled 134 production/QA sources; 29 smoke executions PASS.
- panel-platform-final: complete platform build PASS, 26 tasks; 124 JUnit tests in 27 suites,
  zero failures/errors/skipped. No platform source change was made in this continuation.
- packaged-java8: 29 repeated smoke executions plus one load-only class-origin check PASS.
  Origin check covered nine production classes, including MicGate, the model and the GUI.
  Loading the GUI class without static initialization is NOT a rendered/clicked GUI test.
- panel-mutations-final: five compiled mutants detected by runtime assertions.
  The first mutation run stopped after one mutant because formatting changed a locator;
  the locator was corrected without changing candidate code or weakening the assertions.
- CLIENT14_UI_STATIC_CHECKS.json: 12 panel strings and one hub label checked in each language,
  page placeholders, hub/back connection, session guard and absence of direct server/audio calls.
Ordering is case-insensitive display name with UUID tie-break; selection remains bound to UUID.
The 35 transmit and 25 panel scenarios are included in the smoke executables, not extra executions.

## Artifact identity
Mod: integrated-client14/build/libs/akashicvoice-0.1.0-alpha.5-akashic-voice.11+022dc1f922-dirty.jar
SHA256: b89a23c005a4d45a14d73661790033e06aef181baa5f26630d849c1e0965722d.
DistributionJarSmoke PASS: 277 classes at Java8 major 52, 5 JNI classes, 7 natives,
required notices and 33 license-inventory entries. The DEV jar was runtime-tested;
the reobfuscated Forge jar was structurally inspected, not launched in Minecraft.
Gateway SHA256: 8f1eb303384fa5419cd1b221d87bc2ea0421afa155f4dd7d03625d28dd8d1a65.
Paper SHA256: e5919e987cf006e126b4acbe1caa9b959a5dc9f8b6d34877c197405347b0517a.
Both platform artifacts match the earlier candidates byte-for-byte.
Final verification: 282 active inputs and 317 candidate inputs, zero hash mismatches.
Build lock absent after all jobs completed. Root HEAD remains 022dc1f; no commit/tag/deploy.

## Boundaries and blocked optional work
The model was executed; the actual GUI was compiled and loaded from the DEV jar,
not visually rendered, manually clicked or exercised in a live Minecraft session.
A private javap inspection of Minecraft UI internals was blocked; no alternate private
inspection or constructor-bypass strategy was executed.
An optional pixel-budget refinement script (refine_panel14_labels.py) was prepared, but
its combined apply/build command was blocked. It was NOT applied or executed.
CLIENT14_LABEL_REFINEMENT.json is absent and all tested source hashes still match.
The approved snapshot still uses the original panelWidth-145 label reservation;
real-window typography/layout validation remains open. Do not treat the unrun script as approved.
No native audio/device, physical microphone, live backend transfer or mobile test ran here.
No new claim of stable release, fully completed roadmap, or 100 percent progress is made.
Active source promotion and production deployment did not occur; this is an audit candidate.

## Resume
Use integrated-client14, not the older isolated CLIENT09/10 sources, as the latest tested candidate.
Preserve the active dirty source tree, older candidate snapshots and existing distribution.
Latest evidence: runs-integrated13/transmit-* and runs-integrated14/panel-*-final,
packaged-java8, panel-mutations-final, and CLIENT14_UI_STATIC_CHECKS.json.
The panel's session-local scope and relative 0..100 percent behavior are intentional.
Next gates include real GUI/keyboard/layout validation, physical audio and integrated gameplay,
then the remaining product roadmap. One assistant's review/test passes are not independent reviewers.
Concurrency reference: Oracle Java Language Specification SE8 section 17.4.5.
The language specification explains publication/order; the local tests are the evidence for this patch.
