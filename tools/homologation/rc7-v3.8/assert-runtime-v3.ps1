param([Parameter(Mandatory=$true)][string]$RunDir)
$ErrorActionPreference='Stop'
$dir=[IO.Path]::GetFullPath($RunDir)
function Get-Sha([string]$path){(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()}
function Need([string]$path,[string]$label){
  if(-not(Test-Path -LiteralPath $path -PathType Leaf)){throw "$label not found: $path"}
  return $path
}
$runPath=Need (Join-Path $dir 'RUN-MANIFEST.json') 'run manifest'
$run=Get-Content -LiteralPath $runPath -Raw|ConvertFrom-Json
if([int]$run.schema_version -ne 1 -or [string]$run.checkpoint -ne 'AV-CANARY-EVIDENCE-CHAIN-20260917-52'){throw 'run manifest schema/checkpoint mismatch'}
if([string]$run.runtime_binding_checkpoint -ne 'AV-CANARY-LIVE-RUNTIME-BINDING-20260917-54'){throw 'run manifest lacks live runtime binding'}
$installPath=Need (Join-Path $dir 'install-receipt.json') 'install receipt'
if((Get-Sha $installPath) -ne [string]$run.install_receipt_sha256){throw 'install receipt hash mismatch'}
$install=Get-Content -LiteralPath $installPath -Raw|ConvertFrom-Json
if([string]$install.state -ne 'PASS_INSTALLED'){throw 'install receipt is not PASS_INSTALLED'}
if(@($install.installed).Count -ne 3){throw 'install receipt must contain exactly three runtime entries'}
$seen=@{};$current=[ordered]@{}
foreach($item in @($install.installed)){
  $component=[string]$item.component
  if($component -notin @('forge','gateway','paper')){throw "unexpected runtime component: $component"}
  if($seen.ContainsKey($component)){throw "duplicate runtime component: $component"}
  $seen[$component]=$true
  $destination=[IO.Path]::GetFullPath([string]$item.destination)
  if(-not(Test-Path -LiteralPath $destination -PathType Leaf)){throw "installed runtime missing during canary: $component"}
  $actual=Get-Sha $destination
  $expectedHash=[string]$run.installed_runtime.$component
  if(-not $expectedHash){throw "run manifest missing runtime hash: $component"}
  if($actual -ne $expectedHash){throw "installed runtime changed during canary: $component"}
  $current[$component]=$actual
}
foreach($required in @('forge','gateway','paper')){
  if(-not $seen.ContainsKey($required)){throw "install receipt missing runtime component: $required"}
}
[ordered]@{
  state='PASS'
  checkpoint='AV-CANARY-STAGE-RUNTIME-BINDING-20260917-56'
  run_id=[string]$run.run_id
  installed_runtime=[pscustomobject]$current
}|ConvertTo-Json -Depth 5 -Compress
