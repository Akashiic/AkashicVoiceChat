# RC7 premium live acceptance v3.8

The automated RC7 gates are already complete. This procedure exists only for observations that cannot be proven by compilation, synthetic OpenAL tests, thread dumps, or source inspection.

Use two real clients for voice/listening tests. Do not enter synthetic QA observations into a real release-evidence run.

## Physical output hot-plug

While remote voice is actively audible, disconnect the current output device (for example AirPods/headset), then reconnect it or allow the operating system to select the intended replacement.

Record `device_before` and `device_after`. Confirm the disconnect and reconnect were physically observed, voice resumed without leaving/rejoining the Minecraft server, Minecraft audio also recovered, and no crash, hang, duplicate or ghost voice occurred.

A driver silently migrating devices is acceptable only when actual audible output continues/reappears correctly; a healthy status counter alone is not physical evidence.
## Environmental voice acoustics

Use the same remote speaker and comparable speech while the listener moves through:
- outdoor/open-sky space;
- compact indoor room;
- cave/large enclosed space;
- underwater.

Confirm outdoor remains dry, room/cave effects are audibly distinct, underwater is appropriately filtered, transitions do not leave stale effects or objectionable pops, and intelligibility remains acceptable.

Also test group/radio or another explicitly non-spatial route while standing in a cave. It must remain dry; environmental reverb must not leak into non-spatial voice.

The existing general acoustic block still covers clipping, pumping, echo, background noise and intelligibility. After physical listening, set `agc_limiter_decision` to exactly `NOT_NEEDED` or `IMPLEMENTED_AND_ACCEPTED`. Any other value is HOLD.
## Talking nametag suffix

Observe another actively speaking player while standing normally, sneaking and sleeping.

Confirm the talking icon behaves as a pseudo-suffix of the rendered nametag, follows formatted-name width, and never appears at the feet, world origin or another detached transform.

Record visual notes. Source-level coordinate tests do not substitute for this gate.

## PTT indicator timing

On the transmitting client, repeatedly press and release physical keyboard/mouse PTT. Confirm local press and release have no perceptible UI delay.

On the receiving client, confirm remote start/release behavior is acceptable. Remote start is intentionally driven by the first authenticated audio frame rather than an unauthenticated TALK_START message, so the live gate evaluates perceptual behavior rather than demanding impossible zero-network latency.

Record notes for both clients.
## Stable-review boundary

V3.8 may return `READY_FOR_STABLE_REVIEW` only when every legacy canary gate and every RC7 premium gate passes.

That decision is still not a deployment command and is not automatic stable-release approval. The production canary, operator/player acceptance and any organization-specific release approval remain separate actions.

Never edit objective runtime counters, artifact hashes, provenance blocks or stage receipts to make a run pass. The session finalizer revalidates those fields and should fail closed on drift.
