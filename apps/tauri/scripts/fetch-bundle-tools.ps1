# 预下载 Tauri Windows 打包工具（WiX / NSIS），避免 tauri build 在线拉取失败
param(
    [ValidateSet("nsis", "all")]
    [string]$Target = "nsis",
    [string]$OfflineNsisZip = "",
    [string]$OfflineWixZip = ""
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$TauriDir = Join-Path $Root "src-tauri"

function Get-TauriToolsDirs {
    $dirs = @(
        (Join-Path $env:LOCALAPPDATA "tauri"),
        (Join-Path $TauriDir "target\.tauri")
    )
    foreach ($dir in $dirs) {
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
    }
    return $dirs
}

function Download-WithFallback {
    param(
        [string[]]$Urls,
        [string]$OutFile
    )
    foreach ($url in $Urls) {
        try {
            Write-Host "Downloading $url ..."
            Invoke-WebRequest -Uri $url -OutFile $OutFile -UseBasicParsing -TimeoutSec 180
            return
        } catch {
            Write-Warning "Download failed: $($_.Exception.Message)"
        }
    }
    throw @"
All download URLs failed for $OutFile
If you are behind a firewall, download the file in a browser and rerun with -OfflineZip <path>.
"@
}

function Install-WixTools {
    param([string[]]$BaseDirs)
    $zip = Join-Path $env:TEMP "wix314-binaries.zip"
    if ($OfflineWixZip -and (Test-Path $OfflineWixZip)) {
        Copy-Item $OfflineWixZip $zip -Force
    } else {
    $urls = @(
        "https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip",
        "https://mirror.ghproxy.com/https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip"
    )
    Download-WithFallback -Urls $urls -OutFile $zip
    }
    $extractRoot = Join-Path $env:TEMP "wix314-extract"
    if (Test-Path $extractRoot) { Remove-Item $extractRoot -Recurse -Force }
    Expand-Archive -Path $zip -DestinationPath $extractRoot -Force
    foreach ($base in $BaseDirs) {
        $dest = Join-Path $base "WixTools314"
        if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }
        New-Item -ItemType Directory -Force -Path $dest | Out-Null
        Copy-Item -Path (Join-Path $extractRoot "*") -Destination $dest -Recurse -Force
        Write-Host "WiX ready: $dest"
    }
}

function Install-NsisTools {
    param([string[]]$BaseDirs)
    $zip = Join-Path $env:TEMP "nsis-3.11.zip"
    if ($OfflineNsisZip -and (Test-Path $OfflineNsisZip)) {
        Copy-Item $OfflineNsisZip $zip -Force
    } else {
    $urls = @(
        "https://github.com/tauri-apps/binary-releases/releases/download/nsis-3.11/nsis-3.11.zip",
        "https://mirror.ghproxy.com/https://github.com/tauri-apps/binary-releases/releases/download/nsis-3.11/nsis-3.11.zip"
    )
    Download-WithFallback -Urls $urls -OutFile $zip
    }
    $extractRoot = Join-Path $env:TEMP "nsis-extract"
    if (Test-Path $extractRoot) { Remove-Item $extractRoot -Recurse -Force }
    Expand-Archive -Path $zip -DestinationPath $extractRoot -Force
    $source = Join-Path $extractRoot "nsis-3.11"
    if (-not (Test-Path $source)) { throw "nsis-3.11 folder not found in archive" }

    $utilsZip = Join-Path $env:TEMP "nsis_tauri_utils.dll"
    $utilsUrls = @(
        "https://github.com/tauri-apps/nsis-tauri-utils/releases/download/nsis_tauri_utils-v0.5.3/nsis_tauri_utils.dll",
        "https://mirror.ghproxy.com/https://github.com/tauri-apps/nsis-tauri-utils/releases/download/nsis_tauri_utils-v0.5.3/nsis_tauri_utils.dll"
    )
    Download-WithFallback -Urls $utilsUrls -OutFile $utilsZip

    foreach ($base in $BaseDirs) {
        $dest = Join-Path $base "NSIS"
        if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }
        Copy-Item -Path $source -Destination $dest -Recurse -Force
        $pluginDir = Join-Path $dest "Plugins\x86-unicode\additional"
        New-Item -ItemType Directory -Force -Path $pluginDir | Out-Null
        Copy-Item -Path $utilsZip -Destination (Join-Path $pluginDir "nsis_tauri_utils.dll") -Force
        Write-Host "NSIS ready: $dest"
    }
}

$toolDirs = Get-TauriToolsDirs
Install-NsisTools -BaseDirs $toolDirs
if ($Target -eq "all") {
    Install-WixTools -BaseDirs $toolDirs
}

Write-Host "Bundle tools prepared for target: $Target"
