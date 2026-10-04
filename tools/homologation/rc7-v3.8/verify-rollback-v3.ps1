param(
  [Parameter(Mandatory=$true)][string]$ManifestJson,
  [string]$OutJson
)
$ErrorActionPreference='Stop'
$manifestPath=[IO.Path]::GetFullPath($ManifestJson)
if(-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)){throw "Rollback manifest not found: $manifestPath"}
$m=Get-Content -LiteralPath $manifestPath -Raw|ConvertFrom-Json
$base=Split-Path -Parent $manifestPath
$checks=@()
$allPass=$true
function Add-Check([string]$name,[bool]$pass,[string]$detail){
  $script:checks += [pscustomobject]@{check=$name;pass=$pass;detail=$detail}
  if(-not $pass){$script:allPass=$false}
}
Add-Check 'schema_version' ([int]$m.schema_version -eq 1) ([string]$m.schema_version)
Add-Check 'manifest_state' ([string]$m.state -eq 'PASS') ([string]$m.state)
Add-Check 'no_config_backup' ([int]$m.configuration_files_copied -eq 0) ([string]$m.configuration_files_copied)
Add-Check 'no_deployment' (-not [bool]$m.deployment_performed) ([string]$m.deployment_performed)
$seen=@{}
foreach($entry in @($m.backups)){
  $component=[string]$entry.component
  if([string]::IsNullOrWhiteSpace($component)){Add-Check 'component_name' $false 'empty';continue}
  if($seen.ContainsKey($component)){Add-Check "unique:$component" $false 'duplicate';continue}
  $seen[$component]=$true
  $file=Join-Path $base ([string]$entry.backup_file)
  $exists=Test-Path -LiteralPath $file -PathType Leaf
  Add-Check "exists:$component" $exists $file
  if(-not $exists){continue}
  $hash=(Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
  Add-Check "hash:$component" ($hash -eq [string]$entry.sha256) $hash
  $bytes=(Get-Item -LiteralPath $file).Length
  Add-Check "bytes:$component" ($bytes -eq [long]$entry.bytes) ([string]$bytes)
  Add-Check "destination:$component" (-not [string]::IsNullOrWhiteSpace([string]$entry.original_path)) ([string]$entry.original_path)
}
foreach($required in @('forge','gateway','paper')){Add-Check "required:$required" $seen.ContainsKey($required) $required}
$report=[ordered]@{state=if($allPass){'PASS'}else{'FAIL'};checkpoint=[string]$m.checkpoint;checks=$checks.Count;failed=@($checks|Where-Object{-not $_.pass});details=$checks}
$json=$report|ConvertTo-Json -Depth 8
if($OutJson){[IO.File]::WriteAllText([IO.Path]::GetFullPath($OutJson),$json,(New-Object Text.UTF8Encoding($false)))}
$json
if($allPass){exit 0}else{exit 2}
