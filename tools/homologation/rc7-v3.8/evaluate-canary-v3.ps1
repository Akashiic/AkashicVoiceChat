param(
  [Parameter(Mandatory=$true)][string]$EvidenceJson,
  [string]$OutJson
)
$ErrorActionPreference='Stop'
$evidencePath=[IO.Path]::GetFullPath($EvidenceJson)
if(-not (Test-Path -LiteralPath $evidencePath -PathType Leaf)){throw "Evidence file not found: $evidencePath"}
$e=Get-Content -LiteralPath $evidencePath -Raw|ConvertFrom-Json
$scriptDir=Split-Path -Parent $MyInvocation.MyCommand.Path
$expected=Get-Content -LiteralPath (Join-Path $scriptDir 'EXPECTED-SHA256.json') -Raw|ConvertFrom-Json
$releaseDir=[IO.Path]::GetFullPath((Join-Path $scriptDir $expected.release_dir))
$manifestPath=Join-Path $releaseDir 'RELEASE-MANIFEST.json'
$gates=New-Object System.Collections.Generic.List[object]
$schemaErrors=New-Object System.Collections.Generic.List[string]

function Add-Gate([string]$name,[bool]$pass,[string[]]$reasons){
  $script:gates.Add([pscustomobject]@{gate=$name;pass=$pass;state=if($pass){'PASS'}else{'HOLD'};reasons=$reasons})
}
function NonEmpty($value){ return ($null -ne $value -and -not [string]::IsNullOrWhiteSpace([string]$value)) }
function LongValue($value,[string]$name){
  if($null -eq $value){$script:schemaErrors.Add("Missing numeric field: $name");return -1}
  try{$n=[long]$value}catch{$script:schemaErrors.Add("Invalid numeric field: $name");return -1}
  if($n -lt 0){$script:schemaErrors.Add("Negative numeric field: $name");return -1}
  return $n
}
$artifactReasons=@()
if([int]$e.schema_version -ne 3){$artifactReasons+='schema_version must be 3'}
if([string]$e.checkpoint -ne [string]$expected.checkpoint){$artifactReasons+='checkpoint does not match sealed release'}
if([string]$e.artifact.verification_state -ne 'PASS'){$artifactReasons+='artifact verification_state is not PASS'}
if(-not (NonEmpty $e.artifact.verification_receipt)){$artifactReasons+='artifact verification receipt is missing'}
if(-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)){$artifactReasons+='sealed release manifest is missing'}
else {
  $actualManifest=(Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
  if([string]$e.artifact.release_manifest_sha256 -ne $actualManifest){$artifactReasons+='release manifest hash mismatch'}
}
if(NonEmpty $e.artifact.verification_receipt){
  $receiptPath=[string]$e.artifact.verification_receipt
  if(-not [IO.Path]::IsPathRooted($receiptPath)){$receiptPath=Join-Path (Split-Path -Parent $evidencePath) $receiptPath}
  if(-not (Test-Path -LiteralPath $receiptPath -PathType Leaf)){$artifactReasons+='artifact verification receipt file not found'}
  else {
    try{$receipt=Get-Content -LiteralPath $receiptPath -Raw|ConvertFrom-Json}catch{$receipt=$null}
    if($null -eq $receipt -or [string]$receipt.state -ne 'PASS'){$artifactReasons+='artifact verification receipt is not a PASS JSON receipt'}
    else {
      if([string]$receipt.checkpoint -ne [string]$expected.checkpoint){$artifactReasons+='verification receipt checkpoint mismatch'}
      $rows=@($receipt.results)
      foreach($role in @('forge','gateway','paper')){
        $matches=@($rows|Where-Object{[string]$_.role -eq $role})
        if($matches.Count -ne 1){$artifactReasons+="verification receipt must contain exactly one $role result";continue}
        $row=$matches[0];$want=[string]$expected.artifacts.$role.sha256
        if(-not [bool]$row.exists -or -not [bool]$row.match){$artifactReasons+="$role verification receipt is not a verified match";continue}
        if([string]$row.sha256 -ne $want -or [string]$row.expected -ne $want){$artifactReasons+="$role receipt hash mismatch";continue}
        if(-not (NonEmpty $row.path) -or -not (Test-Path -LiteralPath ([string]$row.path) -PathType Leaf)){$artifactReasons+="$role verified path is missing";continue}
        $current=(Get-FileHash -LiteralPath ([string]$row.path) -Algorithm SHA256).Hash.ToLowerInvariant()
        if($current -ne $want){$artifactReasons+="$role verified path no longer matches sealed release"}
      }
    }
  }
}
Add-Gate 'artifact_integrity' ($artifactReasons.Count -eq 0) $artifactReasons

$baselineReasons=@()
if(-not [bool]$e.baseline.gateway_runtime_online){$baselineReasons+='Gateway runtime not ONLINE'}
if(-not [bool]$e.baseline.required_routes_up){$baselineReasons+='required route not UP'}
if(-not [bool]$e.baseline.legacy_agent_connected){$baselineReasons+='Legacy Agent not connected'}
$active0=LongValue $e.baseline.active_sessions 'baseline.active_sessions'
$pending0=LongValue $e.baseline.pending_handshakes 'baseline.pending_handshakes'
$mediaQ0=LongValue $e.baseline.agent_media_queue 'baseline.agent_media_queue'
$controlQ0=LongValue $e.baseline.agent_control_queue 'baseline.agent_control_queue'
$managed0=LongValue $e.baseline.legacy_managed_players 'baseline.legacy_managed_players'
$legacyQ0=LongValue $e.baseline.legacy_agent_queue 'baseline.legacy_agent_queue'
$drops0=LongValue $e.baseline.legacy_agent_dropped 'baseline.legacy_agent_dropped'
$connects0=LongValue $e.baseline.legacy_agent_connects 'baseline.legacy_agent_connects'
Add-Gate 'baseline_health' ($baselineReasons.Count -eq 0) $baselineReasons

$twoReasons=@()
if((LongValue $e.live.two_client.tester_count 'live.two_client.tester_count') -lt 2){$twoReasons+='fewer than two real testers'}
if(-not [bool]$e.live.two_client.bidirectional_intelligible){$twoReasons+='bidirectional speech not confirmed'}
if(-not [bool]$e.live.two_client.expected_spatial_behavior){$twoReasons+='expected spatial/non-spatial behavior not confirmed'}
if([bool]$e.live.two_client.duplicate_or_ghost_audio){$twoReasons+='duplicate or ghost audio observed'}
Add-Gate 'real_two_client_listening' ($twoReasons.Count -eq 0) $twoReasons

$transferReasons=@()
if(-not [bool]$e.live.transfer.voice_resumed){$transferReasons+='voice did not resume after transfer'}
if(-not [bool]$e.live.transfer.session_reauthenticated){$transferReasons+='fresh voice session was not confirmed'}
if([bool]$e.live.transfer.route_down_observed){$transferReasons+='required route went DOWN during transfer'}
if([bool]$e.live.transfer.duplicate_or_ghost_audio){$transferReasons+='duplicate or ghost audio observed during transfer'}
Add-Gate 'cross_server_transfer' ($transferReasons.Count -eq 0) $transferReasons
$restartReasons=@()
$connectsAfter=LongValue $e.live.restart.agent_connects_after 'live.restart.agent_connects_after'
$dropsAfter=LongValue $e.live.restart.agent_dropped_after 'live.restart.agent_dropped_after'
$queueAfter=LongValue $e.live.restart.agent_queue_after 'live.restart.agent_queue_after'
if(-not [bool]$e.live.restart.voice_resumed){$restartReasons+='voice did not resume after backend restart'}
if($connectsAfter -le $connects0){$restartReasons+='Agent connect counter did not increase'}
if($dropsAfter -lt $drops0){$restartReasons+='Agent dropped counter regressed across reconnect'}
if($queueAfter -ne 0){$restartReasons+='Agent media queue did not drain after restart/speech'}
if(-not [bool]$e.live.restart.gateway_route_up_after){$restartReasons+='Gateway route not UP after restart'}
Add-Gate 'backend_restart_recovery' ($restartReasons.Count -eq 0) $restartReasons

$cleanupReasons=@()
$activeFinal=LongValue $e.live.cleanup.active_sessions_final 'live.cleanup.active_sessions_final'
$pendingFinal=LongValue $e.live.cleanup.pending_handshakes_final 'live.cleanup.pending_handshakes_final'
$managedFinal=LongValue $e.live.cleanup.legacy_managed_players_final 'live.cleanup.legacy_managed_players_final'
$legacyQFinal=LongValue $e.live.cleanup.legacy_agent_queue_final 'live.cleanup.legacy_agent_queue_final'
if($activeFinal -gt $active0){$cleanupReasons+='active sessions did not return to baseline or lower'}
if($pendingFinal -gt $pending0){$cleanupReasons+='pending handshakes did not return to baseline or lower'}
if($managedFinal -gt $managed0){$cleanupReasons+='managed players did not return to baseline or lower'}
if($legacyQFinal -ne 0){$cleanupReasons+='Legacy Agent queue not empty after canary clients left'}
if([bool]$e.live.cleanup.stale_voice_observed){$cleanupReasons+='stale voice observed after clean disconnect'}
Add-Gate 'clean_disconnect' ($cleanupReasons.Count -eq 0) $cleanupReasons

$acousticReasons=@()
if(-not [bool]$e.live.acoustic.performed){$acousticReasons+='physical acoustic pass not performed'}
if(-not [bool]$e.live.acoustic.normal_speech_intelligible){$acousticReasons+='normal speech not intelligible'}
if(-not [bool]$e.live.acoustic.whisper_intelligible){$acousticReasons+='whisper speech not intelligible'}
if([bool]$e.live.acoustic.loud_speech_clipping){$acousticReasons+='clipping observed on loud speech'}
if([bool]$e.live.acoustic.pumping_observed){$acousticReasons+='pumping observed'}
if([bool]$e.live.acoustic.unwanted_echo){$acousticReasons+='unwanted echo observed'}
if([bool]$e.live.acoustic.background_noise_unusable){$acousticReasons+='background noise made voice unusable'}
$agcDecision=[string]$e.live.acoustic.agc_limiter_decision
if($agcDecision -notin @('NOT_NEEDED','IMPLEMENTED_AND_ACCEPTED')){$acousticReasons+='AGC/limiter decision must be NOT_NEEDED or IMPLEMENTED_AND_ACCEPTED'}
if(-not (NonEmpty $e.live.acoustic.notes)){$acousticReasons+='acoustic notes are required'}
Add-Gate 'physical_acoustic_quality' ($acousticReasons.Count -eq 0) $acousticReasons

$hotplugReasons=@()
if(-not [bool]$e.live.output_hotplug.performed){$hotplugReasons+='physical output hot-plug test not performed'}
if(-not (NonEmpty $e.live.output_hotplug.device_before)){$hotplugReasons+='output device before disconnect is missing'}
if(-not (NonEmpty $e.live.output_hotplug.device_after)){$hotplugReasons+='output device after reconnect is missing'}
if(-not [bool]$e.live.output_hotplug.disconnect_observed){$hotplugReasons+='physical disconnect was not observed'}
if(-not [bool]$e.live.output_hotplug.reconnect_observed){$hotplugReasons+='physical reconnect was not observed'}
if(-not [bool]$e.live.output_hotplug.voice_resumed_without_rejoin){$hotplugReasons+='voice did not resume without server rejoin'}
if(-not [bool]$e.live.output_hotplug.minecraft_audio_resumed){$hotplugReasons+='Minecraft audio did not recover with voice output'}
if([bool]$e.live.output_hotplug.crash_or_hang){$hotplugReasons+='crash or hang observed during output hot-plug'}
if([bool]$e.live.output_hotplug.duplicate_or_ghost_audio){$hotplugReasons+='duplicate or ghost audio observed after output recovery'}
if(-not (NonEmpty $e.live.output_hotplug.notes)){$hotplugReasons+='output hot-plug notes are required'}
Add-Gate 'physical_output_hotplug_recovery' ($hotplugReasons.Count -eq 0) $hotplugReasons

$environmentReasons=@()
if(-not [bool]$e.live.environmental_acoustics.performed){$environmentReasons+='environmental acoustic listening pass not performed'}
if(-not [bool]$e.live.environmental_acoustics.outdoor_dry){$environmentReasons+='outdoor voice was not confirmed dry'}
if(-not [bool]$e.live.environmental_acoustics.room_effect_audible){$environmentReasons+='room acoustic change not audible'}
if(-not [bool]$e.live.environmental_acoustics.cave_effect_audible){$environmentReasons+='cave acoustic change not audible'}
if(-not [bool]$e.live.environmental_acoustics.underwater_effect_audible){$environmentReasons+='underwater filter not audible'}
if(-not [bool]$e.live.environmental_acoustics.transitions_clean){$environmentReasons+='environment transitions left stale/popping acoustic state'}
if(-not [bool]$e.live.environmental_acoustics.group_radio_dry){$environmentReasons+='group/radio inherited environmental acoustics'}
if(-not [bool]$e.live.environmental_acoustics.intelligibility_preserved){$environmentReasons+='environmental effects reduced speech intelligibility unacceptably'}
if(-not (NonEmpty $e.live.environmental_acoustics.notes)){$environmentReasons+='environmental acoustic notes are required'}
Add-Gate 'environmental_voice_acoustics' ($environmentReasons.Count -eq 0) $environmentReasons

$nametagReasons=@()
if(-not [bool]$e.live.nametag_indicator.performed){$nametagReasons+='real-game nametag indicator review not performed'}
if(-not [bool]$e.live.nametag_indicator.normal_suffix_aligned){$nametagReasons+='normal standing talking icon is not aligned as nametag suffix'}
if(-not [bool]$e.live.nametag_indicator.sneaking_suffix_aligned){$nametagReasons+='sneaking talking icon is not aligned as nametag suffix'}
if(-not [bool]$e.live.nametag_indicator.sleeping_suffix_aligned){$nametagReasons+='sleeping talking icon alignment not confirmed'}
if(-not [bool]$e.live.nametag_indicator.no_feet_or_world_origin_artifact){$nametagReasons+='feet/world-origin icon artifact still observed'}
if(-not [bool]$e.live.nametag_indicator.tracks_formatted_name_width){$nametagReasons+='talking icon does not track formatted nametag width'}
if(-not (NonEmpty $e.live.nametag_indicator.notes)){$nametagReasons+='nametag indicator notes are required'}
Add-Gate 'nametag_talking_suffix_visual' ($nametagReasons.Count -eq 0) $nametagReasons

$pttReasons=@()
if(-not [bool]$e.live.ptt_indicator.performed){$pttReasons+='real PTT indicator timing review not performed'}
if(-not [bool]$e.live.ptt_indicator.local_press_no_perceptible_delay){$pttReasons+='local PTT press indicator delay is perceptible'}
if(-not [bool]$e.live.ptt_indicator.local_release_no_perceptible_delay){$pttReasons+='local PTT release indicator delay is perceptible'}
if(-not [bool]$e.live.ptt_indicator.remote_start_behavior_acceptable){$pttReasons+='remote talking start indicator behavior is not acceptable'}
if(-not [bool]$e.live.ptt_indicator.remote_release_behavior_acceptable){$pttReasons+='remote talking release behavior is not acceptable'}
if(-not (NonEmpty $e.live.ptt_indicator.notes)){$pttReasons+='PTT indicator timing notes are required'}
Add-Gate 'ptt_indicator_latency_visual' ($pttReasons.Count -eq 0) $pttReasons

$mobileReasons=@()
$devices=@($e.live.mobile.devices)
foreach($required in @($e.live.mobile.required_clients)){
  $matches=@($devices|Where-Object{[string]$_.client -ieq [string]$required})
  if($matches.Count -eq 0){$mobileReasons+="missing real $required device result";continue}
  $ok=$false
  foreach($d in $matches){
    $duration=LongValue $d.duration_minutes "mobile.$required.duration_minutes"
    if($duration -gt 0 -and [bool]$d.join_ok -and [bool]$d.ptt_ok -and [bool]$d.listening_ok `
      -and -not [bool]$d.input_issue -and -not [bool]$d.thermal_issue -and (NonEmpty $d.battery_observation)){$ok=$true;break}
  }
  if(-not $ok){$mobileReasons+="$required result did not satisfy join/PTT/listening/thermal/battery evidence"}
}
Add-Gate 'mobile_pojav_amethyst' ($mobileReasons.Count -eq 0) $mobileReasons

$canaryReasons=@()
$plannedMinutes=LongValue $e.live.canary.planned_duration_minutes 'live.canary.planned_duration_minutes'
$observedMinutes=LongValue $e.live.canary.observed_duration_minutes 'live.canary.observed_duration_minutes'
$plannedTesters=LongValue $e.live.canary.planned_tester_count 'live.canary.planned_tester_count'
$observedTesters=LongValue $e.live.canary.observed_tester_count 'live.canary.observed_tester_count'
$crashes=LongValue $e.live.canary.crashes 'live.canary.crashes'
$disconnects=LongValue $e.live.canary.protocol_disconnects 'live.canary.protocol_disconnects'
$negative=LongValue $e.live.canary.player_negative_feedback_count 'live.canary.player_negative_feedback_count'
if($plannedMinutes -le 0){$canaryReasons+='planned canary duration must be greater than zero'}
if($observedMinutes -lt $plannedMinutes){$canaryReasons+='observed canary duration shorter than the predeclared plan'}
if($plannedTesters -lt 2){$canaryReasons+='planned tester count must be at least two'}
if($observedTesters -lt $plannedTesters){$canaryReasons+='observed tester count lower than the predeclared plan'}
if($crashes -ne 0){$canaryReasons+='crash observed during canary'}
if($disconnects -ne 0){$canaryReasons+='protocol disconnect observed during canary'}
if($negative -ne 0){$canaryReasons+='negative player feedback requires review'}
if(-not [bool]$e.live.canary.operator_accepts){$canaryReasons+='operator did not accept canary behavior'}
if(-not (NonEmpty $e.live.canary.notes)){$canaryReasons+='operator canary notes are required'}
Add-Gate 'production_canary_operator_feedback' ($canaryReasons.Count -eq 0) $canaryReasons

Add-Gate 'evidence_schema' ($schemaErrors.Count -eq 0) @($schemaErrors)
$allPass=(@($gates|Where-Object{-not $_.pass}).Count -eq 0)
$decision=if($allPass){'READY_FOR_STABLE_REVIEW'}else{'HOLD'}
$report=[ordered]@{
  schema_version=3
  tooling_version='3.8'
  checkpoint=[string]$e.checkpoint
  state=if($allPass){'PASS'}else{'HOLD'}
  decision=$decision
  evaluated_at=(Get-Date).ToUniversalTime().ToString('o')
  gates=$gates
  metrics=[ordered]@{
    connects_before=$connects0
    connects_after=$connectsAfter
    dropped_before=$drops0
    dropped_after=$dropsAfter
    active_sessions_before=$active0
    active_sessions_after=$activeFinal
    pending_handshakes_before=$pending0
    pending_handshakes_after=$pendingFinal
    managed_players_before=$managed0
    managed_players_after=$managedFinal
  }
}
$json=$report|ConvertTo-Json -Depth 8
if($OutJson){
  $out=[IO.Path]::GetFullPath($OutJson)
  $parent=Split-Path -Parent $out
  if($parent -and -not (Test-Path -LiteralPath $parent)){New-Item -ItemType Directory -Path $parent -Force|Out-Null}
  [IO.File]::WriteAllText($out,$json,(New-Object Text.UTF8Encoding($false)))
}
$json
if($allPass){exit 0}else{exit 2}
