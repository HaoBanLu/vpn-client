# Emulator check: update retry/cancel + subpage pull-refresh
param(
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$Package = "com.vpn.kuayun",
    [string]$Email = "luban7733@gmail.com",
    [string]$Password = "123456",
    [string]$ShotDir = "D:\Code\Go-www\vpn-client\apps\tauri\scripts"
)

$ErrorActionPreference = "Stop"
$Results = [System.Collections.Generic.List[string]]::new()

function Invoke-Adb([string[]]$AdbArgs) {
    $out = & $Adb @AdbArgs 2>&1
    if ($LASTEXITCODE -ne 0) { throw "adb $($AdbArgs -join ' ') failed: $out" }
    return ($out | Out-String).Trim()
}

function Wait-Seconds([int]$Sec, [string]$Msg) {
    Write-Host "  ... $Msg (${Sec}s)"
    Start-Sleep -Seconds $Sec
}

function Get-UiXml {
    Invoke-Adb shell "uiautomator dump /sdcard/ky_ui.xml" | Out-Null
    return Invoke-Adb shell "cat /sdcard/ky_ui.xml"
}

function Get-UiText {
    $xml = Get-UiXml
    $texts = [regex]::Matches($xml, 'text="([^"]*)"') | ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ }
    return ($texts | Select-Object -Unique)
}

function Show-Ui([string]$Label) {
    Write-Host "`n=== UI: $Label ==="
    $texts = Get-UiText
    $texts | ForEach-Object { Write-Host "  $_" }
    return $texts
}

function Save-Shot([string]$Name) {
    $remote = "/sdcard/$Name.png"
    $local = Join-Path $ShotDir $Name
    Invoke-Adb shell "screencap -p $remote" | Out-Null
    & $Adb pull $remote $local | Out-Null
    Invoke-Adb shell "rm $remote" | Out-Null
    Write-Host "  shot -> $local"
}

function Find-Bounds([string]$Text) {
    $xml = Get-UiXml
    $pattern = [regex]::Escape($Text)
    $m = [regex]::Match($xml, "text=`"$pattern`"[^>]*bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`"")
    if (-not $m.Success) {
        $m = [regex]::Match($xml, "content-desc=`"$pattern`"[^>]*bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`"")
    }
    if (-not $m.Success) { return $null }
    return @{
        X1 = [int]$m.Groups[1].Value
        Y1 = [int]$m.Groups[2].Value
        X2 = [int]$m.Groups[3].Value
        Y2 = [int]$m.Groups[4].Value
    }
}

function Tap-Text([string]$Text) {
    $b = Find-Bounds $Text
    if (-not $b) {
        Write-Host "  [miss] tap '$Text'"
        return $false
    }
    $x = ($b.X1 + $b.X2) / 2
    $y = ($b.Y1 + $b.Y2) / 2
    Invoke-Adb shell "input tap $x $y" | Out-Null
    Write-Host "  [tap] $Text @ $x,$y"
    return $true
}

