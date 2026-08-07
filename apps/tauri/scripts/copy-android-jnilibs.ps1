# Windows 无开发者模式时，用复制代替 Tauri 的 jniLibs 符号链接
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$TargetDir = Join-Path $Root "src-tauri\target"
$JniRoot = Join-Path $Root "src-tauri\gen\android\app\src\main\jniLibs"

$map = @{
    "aarch64-linux-android"   = "arm64-v8a"
    "armv7-linux-androideabi" = "armeabi-v7a"
    "i686-linux-android"      = "x86"
    "x86_64-linux-android"    = "x86_64"
}

$profile = if ($args -contains "--release") { "release" } else { "debug" }
$copied = 0

foreach ($triple in $map.Keys) {
    $abi = $map[$triple]
    $lib = Join-Path $TargetDir "$triple\$profile\libvpn_tauri_lib.so"
    if (-not (Test-Path $lib)) { continue }
    $outDir = Join-Path $JniRoot $abi
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    Copy-Item -Force $lib (Join-Path $outDir "libvpn_tauri_lib.so")
    Write-Host "Copied $lib -> $outDir"
    $copied++
}

if ($copied -eq 0) {
    throw "未找到 libvpn_tauri_lib.so，请先执行 cargo/tauri android 编译"
}
