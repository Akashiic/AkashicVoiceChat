param(
  [Parameter(Mandatory=$true)][string]$ForgeJar,
  [Parameter(Mandatory=$true)][string]$GatewayJar,
  [Parameter(Mandatory=$true)][string]$PaperJar,
  [string]$AddonApiJar,
  [string]$OutJson
)
$ErrorActionPreference='Stop'
$expectedPath=Join-Path $PSScriptRoot 'EXPECTED-SHA256.json'
$expected=Get-Content -LiteralPath $expectedPath -Raw|ConvertFrom-Json
$inputs=[ordered]@{
  forge=$ForgeJar
  gateway=$GatewayJar
  paper=$PaperJar
}
if($AddonApiJar){$inputs.addon_api=$AddonApiJar}
$results=@()
$ok=$true
foreach($key in $inputs.Keys){
  $path=[IO.Path]::GetFullPath([string]$inputs[$key])
  $item=$expected.artifacts.$key
  $exists=Test-Path -LiteralPath $path -PathType Leaf
  $sha=$null
  $match=$false
  if($exists){
    $sha=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    $match=($sha -eq [string]$item.sha256)
  }
  if(-not $exists -or -not $match){$ok=$false}
  $results += [pscustomobject]@{role=$key;path=$path;exists=$exists;sha256=$sha;expected=[string]$item.sha256;match=$match}
}$report=[ordered]@{
  state=if($ok){'PASS'}else{'FAIL'}
  checkpoint=[string]$expected.checkpoint
  checked_at=(Get-Date).ToUniversalTime().ToString('o')
  results=$results
}
$json=$report|ConvertTo-Json -Depth 6
if($OutJson){
  $parent=Split-Path -Parent ([IO.Path]::GetFullPath($OutJson))
  if($parent -and -not (Test-Path $parent)){New-Item -ItemType Directory -Path $parent -Force|Out-Null}
  [IO.File]::WriteAllText([IO.Path]::GetFullPath($OutJson),$json,(New-Object Text.UTF8Encoding($false)))
}
$json
if(-not $ok){exit 2}
exit 0