# Akashic Voice - CLIENT62 current RC5 runtime freshness

Checkpoint: `AV-CURRENT-RC5-RUNTIME-FRESHNESS-20260917-62`.
This checkpoint changes no production source, config, installed mod or sealed artifact.
It refreshes live evidence against the real Technic Forge 1.7.10 process on 2026-09-17.

## Artifact identity

The installed client mod is `mods/akashicvoice-forge-1.7.10.jar`.
Its SHA-256 is `6fab0865c2d2c5a0e382e193f35e099aa7e0861ab4317b0c78a91796ef8d553e`.
That equals the sealed RC5 Forge SHA recorded by CLIENT59.
The observed Minecraft process was PID 9616, started at 18:05:41 -03:00.
It was still live at the 19:03:18 -03:00 identity check.

## Fresh live evidence

A read-only JDK8 `jstack` showed the production Akashic Voice receive, maintenance,
audio and microphone threads alive in the real client.
`AkashicVoice-Voice-Client-Recv` was inside `DatagramSocket.receive`.
`AkashicVoice-Mic` was inside `OpenALCaptureBackend.Device.read` through `MicCapture.loop`.
The microphone lifecycle can only start while `running && authenticated` in RC5 source,
so this is fresh evidence of an authenticated runtime with UDP receive and physical capture active.

A background `PrintWindow` capture succeeded without changing focus or player state.
Its SHA-256 is `5f05831fd6b32f312ba6a2be43787d71bf6c243aff2999721b4fbc69cad8e3e7`.
A `jmap -histo:live` sample found one live `VoiceClient` and zero active `DecoderStream`
instances at that instant. That sample therefore does NOT prove remote media/listening.

The RC5 source already exposes non-sensitive `receivedFrames`, decoder-stream count and drops
through `VoiceClientStatusSnapshot`, rendered in `GuiVoiceChatHub`.
The effective hub key is O (`key_key.akashicvoice.openSettings:24`), but it conflicts with
contextual NEI/JourneyMap bindings. An attempted foreground switch was rejected by Windows
before any key event was sent, so no UI/game state changed and no bypass was attempted.

A Serviceability Agent helper was tried in read-only mode, restricted to diagnostics fields.
It refused attachment before heap access because the installed SA is HotSpot 25.411-b09 while
the Technic runtime is 25.51-b03. The version check was not bypassed and no heap dump was made.

## Engineering audit

The RC5 source tree was searched for TODO/FIXME/unsupported/not-implemented markers.
No production TODO/FIXME/not-implemented implementation gap was found; the matching
`UnsupportedOperationException` occurrences are test assertions for immutable collections,
and `xxx` is a Gradle template comment.

## Remaining completion boundary

The current client log contains no `Received <n> sound frames so far` marker.
That marker only covers the legacy/V2 handlers; the normal V3 epoch path increments
`receivedFrames` without emitting it, so absence is not evidence of media failure.
No active decoder stream existed in the one live-heap sample.
Therefore this checkpoint does not claim a remote sender was heard or decoded.

Only one Desktop Commander device is authorized in this session. No second real client or
server host is available to this tool, so controlled backend restart and two-client WAN/listening
cannot be orchestrated honestly from here. Physical acoustic review and mobile-device review
also require external/human evidence. Stable release remains false and progress remains 99%.
