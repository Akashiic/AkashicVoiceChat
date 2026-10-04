# Akashic Voice - CLIENT17 touch PTT GUI

Checkpoint: AV-TOUCH-PTT-GUI-20260914-17.
Candidate: audit/group-integration-20260913-04/integrated-client17.
State: full candidate built/tested, including DEV-jar runtime replay; not production-deployed.
Active source tree, CLIENT16 and existing distribution were preserved.

## Delivered
GuiTouchTalk is linked from the Voice Chat hub and implements explicit hold-to-talk.
Opening the screen never transmits. Starting requires PTT mode, an authenticated VoiceClient,
game focus and an explicit button press. Continuing additionally requires the same VoiceClient,
physical left-button state, pointer inside the hold surface, focus and the live one-second lease.
Release, leaving the button, focus loss, authentication/route loss, mute/mode change and GUI close
fail closed. A stale screen close cannot cancel a successor lease. Physical PTT remains independent.
The hub was compacted to fit Settings/Group, Player Voices/Quality, Touch and mute/deafen controls.
Long hints use FontRenderer wrapping. Touch strings are present in English, Brazilian Portuguese and German.
The one-second lease is policy, not a latency measurement or a guarantee about dispatched datagrams.

## Executed evidence
- touch-desktop-final: actual JDK8 compiled 144 production/QA sources and 25 touch contracts PASS.
- touch-legacy-final: complete Forge build PASS.
- touch-java8-final: 32 smoke-main executions PASS using the actual Java8 runtime.
- touch-platform-final: 124 JUnit tests in 27 suites, zero failures/errors/skipped.
- touch-packaged-final: 32 inherited/new smoke replays plus one production class-origin check PASS from DEV jar.
- touch-mutations-final: 11 compiled defective variants detected by runtime assertions.
- CLIENT17_UI_STATIC_CHECKS.json: 15 strings in each of en_US/pt_BR/de_DE; hub wiring and release/focus/route guards PASS.
The first mutation run intentionally remains as evidence: one behaviorally neutral mutation survived,
so it was replaced by an actually defective failed-sustain/no-close mutation without weakening tests.
No physical microphone, Pojav/Amethyst device, rendered Minecraft GUI or acoustic listening test ran here.

Mod SHA256: 6635c5fcec04ca8a02e2582e8241e2de2802166513c9de52f20b4eb9993a1dbc.
DistributionJarSmoke PASS: 283 Java8 classes, 5 JNI classes, 7 native binaries and required licenses.
Gateway SHA256: 8f1eb303384fa5419cd1b221d87bc2ea0421afa155f4dd7d03625d28dd8d1a65.
Paper SHA256: e5919e987cf006e126b4acbe1caa9b959a5dc9f8b6d34877c197405347b0517a.
Final monitored hashes: 282 active and 327 candidate inputs, zero mismatches. No build lock remained.
No commit, tag, active-source promotion, distribution replacement or server deployment occurred.

## Boundary
This closes the desktop user-facing implementation and automated runtime gates for touch PTT,
not mobile-device approval. Real touch-event semantics, focus transitions, GUI appearance and
physical microphone/gameplay behavior must still be tested on supported launchers/devices.
