param(
  [Parameter(Mandatory=$true)][string]$ForgeJar,
  [Parameter(Mandatory=$true)][string]$ExpectedForgeSha256,
  [Parameter(Mandatory=$true)][int]$MinecraftPid,
  [Parameter(Mandatory=$true)][string]$JstackExe,
  [Parameter(Mandatory=$true)][string]$OutJson,
  [string]$FmlLog = ''
)
$ErrorActionPreference = 'Stop'

function Get-Sha256([string]$Path) {
  return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}
function Write-Receipt($Receipt, [int]$ExitCode) {
  $parent = Split-Path -Parent $OutJson
  if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
  $Receipt | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $OutJson -Encoding UTF8
  Write-Output ($Receipt | ConvertTo-Json -Depth 8)
  exit $ExitCode
}
function Count-Matches([string]$Text, [string]$Pattern) {
  if ([string]::IsNullOrEmpty($Text)) { return 0 }
  return ([regex]::Matches($Text, $Pattern, 'IgnoreCase')).Count
}

if (!(Test-Path -LiteralPath $ForgeJar -PathType Leaf)) { throw 'ForgeJar does not exist' }
if (!(Test-Path -LiteralPath $JstackExe -PathType Leaf)) { throw 'JstackExe does not exist' }
if ($ExpectedForgeSha256 -notmatch '^[0-9a-fA-F]{64}$') { throw 'Expected SHA256 must be 64 hex chars' }
$actualSha = Get-Sha256 $ForgeJar
$expectedSha = $ExpectedForgeSha256.ToLowerInvariant()
if ($actualSha -ne $expectedSha) {
  Write-Receipt ([ordered]@{
    schema_version=1; state='HOLD_ARTIFACT_MISMATCH'; checkpoint='AV-LIVE-CLIENT-PROBE-V38'
    artifact=[ordered]@{ sha256=$actualSha; expected_sha256=$expectedSha; matches=$false }
    remote_media_proven=$false; stable_release_approved=$false
  }) 2
}

$process = Get-Process -Id $MinecraftPid -ErrorAction Stop
$tmpOut = [IO.Path]::GetTempFileName()
$tmpErr = [IO.Path]::GetTempFileName()
try {
  $probe = Start-Process -FilePath $JstackExe -ArgumentList ([string]$MinecraftPid) `
    -RedirectStandardOutput $tmpOut -RedirectStandardError $tmpErr -WindowStyle Hidden -PassThru
  if (!$probe.WaitForExit(15000)) {
    try { $probe.Kill() } catch {}
    Write-Receipt ([ordered]@{
      schema_version=1; state='HOLD_JSTACK_TIMEOUT'; checkpoint='AV-LIVE-CLIENT-PROBE-V38'
      artifact=[ordered]@{ sha256=$actualSha; expected_sha256=$expectedSha; matches=$true }
      remote_media_proven=$false; stable_release_approved=$false
    }) 2
  }
  $jstack = (Get-Content -LiteralPath $tmpOut -Raw -ErrorAction SilentlyContinue) + "`n" +
    (Get-Content -LiteralPath $tmpErr -Raw -ErrorAction SilentlyContinue)
} finally {
  Remove-Item -LiteralPath $tmpOut,$tmpErr -Force -ErrorAction SilentlyContinue
}
$threads = [ordered]@{
  receive = $jstack.Contains('AkashicVoice-Voice-Client-Recv')
  maintenance = $jstack.Contains('AkashicVoice-Voice-Client-Maintenance')
  audio = $jstack.Contains('AkashicVoice-Audio')
  microphone = $jstack.Contains('AkashicVoice-Mic')
  datagram_receive = $jstack.Contains('java.net.DatagramSocket.receive')
  openal_capture = $jstack.Contains('OpenALCaptureBackend$Device.read')
}
$runtimePass = -not ($threads.Values -contains $false)

$logHealth = [ordered]@{
  supplied = -not [string]::IsNullOrWhiteSpace($FmlLog)
  exists = $false
  voice_timeout_matches = 0
  microphone_unavailable_matches = 0
  packet_handling_failure_matches = 0
  socket_error_matches = 0
}
if ($logHealth.supplied -and (Test-Path -LiteralPath $FmlLog -PathType Leaf)) {
  $logHealth.exists = $true
  $tail = (Get-Content -LiteralPath $FmlLog -Tail 5000 -ErrorAction Stop) -join "`n"
  $logHealth.voice_timeout_matches = Count-Matches $tail 'Voice server timed out'
  $logHealth.microphone_unavailable_matches = Count-Matches $tail 'Microphone unavailable'
  $logHealth.packet_handling_failure_matches = Count-Matches $tail 'Voice client packet handling failed'
  $logHealth.socket_error_matches = Count-Matches $tail 'Voice client socket error'
}

$receipt = [ordered]@{
  schema_version=1
  state= $(if ($runtimePass) { 'PASS_RUNTIME_PREREQUISITES' } else { 'HOLD_RUNTIME_INCOMPLETE' })
  checkpoint='AV-LIVE-CLIENT-PROBE-V38'
  observed_at_utc=(Get-Date).ToUniversalTime().ToString('o')
  artifact=[ordered]@{ sha256=$actualSha; expected_sha256=$expectedSha; matches=$true }
  process=[ordered]@{
    pid=$process.Id; name=$process.ProcessName; start_time=$process.StartTime.ToString('o')
    window_title=$process.MainWindowTitle
  }
  threads=$threads
  authenticated_runtime_lifecycle=$runtimePass
  log_health=$logHealth
  remote_media_proven=$false
  two_client_listening_proven=$false
  controlled_backend_restart_proven=$false
  physical_acoustic_quality_proven=$false
  mobile_real_device_proven=$false
  production_canary_proven=$false
  stable_release_approved=$false
  note='This probe reads artifact/process/thread/log metadata only. It does not inspect audio payloads or secrets.'
}
Write-Receipt $receipt $(if ($runtimePass) { 0 } else { 2 })
