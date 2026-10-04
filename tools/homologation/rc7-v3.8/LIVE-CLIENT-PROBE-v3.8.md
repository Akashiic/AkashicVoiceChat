# RC7 live-client probe v3.8

`probe-live-client.ps1` is a read-only prerequisite probe for a real Forge 1.7.10 client running sealed RC7.

It does not install, restart, inject into, reconfigure, or send input to Minecraft. It does not read voice payloads, microphone samples, credentials, UUIDs, bootstrap secrets, or process command lines.

The probe requires an explicitly supplied Minecraft PID, installed Forge JAR, sealed expected SHA-256, JDK 8 `jstack.exe`, output JSON path, and optionally the current FML log.

A `PASS_RUNTIME_PREREQUISITES` receipt requires the sealed RC7 Forge SHA and these live threads/locations:
- `AkashicVoice-Voice-Client-Recv`
- `AkashicVoice-Voice-Client-Maintenance`
- `AkashicVoice-Audio`
- `AkashicVoice-Mic`
- receive blocked in `DatagramSocket.receive`
- capture observed in `OpenALCaptureBackend$Device.read`
Because microphone capture starts only after successful voice authentication, this is objective authenticated-runtime lifecycle evidence. It is not evidence that another player was audible and it is not proof of physical output recovery.

V3.8 always leaves the following claims false unless separate live evidence is supplied through the canary evidence chain:
- remote media/listening;
- controlled backend restart;
- physical AirPods/headset disconnect/reconnect;
- physical environmental-acoustics quality;
- nametag visual acceptance;
- PTT visual acceptance;
- real Pojav/Amethyst acceptance;
- production canary;
- stable release approval.

Exit 0 means only `PASS_RUNTIME_PREREQUISITES`. Exit 2 means HOLD. Tooling/environment errors must never be reinterpreted as product PASS.
