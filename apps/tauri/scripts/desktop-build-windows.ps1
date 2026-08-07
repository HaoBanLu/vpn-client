# Windows desktop release build: ensure cargo is on PATH, then run tauri build
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

# createUpdaterArtifacts + pubkey 时需要私钥；tauri build 读取 TAURI_SIGNING_PRIVATE_KEY（可为路径或内容）
# Windows 无法传递空环境变量，因此密码必须非空（见 setup:updater）
if (-not $env:TAURI_SIGNING_PRIVATE_KEY) {
    $UpdaterKey = Join-Path $Root ".tauri\updater.key"
    if (Test-Path $UpdaterKey) {
        $env:TAURI_SIGNING_PRIVATE_KEY = $UpdaterKey
        Write-Host "Using updater signing key: $UpdaterKey"
    } else {
        Write-Host "WARNING: .tauri\updater.key missing. Run: npm run setup:updater"
        Write-Host "         Or set TAURI_SIGNING_PRIVATE_KEY"
    }
}

if (-not $env:TAURI_SIGNING_PRIVATE_KEY_PASSWORD) {
    $PasswordPath = Join-Path $Root ".tauri\updater.key.password"
    if (Test-Path $PasswordPath) {
        $password = (Get-Content $PasswordPath -Raw).Trim()
        if ([string]::IsNullOrWhiteSpace($password)) {
            throw "Updater password file is empty: $PasswordPath. Re-run: npm run setup:updater -- -Force"
        }
        $env:TAURI_SIGNING_PRIVATE_KEY_PASSWORD = $password
        Write-Host "Using updater password from: $PasswordPath"
    } elseif ($env:TAURI_SIGNING_PRIVATE_KEY) {
        throw @"
TAURI_SIGNING_PRIVATE_KEY_PASSWORD is required on Windows (empty env vars are dropped).
Run: npm run setup:updater -- -Force
Or set TAURI_SIGNING_PRIVATE_KEY_PASSWORD to a non-empty value.
"@
    }
}

# 与 macOS/Linux desktop-build-unix.sh 一致：打包前确保 mihomo 已下载
if (-not (Test-Path (Join-Path $Root "src-tauri\resources\bin\mihomo.exe"))) {
    Write-Host "mihomo.exe missing; running fetch:mihomo ..."
    npm run fetch:mihomo
}

& "$PSScriptRoot\fetch-bundle-tools.ps1" -Target nsis

npx tauri build
if ($LASTEXITCODE -ne 0) { throw "tauri build failed" }
