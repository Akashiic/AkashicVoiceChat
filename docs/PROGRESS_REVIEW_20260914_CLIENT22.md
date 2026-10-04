# Akashic Voice - weighted progress review after CLIENT22

Reference estimate: **76% of the complete product**, judgment band **71-81%**.
This is a planning estimate, not percent of tests, files, hours or production safety.
Latest fully automated-gated technical baseline: audit/group-integration-20260913-04/integrated-client22.
No source promotion, deployment or stable-release declaration is implied.

| Area | Weight | Points | Current boundary |
| --- | ---: | ---: | --- |
| Foundation, runtime separation, secure protocol | 25 | 24 | Explicit session ID, MTU, metrics, replay/rate limits implemented; live deployment/security soak remains |
| Client audio, controls, UX and performance | 25 | 22 | Codec, recovery, gain, per-speaker controls, presets and touch PTT implemented; measured FEC/DSP/device validation remain |
| Gateway, SVC and transfer lifecycle | 20 | 17 | Secure Gateway/SVC/epochs/local integration strong; live multi-client routing/outage validation and semantics remain |
| Groups, social functions, permissions and API | 15 | 9 | Federated groups and legacy invite lifecycle implemented; radio, central moderation/permissions and public API remain |
| Real compatibility, mobile and release validation | 15 | 4 | Automated/JAR/native-virtual evidence exists; physical/gameplay/mobile/prolonged release gates remain |
| **Total** | **100** | **76** | **Not production-approved** |

## Evidence basis
- CLIENT22_SESSION_ID_HANDOFF_20260914.md and CLIENT22_QA_SUMMARY_20260914.json.
- Final Gradle gates explicitly disabled configuration cache after a stale-artifact incident was detected.
- Actual Java 8 compiled 155 sources; 38 smoke mains plus one extra backend execution passed.
- Platform: 126 JUnit tests in 28 suites, no failures/errors/skips.
- DEV JAR replay: 40 executions PASS with production-class origin verification.
- Eight compiled session/MTU/bootstrap mutants were detected by the targeted assertions.
- CLIENT21 remains historical predecessor; CLIENT22 also contains its legacy-invite hardening.

The 76% estimate credits completed engineering, not live-production proof. The last quarter is disproportionately
weighted toward integration reality: real clients, audio perception, mobile devices, prolonged sessions,
permissions/moderation/radio scope and final release operations. It should not be read as "24% of time remains".
