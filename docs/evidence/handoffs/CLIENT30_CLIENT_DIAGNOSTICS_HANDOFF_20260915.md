# CLIENT30 client diagnostics baseline

Checkpoint: `AV-CLIENT-DIAGNOSTICS-20260915-30`.
State: **PASS / automated baseline**.
Tree: `audit/group-integration-20260913-04/integrated-client30`.
Baseline: CLIENT28 decoder-cap hardening.

## Production delta

Eight source/resource files differ from CLIENT28:
- `VoiceClient.java`
- `VoiceClientHolder.java`
- new `VoiceClientStatusSnapshot.java`
- `GuiVoiceChatHub.java`
- `en_US.lang`, `pt_BR.lang`, `de_DE.lang`
- QA contract `ClientDiagnosticsSmoke.java`

The snapshot contains only aggregate client state: presence/authentication, fresh-bootstrap flag,
received-frame count, active decoder count, decoder capacity and capacity-drop count.
No UUID, session id, endpoint, secret, key material or crypto object is exposed.
## UI behavior

The Voice Chat hub now shows aggregate transport state (`offline`, `connecting`, `refreshing`, `online`),
frames received, active decoder streams versus the 256-stream cap, and capacity drops.
A non-zero drop count is highlighted without per-frame logging or identity rendering.

## Executed evidence

- Forge/legacy build after `spotlessApply`: PASS, 38 actionable tasks.
- Focused diagnostics on actual JDK 8: PASS.
- Full actual-JDK8 matrix: PASS, 160 compiled sources, 43 command steps.
- Full platform: PASS, 149 JUnit tests / 36 suites, zero failures/errors/skips.
- Packaged DEV JAR replay: PASS, 43 executions with production classes loaded from the JAR.
- Diagnostic mutation suite: PASS, 4/4 deliberate faults detected.
- Static privacy/scope audit: PASS, 22/22 checks.
- Reobfuscated distribution smoke: PASS, 296 Java-8 classes, 5 JNI entries, 7 native bundles,
  5 notices and 33 license inventory entries.

DEV JAR SHA256: `a17ec91800a4d22dc60c8d84081f325b2b0d9493a2ed6d251b802be853935069`.
Reobfuscated JAR SHA256: `95ae2877e60a79998b062111e62386dd2c0833a52aad4f75f59e3da337aef22d`.
Gateway SHA256: `8dd7a2ee5285ec66b300f257e71e660cc3441ed98837b2cbcb176021cab373ad` (byte-identical to CLIENT28).
Paper SHA256: `b5e66814edfd8bfb1b197d786ddb9da727a75e9940dad7ca44da9b92b2c21bdb` (byte-identical to CLIENT28).
Addon API SHA256: `6e73a1f96d96251cc631f6a51e4a1e485022257f1cf0a517dc7781e8ff5de347` (byte-identical to CLIENT28).

No production deployment, real-WAN voice, physical microphone/speaker validation or mobile-device validation was performed.
Planning estimate after this checkpoint: approximately **85%**, with a judgment band of roughly 80-90%.
The increase is limited to operational observability; it does not close the live-device gates.
