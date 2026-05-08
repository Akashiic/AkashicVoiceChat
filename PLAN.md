# ProxyChat — Proximity Voice Chat für GTNH (Minecraft 1.7.10)

Plan zur Eigenentwicklung einer Proximity-Voice-Chat-Mod im Stil von Simple Voice Chat (SVC),
ausgelegt auf das Modpack **GregTech: New Horizons** (1.7.10) mit modernem Java-Stack.

---

## 1. Ausgangslage

| Punkt | Status |
|---|---|
| Ziel-Spielversion | Minecraft 1.7.10 (Java Edition, Forge) |
| Ziel-Modpack | GT: New Horizons (GTNH) |
| Java-Runtime auf Server | Java 25 |
| Modloader-Stack im Pack | Forge 1.7.10 + **UniMixins** + **lwjgl3ify** |
| Bestehende SVC-Versionen | ab MC 1.12.2 — **kein offizielles 1.7.10** |
| Bestehende Alternative | *Gliby's Voice Chat* (1.7.10) — funktioniert, gilt als veraltet/qualitativ schwach |
| Community-Wunsch | GTNH-Issue [#22241](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/issues/22241) („Cleanroom Backport Simple Voice Chat") — niemand arbeitet dran |

### Was `lwjgl3ify` für uns ändert

`lwjgl3ify` lässt 1.7.10 mit **Java 17 / 21 / 25** und **LWJGL 3** laufen. Konkret:

- Moderne `javax.sound.sampled`-Implementierung verfügbar.
- LWJGL 3 → moderne OpenAL-Bindings (`org.lwjgl.openal.AL10/AL11/EXTEfx`) statt LWJGL-2-OpenAL.
- Mixins via UniMixins (statt ASM-Coremod-Gefrickel).
- Native-Libs (Opus JNI) für aktuelle Plattformen verfügbar — kein Concentus-Java-Fallback nötig.

→ Der Port ist deutlich einfacher als bei „Vanilla 1.7.10 mit Java 8". Wir können nahezu auf 1.12.2-Niveau arbeiten, plus Mixins.

---

## 2. Lizenzlage — **kritisch**

SVC steht unter:

```
Copyright (c) Max Henkel - All Rights Reserved
```

→ **Kein Code-Reuse. Kein Fork. Kein Copy-Paste.** Auch nicht aus alten Branches (`outdated/1.12.2-2.4.x` etc.).

Erlaubt ist:
- Beobachtung des **beobachtbaren Verhaltens** (Netzwerk-Pakete am Wire, UI-Verhalten, Default-Werte).
- Lesen der **veröffentlichten Dokumentation/API**.
- Eigene Implementierung des **Wire-Protokolls** zwecks Kompatibilität — wenn gewünscht.

Empfehlung: **eigenes Protokoll** entwerfen. Spart juristische Grauzone und hat keinen praktischen Nachteil, weil eh kein SVC-Client gegen unseren 1.7.10-Server reden würde.

Lizenz für ProxyChat selbst: **MIT** vorgeschlagen — passt zu GTNH-Ökosystem (viele Mods MIT/LGPL).

---

## 3. Was eine Proximity-Voice-Chat-Mod technisch tut

Verifiziert durch Source-Inspektion von SVC:

```
Client ──MC-Packet── Server : RequestSecret
Server ──MC-Packet── Client : Secret + UDP-Port
Client ──UDP─────── VoiceServer : Authenticate(secret, uuid)
[loop]
  Client ──UDP──    VoiceServer : MicPacket(opus_frame, seq)
  VoiceServer ──UDP→ andere Clients: PlayerSoundPacket(senderUUID, opus_frame, pos)
  Empfänger-Client → 3D-Audio-Wiedergabe (positional)
```

Komponenten:

1. **Voice-Server** — eigener Thread im MC-Server-Prozess, UDP-Socket (separater Port oder MC-Port).
2. **Handshake** — Shared-Secret-Austausch über Forges Networking.
3. **Audio-Capture** — Mikrofon → 20 ms PCM-Frames @ 48 kHz mono.
4. **Codec** — Opus-Encode (Bitrate ~24 kbps default).
5. **Distribution** — Server kennt alle Spielerpositionen, schickt Frames an Empfänger im Radius.
6. **Wiedergabe** — pro Sender ein Audio-Channel, OpenAL-3D-Source mit Position des Senders.
7. **Push-to-Talk + Voice-Activation** — Client-seitig.

Optional/Phase 2: Gruppen, verschlüsselte Pakete, In-Game-Anzeige der Sprechenden, Recording, Audio-Effekte.

---

## 4. Tech-Stack-Entscheidung

| Layer | Wahl | Begründung |
|---|---|---|
| Modloader | Forge 1.7.10 (10.13.4.1614) | GTNH-Standard |
| Mixins | UniMixins (über GTNH ohnehin geladen) | saubere Hooks, keine Coremods |
| Java | 21 als `--release`-Target | läuft auf 25; bleibt portabel |
| Build | ForgeGradle 1.2 — **GTNH-Fork** (`GTNHGradle`) | nur dieser Fork ist auf modernem Gradle/Java tragfähig |
| Codec | `opus4j` (JNI) primary, Concentus als Fallback | Natives für linux-x86_64 / macos-arm64 / windows-x86_64; alles andere → Concentus |
| Audio I/O Capture | `javax.sound.sampled` (TargetDataLine) | Plattform-portabel |
| Audio I/O Playback | LWJGL 3 OpenAL direkt (über `lwjgl3ify`) | echtes 3D, Streaming-tauglich; Paulscode in 1.7.10 ist für SFX, nicht Streaming |
| Networking (Control) | Forge `SimpleNetworkWrapper` | ausreichend für Handshake/Status |
| Networking (Voice) | Eigener UDP-Socket | wie SVC |

---

## 5. Repository-Struktur

```
ProxyChat/
├── PLAN.md
├── README.md
├── LICENSE                    # MIT
├── build.gradle               # GTNHGradle
├── settings.gradle
├── gradle.properties          # mc=1.7.10, forge=10.13.4.1614, java=21
├── src/main/java/dev/jm/proxychat/
│   ├── ProxyChat.java         # @Mod-Entrypoint
│   ├── ClientProxy.java
│   ├── CommonProxy.java
│   ├── ServerProxy.java
│   ├── net/                   # Forge-Pakete (Handshake)
│   │   ├── ProxyChatChannel.java
│   │   ├── PacketRequestSecret.java
│   │   └── PacketSecret.java
│   ├── voice/
│   │   ├── common/            # Wire-Protokoll, Frame-Format
│   │   │   ├── WireProtocol.java
│   │   │   ├── MicFrame.java
│   │   │   ├── SoundFrame.java
│   │   │   └── AudioConsts.java   # 48000/20ms/960 samples
│   │   ├── server/
│   │   │   ├── VoiceServer.java   # UDP-Loop
│   │   │   ├── VoiceConnection.java
│   │   │   ├── DistributionTask.java  # wer hört wen?
│   │   │   └── SecretRegistry.java
│   │   └── client/
│   │       ├── VoiceClient.java
│   │       ├── MicCapture.java
│   │       ├── PlaybackChannel.java   # OpenAL-Source pro Sender
│   │       ├── PttHandler.java
│   │       └── VoiceActivationDetector.java
│   ├── codec/
│   │   ├── OpusEncoder.java
│   │   └── OpusDecoder.java
│   ├── config/
│   │   └── ProxyChatConfig.java   # Forge Configuration
│   └── render/
│       └── SpeakerOverlay.java     # Sprech-Indicator über Köpfen
└── src/main/resources/
    ├── mcmod.info
    └── assets/proxychat/lang/{en_US,de_DE}.lang
```

---

## 6. Phasen-Plan

### Phase 0 — Toolchain (1–2 Tage)
- [ ] GTNHGradle-Template clonen (`GTNewHorizons/ExampleMod1.7.10`).
- [ ] Build gegen Java 21 grün bekommen.
- [ ] Dev-Client startet mit lwjgl3ify + UniMixins lokal.

### Phase 1 — Mod-Skeleton + Handshake (2–3 Tage)
- [ ] `@Mod`-Klasse, ClientProxy/ServerProxy.
- [ ] `SimpleNetworkWrapper`-Channel.
- [ ] Pakete: `RequestSecret` (C→S), `Secret` (S→C, enthält `UUID secret`, `int udpPort`).
- [ ] Server generiert pro Spieler ein 16-Byte-Secret bei Login.
- [ ] Logging beider Seiten, sichtbar im Chat-Befehl `/pcdebug`.

### Phase 2 — Voice-Server-Loop (3–5 Tage)
- [ ] `VoiceServer` Thread bindet UDP an konfigurierten Port.
- [ ] `Authenticate`-Paket über UDP verifiziert Secret → registriert `VoiceConnection`.
- [ ] Heartbeat/KeepAlive alle 1 s.
- [ ] **Noch keine Audio-Distribution** — nur Echo-Modus zum Testen (Server schickt empfangene Pakete an Sender zurück).

### Phase 3 — Codec + Mikrofon (3–4 Tage)
- [ ] `opus4j` einbinden, native Lib für linux-x86_64/macos-arm64/win-x86_64 packagen.
- [ ] `MicCapture`: TargetDataLine 48 kHz/16 bit/mono, 20 ms Frames.
- [ ] PCM → Opus → UDP-MicFrame.
- [ ] **Lokaler Loopback-Test**: gegen Echo-Server hören, Latenz messen.

### Phase 4 — Wiedergabe + 3D-Audio (4–6 Tage)
- [ ] Pro Sender ein OpenAL-Source via LWJGL 3.
- [ ] Source-Position pro Frame setzen (= Position des Sender-Spielers).
- [ ] Listener-Position = lokaler Spieler.
- [ ] Distance-Model: `AL_INVERSE_DISTANCE_CLAMPED`, `MAX_DISTANCE` aus Config.
- [ ] Jitter-Buffer (~60 ms), Packet-Loss-Concealment (Opus PLC, frame=null).

### Phase 5 — Server-Side-Distribution (2–3 Tage)
- [ ] Pro empfangenem MicFrame: alle Spieler in derselben Dimension mit `getDistanceSqToEntity ≤ radius²` ermitteln, Frame als `SoundFrame` an deren UDP-Endpoint forwarden.
- [ ] Sender-Position in das Paket packen.
- [ ] Whisper/Shout-Modes (kleinerer/größerer Radius) — optional.

### Phase 6 — Polish (5–7 Tage)
- [ ] PTT-Keybind über Forge `KeyBinding`.
- [ ] Voice-Activation-Detection (RMS-Threshold).
- [ ] Sprech-Indicator über Köpfen (Mixin in `RenderManager` oder `RenderPlayerEvent.Specials.Post`).
- [ ] In-Game-Settings-Screen (Mikrofon-Auswahl, Lautstärke, Threshold).
- [ ] Forge-`Configuration` für Server-Settings (Port, Radius, max bitrate).

### Phase 7 — Release-Hygiene (2 Tage)
- [ ] README mit Setup-Anleitung für GTNH-Server-Admins.
- [ ] mcmod.info, Versions-Schema (`MAJOR.MINOR.PATCH+gtnh`).
- [ ] CurseForge / Modrinth Upload.
- [ ] Issue auf GTNH-Repo aktualisieren / PR auf Pack-Manifest.

**Gesamt-Schätzung Prototyp (Phasen 0–4):** ~15 Personentage konzentrierte Arbeit.
**Bis Feature-parität-mit-SVC-light (alle Phasen):** ~40 Personentage.

---

## 7. Knackpunkte / Risiken

| Risiko | Auswirkung | Gegenmaßnahme |
|---|---|---|
| `opus4j` Native ggf. nicht für GTNH-Zielplattformen verfügbar | Build/Runtime bricht | Concentus (Pure-Java) als Fallback einbauen — gleicher Pfad wie SVC |
| LWJGL 3 OpenAL nur via `lwjgl3ify` — Pack ohne lwjgl3ify spielt Mod nicht ab | Inkompatibilität mit „Java-8-Variante" von GTNH | Im Manifest `lwjgl3ify` als hartes Dependency markieren |
| 1.7.10 Forge-Packets sind im 32 KB-Limit — irrelevant für Audio (Opus < 1 KB), aber Init-Pakete im Auge behalten | Crash bei Login | Pakete klein halten / chunken |
| Firewall: extra UDP-Port am Server | Admins müssen Port öffnen | Default = MC-Port (UDP), wie SVC, aber konfigurierbar |
| GTNH läuft auch in Java-8-Variante — Mod-User könnten dort installieren | Crash | Im `@Mod`-Init Java-Version prüfen, klare Fehlermeldung |
| Mikrofon-Permissions auf macOS | Stille | Beim ersten Capture klare Fehlermeldung mit Hinweis auf System-Settings |
| Audio-Latenz > 200 ms unbrauchbar | Schlechte UX | Frame-Size 20 ms, Jitter-Buffer max. 3 Frames, kein TCP irgendwo |

---

## 8. Abgrenzung zu vorhandenen Lösungen

| Mod | MC-Version | Kompatibel mit GTNH+lwjgl3ify? | Einschätzung |
|---|---|---|---|
| Simple Voice Chat (henkelmax) | ≥ 1.12.2 | nein, kein 1.7.10-Build | Vorbild, aber ARR — kein Code-Reuse |
| Plasmo Voice | ≥ 1.16.5 | nein | irrelevant |
| Gliby's Voice Chat | 1.7.10 | ja (hat aber LWJGL2-Annahmen) | als **Last-Resort-Alternative** für User; als **Referenz** für 1.7.10-Forge-Hooks |
| LabyMod Voice Chat | client-only | nein, GTNH ist Vanilla-Forge | irrelevant |

---

## 9. Erste konkrete Aktionen

1. GTNH ExampleMod-Template klonen, Repo initialisieren, Erstcommit.
2. `lwjgl3ify`-fähigen Dev-Client einrichten und Hello-World-Mod laden.
3. Phase-1-Handshake umsetzen — sobald Secret-Austausch zwischen Client und Server in Logs sichtbar ist, ist die schwierigste Forge-Plumbing-Hürde genommen.
4. Phase 2/3 parallel: einer schreibt UDP-Server, einer Mic-Capture+Opus.

---

## 10. Festgelegte Projekt-Parameter

| Punkt | Entscheidung |
|---|---|
| Projektmodus | **Solo-Projekt**. Nach v1-Release im GTNH-Issue [#22241](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/issues/22241) publishen. |
| Discord-Bridge | **Hook-Punkte einplanen**, aber nicht implementieren. Voice-Server kriegt eine `VoiceEventBus`-Abstraktion, an die Add-ons später andocken können (analog SVC's Plugin-API). |
| Server-Authorisierung | **MC-Login-gekoppelt**. Wer auf dem MC-Server ist, darf ins Voice. Keine separate Whitelist, keine Rollen für v1. |
| Default-Hörradius | **48 Blöcke** (wie SVC), per Config änderbar. Whisper/Shout für später. |
| Native-Plattformen für `opus4j` | **Linux x86_64** (Server + Client), **macOS arm64**, **Windows x86_64**. Andere (macOS Intel, Linux ARM) fallen automatisch auf Concentus-Pure-Java zurück. |
| Lizenz | MIT. |
| Repo-Hosting | Privates oder öffentliches GitHub-Repo unter eigenem Account, später ggf. Mirror in GTNH-Org nach v1. |
