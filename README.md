# ProxyChat

Proximity voice chat for **Minecraft 1.7.10**, designed for the **GregTech: New Horizons** modpack.

ProxyChat is an independent, clean-room implementation. It does not share code or assets
with any other voice chat mod.

**Current version:** 0.3.1

## Features

- Proximity-based 3D voice chat (OpenAL positional audio).
- Push-to-talk, voice activation (dB threshold), and always-on modes.
- Per-mod settings GUI with live microphone test (loopback + level meter on a dB scale).
- First-run setup wizard for picking a microphone.
- Microphone amplification slider, basic noise gate (high-pass), playback volume slider.
- Persistent HUD indicator showing mic state (active / muted) bottom-right.
- Connection-failed HUD indicator when the UDP voice link can't be established
  (firewall, missing port forward, Docker mapping, etc.).
- Speaker indicators above other players' heads while they speak.
- Discord-style **groups** with invite/accept flow:
  - One group per player at a time.
  - Distance-independent audio between group members.
  - Server-side persistence (NBT) — groups survive restarts.
- Default UDP port **24454**, default voice radius **48 blocks** — both configurable.
- Bundles [Concentus](https://github.com/lostromb/concentus) (MIT) for pure-Java Opus
  encoding/decoding. No native libraries required.

## Requirements

| | |
|---|---|
| Minecraft | 1.7.10 |
| Forge | 10.13.4.1614 |
| [UniMixins](https://github.com/LegacyModdingMC/UniMixins) | required |
| [lwjgl3ify](https://github.com/GTNewHorizons/lwjgl3ify) | required, supplies LWJGL 3 / Java 17+ runtime |
| Java | 17 / 21 / 25 (matches your lwjgl3ify-modernised setup) |

Same .jar runs on the server and on the client.

## Install

1. Drop `ProxyChat-<version>.jar` into the `mods/` folder of **both** the server and every
   client that wants to use voice.
2. Open **UDP port 24454** to the server (or whatever you set `voice.voicePort` to). On
   Docker setups, expose the port in your compose / run command:
   ```yaml
   ports:
     - "25565:25565/tcp"
     - "24454:24454/udp"   # ← UDP, not TCP
   ```
3. Restart the server. The first launch generates `config/proxychat.cfg` with sensible
   defaults.
4. Connect with a client that has the .jar in its `mods/`. The first time you join, a
   setup wizard opens to let you pick a microphone.

## Keybinds (configurable in Minecraft Controls)

| Default | Action |
|---|---|
| `V` (hold) | Push-to-talk |
| `N` (press) | Toggle microphone (mute / unmute) |
| `O` (press) | Open the Voice Chat hub |

## In-game UI

Press `O` to open the **Voice Chat hub**. From there:

- **Settings** — microphone selection, volume sliders, activation mode, dB threshold,
  microphone test (with self-loopback), 3D audio mode, mic amplification, noise gate.
- **Group** — create a group, invite other voice users, leave.

The HUD bottom-right shows the mic state automatically:
- **Mic-on icon** while you're transmitting.
- **Mic-mute icon** persistently while muted.
- **Plug icon** above the mic when ProxyChat can't reach the voice server (e.g. UDP
  port not open).

## Configuration

All settings live in `config/proxychat.cfg`, generated on first run. Highlights:

- `voice.voicePort` — UDP port (default `24454`).
- `voice.voiceRadius` — audible distance in blocks (default `48`).
- `voice.opusBitrate` — encoder bitrate (default `24000`).
- `voice.debugSelfEcho` — server bounces your own frames back. Useful for solo
  smoke-testing; keep `false` in production.
- `client.activationMode` — `ptt` / `voice` / `always`.
- `client.voiceActivationThresholdDb` — dB threshold for voice activation (default `-50`).
- `client.microphoneAmplification`, `client.playbackVolume`, `client.noiseSuppression`,
  `client.audio3dMode`, `client.referenceDistance`, `client.selectedMicMixer`.

## Building from source

Requires JDK 21 (or higher) on `PATH` to drive the GTNHGradle build.

```sh
./gradlew build
```

Artifacts land in `build/libs/`. The plain `proxychat-<version>.jar` is the one you ship —
the `-dev`, `-sources`, and `-preshadow` jars are build by-products.

To run the client/server in a development environment:

```sh
./gradlew runClient   # dev MC client with the mod loaded
./gradlew runServer   # dev dedicated server with the mod loaded
```

## Solo smoke-test

If you want to verify the audio path on your own without a friend online, set
`voice.debugSelfEcho=true` in `config/proxychat.cfg` on the server, restart, join, talk —
the server echoes your frames back. Set `client.activationMode=always` and your own voice
will reach you immediately. Turn both back to defaults for normal play.

## Credits

### Icons

The in-game icons are sourced from [Flaticon](https://www.flaticon.com/) under their
free-use license, with attribution required. Credit:

- Toggle icons created by [Vector Squad — Flaticon](https://www.flaticon.com/free-icons/toggle)
- On icons created by [Amazona Adorada — Flaticon](https://www.flaticon.com/free-icons/on)
- Mic icons created by [LAFS — Flaticon](https://www.flaticon.com/free-icons/mic)
- Mic icons created by [Hight Quality Icons — Flaticon](https://www.flaticon.com/free-icons/mic)
- Noise icons created by [meaicon — Flaticon](https://www.flaticon.com/free-icons/noise)
- Plug icons created by [Freepik — Flaticon](https://www.flaticon.com/free-icons/plug)

### Bundled libraries

- [Concentus](https://github.com/lostromb/concentus) — pure-Java Opus codec, MIT license.
  Shaded into the jar at `dev.jm.proxychat.shadow.org.concentus.*`.

## License

ProxyChat itself is released under the **MIT License** — see [LICENSE](LICENSE).

Bundled / referenced third parties retain their original licenses (Concentus: MIT,
Flaticon icons: Flaticon free-use license with attribution).
