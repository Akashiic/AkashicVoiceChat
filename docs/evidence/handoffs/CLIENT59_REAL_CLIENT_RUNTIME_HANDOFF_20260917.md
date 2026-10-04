# Akashic Voice - CLIENT59 real client runtime evidence

Checkpoint: `AV-REAL-CLIENT-RUNTIME-EVIDENCE-20260917-59`.
Release under observation remains sealed RC5 from CLIENT47; no JAR or production server was changed.

A real Technic Forge 1.7.10 Forbidden Series client was already running on the authorized desktop.
Its installed `mods/akashicvoice-forge-1.7.10.jar` SHA-256 is exactly the sealed RC5 Forge hash:
`6fab0865c2d2c5a0e382e193f35e099aa7e0861ab4317b0c78a91796ef8d553e`.

FML runtime evidence identifies `dev.akashic.voice.AkashicVoice` as the mod main class and loads the
Akashic Voice resource pack. No Akashic Voice ERROR/FATAL match was found in the current FML client log.
A read-only JDK8 thread dump of the live Minecraft JVM observed all four production runtime threads:
receive, maintenance, audio playback and microphone capture.
The microphone thread was blocked in production `OpenALCaptureBackend.Device.read`; no sample bytes were read,
recorded or inspected by the assistant. In the sealed source, `startMicCapture()` refuses to start unless
`running && authenticated`, and the authentication path starts capture after secure-session acceptance.
Therefore this is evidence that a real RC5 client reached an authenticated voice runtime.

This checkpoint does **not** prove bidirectional intelligibility, remote packet reception, spatial correctness,
acoustic quality, echo/clipping behavior, backend transfer/restart recovery, two-client operation or mobile behavior.
No GUI input was synthesized and no microphone/audio contents were captured.

External reachability from the same desktop was also observed for the configured public Gateway/Agent TCP ports.
SSH transport to an existing server host was reachable, but public-key/agent authentication was unavailable;
no password was attempted and no server filesystem/process was changed.

Progress remains approximately 98% (96-99% band): this closes a real-client load/authentication sub-gate,
but the completion boundary still requires two-client WAN/listening, transfer/restart, physical acoustic evidence,
real Pojav/Amethyst device evidence and the controlled production canary/operator-player review.

A sustained read-only runtime sample then checked the live JVM seven times at 20-second cadence over 124 seconds.
All seven samples found the Minecraft process alive and all four Akashic Voice runtime threads present.
A focused current-log scan found zero matches for voice-server timeout, microphone-open failure, capture failure,
handshake reset or fresh-bootstrap warning during the inspected run.

The public host also answered TCP reachability checks on the configured port numbers, but that observation does not
validate AV UDP reachability and is not counted as a voice-network PASS. A real server-side canary still requires
status receipts from the deployed Gateway/Agent plus the two-client listening/transfer/restart procedure.

Machine-readable evidence: `CLIENT59_REAL_CLIENT_RUNTIME_20260917.json` and
`CLIENT59_SUSTAINED_RUNTIME_RECEIPT.json`. Progress remains 98%; this is real-runtime evidence, not a stable release.
