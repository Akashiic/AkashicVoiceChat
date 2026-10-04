param([Parameter(Mandatory=$true)][string]$RunDir)
$ErrorActionPreference='Stop'
$scriptDir=Split-Path -Parent $MyInvocation.MyCommand.Path
$dir=[IO.Path]::GetFullPath($RunDir)
function Get-Sha([string]$path){(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()}
function Need([string]$path,[string]$label){
  if(-not(Test-Path -LiteralPath $path -PathType Leaf)){throw "$label not found: $path"}
  return $path
}
function Same-Json($a,$b){
  return (($a|ConvertTo-Json -Depth 14 -Compress) -ceq ($b|ConvertTo-Json -Depth 14 -Compress))
}
$run=Get-Content -LiteralPath (Need (Join-Path $dir 'RUN-MANIFEST.json') 'run manifest') -Raw|ConvertFrom-Json
$runtimeJson=& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $scriptDir 'assert-runtime-v3.ps1') -RunDir $dir
if($LASTEXITCODE -ne 0){throw 'runtime binding check failed before final evaluation'}
$runtime=$runtimeJson|ConvertFrom-Json
if([string]$runtime.state -ne 'PASS'){throw 'runtime binding check did not PASS before final evaluation'}
$seedPath=Need (Join-Path $dir 'evidence-00-seeded.json') 'seed evidence'
if((Get-Sha $seedPath) -ne [string]$run.seed_sha256){throw 'seed evidence hash mismatch'}
$cleanupReceipt=Get-Content -LiteralPath (Need (Join-Path $dir 'STAGE-30-CLEANUP.json') 'cleanup receipt') -Raw|ConvertFrom-Json
$cleanupPath=Need (Join-Path $dir ([string]$cleanupReceipt.output_file)) 'cleanup evidence'
if((Get-Sha $cleanupPath) -ne [string]$cleanupReceipt.output_evidence_sha256){throw 'cleanup evidence hash mismatch'}
$obsPath=Need (Join-Path $dir 'evidence-40-observations.json') 'observation evidence'
$seed=Get-Content -LiteralPath $seedPath -Raw|ConvertFrom-Json
$cleanup=Get-Content -LiteralPath $cleanupPath -Raw|ConvertFrom-Json
$obs=Get-Content -LiteralPath $obsPath -Raw|ConvertFrom-Json
if([string]$obs.provenance.run_id -ne [string]$run.run_id){throw 'observation evidence run_id mismatch'}
$s10=Get-Content -LiteralPath (Need (Join-Path $dir 'STAGE-10-BASELINE.json') 'baseline receipt') -Raw|ConvertFrom-Json
$s20=Get-Content -LiteralPath (Need (Join-Path $dir 'STAGE-20-RESTART.json') 'restart receipt') -Raw|ConvertFrom-Json
foreach($stage in @($s10,$s20,$cleanupReceipt)){
  if([string]$stage.run_id -ne [string]$run.run_id){throw 'stage receipt run_id mismatch'}
  if([string]$stage.runtime_binding_checkpoint -ne 'AV-CANARY-STAGE-RUNTIME-BINDING-20260917-56'){throw 'stage receipt lacks runtime binding'}
  if(-not (Same-Json $stage.installed_runtime $run.installed_runtime)){throw 'stage runtime hashes differ from run manifest'}
}
if([string]$s10.source_evidence_sha256 -ne [string]$run.seed_sha256){throw 'baseline chain does not start at seed'}
if([string]$s20.source_evidence_sha256 -ne [string]$s10.output_evidence_sha256){throw 'restart chain mismatch'}
if([string]$cleanupReceipt.source_evidence_sha256 -ne [string]$s20.output_evidence_sha256){throw 'cleanup chain mismatch'}
foreach($stage in @($s10,$s20,$cleanupReceipt)){
  $path=Need (Join-Path $dir ([string]$stage.output_file)) 'stage output'
  if((Get-Sha $path) -ne [string]$stage.output_evidence_sha256){throw "stage output hash mismatch: $($stage.stage)"}
  $statusPath=Need (Join-Path $dir ([string]$stage.status_file)) 'stage status'
  if((Get-Sha $statusPath) -ne [string]$stage.status_sha256){throw "stage status hash mismatch: $($stage.stage)"}
}
if([int]$obs.schema_version -ne [int]$seed.schema_version -or [string]$obs.checkpoint -ne [string]$seed.checkpoint){throw 'observation schema/checkpoint changed'}
if([string]$obs.operator -ne [string]$seed.operator -or [string]$obs.started_at -ne [string]$seed.started_at){throw 'operator/start identity changed'}
if(-not (Same-Json $obs.artifact $seed.artifact)){throw 'artifact provenance changed during observations'}
if(-not (Same-Json $obs.environment $seed.environment)){throw 'environment identity changed during observations'}
if(-not (Same-Json $obs.provenance $seed.provenance)){throw 'provenance block changed during observations'}
if(-not (Same-Json $obs.baseline $cleanup.baseline)){throw 'baseline counters changed during observations'}
if([long]$obs.live.restart.agent_connects_after -ne [long]$cleanup.live.restart.agent_connects_after -or [long]$obs.live.restart.agent_dropped_after -ne [long]$cleanup.live.restart.agent_dropped_after -or [long]$obs.live.restart.agent_queue_after -ne [long]$cleanup.live.restart.agent_queue_after -or [bool]$obs.live.restart.gateway_route_up_after -ne [bool]$cleanup.live.restart.gateway_route_up_after){throw 'restart objective counters changed during observations'}
if([long]$obs.live.cleanup.active_sessions_final -ne [long]$cleanup.live.cleanup.active_sessions_final -or [long]$obs.live.cleanup.pending_handshakes_final -ne [long]$cleanup.live.cleanup.pending_handshakes_final -or [long]$obs.live.cleanup.legacy_managed_players_final -ne [long]$cleanup.live.cleanup.legacy_managed_players_final -or [long]$obs.live.cleanup.legacy_agent_queue_final -ne [long]$cleanup.live.cleanup.legacy_agent_queue_final){throw 'cleanup objective counters changed during observations'}
if([long]$obs.live.canary.planned_duration_minutes -ne [long]$seed.live.canary.planned_duration_minutes){throw 'planned duration changed'}
if([long]$obs.live.canary.planned_tester_count -ne [long]$seed.live.canary.planned_tester_count){throw 'planned tester count changed'}
$finalPath=Join-Path $dir 'evidence-50-final.json'
$reportPath=Join-Path $dir 'CANARY-REPORT.json'
$receiptPath=Join-Path $dir 'SESSION-FINAL-RECEIPT.json'
foreach($path in @($finalPath,$reportPath,$receiptPath)){
  if(Test-Path -LiteralPath $path){throw "final output already exists; refusing overwrite: $path"}
}
$obs.state='FINALIZED_FOR_EVALUATION'
$obs.finished_at=(Get-Date).ToUniversalTime().ToString('o')
[IO.File]::WriteAllText($finalPath,($obs|ConvertTo-Json -Depth 14),(New-Object Text.UTF8Encoding($false)))
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $scriptDir 'evaluate-canary-v3.ps1') `
  -EvidenceJson $finalPath -OutJson $reportPath | Out-Null
$evalExit=$LASTEXITCODE
if($evalExit -notin @(0,2)){
  Remove-Item -LiteralPath $finalPath -Force
  if(Test-Path -LiteralPath $reportPath){Remove-Item -LiteralPath $reportPath -Force}
  throw "evaluate-canary-v3 failed with exit $evalExit"
}
$report=Get-Content -LiteralPath $reportPath -Raw|ConvertFrom-Json
$receipt=[ordered]@{
  schema_version=1;run_id=[string]$run.run_id
  state=if($evalExit -eq 0){'READY_FOR_STABLE_REVIEW'}else{'HOLD'}
  decision=[string]$report.decision;evaluated_at=(Get-Date).ToUniversalTime().ToString('o')
  observations_sha256=Get-Sha $obsPath;final_evidence_sha256=Get-Sha $finalPath
  runtime_binding_checkpoint='AV-CANARY-STAGE-RUNTIME-BINDING-20260917-56'
  installed_runtime=$runtime.installed_runtime
  evaluator_report_sha256=Get-Sha $reportPath
  baseline_receipt_sha256=Get-Sha (Join-Path $dir 'STAGE-10-BASELINE.json')
  restart_receipt_sha256=Get-Sha (Join-Path $dir 'STAGE-20-RESTART.json')
  cleanup_receipt_sha256=Get-Sha (Join-Path $dir 'STAGE-30-CLEANUP.json')
}
[IO.File]::WriteAllText($receiptPath,($receipt|ConvertTo-Json -Depth 6),(New-Object Text.UTF8Encoding($false)))
$receipt|ConvertTo-Json -Depth 6
exit $evalExit
