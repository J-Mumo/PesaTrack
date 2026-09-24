<#
.SYNOPSIS
Sideload the current signed release AAB to a running Android emulator.

.DESCRIPTION
Wraps bundletool build-apks + install-apks. Passwords are read
interactively via Read-Host -AsSecureString so they never touch disk
or shell history.

Prerequisites:
  - Emulator running with Google Play system image (adb devices shows it)
  - android/bundletool.jar present (auto-downloaded once)
  - android/pesatrack-upload.jks present
  - android/app/build/outputs/bundle/release/app-release.aab freshly built
    (run .\gradlew.bat bundleRelease from android/ first)

.EXAMPLE
  cd C:\Eng\PesaTrack
  .\scripts\install-release-to-emulator.ps1
#>

$ErrorActionPreference = 'Stop'

$repoRoot   = Split-Path -Parent $PSScriptRoot
$androidDir = Join-Path $repoRoot 'android'
$aab        = Join-Path $androidDir 'app\build\outputs\bundle\release\app-release.aab'
$keystore   = Join-Path $androidDir 'pesatrack-upload.jks'
$bundletool = Join-Path $androidDir 'bundletool.jar'
$apks       = Join-Path $androidDir 'app.apks'
$adb        = 'C:\Eng\platform-tools\adb.exe'
$keyAlias   = 'pesatrack-upload'

if (-not (Test-Path $aab))        { throw "AAB missing: $aab. Run .\gradlew.bat bundleRelease from android/ first." }
if (-not (Test-Path $keystore))   { throw "Keystore missing: $keystore" }
if (-not (Test-Path $bundletool)) { throw "bundletool.jar missing: $bundletool" }
if (-not (Test-Path $adb))        { $adb = 'adb' } # fall back to PATH

Write-Host "==> Checking emulator..."
$devices = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\S' }
if (-not $devices) { throw "No emulator/device detected. Start the emulator, then re-run." }
$devices | ForEach-Object { Write-Host "    $_" }

Write-Host ""
Write-Host "==> Reading keystore passwords (input hidden)..."
$ksSecure  = Read-Host "Keystore password" -AsSecureString
$keySecure = Read-Host "Key password (Enter to reuse keystore password)" -AsSecureString

$ksPlain = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($ksSecure))
$keyPlain = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($keySecure))
if ([string]::IsNullOrEmpty($keyPlain)) { $keyPlain = $ksPlain }

if (Test-Path $apks) { Remove-Item $apks -Force }

Write-Host ""
Write-Host "==> Building universal APK set from AAB..."
& java -jar $bundletool build-apks `
    --bundle="$aab" `
    --output="$apks" `
    --mode=universal `
    --ks="$keystore" `
    --ks-pass="pass:$ksPlain" `
    --ks-key-alias="$keyAlias" `
    --key-pass="pass:$keyPlain"

# Clear plaintext passwords from memory ASAP
$ksPlain  = $null
$keyPlain = $null
[System.GC]::Collect()

if (-not (Test-Path $apks)) { throw "bundletool did not produce $apks" }
Write-Host "    OK: $(Get-Item $apks | ForEach-Object { '{0} ({1:N2} MB)' -f $_.Name, ($_.Length/1MB) })"

Write-Host ""
Write-Host "==> Installing to emulator..."
& java -jar $bundletool install-apks --apks="$apks"

Write-Host ""
Write-Host "==> Done. Launch PesaTrack on the emulator, then filter Logcat:"
Write-Host "    tag:HomeVM.Coach | tag:CoachInsightRepo"
