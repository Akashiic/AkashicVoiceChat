# Akashic Voice — RC7 Engineering Status — 2026-09-17

## Authority

Latest automated candidate: `release-candidate-20260917-rc7`.

Checkpoint: `AV-PREMIUM-HARDENING-RC7-20260917-58`.

Source candidate: `integrated-client58-premium-hardening-rc7`.

Base sealed candidate: `release-candidate-20260917-rc6`.

RC5 and RC6 remain historical sealed rollback points. Do not overwrite them.

RC7 is **not deployed**, is **not a stable release**, and must not receive live-gate credit from synthetic tests.

## Forge artifact

File: `akashicvoice-forge-1.7.10.jar`.

SHA-256: `c0f9d86cb331598c2e0e7c9d7ebf6091fffee18bc94bbdaf6bbf9cbe6d2eb081`.

Size: 3,213,682 bytes. Classes: 321. Class major: 52 only. Native binaries: 7. JNI classes: 5.
## Automated qualification

- Formatting gate: PASS, no candidate/root drift.
- Forge full build: PASS, 34/34 actionable tasks executed.
- Actual JDK 8 matrix: PASS, 184 sources, 54 commands, no drift.
- Platform regression: PASS, 43 suites / 186 tests / 0 failure / 0 error / 0 skipped.
- Source-contract mutations: PASS, 5/5 detected.
- Compiled behavioral mutations: PASS, 4/4 detected.
- JAR-origin replay: PASS, 9 production classes proven from final RC7 JAR and 7 QA replays.
- Seal preflight: PASS, 43/43.
- Independent sealed verification: PASS, 58/58.
- Production implementation-gap markers: 0.

Gateway, Paper bridge and Addon API artifacts are unchanged from RC6 and retain their sealed hashes.
## User-requested premium features

1. Premium positional 3D voice: automated engineering complete; physical two-client listening acceptance remains open.
2. Environmental acoustics: OUTDOOR / ROOM / CAVE / UNDERWATER with OpenAL EFX, hysteresis and proximity-only routing. Automated complete; physical listening remains open.
3. Talking nametag suffix: vanilla-aligned normal/sneak/sleep/range positioning implemented. Automated complete; real in-game visual acceptance remains open.
4. Speaker/headset hot-plug recovery: ALC disconnect/default-device health, debounce, transparent migration guard, voice quiesce, shared Minecraft sound reload and owned-context retry implemented. Automated complete; physical unplug/replug during live voice remains open.
5. PTT visual latency: raw keyboard/mouse path and immediate local state implemented; 120 ms residual window. Automated complete.

Remote talking visibility remains intentionally driven by the first authenticated audio frame; no unauthenticated TALK_START control was added.
## RC6 → RC7 delta

Added classes:
- `AcousticRoutingPolicy.class`
- `OpenAlOutputHealth$ConnectionState.class`

Changed classes:
- `AudioPlayback$Channel.class`
- `AudioPlayback$ListeningState.class`
- `AudioPlayback.class`
- `OpenAlOutputHealth.class`
- `PlaybackChannel.class`
- `VoiceEnvironmentEffects.class`

Removed entries: none.

The delta is Forge client playback/acoustics only. Wire, Gateway, Paper and Addon API source contracts did not change.
## Remaining live gates

- Real AirPods/headset disconnect and reconnect during active Minecraft voice playback.
- Two real clients over WAN with audible bidirectional speech plus backend transfer/restart recovery.
- Physical cave/room/underwater listening review and explicit AGC/limiter decision.
- Real Pojav/Amethyst device validation: capture, playback, touch PTT, focus/background, thermal and battery.
- Controlled production canary with rollback and explicit operator/player acceptance.

Expanded-scope progress remains **97%**, judgment band **95–98.5%**. This number must not increase merely because more automated checks pass; remaining evidence is predominantly physical/live.
