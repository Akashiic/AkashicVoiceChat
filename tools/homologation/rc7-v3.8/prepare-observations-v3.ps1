param([Parameter(Mandatory=$true)][string]$RunDir)
$ErrorActionPreference='Stop'
$scriptDir=Split-Path -Parent $MyInvocation.MyCommand.Path
$dir=[IO.Path]::GetFullPath($RunDir)
function Get-Sha([string]$path){(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()}
function Need([string]$path,[string]$label){
  if(-not(Test-Path -LiteralPath $path -PathType Leaf)){throw "$label not found: $path"}
  return $path
}
$run=Get-Content -LiteralPath (Need (Join-Path $dir 'RUN-MANIFEST.json') 'run manifest') -Raw|ConvertFrom-Json
$runtimeJson=& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $scriptDir 'assert-runtime-v3.ps1') -RunDir $dir
if($LASTEXITCODE -ne 0){throw 'runtime binding check failed before observations'}
$runtime=$runtimeJson|ConvertFrom-Json
if([string]$runtime.state -ne 'PASS'){throw 'runtime binding check did not PASS before observations'}
$stage=Get-Content -LiteralPath (Need (Join-Path $dir 'STAGE-30-CLEANUP.json') 'cleanup receipt') -Raw|ConvertFrom-Json
if([string]$stage.run_id -ne [string]$run.run_id){throw 'cleanup receipt belongs to another run'}
if([string]$stage.runtime_binding_checkpoint -ne 'AV-CANARY-STAGE-RUNTIME-BINDING-20260917-56'){throw 'cleanup receipt lacks runtime binding'}
if((($stage.installed_runtime|ConvertTo-Json -Depth 5 -Compress)) -cne (($run.installed_runtime|ConvertTo-Json -Depth 5 -Compress))){throw 'cleanup runtime hashes differ from run manifest'}
$source=Need (Join-Path $dir ([string]$stage.output_file)) 'cleanup evidence'
if((Get-Sha $source) -ne [string]$stage.output_evidence_sha256){throw 'cleanup evidence hash mismatch'}
$e=Get-Content -LiteralPath $source -Raw|ConvertFrom-Json
if([string]$e.provenance.run_id -ne [string]$run.run_id){throw 'cleanup evidence run_id mismatch'}
$out=Join-Path $dir 'evidence-40-observations.json'
$receiptPath=Join-Path $dir 'OBSERVATIONS-PREP-RECEIPT.json'
if((Test-Path -LiteralPath $out) -or (Test-Path -LiteralPath $receiptPath)){throw 'observation phase already prepared; refusing overwrite'}
$e.state='AWAITING_LIVE_OBSERVATIONS'
[IO.File]::WriteAllText($out,($e|ConvertTo-Json -Depth 14),(New-Object Text.UTF8Encoding($false)))
$receipt=[ordered]@{
  schema_version=1;run_id=[string]$run.run_id;state='READY_FOR_OBSERVATIONS'
  source_file=[string]$stage.output_file;source_sha256=[string]$stage.output_evidence_sha256
  runtime_binding_checkpoint='AV-CANARY-STAGE-RUNTIME-BINDING-20260917-56'
  installed_runtime=$runtime.installed_runtime
  observations_file='evidence-40-observations.json';initial_observations_sha256=Get-Sha $out
  prepared_at=(Get-Date).ToUniversalTime().ToString('o')
}
[IO.File]::WriteAllText($receiptPath,($receipt|ConvertTo-Json -Depth 6),(New-Object Text.UTF8Encoding($false)))
$receipt|ConvertTo-Json -Depth 6
