# 根据 Android 品牌色生成 Tauri 应用图标
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

Add-Type -AssemblyName System.Drawing

$size = 1024
$bmp = New-Object System.Drawing.Bitmap $size, $size
$graphics = [System.Drawing.Graphics]::FromImage($bmp)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic

$rect = New-Object System.Drawing.Rectangle 0, 0, $size, $size
$brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    $rect,
    [System.Drawing.Color]::FromArgb(255, 0, 212, 255),
    [System.Drawing.Color]::FromArgb(255, 27, 77, 255),
    135
)
$graphics.FillRectangle($brush, $rect)

$white = [System.Drawing.Brushes]::White
$graphics.FillEllipse($white, 180, 430, 360, 290)
$graphics.FillEllipse($white, 390, 360, 410, 340)
$graphics.FillEllipse($white, 560, 430, 300, 260)

$assetsDir = Join-Path $Root "assets"
New-Item -ItemType Directory -Force -Path $assetsDir | Out-Null
$pngPath = Join-Path $assetsDir "app-icon.png"
$bmp.Save($pngPath, [System.Drawing.Imaging.ImageFormat]::Png)
$graphics.Dispose()
$bmp.Dispose()

Write-Host "Generated $pngPath"
npx tauri icon $pngPath
if ($LASTEXITCODE -ne 0) { throw "tauri icon failed" }
Write-Host "Tauri icons updated under src-tauri/icons"
