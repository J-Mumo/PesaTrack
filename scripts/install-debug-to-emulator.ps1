<#
.SYNOPSIS
Build a signed DEBUG APK and install it on a running emulator/device.

.DESCRIPTION
Uses gradlew assembleDebug + adb install. Debug builds have
minifyEnabled=false, HTTP body logging on (OkHttp interceptor), no R8 —
fast iteration for wire-format/UI bug hunting.

Play Billing requires the app to be signed with the same certificate as
the release track on Play Console. This script exports KEYSTORE_PASSWORD
+ KEY_PASSWORD (prompted interactively, hidden input, cleared from
memory after gradle exits) so android/app/build.gradle.kts picks up the
release keystore for the debug variant — see the `debug { … }` block.

Prerequisites:
  - Emulator or device running with Google Play system image
    (adb devices shows it)
  - android/pesatrack-upload.jks present

.EXAMPLE
  cd C:\Eng\PesaTrack
  .\scripts\install-debug-to-emulator.ps1
#>

$ErrorActionPreference = 'Stop'

$repoRoot   = Split-Path -Parent $PSScriptRoot
$androidDir = Join-Path $repoRoot 'android'
$keystore   = Join-Path $androidDir 'pesatrack-upload.jks'
$adb        = 'C:\Eng\platform-tools\adb.exe'
$apk        = Join-Path $androidDir 'app\build\outputs\apk\debug\app-debug.apk'

if (-not (Test-Path $keystore)) { throw "Keystore missing: $keystore" }
if (-not (Test-Path $adb))      { $adb = 'adb' }

Write-Host "==> Checking emulator..."
$devices = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\S' }
if (-not $devices) { throw "No emulator/device detected. Start the emulator, then re-run." }
$devices | ForEach-Object { Write-Host "    $_" }

Write-Host ""
Write-Host "==> Reading keystore passwords (input hidden). Press Enter on both to skip release-signing (billing won't work)."
$ksSecure  = Read-Host "Keystore password" -AsSecureString
$keySecure = Read-Host "Key password (Enter to reuse keystore password)" -AsSecureString

$ksPlain = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($ksSecure))
$keyPlain = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($keySecure))
if ([string]::IsNullOrEmpty($keyPlain)) { $keyPlain = $ksPlain }

if (-not [string]::IsNullOrEmpty($ksPlain)) {
    $env:KEYSTORE_PASSWORD = $ksPlain
    $env:KEY_PASSWORD      = $keyPlain
    Write-Host "    OK: debug variant will be signed with pesatrack-upload.jks (Play Billing enabled)."
} else {
    Write-Host "    Skipping release keystore — debug variant will use the default debug cert (Play Billing WILL FAIL)."
}

Push-Location $androidDir
try {
    Write-Host ""
    Write-Host "==> ./gradlew.bat assembleDebug"
    & .\gradlew.bat assembleDebug 2>&1 |
        Select-String -Pattern '(FAIL|BUILD|error:|Task :app:package|Task :app:assemble)' |
        Select-Object -Last 8

    if (-not (Test-Path $apk)) { throw "Gradle did not produce $apk" }
    Write-Host "    OK: $(Get-Item $apk | ForEach-Object { '{0} ({1:N2} MB)' -f $_.Name, ($_.Length/1MB) })"
}
finally {
    Pop-Location
    # Clear password env vars from the shell session ASAP.
    Remove-Item Env:KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:KEY_PASSWORD      -ErrorAction SilentlyContinue
    $ksPlain  = $null
    $keyPlain = $null
    [System.GC]::Collect()
}

Write-Host ""
Write-Host "==> Installing to device (adb install -r, replace on downgrade)..."
& $adb install -r -d $apk

Write-Host ""
Write-Host "==> Done. Launch PesaTrack on the emulator/device."
Write-Host "    HTTP body logging is ON in debug — filter Logcat with:"
Write-Host "      tag:OkHttp | tag:HomeVM.Coach | tag:CoachInsightRepo"
