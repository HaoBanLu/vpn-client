# Sync src-tauri/android VPN overlay into gen/android
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$OverlayApp = Join-Path $Root "src-tauri\android\app"
$GenApp = Join-Path $Root "src-tauri\gen\android\app"
$OverlayMain = Join-Path $OverlayApp "src\main"
$GenMain = Join-Path $GenApp "src\main"

function Write-Utf8NoBom([string]$Path, [string]$Content) {
    $utf8 = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($Path, $Content, $utf8)
}

if (-not (Test-Path $GenApp)) {
    throw "gen/android missing; run: npx tauri android init --ci"
}

$vpnSrc = Join-Path $OverlayMain "java\com\vpn\kuayun\vpn"
$vpnDst = Join-Path $GenMain "java\com\vpn\kuayun\vpn"
if (-not (Test-Path $vpnSrc)) {
    throw "VPN sources missing: $vpnSrc"
}
if (Test-Path $vpnDst) {
    Remove-Item -Path $vpnDst -Recurse -Force
}
# 清理旧包名残留
$legacyDst = Join-Path $GenMain "java\com\vpn\tauri"
if (Test-Path $legacyDst) {
    Remove-Item -Path $legacyDst -Recurse -Force
}
New-Item -ItemType Directory -Force -Path (Split-Path $vpnDst) | Out-Null
Copy-Item -Path $vpnSrc -Destination $vpnDst -Recurse -Force

# TauriActivity 入口：overlay 持久化，避免 gen 被清后缺 MainActivity
$mainActivitySrc = Join-Path $OverlayMain "java\com\vpn\kuayun\MainActivity.kt"
$mainActivityDst = Join-Path $GenMain "java\com\vpn\kuayun\MainActivity.kt"
if (-not (Test-Path $mainActivitySrc)) {
    throw "MainActivity missing: $mainActivitySrc"
}
New-Item -ItemType Directory -Force -Path (Split-Path $mainActivityDst) | Out-Null
Copy-Item -Path $mainActivitySrc -Destination $mainActivityDst -Force

# 同步 overlay 资源（FileProvider paths 等）
$overlayRes = Join-Path $OverlayMain "res"
$genRes = Join-Path $GenMain "res"
if (Test-Path $overlayRes) {
    New-Item -ItemType Directory -Force -Path $genRes | Out-Null
    Copy-Item -Path (Join-Path $overlayRes "*") -Destination $genRes -Recurse -Force
}

# 官方品牌图标：src-tauri/icons/android → gen res（避免 gen 残留 Tauri 默认图标）
$brandIcons = Join-Path $Root "src-tauri\icons\android"
if (-not (Test-Path $brandIcons)) {
    throw "Brand icons missing: $brandIcons (run npm run generate:icon)"
}
New-Item -ItemType Directory -Force -Path $genRes | Out-Null
Get-ChildItem -Path $brandIcons -Directory | ForEach-Object {
    $dst = Join-Path $genRes $_.Name
    New-Item -ItemType Directory -Force -Path $dst | Out-Null
    Copy-Item -Path (Join-Path $_.FullName "*") -Destination $dst -Recurse -Force
}
# 清理 Android Studio / tauri init 默认 vector，避免与 mipmap 冲突
@(
    "drawable\ic_launcher_background.xml",
    "drawable-v24\ic_launcher_foreground.xml"
) | ForEach-Object {
    $p = Join-Path $genRes $_
    if (Test-Path $p) { Remove-Item -Force $p }
}
Write-Host "Synced brand launcher icons from icons/android"

$genManifest = Join-Path $GenMain "AndroidManifest.xml"
$overlayManifest = Join-Path $OverlayMain "AndroidManifest.xml"
if (-not (Test-Path $genManifest)) { throw "Missing $genManifest" }
if (-not (Test-Path $overlayManifest)) { throw "Missing $overlayManifest" }

$genXml = Get-Content $genManifest -Raw -Encoding UTF8
$overlayXml = Get-Content $overlayManifest -Raw -Encoding UTF8
$marker = "<!-- vpn-tauri-overlay -->"

$permissions = [regex]::Matches($overlayXml, '<uses-permission[^>]+>') | ForEach-Object { $_.Value }
$missingPerms = $permissions | Where-Object { $genXml -notmatch [regex]::Escape($_) } | Select-Object -Unique
$permBlock = ($missingPerms | Select-Object -Unique) -join "`n    "
if ($permBlock) {
    $genXml = $genXml -replace '(<manifest[^>]*>)', "`$1`n    $permBlock"
}

$serviceBlock = [regex]::Match(
    $overlayXml,
    '(?s)<application[^>]*>(.*?)</application>'
).Groups[1].Value.Trim()
if ($serviceBlock) {
    $insert = "        $marker`n        $serviceBlock`n        $marker"
    if ($genXml -match [regex]::Escape($marker)) {
        $genXml = [regex]::Replace(
            $genXml,
            "(?s)$([regex]::Escape($marker)).*?$([regex]::Escape($marker))",
            $insert
        )
    } else {
        $genXml = $genXml -replace '</application>', "$insert`n    </application>"
    }
}
Write-Utf8NoBom $genManifest $genXml

$genGradle = Join-Path $GenApp "build.gradle.kts"
$gradle = Get-Content $genGradle -Raw -Encoding UTF8
$applyLine = 'apply(from = file("../../../android/app/build.gradle.kts"))'
if ($gradle -notmatch [regex]::Escape($applyLine)) {
    $gradle = $gradle.TrimEnd() + "`n`n$applyLine`n"
    Write-Utf8NoBom $genGradle $gradle
}

