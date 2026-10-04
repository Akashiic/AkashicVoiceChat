param(
  [Parameter(Mandatory=$true)][string]$ForgeInstalled,
  [Parameter(Mandatory=$true)][string]$GatewayInstalled,
  [Parameter(Mandatory=$true)][string]$PaperInstalled,
  [Parameter(Mandatory=$true)][string]$RollbackManifestJson,
  [string]$OutJson,
  [switch]$ExecuteInstall
)
$ErrorActionPreference='Stop'
$scriptDir=Split-Path -Parent $MyInvocation.MyCommand.Path
$expected=Get-Content -LiteralPath (Join-Path $scriptDir 'EXPECTED-SHA256.json') -Raw|ConvertFrom-Json
$releaseDir=[IO.Path]::GetFullPath((Join-Path $scriptDir ([string]$expected.release_dir)))
$manifestPath=[IO.Path]::GetFullPath($RollbackManifestJson)
function Get-Sha([string]$path){(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()}
function Emit-Report($report){
  $json=$report|ConvertTo-Json -Depth 9
  if($OutJson){
    $out=[IO.Path]::GetFullPath($OutJson);$parent=Split-Path -Parent $out
    if($parent -and -not(Test-Path $parent)){New-Item -ItemType Directory -Path $parent -Force|Out-Null}
    [IO.File]::WriteAllText($out,$json,(New-Object Text.UTF8Encoding($false)))
  }
  $json
}
$destinations=[ordered]@{
  forge=[IO.Path]::GetFullPath($ForgeInstalled)
  gateway=[IO.Path]::GetFullPath($GatewayInstalled)
  paper=[IO.Path]::GetFullPath($PaperInstalled)
}
foreach($entry in $destinations.GetEnumerator()){
  if(-not(Test-Path -LiteralPath $entry.Value -PathType Leaf)){
    throw "Installed $($entry.Key) JAR not found: $($entry.Value)"
  }
}
if(-not(Test-Path -LiteralPath $manifestPath -PathType Leaf)){throw "Rollback manifest not found: $manifestPath"}
$m=Get-Content -LiteralPath $manifestPath -Raw|ConvertFrom-Json
if([int]$m.schema_version -ne 1){throw "Unsupported rollback schema"}
if([string]$m.state -ne 'PASS'){throw "Rollback manifest is not PASS"}
if([bool]$m.deployment_performed){throw "Rollback manifest already records deployment"}
if([int]$m.configuration_files_copied -ne 0){throw "Rollback manifest contains configuration material"}
if([string]$m.release_checkpoint -ne [string]$expected.checkpoint){throw "Rollback/release checkpoint mismatch"}
if(@($m.backups).Count -ne 3){throw "Rollback manifest must contain exactly three runtime JARs"}
$rollback=@{}
foreach($item in @($m.backups)){
  $component=[string]$item.component
  if($component -notin @('forge','gateway','paper')){throw "Unexpected rollback component: $component"}
  if($rollback.ContainsKey($component)){throw "Duplicate rollback component: $component"}
  $backup=Join-Path (Split-Path -Parent $manifestPath) ([string]$item.backup_file)
  if(-not(Test-Path -LiteralPath $backup -PathType Leaf)){throw "Rollback file missing: $backup"}
  if((Get-Sha $backup) -ne [string]$item.sha256){throw "Rollback hash mismatch: $component"}
  if((Get-Item -LiteralPath $backup).Length -ne [long]$item.bytes){throw "Rollback size mismatch: $component"}
  if([IO.Path]::GetFullPath([string]$item.original_path) -ne $destinations[$component]){throw "Rollback destination mismatch: $component"}
  $rollback[$component]=$item
}
$plan=@()
foreach($component in @('forge','gateway','paper')){
  $artifact=$expected.artifacts.$component
  $source=Join-Path $releaseDir ([string]$artifact.file)
  if(-not(Test-Path -LiteralPath $source -PathType Leaf)){throw "Sealed RC7 artifact missing: $source"}
  $releaseHash=Get-Sha $source
  if($releaseHash -ne [string]$artifact.sha256){throw "Sealed RC7 artifact hash mismatch: $component"}
  $currentHash=Get-Sha $destinations[$component]
  $rollbackHash=[string]$rollback[$component].sha256
  if($currentHash -ne $rollbackHash){throw "Installed JAR changed after rollback backup: $component"}
  $plan += [pscustomobject]@{
    component=$component
    source=$source
    destination=$destinations[$component]
    current_sha256=$currentHash
    rollback_sha256=$rollbackHash
    release_sha256=$releaseHash
  }
}
if(-not $ExecuteInstall){
  Emit-Report ([ordered]@{
    state='READY_DRY_RUN'
    checkpoint='AV-CANARY-INSTALL-PREFLIGHT-20260917-51'
    release_checkpoint=[string]$expected.checkpoint
    rollback_manifest=$manifestPath
    execute_install=$false
    destination_writes=0
    configuration_writes=0
    plan=$plan
  })
  exit 0
}
$tx=[Guid]::NewGuid().ToString('N')
$stages=@{}
$olds=@{}
$touched=New-Object Collections.Generic.List[string]
try {
  foreach($item in $plan){
    $component=[string]$item.component
    $destination=[string]$item.destination
    $stage=$destination+'.akashicvoice-rc7-'+$tx+'.stage'
    $old=$destination+'.akashicvoice-pre-'+$tx+'.old'
    if((Test-Path -LiteralPath $stage) -or (Test-Path -LiteralPath $old)){throw "Temporary install path already exists: $component"}
    Copy-Item -LiteralPath ([string]$item.source) -Destination $stage
    if((Get-Sha $stage) -ne [string]$item.release_sha256){throw "Staged RC7 hash mismatch: $component"}
    $stages[$component]=$stage
    $olds[$component]=$old
  }
  foreach($item in $plan){
    $component=[string]$item.component
    $destination=[string]$item.destination
    Move-Item -LiteralPath $destination -Destination $olds[$component]
    $touched.Add($component)
    Move-Item -LiteralPath $stages[$component] -Destination $destination
    if((Get-Sha $destination) -ne [string]$item.release_sha256){throw "Installed RC7 hash mismatch: $component"}
  }
  foreach($component in @('forge','gateway','paper')){
    Remove-Item -LiteralPath $olds[$component] -Force
  }
  Emit-Report ([ordered]@{
    state='PASS_INSTALLED'
    checkpoint='AV-CANARY-INSTALL-PREFLIGHT-20260917-51'
    release_checkpoint=[string]$expected.checkpoint
    rollback_manifest=$manifestPath
    execute_install=$true
    destination_writes=3
    configuration_writes=0
    installed=$plan
  })
  exit 0
} catch {
  $installError=$_.Exception.Message
  $restoreErrors=New-Object Collections.Generic.List[string]
  foreach($component in @('paper','gateway','forge')){
    if(-not $touched.Contains($component)){continue}
    try {
      $destination=$destinations[$component]
      if(Test-Path -LiteralPath $destination){Remove-Item -LiteralPath $destination -Force}
      if(-not(Test-Path -LiteralPath $olds[$component] -PathType Leaf)){throw "local pre-install copy missing"}
      Move-Item -LiteralPath $olds[$component] -Destination $destination
      if((Get-Sha $destination) -ne [string]$rollback[$component].sha256){throw "restored hash mismatch"}
    } catch {
      $restoreErrors.Add($component+': '+$_.Exception.Message)
    }
  }
  foreach($component in @('forge','gateway','paper')){
    if($stages.ContainsKey($component) -and (Test-Path -LiteralPath $stages[$component])){
      Remove-Item -LiteralPath $stages[$component] -Force
    }
  }
  $rolledBack=($restoreErrors.Count -eq 0)
  Emit-Report ([ordered]@{
    state=if($rolledBack){'FAIL_ROLLED_BACK'}else{'CRITICAL_ROLLBACK_FAILED'}
    checkpoint='AV-CANARY-INSTALL-PREFLIGHT-20260917-51'
    release_checkpoint=[string]$expected.checkpoint
    rollback_manifest=$manifestPath
    execute_install=$true
    configuration_writes=0
    touched_components=@($touched)
    rollback_restored=$rolledBack
    install_error=$installError
    rollback_errors=@($restoreErrors)
  })
  if($rolledBack){exit 2}else{exit 3}
}
