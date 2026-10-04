param(
  [Parameter(Mandatory=$true)][string]$EvidenceJson,
  [Parameter(Mandatory=$true)][string]$StatusJson,
  [Parameter(Mandatory=$true)][ValidateSet('Baseline','Restart','Cleanup')][string]$Stage,
  [Parameter(Mandatory=$true)][string]$OutJson
)
$ErrorActionPreference='Stop'
$evidencePath=[IO.Path]::GetFullPath($EvidenceJson)
$statusPath=[IO.Path]::GetFullPath($StatusJson)
$out=[IO.Path]::GetFullPath($OutJson)
if($out -eq $evidencePath){throw 'OutJson must be a new file; source evidence is immutable'}
if(-not (Test-Path -LiteralPath $evidencePath -PathType Leaf)){throw "Evidence file not found: $evidencePath"}
if(-not (Test-Path -LiteralPath $statusPath -PathType Leaf)){throw "Status JSON not found: $statusPath"}
$e=Get-Content -LiteralPath $evidencePath -Raw|ConvertFrom-Json
$s=Get-Content -LiteralPath $statusPath -Raw|ConvertFrom-Json
if([int]$e.schema_version -ne 3){throw 'Evidence schema_version must be 3'}
if([int]$s.schema_version -ne 1){throw 'Status schema_version must be 1'}

switch($Stage){
  'Baseline' {
    $e.baseline.gateway_runtime_online=[bool]$s.gateway.runtime_online
    $e.baseline.required_routes_up=[bool]$s.gateway.required_routes_up
    $e.baseline.active_sessions=[long]$s.gateway.active_sessions
    $e.baseline.pending_handshakes=[long]$s.gateway.pending_handshakes
    $e.baseline.agent_media_queue=[long]$s.gateway.agent_media_queue
    $e.baseline.agent_control_queue=[long]$s.gateway.agent_control_queue
    $e.baseline.legacy_agent_connected=[bool]$s.legacy.connected
    $e.baseline.legacy_managed_players=[long]$s.legacy.managed_players
    $e.baseline.legacy_agent_queue=[long]$s.legacy.queue
    $e.baseline.legacy_agent_dropped=[long]$s.legacy.dropped
    $e.baseline.legacy_agent_connects=[long]$s.legacy.connects
  }
  'Restart' {
    $e.live.restart.agent_connects_after=[long]$s.legacy.connects
    $e.live.restart.agent_dropped_after=[long]$s.legacy.dropped
    $e.live.restart.agent_queue_after=[long]$s.legacy.queue
    $e.live.restart.gateway_route_up_after=([bool]$s.gateway.runtime_online -and [bool]$s.gateway.required_routes_up)
  }
  'Cleanup' {
    $e.live.cleanup.active_sessions_final=[long]$s.gateway.active_sessions
    $e.live.cleanup.pending_handshakes_final=[long]$s.gateway.pending_handshakes
    $e.live.cleanup.legacy_managed_players_final=[long]$s.legacy.managed_players
    $e.live.cleanup.legacy_agent_queue_final=[long]$s.legacy.queue
  }
}
$parent=Split-Path -Parent $out
if($parent -and -not (Test-Path -LiteralPath $parent)){New-Item -ItemType Directory -Path $parent -Force|Out-Null}
$json=$e|ConvertTo-Json -Depth 12
[IO.File]::WriteAllText($out,$json,(New-Object Text.UTF8Encoding($false)))
$json
