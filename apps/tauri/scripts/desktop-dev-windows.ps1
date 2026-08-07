# Windows desktop dev: cargo + MSVC + tauri dev
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

function Import-DotEnvFile {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return }
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith('#')) { return }
        if ($line -match '^(?<key>[A-Za-z_][A-Za-z0-9_]*)=(?<val>.*)$') {
            $value = $matches.val.Trim().Trim('"').Trim("'")
            Set-Item -Path "env:$($matches.key)" -Value $value
        }
    }
}

# 强制以 apps/tauri/.env 为准，避免系统里残留旧 VITE_API_BASE_URL（如 sqginx）
Import-DotEnvFile (Join-Path $Root ".env")
Import-DotEnvFile (Join-Path $Root ".env.local")
Import-DotEnvFile (Join-Path $Root ".env.development")
Import-DotEnvFile (Join-Path $Root ".env.development.local")
if ($env:VITE_API_BASE_URL) {
    Write-Host "VITE_API_BASE_URL=$($env:VITE_API_BASE_URL)"
}

$CargoBin = Join-Path $env:USERPROFILE ".cargo\bin"
if ((Test-Path $CargoBin) -and ($env:Path -notlike "*$CargoBin*")) {
    $env:Path = "$CargoBin;$env:Path"
}

if (-not (Get-Command cargo -ErrorAction SilentlyContinue)) {
    throw @"
cargo not found.

1. Install Rust: https://rustup.rs  (or run: winget install Rustlang.Rustup)
2. Close and reopen PowerShell, then retry: npm run tauri:win:dev
"@
}

function Import-MsvcDevEnvironment {
    if (Get-Command link.exe -ErrorAction SilentlyContinue) {
        return $true
    }
    $vswhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
    if (-not (Test-Path $vswhere)) {
        return $false
    }
    $installPath = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath 2>$null
    if (-not $installPath) {
        return $false
    }
    $vcvars = Join-Path $installPath "VC\Auxiliary\Build\vcvars64.bat"
    if (-not (Test-Path $vcvars)) {
        return $false
    }
    Write-Host "Loading MSVC environment from: $installPath"
    cmd /c "`"$vcvars`" >nul 2>&1 && set" | ForEach-Object {
        if ($_ -match '^(?<key>[^=]+)=(?<val>.*)$') {
            Set-Item -Path "env:$($matches.key)" -Value $matches.val
        }
    }
    return [bool](Get-Command link.exe -ErrorAction SilentlyContinue)
}

if (-not (Import-MsvcDevEnvironment)) {
    throw @"
MSVC linker (link.exe) not found. Tauri on Windows needs Visual Studio C++ Build Tools.

Install (admin PowerShell):
  winget install -e --id Microsoft.VisualStudio.2022.BuildTools --override ""--wait --passive --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended""

Then reopen PowerShell and run: npm run tauri:win:dev
"@
}

if (-not (Test-Path "src-tauri\resources\bin\mihomo.exe")) {
    Write-Host "mihomo.exe missing, running fetch:mihomo ..."
    npm run fetch:mihomo
}

npx tauri dev
