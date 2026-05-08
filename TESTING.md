# ProxyChat — Testing Guide

Drei Testpfade, in steigender Komplexität. Fang mit **Pfad 1** an —
das ist ein 5-Minuten-Solo-Test. Wenn das funktioniert, weiter mit Pfad 2 / 3.

---

## Pfad 1 — Solo-Smoke-Test (Dev-Forge, du hörst dich selbst)

**Was wird getestet:** Mod lädt, Mikrofon → Opus-Encode → UDP →
Server → UDP → Opus-Decode → OpenAL-Wiedergabe. Das gesamte Audio-Pipeline,
mit dir alleine.

**Voraussetzungen:** ein Terminal, das Repo, Internet (erstmaliger Asset-Download).

### Schritte

1. **Debug-Self-Echo aktivieren.** Öffne die Datei
   `run/client/config/proxychat.cfg`. Existiert sie noch nicht? Dann
   einmal `./gradlew runClient` starten und sofort wieder schließen — die
   Datei wird beim ersten Start angelegt. Im Block `voice {` die Zeile
   ```
   B:debugSelfEcho=false
   ```
   ändern zu
   ```
   B:debugSelfEcho=true
   ```
   und speichern.

2. **Aktivierungs-Modus auf `always` stellen** (damit du dir nicht erst die
   PTT-Taste belegen musst). In derselben Datei, Block `client {`:
   ```
   S:activationMode=ptt
   ```
   ändern zu
   ```
   S:activationMode=always
   ```

3. **Dev-Client starten:**
   ```
   ./gradlew runClient
   ```
   Beim ersten Start lädt das ~400 MB Minecraft-Assets — kann mehrere
   Minuten dauern. Folgenstart: Sekunden.

4. **Beim ersten Mikrofon-Zugriff fragt macOS** nach der Berechtigung für
   "Java" oder "OpenJDK Platform binary". **Erlauben.** Wenn du es
   versehentlich verweigerst: System-Einstellungen → Privatsphäre &
   Sicherheit → Mikrofon → Java aktivieren.

5. **Im Spiel:** Singleplayer → "Create New World" → irgendeine Welt → in
   die Welt joinen.

6. **Sprich.** Du solltest dich aus deiner eigenen Position hören —
   3D-Audio, also wenn du den Kopf drehst, wandert die Stimme.

7. **Verifiziere im Log** (Konsole, in der du `runClient` gestartet hast):
   - `[proxychat]: ProxyChat preInit (...)`
   - `[proxychat]: Voice server bound to UDP port 24454`
   - `[proxychat]: Received voice secret (port 24454)`
   - `[proxychat]: Voice auth ok for ...`
   - `[proxychat]: Microphone capture started ...`
   - alle ~250 Frames: `Decoded N sound frames so far ...`

### Wenn was nicht klappt

- **Stille, aber Logs zeigen "Microphone capture started":** macOS-Mic-
  Berechtigung fehlt → Systemeinstellungen prüfen.
- **Stille, aber keine "Decoded N sound frames"-Logs:** UDP wird vermutlich
  blockiert. Auf macOS unwahrscheinlich; im Zweifel Firewall checken.
- **Crash beim Connect:** Log nach `Exception` durchsuchen, mir zeigen.
- **Audio knackt / pausiert:** OpenAL-Buffer voll. Erstmal kein Bug —
  bei manchen Audio-Setups kann es ruckeln. Ignorieren wenn Stimme
  trotzdem erkennbar.

### Aufräumen vor "echtem" Betrieb

Wenn Pfad 1 funktioniert hat, **dreh `debugSelfEcho` wieder zurück auf
`false`** und `activationMode` auf `ptt`. Sonst hörst du dich später auf
deinem Server immer selbst und alle reden permanent.

---

## Pfad 2 — Zwei Dev-Clients gegeneinander (echter Multiplayer-Test)

**Was wird getestet:** Echte Proximity-Distribution. Du als Spieler A,
ein zweiter Client als Spieler B; beide hören sich auf Distanz, nicht
mehr ab Radius `voiceRadius` (default 48 Blöcke).

### Schritte

1. **Repo zweimal auschecken**, weil zwei Dev-Clients nicht denselben
   `run/`-Ordner teilen können:
   ```
   cd ~/Projects
   git clone git@github.com:Tony20494/ProxyChat.git ProxyChat-B
   ```
   In `ProxyChat-B/gradle.properties` zusätzlich noch dieselbe Zeile
   hinzufügen, die wir auch im Original haben (Java 25 Pfad):
   ```
   org.gradle.java.installations.paths=/Users/juliusmeinking/.jdks/jdk-25.0.3+9/Contents/Home
   ```

2. **Server starten** im Original-Repo (Terminal 1):
   ```
   cd ~/Projects/ProxyChat
   ./gradlew runServer
   ```
   Warten bis `[Server thread/INFO]: Done (...). For help, type "help"`
   und `Voice server bound to UDP port 24454` erscheinen.

   Beim ersten Start: `eula.txt` in `run/server/` öffnen, `eula=false`
   auf `eula=true` setzen, `runServer` neu starten.

