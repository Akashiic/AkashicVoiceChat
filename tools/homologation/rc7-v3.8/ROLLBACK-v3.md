# RC5 canary rollback v3

This procedure backs up and restores only the three runtime JARs (Forge, Gateway, Paper).
It never copies, replaces or deletes Akashic Voice configuration, PSKs, secrets or player data.
The scripts do not install RC5 automatically.

## 1. Before installing RC5

Stop at a maintenance boundary appropriate for your server process, then run `prepare-rollback-v3.ps1` against the exact currently installed JAR paths and a **new, nonexistent** backup directory.

```powershell
.\prepare-rollback-v3.ps1 `
  -ForgeInstalled  'C:\path\legacy\mods\current-akashicvoice.jar' `
  -GatewayInstalled 'C:\path\velocity\plugins\current-akashicvoice.jar' `
  -PaperInstalled   'C:\path\paper\plugins\current-akashicvoice.jar' `
  -BackupDir        'C:\safe\backup\akashicvoice-pre-rc5'
```

The script first verifies all sealed RC5 source artifacts against `EXPECTED-SHA256.json`.
It then copies the currently installed runtime JARs and re-hashes every backup.
An existing backup directory is a hard failure; it is never reused or overwritten.
Configuration files are deliberately excluded.

## 2. Verify rollback material

```powershell
.\verify-rollback-v3.ps1 -ManifestJson 'C:\safe\backup\akashicvoice-pre-rc5\ROLLBACK-MANIFEST.json'
```

Exit `0` means the three backups match the recorded hashes and sizes. Exit `2` means FAIL/stop.

## 3. Dry-run a restore

`restore-rollback-v3.ps1` is a dry-run unless the explicit switch is present:

```powershell
.\restore-rollback-v3.ps1 -ManifestJson 'C:\safe\backup\akashicvoice-pre-rc5\ROLLBACK-MANIFEST.json'
```

The dry-run verifies all three backups and destination parents and writes nothing.

## 4. Execute rollback after a stop condition

Stop the relevant server processes before replacing their JARs. Then run:

```powershell
.\restore-rollback-v3.ps1 `
  -ManifestJson 'C:\safe\backup\akashicvoice-pre-rc5\ROLLBACK-MANIFEST.json' `
  -ExecuteRollback
```

All three backup files are hash/size verified **before** any destination write.
Each artifact is copied to a temporary sibling file, re-hashed, then moved into its recorded original path.
If a backup is missing/tampered, restoration is aborted before any destination is changed.
The three final moves are individually verified but are not a cross-filesystem transactional operation; keep the server processes stopped until all three report restored.

Do not restore configuration/PSK files as part of a normal RC5 rollback. The canary contract assumes configuration remains unchanged.
