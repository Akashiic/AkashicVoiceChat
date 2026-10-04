# RC7 canary install v3

This procedure installs only the three runtime JARs (Forge, Gateway, Paper).
It never reads, copies, rewrites or deletes Akashic Voice configuration, PSKs, player data or addon state.
The sealed RC7 source artifacts are always re-hashed before installation.

## Required order

1. Stop at a maintenance boundary appropriate for the three server processes.
2. Create a fresh rollback set with `prepare-rollback-v3.ps1`.
3. Verify it with `verify-rollback-v3.ps1` and require PASS.
4. Run `install-canary-v3.ps1` without `-ExecuteInstall` first.
5. Review the `READY_DRY_RUN` plan.
6. Run the same command with `-ExecuteInstall` only while the relevant processes are stopped.
7. Start the canary processes and run `verify-install.ps1` against the installed JARs.
8. Proceed to the live canary evidence procedure only after verification passes.

The installer requires the current installed JAR hashes to still match the rollback snapshot.
If any runtime JAR changed after the backup was made, installation aborts before writing.
## Dry-run

```powershell
.\install-canary-v3.ps1 `
  -ForgeInstalled  'C:\path\legacy\mods\current-akashicvoice.jar' `
  -GatewayInstalled 'C:\path\velocity\plugins\current-akashicvoice.jar' `
  -PaperInstalled   'C:\path\paper\plugins\current-akashicvoice.jar' `
  -RollbackManifestJson 'C:\safe\backup\akashicvoice-pre-rc5\ROLLBACK-MANIFEST.json' `
  -OutJson '.\install-dry-run.json'
```

A successful dry-run reports `READY_DRY_RUN` and `destination_writes: 0`.
It verifies the sealed RC7 hashes, rollback material, destination identity and pre-install hashes.

## Explicit installation

Repeat the same command with `-ExecuteInstall`.
All three RC7 sources are first staged beside their destinations and re-hashed.
Only after all staging succeeds does activation begin.
Each installed destination is immediately checked against the sealed RC7 SHA-256.
On success the report is `PASS_INSTALLED` with exactly three runtime destination writes.
## Failure during activation

The installer keeps a temporary sibling copy of each pre-install runtime JAR while activation is in progress.
If a later activation step fails, every component already touched is restored from that local copy and re-hashed.
A successful automatic recovery reports `FAIL_ROLLED_BACK` and exits nonzero.
If automatic recovery itself cannot restore a verified hash, the report is `CRITICAL_ROLLBACK_FAILED`.
In that case keep all server processes stopped and use the separately verified rollback bundle from `ROLLBACK-v3.md`.

The three runtime replacements are not a single cross-filesystem transaction.
This is why the procedure requires stopped processes, preverified rollback material and post-install hash verification.
No script in this kit starts, stops or restarts Minecraft/Velocity/Paper automatically.

## Critical rollback boundary
If installation reports CRITICAL_ROLLBACK_FAILED, do not start a canary. Perform and verify the external rollback, then perform a fresh RC7 install and verification. v3.3 also revalidates the live installed JARs when a new canary session is created, so an older PASS receipt cannot bypass this boundary.

