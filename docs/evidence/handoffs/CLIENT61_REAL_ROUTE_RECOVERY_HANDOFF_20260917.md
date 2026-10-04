# Akashic Voice - CLIENT61 real route/recovery evidence

Checkpoint: `AV-REAL-ROUTE-RECOVERY-EVIDENCE-20260917-61`.
Release under observation remains sealed RC5 / CLIENT47; no release JAR or server was changed.

Archived FML evidence from a real Technic Forge 1.7.10 multiplayer run shows the voice client using the configured
public endpoint. The logged endpoint address resolves from `<public-voice-host>`, port <port>.
The run contains session-bound bootstrap receipts followed by secure authentication on the production VoiceClient.

Three route-change cycles were observed. Each cycle stopped the old voice client, requested a fresh bootstrap,
received a new session-bound bootstrap, started the public-endpoint voice client, and authenticated successfully.
Gateway RC5 source sends this rebootstrap on backend `ServerPostConnectEvent` or on route-up when the player's
backend lacks a voice session. The client log also contains remote server-sync/Netty transition context.
Six transport timeout warnings were also observed. All six eventually reached a new `Secure voice session authenticated`
state. Microphone capture was stopped and restarted across these recovery cycles without a client crash.
This is live evidence of secure-route recovery and real backend-handoff/rebootstrap behavior, not just a synthetic soak.

The evidence does not prove a deliberately commanded backend process restart: the origin of timeout-only cycles is not
attributed. It also does not prove decoded remote speech, two-client listening, acoustic quality, mobile operation or
operator/player acceptance. No voice payload or microphone sample was inspected.

CLIENT59 separately proves sealed-artifact identity, a sustained authenticated client runtime, OpenAL capture, a real UDP
receive socket and native `opus_jni_rust.dll`. CLIENT60 proves a clean real client restart does not open voice/microphone
threads while the player remains outside a world.

Current planning estimate: approximately 99% (98-99.5% judgment band). Completion remains blocked on the final live
acceptance gates, not on another round of synthetic automation.
