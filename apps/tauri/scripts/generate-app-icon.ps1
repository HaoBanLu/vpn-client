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

# tauri icon 的 Android mipmap 偶发产出纯色坏图；用品牌源图覆盖各密度
Add-Type -AssemblyName System.Drawing
$src = [System.Drawing.Image]::FromFile($pngPath)
$androidRoot = Join-Path $Root "src-tauri\icons\android"
$legacy = @{ "mipmap-mdpi" = 48; "mipmap-hdpi" = 72; "mipmap-xhdpi" = 96; "mipmap-xxhdpi" = 144; "mipmap-xxxhdpi" = 192 }
$fg = @{ "mipmap-mdpi" = 108; "mipmap-hdpi" = 162; "mipmap-xhdpi" = 216; "mipmap-xxhdpi" = 324; "mipmap-xxxhdpi" = 432 }
function Save-Resized([System.Drawing.Image]$image, [int]$size, [string]$path) {
  $bmp = New-Object System.Drawing.Bitmap $size, $size
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  $g.Clear([System.Drawing.Color]::Transparent)
  $g.DrawImage($image, 0, 0, $size, $size)
  $g.Dispose()
  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
}
foreach ($dir in $legacy.Keys) {
  $folder = Join-Path $androidRoot $dir
  New-Item -ItemType Directory -Force -Path $folder | Out-Null
  Save-Resized $src $legacy[$dir] (Join-Path $folder "ic_launcher.png")
  Save-Resized $src $legacy[$dir] (Join-Path $folder "ic_launcher_round.png")
}
foreach ($dir in $fg.Keys) {
  Save-Resized $src $fg[$dir] (Join-Path (Join-Path $androidRoot $dir) "ic_launcher_foreground.png")
}
$src.Dispose()
$bgXml = @"
<?xml version="1.0" encoding="utf-8"?>
<resources>
  <color name="ic_launcher_background">#1B4DFF</color>
</resources>
"@
$valuesDir = Join-Path $androidRoot "values"
New-Item -ItemType Directory -Force -Path $valuesDir | Out-Null
Set-Content -Path (Join-Path $valuesDir "ic_launcher_background.xml") -Value $bgXml -Encoding UTF8

Write-Host "Tauri icons updated under src-tauri/icons (Android mipmap synced)"
