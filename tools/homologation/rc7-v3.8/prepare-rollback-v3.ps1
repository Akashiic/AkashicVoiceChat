param(
  [Parameter(Mandatory=$true)][string]$ForgeInstalled,
  [Parameter(Mandatory=$true)][string]$GatewayInstalled,
  [Parameter(Mandatory=$true)][string]$PaperInstalled,
  [Parameter(Mandatory=$true)][string]$BackupDir
)
$ErrorActionPreference='Stop'
$scriptDir=Split-Path -Parent $MyInvocation.MyCommand.Path
$expected=Get-Content -LiteralPath (Join-Path $scriptDir 'EXPECTED-SHA256.json') -Raw|ConvertFrom-Json
$releaseDir=[IO.Path]::GetFullPath((Join-Path $scriptDir $expected.release_dir))
$backup=[IO.Path]::GetFullPath($BackupDir)
if(Test-Path -LiteralPath $backup){throw "BackupDir already exists; refusing overwrite: $backup"}

$installed=[ordered]@{
  forge=[IO.Path]::GetFullPath($ForgeInstalled)
  gateway=[IO.Path]::GetFullPath($GatewayInstalled)
  paper=[IO.Path]::GetFullPath($PaperInstalled)
}
foreach($entry in $installed.GetEnumerator()){
  if(-not (Test-Path -LiteralPath $entry.Value -PathType Leaf)){throw "Installed $($entry.Key) JAR not found: $($entry.Value)"}
}

foreach($entry in $expected.artifacts.PSObject.Properties){
  $file=Join-Path $releaseDir $entry.Value.file
  if(-not (Test-Path -LiteralPath $file -PathType Leaf)){throw "Sealed RC7 artifact missing: $file"}
  $actual=(Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
  if($actual -ne [string]$entry.Value.sha256){throw "Sealed RC7 artifact hash mismatch: $($entry.Value.file)"}
}New-Item -ItemType Directory -Path $backup|Out-Null
$records=@()
try {
  foreach($entry in $installed.GetEnumerator()){
    $name=$entry.Key+'-precanary.jar'
    $dest=Join-Path $backup $name
    Copy-Item -LiteralPath $entry.Value -Destination $dest
    $sourceHash=(Get-FileHash -LiteralPath $entry.Value -Algorithm SHA256).Hash.ToLowerInvariant()
    $backupHash=(Get-FileHash -LiteralPath $dest -Algorithm SHA256).Hash.ToLowerInvariant()
    if($sourceHash -ne $backupHash){throw "Backup verification failed for $($entry.Key)"}
    $records += [pscustomobject]@{
      component=$entry.Key
      original_path=$entry.Value
      backup_file=$name
      sha256=$backupHash
      bytes=(Get-Item -LiteralPath $dest).Length
    }
  }
  $manifest=[ordered]@{
    schema_version=1
    state='PASS'
    checkpoint='AV-CANARY-ROLLBACK-PREFLIGHT-20260917-50'
    created_at=(Get-Date).ToUniversalTime().ToString('o')
    release_checkpoint=[string]$expected.checkpoint
    release_dir=$releaseDir
    backups=$records
    configuration_files_copied=0
    deployment_performed=$false
  }
  $manifestPath=Join-Path $backup 'ROLLBACK-MANIFEST.json'
  [IO.File]::WriteAllText($manifestPath,($manifest|ConvertTo-Json -Depth 8),(New-Object Text.UTF8Encoding($false)))
  $manifest|ConvertTo-Json -Depth 8
} catch {
  if(Test-Path -LiteralPath $backup){Remove-Item -LiteralPath $backup -Recurse -Force}
  throw
}
