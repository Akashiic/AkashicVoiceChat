# Akashic Voice - progress review after CLIENT24

Central planning estimate: **79% of the complete product**.
Judgment band: **74-84%**.
This is not calculated from elapsed time or number of tests.

| Area | Weight | Estimated points | Current basis |
| --- | ---: | ---: | --- |
| Foundation, runtime separation, secure protocol | 25 | 24 | explicit session identity, MTU, replay/rate/security gates; live deployment remains |
| Client audio, controls, UX and performance | 25 | 22 | codec/recovery/gain/controls/touch PTT/quality UI; physical-device UX remains |
| Gateway, SVC and transfer lifecycle | 20 | 18 | agent/SVC, epoch handoff, telemetry and persistent moderation; real multi-server soak remains |
| Groups, social, moderation and addon API | 15 | 11 | federated groups, invite hardening, global mute and public read/event API v1; radio/media API remains |
| Real compatibility, mobile and release validation | 15 | 4 | extensive automated/package evidence; physical/gameplay/mobile/release gates remain |
| **Total** | **100** | **79** | **engineering baseline, not production approval** |

CLIENT24 receives one additional point for a separately consumable, tested and documented addon API v1.
The API deliberately excludes raw frames/media injection, so cross-server radio and media extension remain open.
