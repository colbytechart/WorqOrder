# Milestone 55B — Owner-Only Candidate Commands

These commands prepare and inspect the `0.6.0` signed candidate. The owner approved
`versionName = 0.6.0` and `versionCode = 6` on 2026-09-24. They do not authorize a commit, merge,
tag, upload, or publication.

Run only from the intended release source after confirming no unexpected working-tree changes.

## Build and inspect

```powershell
Set-Location 'T:\_SC Video\PROJECTS\2026\DNA Work Order App\app\WorqOrder'

$env:JAVA_HOME = 'S:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = 'C:\Users\colby\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = (Resolve-Path -LiteralPath '.gradle').Path

if (-not (Test-Path -LiteralPath '.android-user' -PathType Container)) {
    New-Item -ItemType Directory -Path '.android-user' | Out-Null
}
$projectUserHome = (Resolve-Path -LiteralPath '.android-user').Path

git status --short
if ($LASTEXITCODE -ne 0) { throw 'Git status failed.' }

git diff --check
if ($LASTEXITCODE -ne 0) { throw 'Whitespace/formatting check failed.' }

& .\gradlew.bat "-Duser.home=$projectUserHome" --stop
& .\gradlew.bat "-Duser.home=$projectUserHome" `
    --offline --no-daemon `
    :app:lintDebug :app:lintRelease `
    :app:testDebugUnitTest `
    :app:assembleDebug :app:assembleRelease

if ($LASTEXITCODE -ne 0) {
    throw 'Candidate build, lint, or JVM tests failed; do not continue.'
}

$apk = (Resolve-Path -LiteralPath 'app\build\outputs\apk\release\app-release.apk').Path
$buildTools = Get-ChildItem -LiteralPath (Join-Path $env:ANDROID_HOME 'build-tools') -Directory |
    Sort-Object { [version]$_.Name } -Descending |
    Select-Object -First 1

& (Join-Path $buildTools.FullName 'apksigner.bat') verify --verbose --print-certs $apk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }

& (Join-Path $buildTools.FullName 'aapt2.exe') dump badging $apk |
    Select-String 'package:|sdkVersion:|targetSdkVersion:|application-label:'

Get-FileHash -LiteralPath $apk -Algorithm SHA256 | Format-List Path,Hash
```

Expected permanent signer SHA-1:

```text
57510ccb3001a7e70c4391c916a80a0cc603bb19
```

Do not use a previous APK hash, version, or package output as evidence for a rebuilt candidate.

## Signed populated update on a disposable emulator

First install the official signed `0.5.0` APK and create representative local data. Then, without
uninstalling, install the signed candidate over it:

```powershell
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$upgradeDevice = Read-Host 'Enter the disposable populated emulator serial exactly as shown by adb devices'

& $adb -s $upgradeDevice install -r $apk
if ($LASTEXITCODE -ne 0) {
    throw 'Signed install-over failed. Do not uninstall the populated baseline until the failure is understood.'
}

& $adb -s $upgradeDevice shell dumpsys package worq.order |
    Select-String 'versionCode=|versionName='
```

Launch WorqOrder and verify retained data, then create a portable backup, make a distinct local
change, Import, Restore, and verify Google is disconnected/Auto Export disabled after replacement.
Use the known fixture or a newly created backup only on a disposable emulator.

## Fresh install on a separate disposable emulator

```powershell
$freshDevice = Read-Host 'Enter a separate disposable fresh emulator serial'

& $adb -s $freshDevice install $apk
if ($LASTEXITCODE -ne 0) { throw 'Fresh candidate installation failed.' }

& $adb -s $freshDevice shell monkey -p worq.order 1
& $adb -s $freshDevice shell dumpsys package worq.order |
    Select-String 'versionCode=|versionName='
```

Verify launch, create/import/Restore workflow, task/Client/Consultant/Tag behavior, CSV/XLSX/manual
Google export, automatic Google behavior if connected, and no unexpected permission/storage prompt.

## Connected suites

Run the complete `:app:connectedDebugAndroidTest` suite separately on disposable API 26 and current
target emulators using the same environment variables and `-Duser.home=$projectUserHome` above. Do
not run instrumentation against a populated signed-update fixture because debug-test installation
can replace or clear it.

The remaining manual API 26 and physical-device matrices are intentionally deferred under D-122 and
must be reported as unexecuted, not passing. See `DEFERRED_TESTS.md`.
