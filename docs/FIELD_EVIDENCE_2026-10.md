# Field evidence — Technic client logs, 2026-09-15 → 2026-10-03

**Grade:** LIVE-OBSERVED (client logs). **Scope:** one workstation, one modpack. **Author's confidence:** observations high; causal explanations are hypotheses unless stated.

This is the first review of real play logs against the automated record. The automated gates (loopback, fake hardware) passed; the field logs show failure modes those gates cannot reach. No code was changed as part of this review.

## 1. Method and limits

- Source: `latest.log` plus 153 rotated `.log.gz` files of the Technic *Forbidden Series* client, 2026-08-19 → 2026-10-03 (154 files).
- Filter: lines mentioning `AkashicVoice` / `Akashic Voice` / `dev.akashic.voice`, then manual reading of context in three sessions (`2026-09-26-4`, `2026-10-02-1`, `latest.log`) plus targeted extraction of out-of-memory signatures.
- Never read: audio content, session secrets, bootstrap material. Only log text.
- Counts are **log lines**, not distinct incidents. Rotated logs are not guaranteed to be contiguous.
- Environment seen in the logs: client JVM 1.8.0_51, LWJGL 2.9.1, paulscode SoundSystem, ArchaicFix `SoundDeviceThread` ("Default Sound Device Checker"), NecroTempus modern-font support, microphone `Microfone (WO Mic Device)` via OpenAL Capture.
- Build identity: all sessions carry the shared label (see [`VERSIONING_AND_PROVENANCE.md`](VERSIONING_AND_PROVENANCE.md)); RC7 is inferred from the installed file timestamp.

## 2. Summary

| Metric | Value |
|---|---|
| Logs scanned | 154 |
| First log mentioning the mod | `2026-09-15-1` (resource-domain error only) |
| Sessions with the `preInit (version …, serverMode=direct, voicePort=24454)` line | 39 (`2026-09-23-7` → `latest.log`) |
| Sessions that authenticated at least once | 29 of those 39 |
| `Secure voice session authenticated` | 138 |
| `Microphone capture started` | 203 (backend `OpenAL Capture`, 48 000 Hz, 960-sample frames) |
| `Voice server timed out; restarting secure handshake` | 32 lines, 19 sessions |
| `Voice secure rehandshake failed; requesting a fresh bootstrap` | 4 |
| `Voice output recovery requested: DISCONNECTED (ownsOpenAL=false)` | 24 |
| `Voice output recovery requested: UNAVAILABLE (ownsOpenAL=false)` | 5 |
| `OpenAL EFX probe failed; environmental voice acoustics disabled` | 5 (sessions `09-27-5`, `09-27-6`, `10-02-1` ×3) |
| `Audio frame failed` | 38 (`09-26-4`: 26, `09-27-1`: 12) |
| `Voice listener pose update failed; retrying` | 1 (`10-03-1`) |
| `UnsatisfiedLinkError` attributed to an AkashicVoice thread | 27 sessions, 85 header lines |
| `Failed to read modern fonts from domain akashicvoice` | 41 sessions (one per launch) |
| Whole-client `OutOfMemoryError` signatures | 5 sessions (`09-25-1`, `09-25-3`, `09-25-4`, `09-26-1`, `09-26-3`) |

Positive findings: the secure handshake authenticates routinely, OpenAL Capture starts and EFX initialises ("OpenAL EFX enabled for environmental voice acoustics"), and the client recovers from voice-server timeouts by re-authenticating (observed 6–11 s later).

## 3. Findings

### F1 — OpenAL natives become unlinked after a sound-system reload (issue #3, severity: high for the premium path)

Pattern, verified by hand in three sessions:

```text
[AkashicVoice-Audio/WARN]: Voice output recovery requested: DISCONNECTED (ownsOpenAL=false)
[Client thread/INFO]: SoundSystem shutting down...
[Default Sound Device Checker/INFO]: Sound device is not valid anymore, reloading sound system   (10-02-1)
[AkashicVoice-Mic/...]: java.lang.UnsatisfiedLinkError: org.lwjgl.openal.ALC10.nalcGetError(J)I   (09-26-4)
        at ...OpenALCaptureBackend$Device.availableSamples(OpenALCaptureBackend.java:170)
[AkashicVoice-Audio/WARN]: Audio frame failed
java.lang.UnsatisfiedLinkError: org.lwjgl.openal.AL11.nalSource3i(IIIII)V          x25 (09-26-4)
        at ...PlaybackChannel.setEnvironmentEffects(PlaybackChannel.java:128)
[AkashicVoice-Audio/WARN]: OpenAL EFX probe failed; environmental voice acoustics disabled
java.lang.UnsatisfiedLinkError: org.lwjgl.openal.EFX10.nalEffecti(III)V            (10-02-1, 3×)
        at ...VoiceEnvironmentEffects.create(VoiceEnvironmentEffects.java:40)
```

