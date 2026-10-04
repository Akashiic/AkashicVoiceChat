param(
  [Parameter(Mandatory=$true)][string]$ManifestJson,
  [switch]$ExecuteRollback
)
$ErrorActionPreference='Stop'
$manifestPath=[IO.Path]::GetFullPath($ManifestJson)
if(-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)){throw "Rollback manifest not found: $manifestPath"}
$m=Get-Content -LiteralPath $manifestPath -Raw|ConvertFrom-Json
if([int]$m.schema_version -ne 1 -or [string]$m.state -ne 'PASS'){throw 'Rollback manifest is not an approved v1 PASS manifest'}
if([int]$m.configuration_files_copied -ne 0 -or [bool]$m.deployment_performed){throw 'Rollback manifest violates no-config/no-deployment contract'}
$base=Split-Path -Parent $manifestPath
$records=@($m.backups)
if($records.Count -ne 3){throw "Expected exactly 3 runtime artifact backups, got $($records.Count)"}
$required=@('forge','gateway','paper')
$verified=@()
foreach($component in $required){
  $matches=@($records|Where-Object{[string]$_.component -eq $component})
  if($matches.Count -ne 1){throw "Expected exactly one backup for $component"}
  $entry=$matches[0]
  $backup=Join-Path $base ([string]$entry.backup_file)
  if(-not (Test-Path -LiteralPath $backup -PathType Leaf)){throw "Missing backup for ${component}: $backup"}
  $hash=(Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash.ToLowerInvariant()
  if($hash -ne [string]$entry.sha256){throw "Backup hash mismatch for $component"}
  $bytes=(Get-Item -LiteralPath $backup).Length
  if($bytes -ne [long]$entry.bytes){throw "Backup size mismatch for $component"}
  $destination=[IO.Path]::GetFullPath([string]$entry.original_path)
  $parent=Split-Path -Parent $destination
  if(-not (Test-Path -LiteralPath $parent -PathType Container)){throw "Destination parent missing for ${component}: $parent"}
  $verified += [pscustomobject]@{component=$component;backup=$backup;destination=$destination;sha256=$hash;bytes=$bytes}
}$report=[ordered]@{
  state=if($ExecuteRollback){'READY_TO_RESTORE'}else{'DRY_RUN'}
  checkpoint=[string]$m.checkpoint
  execute_requested=[bool]$ExecuteRollback
  verified_backups=$verified
  configuration_files_touched=0
}
if(-not $ExecuteRollback){
  $report|ConvertTo-Json -Depth 8
  exit 0
}

# Stage all copies before replacing any destination. This does not make the three-file set transactional,
# but it prevents a corrupt/missing backup from causing a partial restore.
$staged=@()
try {
  foreach($entry in $verified){
    $temp=$entry.destination+'.akashicvoice-rollback.tmp'
    if(Test-Path -LiteralPath $temp){Remove-Item -LiteralPath $temp -Force}
    Copy-Item -LiteralPath $entry.backup -Destination $temp
    $tempHash=(Get-FileHash -LiteralPath $temp -Algorithm SHA256).Hash.ToLowerInvariant()
    if($tempHash -ne $entry.sha256){throw "Staged rollback hash mismatch for $($entry.component)"}
    $staged += [pscustomobject]@{component=$entry.component;temp=$temp;destination=$entry.destination;sha256=$entry.sha256}
  }
  foreach($entry in $staged){
    Move-Item -LiteralPath $entry.temp -Destination $entry.destination -Force
    $finalHash=(Get-FileHash -LiteralPath $entry.destination -Algorithm SHA256).Hash.ToLowerInvariant()
    if($finalHash -ne $entry.sha256){throw "Restored artifact hash mismatch for $($entry.component)"}
  }
  $report.state='RESTORED'
  $report.restored=($staged|ForEach-Object{$_.component})
  $report|ConvertTo-Json -Depth 8
} finally {
  foreach($entry in $staged){if(Test-Path -LiteralPath $entry.temp){Remove-Item -LiteralPath $entry.temp -Force}}
}
