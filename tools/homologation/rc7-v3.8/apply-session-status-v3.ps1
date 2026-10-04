param(
  [Parameter(Mandatory=$true)][string]$RunDir,
  [Parameter(Mandatory=$true)][string]$StatusJson,
  [Parameter(Mandatory=$true)][ValidateSet('Baseline','Restart','Cleanup')][string]$Stage
)
$ErrorActionPreference='Stop'
$scriptDir=Split-Path -Parent $MyInvocation.MyCommand.Path
$dir=[IO.Path]::GetFullPath($RunDir)
if(-not(Test-Path -LiteralPath $dir -PathType Container)){throw "RunDir not found: $dir"}
function Get-Sha([string]$path){(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()}
function Need([string]$path,[string]$label){
  if(-not(Test-Path -LiteralPath $path -PathType Leaf)){throw "$label not found: $path"}
  return $path
}
$runPath=Need (Join-Path $dir 'RUN-MANIFEST.json') 'run manifest'
$run=Get-Content -LiteralPath $runPath -Raw|ConvertFrom-Json
if([int]$run.schema_version -ne 1 -or [string]$run.checkpoint -ne 'AV-CANARY-EVIDENCE-CHAIN-20260917-52'){throw 'run manifest schema/checkpoint mismatch'}
$runtimeJson=& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $scriptDir 'assert-runtime-v3.ps1') -RunDir $dir
if($LASTEXITCODE -ne 0){throw "runtime binding check failed before $Stage"}
$runtime=$runtimeJson|ConvertFrom-Json
if([string]$runtime.state -ne 'PASS'){throw "runtime binding check did not PASS before $Stage"}
$statusSource=Need ([IO.Path]::GetFullPath($StatusJson)) 'status JSON'
$status=Get-Content -LiteralPath $statusSource -Raw|ConvertFrom-Json
if([int]$status.schema_version -ne 1){throw 'status schema_version must be 1'}
$sourceName=$null;$sourceHash=$null;$outputName=$null;$statusName=$null;$receiptName=$null;$previousReceipt=$null;$nextStage=$null
switch($Stage){
  'Baseline' {$sourceName='evidence-00-seeded.json';$sourceHash=[string]$run.seed_sha256;$outputName='evidence-10-baseline.json';$statusName='status-10-baseline.json';$receiptName='STAGE-10-BASELINE.json';$nextStage='Restart'}
  'Restart' {$sourceName='evidence-10-baseline.json';$outputName='evidence-20-restart.json';$statusName='status-20-restart.json';$receiptName='STAGE-20-RESTART.json';$previousReceipt='STAGE-10-BASELINE.json';$nextStage='Cleanup'}
  'Cleanup' {$sourceName='evidence-20-restart.json';$outputName='evidence-30-cleanup.json';$statusName='status-30-cleanup.json';$receiptName='STAGE-30-CLEANUP.json';$previousReceipt='STAGE-20-RESTART.json';$nextStage='Observations'}
}
$sourcePath=Need (Join-Path $dir $sourceName) 'source evidence'
if($previousReceipt){
  $prevPath=Need (Join-Path $dir $previousReceipt) 'previous stage receipt'
  $prev=Get-Content -LiteralPath $prevPath -Raw|ConvertFrom-Json
  if([string]$prev.run_id -ne [string]$run.run_id){throw 'previous stage belongs to another run'}
  if([string]$prev.runtime_binding_checkpoint -ne 'AV-CANARY-STAGE-RUNTIME-BINDING-20260917-56'){throw 'previous stage lacks runtime binding'}
  if((($prev.installed_runtime|ConvertTo-Json -Depth 5 -Compress)) -cne (($run.installed_runtime|ConvertTo-Json -Depth 5 -Compress))){throw 'previous stage runtime hashes differ from run manifest'}
  $sourceHash=[string]$prev.output_evidence_sha256
}
if((Get-Sha $sourcePath) -ne $sourceHash){throw "source evidence hash mismatch for $Stage"}
$source=Get-Content -LiteralPath $sourcePath -Raw|ConvertFrom-Json
if([string]$source.provenance.run_id -ne [string]$run.run_id){throw 'source evidence run_id mismatch'}
$outputPath=Join-Path $dir $outputName
$statusCopy=Join-Path $dir $statusName
$receiptPath=Join-Path $dir $receiptName
foreach($path in @($outputPath,$statusCopy,$receiptPath)){
  if(Test-Path -LiteralPath $path){throw "stage output already exists; refusing overwrite: $path"}
}
Copy-Item -LiteralPath $statusSource -Destination $statusCopy
try {
  & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $scriptDir 'apply-status-v3.ps1') `
    -EvidenceJson $sourcePath -StatusJson $statusCopy -Stage $Stage -OutJson $outputPath | Out-Null
  if($LASTEXITCODE -ne 0){throw "apply-status-v3 failed with exit $LASTEXITCODE"}
  $output=Get-Content -LiteralPath $outputPath -Raw|ConvertFrom-Json
  if([string]$output.provenance.run_id -ne [string]$run.run_id){throw 'output evidence run_id mismatch'}
  $receipt=[ordered]@{
    schema_version=1;run_id=[string]$run.run_id;stage=$Stage;applied_at=(Get-Date).ToUniversalTime().ToString('o')
    source_file=$sourceName;source_evidence_sha256=$sourceHash
    status_file=$statusName;status_sha256=Get-Sha $statusCopy
    runtime_binding_checkpoint='AV-CANARY-STAGE-RUNTIME-BINDING-20260917-56'
    installed_runtime=$runtime.installed_runtime
    output_file=$outputName;output_evidence_sha256=Get-Sha $outputPath;next_stage=$nextStage
  }
  [IO.File]::WriteAllText($receiptPath,($receipt|ConvertTo-Json -Depth 6),(New-Object Text.UTF8Encoding($false)))
  $receipt|ConvertTo-Json -Depth 6
} catch {
  if(Test-Path -LiteralPath $outputPath){Remove-Item -LiteralPath $outputPath -Force}
  if(Test-Path -LiteralPath $statusCopy){Remove-Item -LiteralPath $statusCopy -Force}
  throw
}
