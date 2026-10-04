# Akashic Voice - CLIENT42 client recovery escalation qualification

Checkpoint: `AV-CLIENT-RECOVERY-ESCALATION-20260915-42`.
Production baseline remains `integrated-client39`; QA only.

A standalone Java 8 smoke exercised the production `VoiceClient` maintenance state machine without opening UDP,
microphone or OpenAL devices. The CLIENT39 `VoiceClient.java` hash was verified identical to the clean DEV JAR
from reviewed CLIENT36 before the test was compiled against that artifact.

PASS scenarios:
1. Authenticated server silence beyond 8 s enters recovery handshake; forcing the 6 s recovery window to expire
   transitions to `needsFreshBootstrap`, clears authentication/crypto, and does not start microphone capture.
2. An initial HELLO timeout retries/rotates handshake state without prematurely demanding a fresh bootstrap.
3. A recovery AUTH_RESPONSE timeout escalates to fresh bootstrap and remains unauthenticated.

`ClientVoiceBootstrap` source was also inspected: on `needsFreshBootstrap` it stops the old VoiceClient, clears the
cached material, resets request state and sends a new `PacketRequestSecret`; bootstrap response timeout and prolonged
unauthenticated state also trigger bounded retry/rebootstrap behavior.

Result: `CLIENT_RECOVERY_ESCALATION_OK scenarios=3` on JDK 1.8.0_411.
No production source/artifact/deployment changed. Planning estimate: approximately **94%** (90-97% band).
Remaining closure is dominated by release packaging/operator checks plus live WAN, real listening/microphone,
mobile Pojav/Amethyst and production deployment evidence.
