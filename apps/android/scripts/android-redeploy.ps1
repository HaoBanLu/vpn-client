# VPN App 连接时 Android Studio Run 常报 Couldn't terminate previous instance
# 用法（在 apps/android 目录）：powershell -ExecutionPolicy Bypass -File scripts/android-redeploy.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$localProps = Join-Path $root "local.properties"
if (-not (Test-Path $localProps)) {
    Write-Error "缺少 local.properties，请先配置 Android SDK"
}

$sdkDir = (Get-Content $localProps | Where-Object { $_ -match '^\s*sdk\.dir=' }) -replace '^\s*sdk\.dir=', '' -replace '\\', '\'
$adb = Join-Path $sdkDir "platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    Write-Error "未找到 adb: $adb"
}

Write-Host ">> 强制停止 com.vpn.member ..."
& $adb shell am force-stop com.vpn.member
Start-Sleep -Milliseconds 800

Write-Host ">> 编译并安装 Debug 包 ..."
& .\gradlew.bat installDebug
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ">> 启动 MainActivity ..."
& $adb shell am start -n "com.vpn.member/.MainActivity"
Write-Host ">> 完成"