Effects seen: microphone thread dies and capture restarts (10-02-1: 12 UnsatisfiedLinkError headers, 14 capture starts, 2 authentications); 25 consecutive per-frame playback failures in one session; EFX acoustics disabled until restart; noisy traces. At game exit (`latest.log`, 21:11:30) the same mic-thread trace follows "SoundSystem shutting down…".

*Hypothesis H1 (unverified):* LWJGL 2.9.1's `AL.destroy()` resets the native stubs for AL10/AL11/ALC10/ALC11/EFX10, so any AL/ALC call made between the destroy and the next `AL.create()` raises `UnsatisfiedLinkError`. Being an `Error`, it passes `catch (Exception)` handlers. `ownsOpenAL=false` indicates the OpenAL context is shared with Minecraft's SoundSystem, so Minecraft reloading it (triggered by ArchaicFix's device checker or by Akashic's own "controlled sound reload") invalidates Akashic's bindings mid-flight. To verify: read LWJGL 2.9.1 `AL.java`, then reproduce with a Java 8 harness that calls `AL.destroy()/create()` while capture and playback threads poll.

*Suggested direction (not implemented):* gate every AL/ALC call on `AL.isCreated()` plus a context-generation token; catch `LinkageError` at the capture, playback and EFX boundaries and park until the context is re-created; re-run EFX probe on every new context; add a regression harness for destroy/create under load.

### F2 — Voice-server timeouts around backend transfers (issue #4)

32 timeout lines in 19 sessions; 4 failed rehandshakes. Sampled sequences coincide with server (re)connection (`FMLModIdMappingEvent` phase right after the timeout, `09-26-4` 18:02:47; Netty `NetworkDispatcher exception` at 19:13:02) and recover in 6–11 s (`latest.log`: 20:56:15 → 20:56:26). In direct mode each backend has its own `VoiceServer`, so the old endpoint stops answering after a transfer and the client waits for the timeout before requesting a fresh bootstrap. *Hypothesis H2:* the client could rebootstrap on the Forge connection/transfer event instead of waiting; the Gateway already pushes a rebootstrap server-side (CLIENT61), which is not in use in direct mode.

### F3 — Output-recovery events in ordinary sessions (issue #5)

29 recovery requests (24 DISCONNECTED, 5 UNAVAILABLE) across sessions, several per session on some days, always with `ownsOpenAL=false`. Some coincide with a genuine `Sound device is not valid anymore` message from ArchaicFix. Whether each is a real device change (USB/Bluetooth/virtual mic) or a false positive from `ALC_EXT_disconnect` is **not established**. In the sampled logs the RC7 hot-plug recovery was not seen completing cleanly (in `10-02-1` EFX acoustics were disabled right after it); it needs a controlled physical test (gate G6) and a decision on coordination with ArchaicFix's checker.

### F4 — Missing `modern_fonts.json` (issue #6, severity: cosmetic)

