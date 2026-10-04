param(
  [Parameter(Mandatory=$true)][string]$OutDir,
  [Parameter(Mandatory=$true)][string]$Operator,
  [Parameter(Mandatory=$true)][string]$Proxy,
  [Parameter(Mandatory=$true)][string]$LegacyBackend,
  [Parameter(Mandatory=$true)][string]$PaperBackend,
  [Parameter(Mandatory=$true)][string]$NetworkPath,
  [Parameter(Mandatory=$true)][ValidateRange(1,1440)][int]$PlannedDurationMinutes,
  [Parameter(Mandatory=$true)][ValidateRange(2,100)][int]$PlannedTesterCount,
  [Parameter(Mandatory=$true)][string]$VerificationReceipt,
  [Parameter(Mandatory=$true)][string]$InstallReceipt,
  [Parameter(Mandatory=$true)][string]$RollbackManifestJson
)
$ErrorActionPreference='Stop'
$scriptDir=Split-Path -Parent $MyInvocation.MyCommand.Path
$out=[IO.Path]::GetFullPath($OutDir)
if(Test-Path -LiteralPath $out){throw "OutDir already exists; refusing overwrite: $out"}
function Get-Sha([string]$path){(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()}
function Require-File([string]$path,[string]$label){
  $full=[IO.Path]::GetFullPath($path)
  if(-not(Test-Path -LiteralPath $full -PathType Leaf)){throw "$label not found: $full"}
  return $full
}
$expected=Get-Content -LiteralPath (Join-Path $scriptDir 'EXPECTED-SHA256.json') -Raw|ConvertFrom-Json
$releaseDir=[IO.Path]::GetFullPath((Join-Path $scriptDir ([string]$expected.release_dir)))
$releaseManifest=Require-File (Join-Path $releaseDir 'RELEASE-MANIFEST.json') 'release manifest'
$verifyPath=Require-File $VerificationReceipt 'verification receipt'
$installPath=Require-File $InstallReceipt 'install receipt'
$rollbackPath=Require-File $RollbackManifestJson 'rollback manifest'
$verify=Get-Content -LiteralPath $verifyPath -Raw|ConvertFrom-Json
$install=Get-Content -LiteralPath $installPath -Raw|ConvertFrom-Json
$rollback=Get-Content -LiteralPath $rollbackPath -Raw|ConvertFrom-Json
if([string]$verify.state -ne 'PASS'){throw 'verification receipt is not PASS'}
if([string]$verify.checkpoint -ne [string]$expected.checkpoint){throw 'verification checkpoint mismatch'}
$verifyRoles=@{}
$verifyByRole=@{}
foreach($item in @($verify.results)){
  $role=[string]$item.role
  if($role -notin @('forge','gateway','paper','addon_api')){throw "unexpected verification role: $role"}
  if($verifyRoles.ContainsKey($role)){throw "duplicate verification role: $role"}
  if(-not [bool]$item.match){throw "verification artifact mismatch: $role"}
  $verifyRoles[$role]=$true
  $verifyByRole[$role]=$item
}
foreach($required in @('forge','gateway','paper')){
  if(-not $verifyRoles.ContainsKey($required)){throw "verification receipt missing required role: $required"}
}
if([string]$install.state -ne 'PASS_INSTALLED'){throw 'install receipt is not PASS_INSTALLED'}
if([string]$install.release_checkpoint -ne [string]$expected.checkpoint){throw 'install release checkpoint mismatch'}
if([int]$install.destination_writes -ne 3 -or [int]$install.configuration_writes -ne 0){throw 'install receipt has unexpected write counts'}
if([IO.Path]::GetFullPath([string]$install.rollback_manifest) -ne $rollbackPath){throw 'install receipt references a different rollback manifest'}
if(@($install.installed).Count -ne 3){throw 'install receipt must contain exactly three installed runtime entries'}
$installedRoles=@{}
$installedByRole=@{}
$runtimeHashes=[ordered]@{}
foreach($item in @($install.installed)){
  $component=[string]$item.component
  if($component -notin @('forge','gateway','paper')){throw "unexpected installed component: $component"}
  if($installedRoles.ContainsKey($component)){throw "duplicate installed component: $component"}
  $installedRoles[$component]=$true
  $installedByRole[$component]=$item
  $destination=Require-File ([string]$item.destination) "installed runtime $component"
  $expectedHash=[string]$expected.artifacts.$component.sha256
  if([string]$item.release_sha256 -ne $expectedHash){throw "install receipt release hash mismatch: $component"}
  $actualHash=Get-Sha $destination
  if($actualHash -ne $expectedHash){throw "installed runtime no longer matches sealed RC7: $component"}
  $runtimeHashes[$component]=$actualHash
}
foreach($required in @('forge','gateway','paper')){
  if(-not $installedRoles.ContainsKey($required)){throw "install receipt missing installed component: $required"}
}
if([int]$rollback.schema_version -ne 1 -or [string]$rollback.state -ne 'PASS'){throw 'rollback manifest is not PASS schema 1'}
if([string]$rollback.release_checkpoint -ne [string]$expected.checkpoint){throw 'rollback release checkpoint mismatch'}
if([int]$rollback.configuration_files_copied -ne 0){throw 'rollback manifest contains configuration material'}
if([bool]$rollback.deployment_performed){throw 'rollback manifest already records deployment'}
if(@($rollback.backups).Count -ne 3){throw 'rollback manifest must contain exactly three runtime JARs'}
$rollbackRoles=@{}
$rollbackByRole=@{}
$backupDir=Split-Path -Parent $rollbackPath
foreach($item in @($rollback.backups)){
  $component=[string]$item.component
  if($component -notin @('forge','gateway','paper')){throw "unexpected rollback component: $component"}
  if($rollbackRoles.ContainsKey($component)){throw "duplicate rollback component: $component"}
  $rollbackRoles[$component]=$true
  $rollbackByRole[$component]=$item
  $file=Join-Path $backupDir ([string]$item.backup_file)
  if(-not(Test-Path -LiteralPath $file -PathType Leaf)){throw "rollback file missing: $file"}
  if((Get-Sha $file) -ne [string]$item.sha256){throw "rollback file hash mismatch: $component"}
  if((Get-Item -LiteralPath $file).Length -ne [long]$item.bytes){throw "rollback file size mismatch: $component"}
}
foreach($required in @('forge','gateway','paper')){
  if(-not $rollbackRoles.ContainsKey($required)){throw "rollback manifest missing required component: $required"}
}
foreach($required in @('forge','gateway','paper')){
  $installItem=$installedByRole[$required]
  $rollbackItem=$rollbackByRole[$required]
  $verifyItem=$verifyByRole[$required]
  $installedPath=[IO.Path]::GetFullPath([string]$installItem.destination)
  $rollbackOriginal=[IO.Path]::GetFullPath([string]$rollbackItem.original_path)
  $verifiedPath=[IO.Path]::GetFullPath([string]$verifyItem.path)
  $expectedSource=[IO.Path]::GetFullPath((Join-Path $releaseDir ([string]$expected.artifacts.$required.file)))
  $installSource=[IO.Path]::GetFullPath([string]$installItem.source)
  if($installedPath -ne $rollbackOriginal){throw "install/rollback destination mismatch: $required"}
  if($verifiedPath -ne $installedPath){throw "verification/install path mismatch: $required"}
  if($installSource -ne $expectedSource){throw "install source is not sealed RC7 artifact: $required"}
}
$templatePath=Require-File (Join-Path $scriptDir 'CANARY-EVIDENCE-v3.json') 'evidence template'
$e=Get-Content -LiteralPath $templatePath -Raw|ConvertFrom-Json
if([int]$e.schema_version -ne 3 -or [string]$e.checkpoint -ne [string]$expected.checkpoint){throw 'evidence template checkpoint/schema mismatch'}
$created=(Get-Date).ToUniversalTime().ToString('o')
$runId=[Guid]::NewGuid().ToString('N')
$e.state='READY_FOR_BASELINE'
$e.operator=$Operator
$e.started_at=$created
$e.artifact.verification_state='PASS'
$e.artifact.verification_receipt='artifact-verification.json'
$e.artifact.release_manifest_sha256=Get-Sha $releaseManifest
$e.environment.proxy=$Proxy
$e.environment.legacy_backend=$LegacyBackend
$e.environment.paper_backend=$PaperBackend
$e.environment.network_path=$NetworkPath
$e.live.canary.planned_duration_minutes=$PlannedDurationMinutes
$e.live.canary.planned_tester_count=$PlannedTesterCount
$provenance=[pscustomobject]@{
  run_id=$runId
  tooling_checkpoint='AV-CANARY-EVIDENCE-CHAIN-20260917-52'
  created_at=$created
  template_sha256=Get-Sha $templatePath
  release_manifest_sha256=Get-Sha $releaseManifest
  verification_receipt_sha256=Get-Sha $verifyPath
  install_receipt_sha256=Get-Sha $installPath
  rollback_manifest_sha256=Get-Sha $rollbackPath
  runtime_binding_checkpoint='AV-CANARY-LIVE-RUNTIME-BINDING-20260917-54'
  installed_runtime=[pscustomobject]$runtimeHashes
}
$e|Add-Member -NotePropertyName provenance -NotePropertyValue $provenance
try {
  New-Item -ItemType Directory -Path $out|Out-Null
  Copy-Item -LiteralPath $verifyPath -Destination (Join-Path $out 'artifact-verification.json')
  Copy-Item -LiteralPath $installPath -Destination (Join-Path $out 'install-receipt.json')
  Copy-Item -LiteralPath $rollbackPath -Destination (Join-Path $out 'rollback-manifest.json')
  $seedPath=Join-Path $out 'evidence-00-seeded.json'
  [IO.File]::WriteAllText($seedPath,($e|ConvertTo-Json -Depth 14),(New-Object Text.UTF8Encoding($false)))
  $run=[ordered]@{
    schema_version=1
    run_id=$runId
    state='READY_FOR_BASELINE'
    checkpoint='AV-CANARY-EVIDENCE-CHAIN-20260917-52'
    release_checkpoint=[string]$expected.checkpoint
    created_at=$created
    seed_file='evidence-00-seeded.json'
    seed_sha256=Get-Sha $seedPath
    artifact_verification_sha256=Get-Sha (Join-Path $out 'artifact-verification.json')
    install_receipt_sha256=Get-Sha (Join-Path $out 'install-receipt.json')
    rollback_manifest_sha256=Get-Sha (Join-Path $out 'rollback-manifest.json')
    runtime_binding_checkpoint='AV-CANARY-LIVE-RUNTIME-BINDING-20260917-54'
    installed_runtime=[pscustomobject]$runtimeHashes
    next_stage='Baseline'
  }
  [IO.File]::WriteAllText((Join-Path $out 'RUN-MANIFEST.json'),($run|ConvertTo-Json -Depth 8),(New-Object Text.UTF8Encoding($false)))
  $run|ConvertTo-Json -Depth 8
} catch {
  if(Test-Path -LiteralPath $out){Remove-Item -LiteralPath $out -Recurse -Force}
  throw
}
