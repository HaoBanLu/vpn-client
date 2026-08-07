# Generate Tauri updater minisign keypair into .tauri/ and apply tauri.conf.json
# Windows cannot pass empty env vars, so keys always use a non-empty password stored in
# .tauri/updater.key.password (gitignored with .tauri/).
param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$CargoBin = Join-Path $env:USERPROFILE ".cargo\bin"
if ((Test-Path $CargoBin) -and ($env:Path -notlike "*$CargoBin*")) {
    $env:Path = "$CargoBin;$env:Path"
}

$KeyDir = Join-Path $Root ".tauri"
New-Item -ItemType Directory -Force -Path $KeyDir | Out-Null
$KeyPath = Join-Path $KeyDir "updater.key"
$PasswordPath = Join-Path $KeyDir "updater.key.password"

function New-UpdaterPassword {
    $bytes = New-Object byte[] 24
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return [Convert]::ToBase64String($bytes)
}

if (-not (Test-Path $PasswordPath) -or $Force) {
    $password = New-UpdaterPassword
    Set-Content -Path $PasswordPath -Value $password -NoNewline -Encoding ascii
    Write-Host "Wrote updater password file: $PasswordPath"
} else {
    $password = (Get-Content $PasswordPath -Raw).Trim()
    if ([string]::IsNullOrWhiteSpace($password)) {
        throw "Password file is empty: $PasswordPath (Windows cannot use empty updater passwords). Re-run with -Force."
    }
}

if ($Force -or -not (Test-Path $KeyPath)) {
    Write-Host "Generating updater signing key..."
    $env:CI = "true"
    # Avoid PowerShell eating quotes: pass password via env for the child cmd
    $env:TAURI_SIGNER_PASSWORD = $password
    cmd /c "npx tauri signer generate -w `"$KeyPath`" -f --ci -p %TAURI_SIGNER_PASSWORD%"
    Remove-Item Env:TAURI_SIGNER_PASSWORD -ErrorAction SilentlyContinue
    if ($LASTEXITCODE -ne 0) {
        throw "tauri signer generate failed. Ensure @tauri-apps/cli is installed."
    }
} else {
    Write-Host "Key already exists: $KeyPath (use -Force to regenerate)"
}

$PubPath = "$KeyPath.pub"
if (Test-Path $PubPath) {
    Write-Host "Public key file: $PubPath"
    Get-Content $PubPath
}

& "$PSScriptRoot\apply-updater-config.ps1"
Write-Host "Done. Build scripts auto-load .tauri/updater.key + updater.key.password."
Write-Host "When publishing, set updater_signature on app_versions and upload the signed installer."
