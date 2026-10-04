# Cross-server radio

CLIENT25 adds an ephemeral, proxy-authoritative radio layer to Akashic Voice.
It does not change the wire protocol and does not inject addon callbacks into the media hot path.

## Player commands

- `/avradio status`
- `/avradio join <channel>`
- `/avradio tune <channel>`
- `/avradio leave`

Permission: `akashicvoice.radio.use`.

Channel names are normalized to lowercase ASCII and may contain only `a-z`, `0-9`, `_`, and `-`.
Length is 1-24 characters.

## Hard bounds

- 128 simultaneous channels.
- 64 tuned players per channel.
- 4096 tuned players globally.
- One tuned channel per player.

State is session-ephemeral at the proxy. Backend transfers preserve tuning; a real proxy disconnect removes it.
## Routing semantics

Radio is an additional direct-voice relation, not a replacement for normal proximity to outsiders.
For each microphone frame the Gateway still forwards the speaker frame to the owning backend agent.

For direct recipients, the effective relation is `group union radio`.
If two players share both, group routing wins and only one direct frame is sent.
SVC delivery/reset for a pair is suppressed while that pair has an active direct relation.

Radio frames are non-spatial and preserve the authenticated sender identity, transport sequence, and raw Opus bytes.
Group and radio counters remain separate (`groupFramesSent` and `radioFramesSent`).

Joining, leaving, or switching a radio performs pair-scoped hard resets only when the effective direct relation changes.
If a group still relates the same pair, radio changes do not create unnecessary resets.

## Hot-path policy

Media routing reads immutable radio snapshots.
No radio mutation lock, channel copy, listener callback, persistence I/O, or temporary recipient-set construction occurs per audio frame.
Mutations publish new bounded snapshots outside the frame path.