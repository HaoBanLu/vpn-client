# Windows Android dev: sync VPN overlay, ensure cargo PATH, then run Tauri Android dev.
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$CargoBin = Join-Path $env:USERPROFILE ".cargo\bin"
if ((Test-Path $CargoBin) -and ($env:Path -notlike "*$CargoBin*")) {
    $env:Path = "$CargoBin;$env:Path"
}

& "$PSScriptRoot\sync-android-vpn.ps1"

cmd /c "npx tauri android dev"
if ($LASTEXITCODE -ne 0) {
    throw "Tauri Android dev failed"
}
