# 下载 Mihomo Windows 二进制到 src-tauri/resources/bin/
$ErrorActionPreference = "Stop"
$Version = if ($env:MIHOMO_VERSION) { $env:MIHOMO_VERSION } else { "1.19.0" }
$Root = Split-Path -Parent $PSScriptRoot
$OutDir = Join-Path $Root "src-tauri\resources\bin"
$Zip = Join-Path $env:TEMP "mihomo-windows-amd64.zip"
$Asset = "mihomo-windows-amd64-v$Version.zip"
$Url = "https://github.com/MetaCubeX/mihomo/releases/download/v$Version/$Asset"

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
Write-Host "Downloading $Url ..."
Invoke-WebRequest -Uri $Url -OutFile $Zip
Expand-Archive -Path $Zip -DestinationPath $env:TEMP -Force
$Exe = Get-ChildItem -Path $env:TEMP -Recurse -Filter "mihomo*.exe" | Select-Object -First 1
if (-not $Exe) { throw "mihomo.exe not found in archive" }
Copy-Item $Exe.FullName (Join-Path $OutDir "mihomo.exe") -Force
Write-Host "Installed: $OutDir\mihomo.exe"
