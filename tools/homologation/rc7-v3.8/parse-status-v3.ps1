param(
  [Parameter(Mandatory=$true)][string]$GatewayStatus,
  [Parameter(Mandatory=$true)][string]$LegacyDebug,
  [Parameter(Mandatory=$true)][string]$OutJson
)
$ErrorActionPreference='Stop'
$gatewayPath=[IO.Path]::GetFullPath($GatewayStatus)
$legacyPath=[IO.Path]::GetFullPath($LegacyDebug)
if(-not (Test-Path -LiteralPath $gatewayPath -PathType Leaf)){throw "Gateway status file not found: $gatewayPath"}
if(-not (Test-Path -LiteralPath $legacyPath -PathType Leaf)){throw "Legacy debug file not found: $legacyPath"}
$gw=Get-Content -LiteralPath $gatewayPath -Raw
$legacy=Get-Content -LiteralPath $legacyPath -Raw

function Need-Match([string]$text,[string]$pattern,[string]$label){
  $m=[regex]::Match($text,$pattern,[Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [Text.RegularExpressions.RegexOptions]::Multiline)
  if(-not $m.Success){throw "Missing or malformed $label"}
  return $m
}

$runtime=Need-Match $gw 'Runtime:\s*(ONLINE|OFFLINE)\s*\|\s*UDP\s*(\d+)\s*\|\s*Agent\s*(\d+)' 'Gateway Runtime line'
$sessions=Need-Match $gw 'Sessions:\s*active=(\d+)\s*pending=(\d+)\s*opened=(\d+)\s*closed=(\d+)' 'Gateway Sessions line'
$routesLine=Need-Match $gw 'Routes:\s*([^\r\n]+)' 'Gateway Routes line'
$agentQ=Need-Match $gw 'Agent queue:\s*dropped=(\d+)\s*mediaQ=(\d+)\s*controlQ=(\d+)' 'Gateway Agent queue line'
$routes=@{}
$routeText=$routesLine.Groups[1].Value.Trim()
if($routeText -ne 'none'){
  foreach($part in $routeText -split ','){
    $item=$part.Trim()
    $m=[regex]::Match($item,'^([^=]+)=(UP|DOWN)$',[Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if(-not $m.Success){throw "Malformed route entry: $item"}
    $name=$m.Groups[1].Value.Trim()
    if([string]::IsNullOrWhiteSpace($name)){throw 'Empty route name'}
    $routes[$name]=($m.Groups[2].Value -ieq 'UP')
  }
}
$allRoutesUp=($routes.Count -gt 0 -and @($routes.Values|Where-Object{-not $_}).Count -eq 0)

$legacyMode=Need-Match $legacy 'Akashic Voice:\s*mode=agent,\s*backend=([^,]+),\s*gateway=([^,]+),\s*connected=(true|false)' 'Legacy Agent mode line'
$legacyAgent=Need-Match $legacy 'Agent:\s*managed=(\d+),\s*deliveries=(\d+),\s*resets=(\d+),\s*queue=(\d+),\s*dropped=(\d+),\s*connects=(\d+)' 'Legacy Agent counters line'

$report=[ordered]@{
  schema_version=1
  gateway=[ordered]@{
    runtime_online=($runtime.Groups[1].Value -ieq 'ONLINE')
    udp_port=[int]$runtime.Groups[2].Value
    agent_port=[int]$runtime.Groups[3].Value
    active_sessions=[long]$sessions.Groups[1].Value
    pending_handshakes=[long]$sessions.Groups[2].Value
    sessions_opened=[long]$sessions.Groups[3].Value
    sessions_closed=[long]$sessions.Groups[4].Value
    routes=$routes
    required_routes_up=$allRoutesUp
    agent_dropped=[long]$agentQ.Groups[1].Value
    agent_media_queue=[long]$agentQ.Groups[2].Value
    agent_control_queue=[long]$agentQ.Groups[3].Value
  }
  legacy=[ordered]@{
    backend=$legacyMode.Groups[1].Value.Trim()
    gateway=$legacyMode.Groups[2].Value.Trim()
    connected=($legacyMode.Groups[3].Value -ieq 'true')
    managed_players=[long]$legacyAgent.Groups[1].Value
    deliveries=[long]$legacyAgent.Groups[2].Value
    resets=[long]$legacyAgent.Groups[3].Value
    queue=[long]$legacyAgent.Groups[4].Value
    dropped=[long]$legacyAgent.Groups[5].Value
    connects=[long]$legacyAgent.Groups[6].Value
  }
}
$json=$report|ConvertTo-Json -Depth 8
$out=[IO.Path]::GetFullPath($OutJson)
$parent=Split-Path -Parent $out
if($parent -and -not (Test-Path -LiteralPath $parent)){New-Item -ItemType Directory -Path $parent -Force|Out-Null}
[IO.File]::WriteAllText($out,$json,(New-Object Text.UTF8Encoding($false)))
$json
