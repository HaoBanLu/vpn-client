# Verify Tauri app build prerequisites and generated overlay state.
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$CargoBin = Join-Path $env:USERPROFILE ".cargo\bin"
if ((Test-Path $CargoBin) -and ($env:Path -notlike "*$CargoBin*")) {
    $env:Path = "$CargoBin;$env:Path"
}

Write-Host "1/4 Sync Android VPN overlay"
& "$PSScriptRoot\sync-android-vpn.ps1"

Write-Host "2/5 Frontend unit tests"
cmd /c "npm run test"
if ($LASTEXITCODE -ne 0) { throw "npm run test failed" }

Write-Host "3/5 Frontend build"
cmd /c "npm run build"
if ($LASTEXITCODE -ne 0) { throw "npm run build failed" }

Write-Host "4/5 Rust cargo check"
Push-Location "$Root\src-tauri"
try {
    cmd /c "cargo check"
    if ($LASTEXITCODE -ne 0) { throw "cargo check failed" }
} finally {
    Pop-Location
}

Write-Host "5/5 PowerShell script syntax"
Get-ChildItem -Path "$PSScriptRoot" -Filter "*.ps1" | ForEach-Object {
    $script = Get-Content -Raw $_.FullName
    [scriptblock]::Create($script) | Out-Null
}

Write-Host "Verify OK"