3. **Client A starten** (Terminal 2):
   ```
   cd ~/Projects/ProxyChat
   ./gradlew runClient --username=Alice
   ```

4. **Client B starten** (Terminal 3):
   ```
   cd ~/Projects/ProxyChat-B
   ./gradlew runClient --username=Bob
   ```

5. **Beide Clients connecten auf `localhost`** (Multiplayer → Direct
   Connect → `localhost`).

6. **Sprich auf Client A.** Auf Client B sollte Alice hörbar sein, mit
   Position aus der Welt. Über Bobs Kopf erscheint ein grüner Quad,
   wenn Alice spricht (und umgekehrt).

7. **Lauf weg auf Client B.** Wenn Distanz > 48 Blöcke, hört Bob nichts
   mehr von Alice.

`debugSelfEcho` muss für Pfad 2 **aus** sein. `activationMode` empfohlen
auf `ptt` für realistisches Verhalten (Taste `V` halten).

---

## Pfad 3 — Echte GTNH-Installation (lwjgl3ify-Kompatibilität)

**Was wird getestet:** Läuft die Mod in deinem realen GTNH-Setup mit
Java 25 + lwjgl3ify? Bisher haben wir nur den Forge-Dev-Stand auf
Java 8 verifiziert.

### Voraussetzungen

- Eine GTNH-Installation (Client und/oder Server) mit lwjgl3ify +
  UniMixins, lauffähig auf Java 17+ (idealerweise 25, wie dein
  Server-Setup).

### Schritte

1. **Jar bauen:**
   ```
   ./gradlew build
   ```
   Output: `build/libs/proxychat-<version>.jar`. Den Dev-Suffix-Jar
   (`-dev.jar`) ignorierst du — du nimmst die ohne `-dev`.

   ⚠️ Wichtig: aktuelle Version bündelt **Concentus nicht**. Du musst
   parallel `Concentus-3885c4e465.jar` mit reinpacken (siehe Punkt 2).
   Das wird in Phase 7 (Release-Hygiene) automatisiert via Shadow-JAR;
   bis dahin manuell.

2. **Concentus besorgen.** Auf JitPack laden:
   ```
   curl -L -o Concentus-3885c4e465.jar \
     "https://jitpack.io/com/github/lostromb/concentus/Concentus/3885c4e465/Concentus-3885c4e465.jar"
   ```

3. **Beide Jars in `mods/` kopieren** — sowohl in deinen GTNH-Client
   als auch auf den GTNH-Server.

4. **GTNH-Server starten.** Im Server-Log nach `Voice server bound to
   UDP port 24454` suchen.

5. **GTNH-Client starten und auf den Server connecten.**

6. **UDP-Port 24454 muss erreichbar sein.** Wenn der Server hinter
   einer Firewall steht: Port forwarden / freischalten. UDP, nicht TCP.

7. Smoketest wie Pfad 2: zwei Spieler, einer redet, der andere hört.

### Bekannte Risiken in Pfad 3

- **OpenAL-Konflikt mit lwjgl3ify-Shim:** Wir initialisieren OpenAL über
  die LWJGL-2-API. Lwjgl3ify übersetzt das auf LWJGL 3. Wenn das nicht
  sauber durchschlägt, könnte die Wiedergabe stumm bleiben oder MC
  crashen. Im Crash-Fall den Log mir zeigen.
- **Java 25 vs. Concentus:** Concentus ist ältere Java-Lib. Sollte
  laufen, aber bei Class-Verifier-Problemen ggf. JVM-Flag
  `--enable-native-access=ALL-UNNAMED` ergänzen (haben GTNH-Setups
  meist eh schon).
- **Mikrofon-Berechtigung auf macOS-Client:** wie in Pfad 1, aber für
  den GTNH-Java-Prozess.

---

## Welcher Pfad wann

| Wenn du... | Pfad |
|---|---|
| schauen willst „funktioniert die Pipeline überhaupt" | 1 |
| schauen willst „funktioniert die echte Proximity-Verteilung" | 2 |
| schauen willst „läuft das auf meinem GTNH-Server" | 3 |

Zuerst Pfad 1. Wenn das durch ist, ist die meiste Komplexität durch.

---

## Standardwerte zur Erinnerung

| Setting | Default | Datei |
|---|---|---|
| Voice-UDP-Port | 24454 | `proxychat.cfg` `voice.voicePort` |
| Hörradius | 48 Blöcke | `proxychat.cfg` `voice.voiceRadius` |
| Aktivierungs-Modus | PTT (Taste `V`) | `proxychat.cfg` `client.activationMode` |
| Mute-Toggle | Taste `N` | hardcoded, umbindbar in MC-Controls-Menü |
| Opus-Bitrate | 24 kbps | `proxychat.cfg` `voice.opusBitrate` |
| Debug-Self-Echo | aus | `proxychat.cfg` `voice.debugSelfEcho` |
