# Sync src-tauri/android VPN overlay into gen/android
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$OverlayApp = Join-Path $Root "src-tauri\android\app"
$GenApp = Join-Path $Root "src-tauri\gen\android\app"
$OverlayMain = Join-Path $OverlayApp "src\main"
$GenMain = Join-Path $GenApp "src\main"

if (-not (Test-Path $GenApp)) {
    throw "gen/android missing; run: npx tauri android init --ci"
}

$vpnSrc = Join-Path $OverlayMain "java\com\vpn\tauri\vpn"
$vpnDst = Join-Path $GenMain "java\com\vpn\tauri\vpn"
if (-not (Test-Path $vpnSrc)) {
    throw "VPN sources missing: $vpnSrc"
}
if (Test-Path $vpnDst) {
    Remove-Item -Path $vpnDst -Recurse -Force
}
New-Item -ItemType Directory -Force -Path (Split-Path $vpnDst) | Out-Null
Copy-Item -Path $vpnSrc -Destination $vpnDst -Recurse -Force

$genManifest = Join-Path $GenMain "AndroidManifest.xml"
$overlayManifest = Join-Path $OverlayMain "AndroidManifest.xml"
if (-not (Test-Path $genManifest)) { throw "Missing $genManifest" }
if (-not (Test-Path $overlayManifest)) { throw "Missing $overlayManifest" }

$genXml = Get-Content $genManifest -Raw -Encoding UTF8
$overlayXml = Get-Content $overlayManifest -Raw -Encoding UTF8
$marker = "<!-- vpn-tauri-overlay -->"

if ($genXml -notmatch [regex]::Escape($marker)) {
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
        $genXml = $genXml -replace '</application>', "$insert`n    </application>"
    }
    Set-Content -Path $genManifest -Value $genXml -Encoding UTF8 -NoNewline
}

$genGradle = Join-Path $GenApp "build.gradle.kts"
$gradle = Get-Content $genGradle -Raw -Encoding UTF8
$applyLine = 'apply(from = file("../../../android/app/build.gradle.kts"))'
if ($gradle -notmatch [regex]::Escape($applyLine)) {
    $gradle = $gradle.TrimEnd() + "`n`n$applyLine`n"
    Set-Content -Path $genGradle -Value $gradle -Encoding UTF8 -NoNewline
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
        Set-Content -Path $gradleProps -Value $props -Encoding UTF8 -NoNewline
    }
}

$GenAndroidRoot = Split-Path $GenApp
$genSettings = Join-Path $GenAndroidRoot "settings.gradle.kts"
if (Test-Path $genSettings) {
    $settings = Get-Content $genSettings -Raw -Encoding UTF8
    if ($settings -notmatch 'include\(":mihomo-core"\)') {
        $settings = $settings.TrimEnd() + @"

include(":mihomo-core")
project(":mihomo-core").projectDir = file("../../../../android/mihomo-core")
"@
        Set-Content -Path $genSettings -Value $settings -Encoding UTF8 -NoNewline
    }
}

$genRootBuild = Join-Path $GenAndroidRoot "build.gradle.kts"
if (Test-Path $genRootBuild) {
    $rootBuild = Get-Content $genRootBuild -Raw -Encoding UTF8
    if ($rootBuild -notmatch "kotlin\.plugin\.serialization") {
        $rootBuild = $rootBuild -replace '(id\("org\.jetbrains\.kotlin\.android"\)[^\n]+)', '$1
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false'
        Set-Content -Path $genRootBuild -Value $rootBuild -Encoding UTF8 -NoNewline
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
