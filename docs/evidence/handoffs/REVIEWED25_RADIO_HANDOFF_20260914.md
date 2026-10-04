# CLIENT25 reviewed handoff - cross-server radio

Checkpoint: `AV-CROSS-SERVER-RADIO-20260914-25`
State: **VERIFIED automated baseline**.
Reviewed tree: `audit/group-integration-20260913-04/reviewed-client25-20260914`.
Source origin: `integrated-client25`; final source identity check passed with 369 audited files and zero changes.
The active project tree and production distribution were not promoted or replaced.

## Delivered

- Proxy-authoritative ephemeral cross-server radio.
- `/avradio status`, `/avradio join <channel>`, `/avradio tune <channel>`, `/avradio leave`.
- Permission: `akashicvoice.radio.use`.
- Bounds: 128 channels, 64 members/channel, 4096 tuned players, one channel/player.
- Normalized ASCII channel names, 1-24 chars (`a-z`, `0-9`, `_`, `-`).
- Backend handoff preserves tuning; real proxy disconnect removes it.
- Permission/offline pruning runs on the existing one-second Gateway maintenance cadence.

## Media semantics

Effective direct relation is `group union radio`.
Group wins when both relations exist, preventing duplicate direct frames.
Radio-only audio is non-spatial and preserves authenticated sender identity, transport sequence and raw Opus bytes.
SVC delivery/reset for a pair is suppressed while that pair has an effective direct relation.
Join/leave/switch uses pair-scoped hard resets only when the effective relation changes.

## Hot-path policy

`onMicFrame` reads immutable radio snapshots directly.
There is no radio mutation lock, `HashSet`, peer-set copy, persistence I/O or addon callback per audio frame.
Group membership and radio membership are iterated separately; the group set is reused for deduplication.
`radioFramesSent` is independent from `groupFramesSent` and SVC counters.

## Final gates

- Reviewed Forge root: PASS, 35/35 tasks executed, no configuration cache.
- Reviewed platform: PASS, 38 tasks, 31 executed.
- JUnit: 151 tests / 36 suites, zero failures, errors or skips.
- Actual JDK8: 155 sources compiled, 40 QA steps PASS.
- DEV-JAR origin/replay: 40/40 PASS.
- Distribution JAR: 295 Java8 classes, 5 JNI classes, 7 natives, 5 required notices, 33 inventory entries.
- Static radio audit: 22/22 PASS.
- Mutation sensitivity: 7/7 compiled mutants detected as test failures.
- Candidate/active changes during the Java8 structured gate: zero.
- Final validation lock: absent.

Artifacts and receipts:
`REVIEWED25_QA_SUMMARY_20260914.json`, `REVIEWED25_ARTIFACTS_20260914.json`,
`REVIEWED25_DIFF_20260914.json`, `CLIENT25_RADIO_STATIC_AUDIT.json`,
and `runs-reviewed25-mutations/status.json`.

## Artifact identity

- Legacy DEV JAR: `8d2b04a191ee8415d77c928c6a4e3b9491e7ce632d3167fdea9d7ca592276936`, class major 52.
- addon-api JAR: `6e73a1f96d96251cc631f6a51e4a1e485022257f1cf0a517dc7781e8ff5de347`, major 65.
- Gateway JAR: `8dd7a2ee5285ec66b300f257e71e660cc3441ed98837b2cbcb176021cab373ad`, majors 52/65/69.
- Paper JAR: `b5e66814edfd8bfb1b197d786ddb9da727a75e9940dad7ca44da9b92b2c21bdb`, majors 52/65.

CLIENT24 -> CLIENT25 audited scope: 6 changed Gateway production files, 2 new Gateway production classes,
2 new Gateway tests, `docs/RADIO.md`, zero removals. Legacy mod, addon-api and Paper artifacts remain byte-identical.

Planning progress after this gate: approximately **81%**. This is not a production/live approval.
Open evidence remains physical audio quality, real multi-player/multi-backend gameplay, mobile/Pojav/Amethyst,
longer live soak and release/deployment approval. Do not replace production distribution from this checkpoint alone.
