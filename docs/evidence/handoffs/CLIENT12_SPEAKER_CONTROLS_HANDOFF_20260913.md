# Akashic Voice - CLIENT12 speaker-control core and compressed-audio boundaries

Checkpoint: AV-SPEAKER-CONTROLS-20260913-12.
Candidate: audit/group-integration-20260913-04/integrated-client12.
Status: core built/tested; individual-player GUI is NOT delivered; not a stable release.
Active production sources, CLIENT11 and existing distribution were preserved.

## Delivered implementation
SpeakerControls is a bounded, session-local, UUID-indexed control directory (maximum 4096 entries).
Individual volume is 0..100 percent of the current voice master; mute retains the selected level.
Immutable snapshots are published to the audio worker; UI/API writers do not call native audio.
PCM captures the sender/control identity before decoding. Muting and rapid unmuting invalidate
old acquired/queued PCM and retire only the affected native channel. Positive volume changes
keep the listening identity. Empty/invalid IDs or out-of-range updates are rejected.
Defaults and muted tombstones are retained for the playback session, not saved across servers.
The decoder continues consuming muted audio into its discard buffer rather than rewinding state.
No network protocol, server module, codec, native gain policy or dependency was changed.

## Additional reproduced defect and correction
The initial core passed 21 tests but an expanded test exposed compressed audio still held in jitter.
That audio could reappear after an individual mute or global deafen/un-deafen cycle.
The decoder now drains the bounded old encoded backlog to discard PCM on control-generation
changes, preserving its decoder object and advancing its sequence, before accepting fresh audio.
The global defect was also reproduced against the original CLIENT11 DEV JAR: two failures.
Both global cases pass against CLIENT12, as do all 24 individual-control cases.
The extra timestamp releases jitter slots synchronously; it does not sleep or schedule future work.

Production delta: AudioPlayback.java, PlaybackFrame.java, DecoderStream.java and new SpeakerControls.java.
