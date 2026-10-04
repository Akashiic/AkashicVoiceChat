# Akashic Voice - weighted progress review

Reference estimate: **70% of the complete product**, judgment range **65-75%**.
This is a planning estimate, not a measured fraction of test cases, elapsed time or remaining hours.
Rubric retains the five weights used in the earlier ~60% estimate (whose point sum was 61).
The update reflects accumulated validated candidates through CLIENT14, not a ten-point gain from CLIENT15.
CLIENT15 source/core tests do not receive additional completed-delivery credit before their full gates pass.
The latest full-built tree remains audit/group-integration-20260913-04/integrated-client14.

| Area | Weight | Estimated points | Evidence / remaining boundary |
| --- | ---: | ---: | --- |
| Foundation, runtime separation, secure protocol | 25 | 22 | Java8/build/crypto gates; deployment identity and transport hardening remain |
| Client audio, controls, UX and performance | 25 | 20 | Native/Java codecs, stream recovery, isolated gain, per-player panel; remaining audio and live UX gates |
| Gateway, SVC and transfer lifecycle | 20 | 16 | Compiled bridge, controller and epoch transport tests; actual cross-client/network deployment validation remains |
| Groups, social functions, permissions and API | 15 | 8 | Groups/persistence/controls implemented; radio, public addon API and broader moderation are not approved |
| Real compatibility, mobile and release validation | 15 | 4 | Binary/source checks and existing automated/native virtual evidence; physical/gameplay/mobile/release gates remain |
| Total | 100 | 70 | Not production-ready and not a stable release |

## Basis
- docs/AUDIT.md: Gates 0/1/4 and partial identity/audio/performance/live gates.
- docs/ROADMAP.md and README.md: full product scope, including mobile, radio/API and release testing.
- audit/group-integration-20260913-04/CLIENT14_SPEAKER_PANEL_HANDOFF_20260913.md.
- CLIENT14_FINAL_QA_SUMMARY.json and original run receipts in runs-integrated14.
- CLIENT11_VOICE_GAIN_HANDOFF_20260913.md: gain isolation and limited native virtual rendering.
Historical roadmap wording may lag candidate code; these newer handoffs distinguish implementation from approval.

A score increase is not a substitute for active-source promotion, real-device validation, or deployment approval.
Do not infer that 30% of the elapsed development time remains, or that the project is 70% safe for production.