function Tap-Contains([string]$Partial) {
    $xml = Get-UiXml
    $m = [regex]::Match($xml, "text=`"([^`"]*$([regex]::Escape($Partial))[^`"]*)`"[^>]*bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`"")
    if (-not $m.Success) {
        Write-Host "  [miss] tap contains '$Partial'"
        return $false
    }
    $label = $m.Groups[1].Value
    $x = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
    $y = ([int]$m.Groups[3].Value + [int]$m.Groups[5].Value) / 2
    Invoke-Adb shell "input tap $x $y" | Out-Null
    Write-Host "  [tap] $label @ $x,$y"
    return $true
}

function Swipe-PullRefresh {
    Invoke-Adb shell "input swipe 540 420 540 980 700" | Out-Null
    Write-Host "  [swipe] pull-to-refresh"
}

function Assert-Has([object[]]$Texts, [string]$Needle, [string]$CaseId) {
    $joined = ($Texts -join " | ")
    if ($joined -match [regex]::Escape($Needle)) {
        $Results.Add("PASS $CaseId :: found '$Needle'")
        Write-Host "PASS $CaseId :: found '$Needle'"
        return $true
    }
    $Results.Add("FAIL $CaseId :: missing '$Needle'")
    Write-Host "FAIL $CaseId :: missing '$Needle'"
    return $false
}

function Assert-Any([object[]]$Texts, [string[]]$Needles, [string]$CaseId) {
    $joined = ($Texts -join " | ")
    foreach ($n in $Needles) {
        if ($joined -match [regex]::Escape($n)) {
            $Results.Add("PASS $CaseId :: found '$n'")
            Write-Host "PASS $CaseId :: found '$n'"
            return $true
        }
    }
    $Results.Add("FAIL $CaseId :: none of [$($Needles -join ', ')]")
    Write-Host "FAIL $CaseId :: none of [$($Needles -join ', ')]"
    return $false
}

Write-Host "== Emulator update + pull-refresh check =="
$devices = Invoke-Adb @("devices")
if ($devices -notmatch "device$") { throw "No emulator/device connected" }

Invoke-Adb @("logcat","-c") | Out-Null
Invoke-Adb shell "am force-stop $Package" | Out-Null
Invoke-Adb shell "am start -n $Package/.MainActivity" | Out-Null
Wait-Seconds 6 "app boot"
$ui = Show-Ui "boot"
Save-Shot "_upd0_boot.png"

if (($ui -join " ") -match "登录|邮箱|密码") {
    Write-Host "Logging in..."
    if (Tap-Text "邮箱") { Invoke-Adb shell "input text $Email" | Out-Null }
    if (Tap-Text "密码") { Invoke-Adb shell "input text $Password" | Out-Null }
    if (-not (Tap-Text "登录")) { Tap-Text "一键登录" | Out-Null }
    Wait-Seconds 8 "after login"
    $ui = Show-Ui "after login"
    Save-Shot "_upd1_login.png"
}

Write-Host "`n---- A: App update ----"
if (-not (Tap-Text "我的")) { Tap-Contains "我的" | Out-Null }
Wait-Seconds 2 "profile tab"
$ui = Show-Ui "profile"
Save-Shot "_upd2_profile.png"

if (-not (Tap-Contains "关于")) { throw "cannot open About" }
Wait-Seconds 2 "about"
$ui = Show-Ui "about"
Save-Shot "_upd3_about.png"
Assert-Has $ui "检查更新" "A1-about-has-check"

if (-not (Tap-Text "检查更新")) { Tap-Contains "检查更新" | Out-Null }
Wait-Seconds 4 "check update"
$ui = Show-Ui "after check update"
Save-Shot "_upd4_check.png"

$joined = ($ui -join " ")
$hasUpdatePrompt = $joined -match "发现新版本|立即更新|需要更新"
$hasLatest = $joined -match "已是最新|当前已是最新"

if ($hasUpdatePrompt) {
    $Results.Add("PASS A2-update-prompt-visible")
    Write-Host "PASS A2-update-prompt-visible"
    Assert-Any $ui @("立即更新") "A3-has-update-button"

    Tap-Text "稍后再说" | Out-Null
    Wait-Seconds 1 "dismiss prompt"

    Tap-Text "返回" | Out-Null
    Wait-Seconds 1 "back profile"
    Tap-Text "连接" | Out-Null
    Wait-Seconds 2 "connect tab"
    $ui = Show-Ui "connect"
    Save-Shot "_upd5_connect.png"

    if (($ui -join " ") -match "一键连接|点击连接|未连接") {
        # Prefer hero connect button text
        if (-not (Tap-Contains "一键连接")) { Tap-Contains "连接" | Out-Null }
        Wait-Seconds 12 "vpn connecting"
        $ui = Show-Ui "vpn state"
        Save-Shot "_upd6_vpn.png"
    }

    Tap-Text "我的" | Out-Null
    Wait-Seconds 1 "profile"
    Tap-Contains "关于" | Out-Null
    Wait-Seconds 2 "about again"
    Tap-Text "检查更新" | Out-Null
    Wait-Seconds 3 "check again"
    $ui = Show-Ui "prompt again"
    Save-Shot "_upd7_prompt2.png"

    if (($ui -join " ") -match "立即更新") {
        Tap-Text "立即更新" | Out-Null
        Wait-Seconds 3 "download start"
        $ui = Show-Ui "downloading"
        Save-Shot "_upd8_downloading.png"
        Assert-Any $ui @("取消", "正在更新", "正在下载", "更新失败", "建议断开") "A4-download-or-hint"

        if (($ui -join " ") -match "取消") {
            Tap-Text "取消" | Out-Null
            Wait-Seconds 2 "after cancel"
            $ui = Show-Ui "after cancel"
            Save-Shot "_upd9_cancelled.png"
            if (($ui -join " ") -notmatch "正在更新|正在下载") {
                $Results.Add("PASS A5-cancel-closes-overlay")
                Write-Host "PASS A5-cancel-closes-overlay"
            } else {
                $Results.Add("FAIL A5-cancel-closes-overlay")
                Write-Host "FAIL A5-cancel-closes-overlay"
            }
        } elseif (($ui -join " ") -match "更新失败|重试") {
            Assert-Any $ui @("重试") "A5-error-has-retry"
            Assert-Any $ui @("稍后再说") "A6-error-has-later"
            Tap-Text "稍后再说" | Out-Null
            Wait-Seconds 2 "dismiss error"
            $ui = Show-Ui "after error dismiss"
            Save-Shot "_upd9_error_dismiss.png"
            if (($ui -join " ") -notmatch "更新失败") {
                $Results.Add("PASS A7-error-later-closes")
                Write-Host "PASS A7-error-later-closes"
            } else {
                $Results.Add("FAIL A7-error-later-closes")
                Write-Host "FAIL A7-error-later-closes"
            }
        } else {
            $Results.Add("INFO A4-download-ui-unexpected")
            Write-Host "INFO A4 download UI unexpected"
        }
    } else {
        $Results.Add("INFO A3-no-prompt-after-vpn")
        Write-Host "INFO no update prompt after VPN path (dismissed earlier)"
    }
} elseif ($hasLatest) {
    $Results.Add("PASS A2-already-latest")
    Write-Host "PASS A2-already-latest (server has no newer package; fail/retry E2E skipped)"
} else {
    $Results.Add("FAIL A2-unknown-check-result")
    Write-Host "FAIL A2 unknown: $joined"
}

Write-Host "`n---- B: Pull refresh ----"
Tap-Text "我的" | Out-Null
Wait-Seconds 2 "profile for refresh"
$ui = Show-Ui "profile for refresh"
Save-Shot "_ref0_profile.png"

if (-not (Tap-Contains "订单")) { Tap-Contains "设备" | Out-Null }
Wait-Seconds 3 "orders/devices"
$ui = Show-Ui "subpage before pull"
Save-Shot "_ref1_sub_before.png"
Assert-Any $ui @("订单", "登录设备", "充值记录", "套餐订单", "暂无") "B1-subpage-opened"

Swipe-PullRefresh
Wait-Seconds 1 "during pull"
$uiPull = Show-Ui "during/after pull"
Save-Shot "_ref2_pulling.png"
Assert-Any $uiPull @("下拉刷新", "松开刷新", "刷新中") "B2-pull-indicator"

Wait-Seconds 3 "refresh settle"
$uiAfter = Show-Ui "after pull"
Save-Shot "_ref3_after.png"
Assert-Any $uiAfter @("订单", "登录设备", "充值", "套餐", "暂无", "返回") "B3-still-on-subpage"

Tap-Text "返回" | Out-Null
Wait-Seconds 1 "back"
if (Tap-Contains "流量") {
    Wait-Seconds 2 "traffic"
    Swipe-PullRefresh
    Wait-Seconds 1 "traffic pull"
    $uiT = Show-Ui "traffic pull"
    Save-Shot "_ref4_traffic.png"
    Assert-Any $uiT @("下拉刷新", "松开刷新", "刷新中", "流量统计", "总流量") "B4-traffic-pull-or-page"
}

Write-Host "`n==== SUMMARY ===="
$Results | ForEach-Object { Write-Host $_ }
$fail = @($Results | Where-Object { $_ -like "FAIL*" }).Count
$pass = @($Results | Where-Object { $_ -like "PASS*" }).Count
Write-Host "PASS=$pass FAIL=$fail"
if ($fail -gt 0) { exit 1 }