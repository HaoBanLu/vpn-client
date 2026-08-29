# 模拟器断网/恢复 UI 与日志快速验收
param(
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$Package = "com.vpn.kuayun",
    [string]$Email = "luban7733@gmail.com",
    [string]$Password = "123456"
)

$ErrorActionPreference = "Stop"

function Invoke-Adb([string[]]$Args) {
    $out = & $Adb @Args 2>&1
    if ($LASTEXITCODE -ne 0) { throw "adb $($Args -join ' ') failed: $out" }
    return ($out | Out-String).Trim()
}

function Wait-Seconds([int]$Sec, [string]$Msg) {
    Write-Host "  ... $Msg (${Sec}s)"
    Start-Sleep -Seconds $Sec
}

function Get-UiText {
    Invoke-Adb shell "uiautomator dump /sdcard/ky_ui.xml" | Out-Null
    $xml = Invoke-Adb shell "cat /sdcard/ky_ui.xml"
    $texts = [regex]::Matches($xml, 'text="([^"]*)"') | ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ }
    return ($texts | Select-Object -Unique)
}

function Show-Ui([string]$Label) {
    Write-Host "`n=== UI: $Label ==="
    $texts = Get-UiText
    $texts | ForEach-Object { Write-Host "  $_" }
    return $texts
}

function Tap-Text([string]$Text, [object[]]$Texts) {
    Invoke-Adb shell "uiautomator dump /sdcard/ky_ui.xml" | Out-Null
    $xml = Invoke-Adb shell "cat /sdcard/ky_ui.xml"
    $pattern = [regex]::Escape($Text)
    if ($xml -notmatch "text=`"$pattern`"[^>]*bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`"") {
        return $false
    }
    $x1 = [int]$Matches[1]; $y1 = [int]$Matches[2]; $x2 = [int]$Matches[3]; $y2 = [int]$Matches[4]
    $x = ($x1 + $x2) / 2; $y = ($y1 + $y2) / 2
    Invoke-Adb shell "input tap $x $y" | Out-Null
    return $true
}

function Set-AirplaneMode([bool]$On) {
    $v = if ($On) { "1" } else { "0" }
    Invoke-Adb shell "cmd connectivity airplane-mode $(if ($On) { 'enable' } else { 'disable' })" | Out-Null
    Invoke-Adb shell "settings put global airplane_mode_on $v" | Out-Null
}

function Get-NetworkLogs {
    Invoke-Adb logcat -d -t 200 | Select-String -Pattern "物理网|network|probe_streak|网络已断开|markNetwork|VpnNetwork|rebindUnderlying" | Select-Object -Last 30
}

Write-Host "== Emulator network UI check =="
$devices = Invoke-Adb devices
if ($devices -notmatch "device$") { throw "No emulator/device connected" }

Invoke-Adb logcat -c | Out-Null
Invoke-Adb shell "am force-stop $Package" | Out-Null
Invoke-Adb shell "am start -n $Package/.MainActivity" | Out-Null
Wait-Seconds 5 "app boot"

$ui = Show-Ui "launch"

# 登录（若需要）
if ($ui -match "登录|邮箱|密码") {
    Write-Host "Logging in..."
    Tap-Text "邮箱" $ui | Out-Null
    Invoke-Adb shell "input text $Email" | Out-Null
    Tap-Text "密码" $ui | Out-Null
    Invoke-Adb shell "input text $Password" | Out-Null
    Invoke-Adb shell "input keyevent 66" | Out-Null
    if (-not (Tap-Text "登录" $ui)) { Tap-Text "一键登录" $ui | Out-Null }
    Wait-Seconds 8 "after login"
    $ui = Show-Ui "after login"
}

# 进入连接页
if (-not (Tap-Text "连接" $ui)) {
    Invoke-Adb shell "input tap 120 2280" | Out-Null  # 底部 Tab 兜底
}
Wait-Seconds 3 "connect tab"
$ui = Show-Ui "connect tab"

# 一键连接
if ($ui -contains "一键连接") {
    Tap-Text "一键连接" $ui | Out-Null
    Write-Host "Tapped 一键连接, waiting VPN..."
    Wait-Seconds 3 "VPN permission"
    # 系统 VPN 授权
    $perm = Get-UiText
    if ($perm -contains "确定" -or $perm -contains "OK" -or $perm -match "允许") {
        Tap-Text "确定" $perm | Out-Null
        if (-not $?) { Tap-Text "OK" $perm | Out-Null }
    }
    Wait-Seconds 15 "VPN connect"
}

$uiConnected = Show-Ui "connected baseline"
$connectedOk = ($uiConnected -contains "已保护") -or ($uiConnected -contains "连接不稳定")
Write-Host "Connected baseline OK: $connectedOk"

Write-Host "`n== Enable airplane mode =="
Set-AirplaneMode $true
Wait-Seconds 2 "airplane on"
$uiOffline = Show-Ui "airplane ON"
$offlineOk = ($uiOffline -contains "网络已断开") -or ($uiOffline -match "恢复后将自动重连")
$rateZero = ($uiOffline -match "0\.0 KB/s") -or ($uiOffline -contains "实时速率为 0")
Write-Host "Offline UI OK (网络已断开): $offlineOk"
Write-Host "Rate zero hint: $rateZero"

Write-Host "`n== Disable airplane mode =="
Set-AirplaneMode $false
Wait-Seconds 2 "airplane off initial"
$uiRecovering = Show-Ui "airplane OFF +2s"
Wait-Seconds 12 "auto reconnect"
$uiRecovered = Show-Ui "airplane OFF +14s"
$recoveringSeen = ($uiRecovering -match "恢复") -or ($uiRecovered -match "恢复|连接中|正在恢复")
$recoveredOk = ($uiRecovered -contains "已保护") -or ($uiRecovered -contains "连接不稳定")
Write-Host "Recovering seen: $recoveringSeen"
Write-Host "Recovered OK: $recoveredOk"

Write-Host "`n== Recent network logs =="
Get-NetworkLogs | ForEach-Object { Write-Host $_ }

$pass = $offlineOk -and ($connectedOk -or $recoveringSeen)
Write-Host "`n== SUMMARY =="
Write-Host "PASS overall (offline UI): $pass"
if (-not $pass) { exit 1 }