$overlayProguard = Join-Path $OverlayApp "proguard-rules.pro"
$genProguard = Join-Path $GenApp "proguard-rules.pro"
if (Test-Path $overlayProguard) {
    Copy-Item -Force $overlayProguard $genProguard
}

$gradleProps = Join-Path (Split-Path $GenApp) "gradle.properties"
if (Test-Path $gradleProps) {
    $props = Get-Content $gradleProps -Raw -Encoding UTF8
    if ($props -notmatch "kotlin\.compiler\.execution\.strategy") {
        if (-not $props.EndsWith("`n")) { $props += "`n" }
        $props += "kotlin.compiler.execution.strategy=in-process`n"
        Write-Utf8NoBom $gradleProps $props
    }
}

$GenAndroidRoot = Split-Path $GenApp
$mihomoBlock = @"

include(":mihomo-core")
project(":mihomo-core").projectDir = file("../../../../android/mihomo-core")
"@
function Patch-MihomoSettings([string]$settingsPath) {
    if (-not (Test-Path $settingsPath)) { return $false }
    $settings = Get-Content $settingsPath -Raw -Encoding UTF8
    if ($settings -match 'include\(":mihomo-core"\)') { return $true }
    $settings = $settings.TrimEnd() + $mihomoBlock
    Write-Utf8NoBom $settingsPath $settings
    Write-Host "Patched mihomo-core into $settingsPath"
    return $true
}

$patched = $false
# Tauri 2 当前生成 settings.gradle（Groovy）；兼容旧 .kts
foreach ($name in @("settings.gradle.kts", "settings.gradle")) {
    if (Patch-MihomoSettings (Join-Path $GenAndroidRoot $name)) { $patched = $true }
}
if (-not $patched) {
    throw "failed to include :mihomo-core; no settings.gradle* under gen/android"
}

$genRootBuild = Join-Path $GenAndroidRoot "build.gradle.kts"
$serializationVer = if ($env:KUAYUN_KOTLIN_SERIALIZATION_VERSION) { $env:KUAYUN_KOTLIN_SERIALIZATION_VERSION } else { "1.9.25" }
$serializationLine = "    id(`"org.jetbrains.kotlin.plugin.serialization`") version `"$serializationVer`" apply false"
if (Test-Path $genRootBuild) {
    $rootBuild = Get-Content $genRootBuild -Raw -Encoding UTF8
    if ($rootBuild -notmatch "kotlin\.plugin\.serialization") {
        if ($rootBuild -match "plugins\s*\{") {
            $rootBuild = $rootBuild -replace "plugins\s*\{", "plugins {`n$serializationLine", 1
        } else {
            $rootBuild = "plugins {`n$serializationLine`n}`n`n" + $rootBuild
        }
        Write-Utf8NoBom $genRootBuild $rootBuild
        Write-Host "Patched serialization plugin into $genRootBuild"
    }
}

$settingsGroovy = Join-Path $GenAndroidRoot "settings.gradle"
if ((Test-Path $settingsGroovy) -and ((Get-Content $settingsGroovy -Raw) -notmatch "kotlin\.plugin\.serialization")) {
    $sg = Get-Content $settingsGroovy -Raw -Encoding UTF8
    $pluginLine = "        id 'org.jetbrains.kotlin.plugin.serialization' version '$serializationVer'`n"
    if ($sg -match "pluginManagement\s*\{") {
        if ($sg -match "pluginManagement\s*\{[\s\S]*?plugins\s*\{") {
            $sg = $sg -replace "(pluginManagement\s*\{[\s\S]*?plugins\s*\{)", "`$1`n$pluginLine", 1
        } else {
            $sg = $sg -replace "(pluginManagement\s*\{)", "`$1`n    plugins {`n$pluginLine    }`n", 1
        }
    } else {
        $sg = "pluginManagement {`n    plugins {`n$pluginLine    }`n}`n" + $sg
    }
    Write-Utf8NoBom $settingsGroovy $sg
    Write-Host "Patched serialization plugin into $settingsGroovy"
}

if (-not (Select-String -Path (Join-Path $GenAndroidRoot "build.gradle.kts"), (Join-Path $GenAndroidRoot "settings.gradle") -Pattern "kotlin.plugin.serialization" -Quiet -ErrorAction SilentlyContinue)) {
    throw "kotlin.plugin.serialization not declared for gen/android"
}

$appGradle = Join-Path $GenApp "build.gradle.kts"
if (Test-Path $appGradle) {
    $ag = Get-Content $appGradle -Raw -Encoding UTF8
    $agOrig = $ag
    if ($ag -match "minSdk\s*=\s*\d+") {
        $ag = [regex]::Replace($ag, "minSdk\s*=\s*\d+", "minSdk = 26", 1)
    }
    # gen 可能仍是旧 identifier；与 tauri.conf / overlay 包名对齐
    $ag = [regex]::Replace($ag, 'namespace\s*=\s*"[^"]+"', 'namespace = "com.vpn.kuayun"')
    $ag = [regex]::Replace($ag, 'applicationId\s*=\s*"[^"]+"', 'applicationId = "com.vpn.kuayun"')
    if ($ag -ne $agOrig) {
        Write-Utf8NoBom $appGradle $ag
        Write-Host "Patched app identity/minSdk into $appGradle"
    }
}

$mihomoJni = Join-Path $Root "..\android\mihomo-core\src\main\jniLibs"
if (-not (Test-Path $mihomoJni)) {
    $setupScript = Join-Path $Root "..\android\scripts\setup-mihomo-native.sh"
    if (Test-Path $setupScript) {
        Write-Host "mihomo jniLibs missing; run: cd apps/android && bash scripts/setup-mihomo-native.sh"
    }
}

Write-Host "Synced VPN overlay to gen/android"