`[ERROR] Failed to read modern fonts from domain akashicvoice … FileNotFoundException: akashicvoice:glyphs/modern_fonts.json` once per launch. The stack trace originates in NecroTempus `ModernFontSupport`, which probes every resource domain. Harmless, but it puts an ERROR with a stack trace in every log. Shipping an empty/valid `assets/akashicvoice/glyphs/modern_fonts.json` would silence it (check NecroTempus' expected schema first).

### F5 — Audio-thread out-of-memory signature (issue #7)

`2026-09-26-4`, 19:20:01, `[AkashicVoice-Audio/WARN]: Audio frame failed — OutOfMemoryError: Java heap space` at `ArrayDeque.add ← PlaybackChannel.recycleProcessed(PlaybackChannel.java:231) ← PlaybackChannel.enqueue`. The same days contain whole-client OOM crashes (`Could not save crash report … OutOfMemoryError`, Distant Horizons threads, five sessions), so the audio thread is probably a bystander of general heap exhaustion. Still, the 128-frame PCM pool is documented as bounded; `recycleProcessed` should be audited to confirm the deque cannot grow beyond the pool.

### Not Akashic Voice

`DiscordIntegrationThread … UnsatisfiedLinkError … jna.Native` (Iron Dome / DiscordIntegration), `Frame pacing` and `Baubles cache` messages, Distant Horizons OOM — present in the same logs, unrelated to this mod.

## 4. What these logs do not show

Audible quality, remote frames received, two-client operation, mobile, Gateway/Paper behaviour (every session is `serverMode=direct`), or whether RC7's premium acoustics sound correct. They also cannot say how many of the recoveries were physical device changes.

## 5. Next steps

1. Triage #3 first (it blocks meaningful G6 and probably explains the capture restarts), then #5 and #4.
2. Add a destroy/create stress harness to `tools/qa/` so the next candidate is gated on it.
3. Keep logs of each future live session under a unique label with the JAR SHA-256 beside them, so build identity is no longer inferred.
4. Re-run this scan after fixes: expect zero `UnsatisfiedLinkError` headers from AkashicVoice threads and zero `EFX probe failed`.

## 6. Addendum — real Android device runs (Forbidden Mobile Launcher engineering QA)

**Grade:** LIVE-OBSERVED on a real device. **Source:** 59 logs under the launcher's `qa/device` folder (package `br.com.redeakashic.forbiddenmobile.engineering.qa`, runs labelled `018-*`, `0181-*`, `crash-update-menu-20260921-*`, around 2026-09-21). The managed-pack hash list of one run (`managed-sha256.txt`) records `c0f9d86c…` — the RC7 Forge JAR — in `mods/`. The runtime is LWJGL 3 based (`org.lwjgl.system.MemoryStack` in traces), unlike the desktop client (LWJGL 2.9.1).

**This corrects an earlier statement:** the roadmap and RC7 manifest list "Pojav/Amethyst real device" as having no evidence. There is partial evidence — the mod loads, authenticates a loopback session and degrades gracefully in places — but it is mostly **negative** for microphone capture.

| Observation (distinct log templates, counts across runs) | Count |
|---|---:|
| `Akashic Voice preInit (version …, serverMode=direct, voicePort=24454)` | 24 |
| `Secure voice server bound to UDP port 24454` / `Issued voice bootstrap` / `Received voice bootstrap (session-bound=true)` / `Voice client started, server=127.0.0.1:24454` | 11 / 10 / 10 / 10 |
| `Secure voice session authenticated` (client) and `Secure voice auth ok … active connections: N` (integrated server) | 10 / 10 |
| `OpenAL EFX unavailable; environmental voice acoustics disabled` | 10 |
| **`Microphone unavailable: No audio capture backend could open a microphone`** | **10 (every authenticated session)** |
| **`AkashicVoice-Audio` thread killed by `OutOfMemoryError: Out of stack space`** | **1** |
| `Failed to read modern fonts from domain akashicvoice` (NecroTempus) | 21 |

### F6 — No capture backend could open a microphone (issue #18)
Every on-device session that authenticated logged the warning above, so no capture started in any of them. The logs do not say whether this is the missing Android `RECORD_AUDIO` permission in the QA build, OpenAL Capture being unavailable in the runtime, or both. Either way it is the strongest evidence so far for the roadmap item currently marked CONDITIONAL ("AudioRecord/Oboe fallback — only if the real-device matrix demonstrates a need"): on these runs it does.

### F7 — RC7 output-health poll kills the playback thread on an LWJGL 3 runtime (issue #17, severity: high on mobile)
```text
Exception in thread "AkashicVoice-Audio" java.lang.OutOfMemoryError: Out of stack space.
    at org.lwjgl.system.MemoryStack.nmalloc(MemoryStack.java:322)
    at org.lwjgl.system.MemoryStack.stackUTF8(MemoryStack.java:1098)
    at org.lwjgl.openal.ALC10.alcIsExtensionPresent(ALC10.java:520)
    at ...playback.OpenAlOutputHealth.defaultDeviceName(OpenAlOutputHealth.java:131)
    at ...playback.OpenAlOutputHealth.poll(OpenAlOutputHealth.java:56)
    at ...playback.AudioPlayback.pollOutputHealth(AudioPlayback.java:616)
    at ...playback.AudioPlayback.loop(AudioPlayback.java:581)
```
Observed once (`018-biome-resume-before`): the client authenticated at 07:37:31 and the audio thread died at 07:49:02 (11 min 31 s later); the thread is not restarted, so playback is gone for the rest of the session. *Hypothesis H3 (unverified):* each health poll calls `alcIsExtensionPresent` through the LWJGL 2 → 3 compatibility layer, which allocates on the thread-local `MemoryStack` without releasing it, until the fixed stack is exhausted. The same polling code runs on desktop (LWJGL 2), where it has not shown this failure, which is why the desktop logs and the automated gates never saw it. The health monitor is an RC6/RC7 feature (`OpenAlOutputHealth`), so earlier candidates are not expected to be affected.

*Suggested direction (not implemented):* poll `ALC_EXT_disconnect` support once per context rather than per cycle, cache `defaultDeviceName`, wrap each poll in a stack push/pop on LWJGL 3, catch `Throwable` around the whole poll so a health-check failure can never terminate the playback loop, and restart the loop on unexpected exit.

### F8 — EFX unavailable path is graceful (positive)
`OpenAL EFX unavailable; environmental voice acoustics disabled` is logged once per session and playback continues, which is the intended degradation on devices without EFX.

### Cross-environment note on F1
`akashic-index-checkpoints/stutter-20260921-011455/fml-last64k.txt` (a different instance, 2026-09-20/21) also contains an `AkashicVoice-Mic … UnsatisfiedLinkError`, so F1 is not specific to the Technic client or to one day.
