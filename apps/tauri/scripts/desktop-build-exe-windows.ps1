# 仅编译 exe，不生成安装包（网络受限时的兜底方案）
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

# 生产构建优先 .env.production，再回落 .env（避免旧域名残留）
Import-DotEnvFile (Join-Path $Root ".env")
Import-DotEnvFile (Join-Path $Root ".env.local")
Import-DotEnvFile (Join-Path $Root ".env.production")
Import-DotEnvFile (Join-Path $Root ".env.production.local")
if ($env:VITE_API_BASE_URL) {
    Write-Host "VITE_API_BASE_URL=$($env:VITE_API_BASE_URL)"
}

$CargoBin = Join-Path $env:USERPROFILE ".cargo\bin"
if ((Test-Path $CargoBin) -and ($env:Path -notlike "*$CargoBin*")) {
    $env:Path = "$CargoBin;$env:Path"
}

if (-not (Get-Command cargo -ErrorAction SilentlyContinue)) {
    throw "cargo not found. Install Rust from https://rustup.rs and restart the terminal."
}

npx tauri build --no-bundle
if ($LASTEXITCODE -ne 0) { throw "tauri build --no-bundle failed" }

$exe = Join-Path $Root "src-tauri\target\release\vpn-tauri.exe"
$binDir = Join-Path $Root "src-tauri\target\release\bin"
New-Item -ItemType Directory -Force -Path $binDir | Out-Null
$mihomo = Join-Path $Root "src-tauri\resources\bin\mihomo.exe"
if (Test-Path $mihomo) {
    Copy-Item $mihomo (Join-Path $binDir "mihomo.exe") -Force
}

Write-Host "EXE output: $exe"
Write-Host "Copy mihomo alongside exe: $binDir\mihomo.exe"
