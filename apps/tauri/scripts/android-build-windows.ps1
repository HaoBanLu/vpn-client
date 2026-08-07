# Windows Android build: sync VPN overlay, copy jniLibs if symlink fails, run Gradle
param(
    [switch]$Release,
    [switch]$Install
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$CargoBin = Join-Path $env:USERPROFILE ".cargo\bin"
if ((Test-Path $CargoBin) -and ($env:Path -notlike "*$CargoBin*")) {
    $env:Path = "$CargoBin;$env:Path"
}

function Get-ApkSigner {
    $sdkRoot = $env:ANDROID_HOME
    if (-not $sdkRoot) { $sdkRoot = $env:ANDROID_SDK_ROOT }
    if (-not $sdkRoot) { $sdkRoot = "D:\Android\Sdk" }
    $buildTools = Join-Path $sdkRoot "build-tools"
    if (-not (Test-Path $buildTools)) { return $null }
    return Get-ChildItem -Path $buildTools -Recurse -Filter "apksigner.bat" -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending |
        Select-Object -First 1
}

function Sign-WithDebugKeystore($UnsignedApk) {
    $apksigner = Get-ApkSigner
    if (-not $apksigner) {
        Write-Warning "apksigner.bat not found; release APK remains unsigned."
        return $UnsignedApk
    }
    $debugKeystore = Join-Path $env:USERPROFILE ".android\debug.keystore"
    if (-not (Test-Path $debugKeystore)) {
        Write-Warning "debug.keystore not found; release APK remains unsigned."
        return $UnsignedApk
    }
    $signedApk = $UnsignedApk.FullName -replace "-unsigned\.apk$", "-debugsigned.apk"
    if ($signedApk -eq $UnsignedApk.FullName) {
        $signedApk = $UnsignedApk.FullName -replace "\.apk$", "-debugsigned.apk"
    }
    & $apksigner.FullName sign `
        --ks $debugKeystore `
        --ks-key-alias androiddebugkey `
        --ks-pass pass:android `
        --key-pass pass:android `
        --out $signedApk `
        $UnsignedApk.FullName
    if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }
    & $apksigner.FullName verify --verbose $signedApk | Out-Host
    return Get-Item $signedApk
}

& "$PSScriptRoot\sync-android-vpn.ps1"

$profile = if ($Release) { "release" } else { "debug" }
$tauriArgs = if ($Release) {
    "android build --target aarch64"
} else {
    "android build --debug --target aarch64"
}

$buildLog = Join-Path $env:TEMP "tauri-android-build.log"
$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
cmd /c "npx tauri $tauriArgs > `"$buildLog`" 2>&1"
$exitCode = $LASTEXITCODE
$ErrorActionPreference = $prevEap
Get-Content $buildLog

$log = if (Test-Path $buildLog) { Get-Content $buildLog -Raw } else { "" }
$symlinkFailed = $log -match "symbolic link|symlink"

if ($exitCode -ne 0 -and $symlinkFailed) {
    Write-Host "jniLibs symlink failed; copying .so files instead..."
    if ($Release) {
        & "$PSScriptRoot\copy-android-jnilibs.ps1" --release
    } else {
        & "$PSScriptRoot\copy-android-jnilibs.ps1"
    }
    $gradleTask = if ($Release) { "assembleArm64Release" } else { "assembleArm64Debug" }
    $skipRust = if ($Release) {
        "-x rustBuildArm64Release -x rustBuildUniversalRelease"
    } else {
        "-x rustBuildArm64Debug -x rustBuildUniversalDebug"
    }
    Push-Location "$Root\src-tauri\gen\android"
    try {
        cmd /c ".\gradlew.bat $gradleTask $skipRust"
        if ($LASTEXITCODE -ne 0) { throw "Gradle $gradleTask failed" }
    } finally {
        Pop-Location
    }
} elseif ($exitCode -ne 0) {
    throw "Android build failed; see $buildLog"
} else {
    Write-Host "Android $profile APK built via tauri android build"
}

$apkPattern = if ($Release) { "*arm64*release*.apk" } else { "*arm64*debug*.apk" }
$apk = Get-ChildItem -Path "$Root\src-tauri\gen\android\app\build\outputs\apk" -Recurse -Filter $apkPattern -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($Release -and $apk -and $apk.Name -match "unsigned") {
    Write-Host "Release APK is unsigned; signing with local debug keystore for test install..."
    $apk = Sign-WithDebugKeystore $apk
}
if ($apk) {
    $sizeMb = [math]::Round($apk.Length / 1MB, 1)
    Write-Host "APK output: $($apk.FullName) ($sizeMb MB)"
}

if ($Install) {
    if ($Release) {
        & "$PSScriptRoot\android-install-windows.ps1" -Release
    } else {
        & "$PSScriptRoot\android-install-windows.ps1" -Debug
    }
}
