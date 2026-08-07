# Read API base / optional updater pubkey from .env and write into tauri.conf.json
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $Root ".env"
$ConfFile = Join-Path $Root "src-tauri\tauri.conf.json"

function Read-DotEnv($path) {
    $map = @{}
    if (-not (Test-Path $path)) { return $map }
    Get-Content $path | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) { return }
        $idx = $line.IndexOf("=")
        if ($idx -lt 1) { return }
        $key = $line.Substring(0, $idx).Trim()
        $val = $line.Substring($idx + 1).Trim().Trim('"')
        $map[$key] = $val
    }
    return $map
}

$envMap = Read-DotEnv $EnvFile
$apiBase = $envMap["VITE_API_BASE_URL"]
if ([string]::IsNullOrWhiteSpace($apiBase)) {
    $apiBase = "http://192.229.87.112:44080/api"
}
$pubkey = $envMap["TAURI_UPDATER_PUBKEY"]
if ([string]::IsNullOrWhiteSpace($pubkey) -and (Test-Path (Join-Path $Root ".tauri\updater.key.pub"))) {
    $pubkey = (Get-Content (Join-Path $Root ".tauri\updater.key.pub") -Raw).Trim()
}

$conf = Get-Content $ConfFile -Raw -Encoding UTF8 | ConvertFrom-Json
$endpoint = "$apiBase/v1/client/version/tauri-manifest?target={{target}}&current_version={{current_version}}"
$conf.plugins.updater.endpoints = @($endpoint)
# http 端点需显式允许，否则 release 启动会直接 panic
$useInsecure = $endpoint -match '^https?://' -and $endpoint -notmatch '^https://'
$conf.plugins.updater | Add-Member -NotePropertyName dangerousInsecureTransportProtocol -NotePropertyValue $useInsecure -Force
if (-not [string]::IsNullOrWhiteSpace($pubkey)) {
    $conf.plugins.updater.pubkey = $pubkey
    $conf.plugins.updater.active = $true
    Write-Host "Updater enabled (pubkey loaded)."
} else {
    $conf.plugins.updater.active = $false
    Write-Host "TAURI_UPDATER_PUBKEY missing; updater stays active:false (API fallback still works)."
}
if ($useInsecure) {
    Write-Host "WARNING: updater endpoint uses http; dangerousInsecureTransportProtocol=true"
}

# ConvertTo-Json may reorder keys; write UTF-8 without BOM and keep compact readable JSON via Node if available
$json = $conf | ConvertTo-Json -Depth 20
# PowerShell escapes & as \u0026; restore for Tauri endpoint templates
$json = $json -replace '\\u0026', '&'
[System.IO.File]::WriteAllText($ConfFile, $json + "`n", (New-Object System.Text.UTF8Encoding $false))
Write-Host "Updater endpoint: $endpoint"
