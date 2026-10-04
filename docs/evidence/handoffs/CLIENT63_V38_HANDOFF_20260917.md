# CLIENT63 / V38 handoff — 2026-09-17

## Current authority

Automated release candidate: `release-candidate-20260917-rc7`.

Checkpoint: `AV-PREMIUM-HARDENING-RC7-20260917-58`.

Forge SHA-256: `c0f9d86cb331598c2e0e7c9d7ebf6091fffee18bc94bbdaf6bbf9cbe6d2eb081`.

Homologation tooling: `AV-PREMIUM-LIVE-ACCEPTANCE-V38-20260917-63`.

Bundle: `akashic-voice-rc7-homologation-v3.8-20260917.zip`.

Bundle SHA-256: `497273cdd235b33e6678604fcc585b49c04fb966c64f36400d159c99ddca1c2e`.

RC7 and the V38 bundle are sealed, not deployed and not approved stable.
## Automated evidence

RC7 release engineering:
- Forge build PASS.
- Actual Java 8 matrix: 184 sources / 54 commands PASS.
- Platform: 43 suites / 186 tests / zero failures/errors/skips.
- Source mutations: 5/5 detected.
- Compiled mutations: 4/4 detected.
- JAR-origin: 9 production classes / 7 replays PASS.
- Seal: 43/43 PASS.
- Independent sealed verification: 58/58 PASS.

V38 tooling:
- Preseal coherent synthetic evaluator PASS.
- Five premium negative controls HOLD as expected.
- Bundle seal: 59/59 PASS.
- Extracted full workflow smoke: 31/31 PASS.
## Premium feature state

- Premium 3D positional voice: automated complete; real two-client listening open.
- Environmental acoustics: automated complete; physical outdoor/room/cave/underwater listening open.
- Talking nametag suffix: automated complete; real-game visual acceptance open.
- Output hot-plug recovery: automated complete; physical AirPods/headset unplug/replug open.
- Local PTT indicator latency: automated complete; live human visual acceptance open.
- Remote talking indicator intentionally begins on the first authenticated audio frame.

Expanded-scope progress remains 97% (judgment band 95–98.5%). Do not increase this for additional synthetic tests.
## Required live order

1. Prepare and verify rollback material.
2. Dry-run the RC7 canary installer.
3. At an explicit maintenance boundary, perform the controlled RC7 canary install.
4. Verify installed hashes before starting evidence collection.
5. Create a unique V38 canary session and bind runtime paths/hashes.
6. Capture Baseline.
7. Run real two-client speech, backend transfer and controlled backend restart.
8. Perform physical output hot-plug while remote voice is audible.
9. Perform outdoor/room/cave/underwater listening plus general acoustic review and record the AGC/limiter decision.
10. Review normal/sneaking/sleeping nametag suffix and local/remote PTT visual behavior.
11. Run real Pojav and Amethyst acceptance.
12. Capture Cleanup and finalize the V38 evidence chain.
13. Only after every gate passes may the result enter stable-review; this is still not automatic deployment approval.

Do not replace any missing live observation with synthetic QA.
