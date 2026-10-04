# Related projects found on the engineering workstation

**Search performed:** 2026-10-04, `C:\Users\User\Desktop\Codex` (108 top-level folders, 54 loose files), `Documents\GitHub`, and the Technic install. Method: folder names, plus a bounded text scan (depth 4, files < 2 MB: md/txt/json/gradle/properties/toml/ps1/yml/cfg) for `akashic voice`, `dev.akashic.voice`, `AV-PREMIUM`, `voicechat`, `proxychat`. A name match or a text match is a lead, not proof of a dependency.

## 1. What was asked and what exists

The request mentioned an **"Akashic Compatibility Lab"** possibly holding companions (paper, velocity). **No folder with that name, and no folder named for a compatibility lab, exists** under `Desktop\Codex` or `Documents\GitHub`. The nearest names are unrelated compatibility efforts (`Akashic-DH-EndlessIDs-Compat`, `Akashic-Hodgepodge-AsyncCompat*`, `AkashicBackpackCompat-1.0.0-rc1`, `akashic-mirai-*-lab`).

The **Paper and Velocity companions are not separate projects**: they live inside Akashic Voice itself under `platform/` (`svc-paper` for Paper 1.21.1 / SVC API 2.6.20, `gateway-velocity` for Velocity 4.0.0, plus `bridge-core`, `agent-protocol`, `addon-api`). They are part of this repository.

## 2. Direct relations (Akashic Voice is involved)

| Folder | What it is | Relation to Akashic Voice | Evidence |
|---|---|---|---|
| `Akashic Voice` | Original working checkout (read-only for this assembly) | Source of this repository: history, dirty-tree snapshot, `audit/`, `dist/`, RC7 operator pack | Imported; see [`FRAGMENTATION_MAP.md`](FRAGMENTATION_MAP.md) |
| `forbidden-mobile-engineering` | Android launcher engineering build (`ForbiddenMobile-0.4.3-engineering-arm64`) | Source of the 59 Android QA logs; the launcher's managed pack contained the RC7 JAR | [`FIELD_EVIDENCE_2026-10.md`](FIELD_EVIDENCE_2026-10.md) §6 |
| Technic `forbidden-series` modpack | The desktop client used daily | RC7 JAR installed in `mods/`; source of the 154 desktop logs | Hash match, `PROJECT_STATE.md` §2 |
| `akashic-world-firewall` | A server-side guard mod for the Forbidden modpack | Its `audit/forbidden-modpack-surface.*` inventories the pack's mods and lists `akashicvoice-forge-1.7.10.jar` (`modids=akashicvoice`, 26 matching classes at one scan) | Text scan match; not otherwise coupled |
| `Akashic-Player-Backport-GitHub` | A separately packaged Akashic project with its own repository layout | **Reference model** for the fragmentation map and documentation style; its `docs/FRAGMENTATION-MAP.md` lists `Akashic Voice` as a separate effort sharing the folder | `docs/FRAGMENTATION-MAP.md` |
| `akashic-index-checkpoints` | Stutter-investigation checkpoints | One `stutter-…/fml-last64k.txt` mentions Akashic Voice (a client log excerpt) | Text scan match |

## 3. Environment relations (shape the field evidence, not dependencies)

| Folder(s) | Why it matters for voice |
|---|---|
| `fml-transfer-branch` | A git-tracked Forge client instance (mods/config/options plus the engineering APK payload). Its name suggests FML server-transfer work, which would be relevant to issue #4 (timeouts around transfers); its contents were not read for that purpose. Its `hodgepodge.cfg` matched the scan only because of a "voice chat" setting. |
| `forbidden-mobile-launcher`, `ForbiddenMobile-A57-Diagnostics-2026-09-19`, `Forbidden-Production-0.20.0` | Android launcher toolchain, diagnostics and a release APK. Context for issues #17 and #18 (LWJGL 3 runtime, audio routing), not Akashic Voice code. |
| `Akashic-ThePatcher-*`, `Akashic-Hodgepodge-*`, `Angelica-*`, `akashic-mirai-*` | Rendering/startup performance and compatibility work on the same modpack. Their rollback folders contain `hodgepodge.cfg` copies that match the scan on the word "voice chat"; they are **not** Akashic Voice material. They matter only as co-installed mods that can influence audio/sound-system behavior (ArchaicFix, Angelica, lwjgl3ify are listed as compatible targets in the contract). |
| `akashic-audit*`, `entregas`, `chatgpt-skills` | Audit/delivery material for other Akashic projects. No Akashic Voice content found. |

## 4. Not related

All other `Akashic-*`, `immersive*-backport`, `skript-reflect-*`, terrain/atlas probes, compiler/hotspot tests, `Mekanism-CE-*`, `Reliquary-*`, `Allthemodium-*`, `the-end-of-forbidden-spawn`, `The_End_of_Forbidden_Documentacao_Completa` and `_backhand_*`/`_rfg_inspect` showed no reference to Akashic Voice in the bounded scan. `Documents\GitHub\Auctions` is an unrelated 2021 folder.

## 5. Limits

- The scan was depth-limited (4) and skipped files ≥ 2 MB and binary archives; a reference inside a JAR, APK or deeper folder would be missed.
- `The_End_of_Forbidden_Documentacao_Completa` and the Technic pack were not read in full; they may describe the voice setup operationally.
- Nothing here was modified. These folders are siblings; they are not imported into this repository.
