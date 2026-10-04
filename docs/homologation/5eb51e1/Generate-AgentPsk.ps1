$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $rng.GetBytes($bytes)
    $base64 = [Convert]::ToBase64String($bytes)
    Write-Host "Generated 256-bit Akashic Voice agent PSK:"
    Write-Host $base64
    Write-Host ""
    Write-Host "Paste the same value into:"
    Write-Host "  Velocity: agent-psk.lobby.base64"
    Write-Host "  Paper:    gateway.psk-base64"
} finally {
    [Array]::Clear($bytes, 0, $bytes.Length)
    $rng.Dispose()
}
