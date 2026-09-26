# Milestone 55A — `0.6.0` Audit and Evidence Map

Audit date: 2026-09-24  
Branch: `milestone55`  
Starting weekly token budget reported by owner: 16%

Closure note: the owner later completed every non-deferred gate, merged through
`v0.6.0-development` into `main`, published `0.6.0`, and independently verified the downloaded
release APK. This document otherwise preserves the Task 55A baseline as it existed when audited.

## Repository audit

- `milestone55` is clean and points to the merged `v0.6.0-development` baseline.
- `v0.6.0-development` contains the Milestone 54 merge; `main` remains behind it.
- No tracked signing material, local properties, APK/AAB, or generated fixture backup was found.
- `m54-fixtures/` remains ignored.
- Milestone 54 is complete under the owner-approved reduced matrix. Milestone 55 is the active
  release-audit milestone; no version identity has been changed.

## Requirement-to-evidence map

| Area | Implementation/evidence | Release status |
|---|---|---|
| Versioned portable ZIP | Strict two-entry manifest/data archive, Deflate writer, streamed current reader, checksums and hard bounds | Automated evidence present; owner gates required |
| Portable state and exclusions | Room/DataStore logical snapshot with credentials, Google connection, runtime, journal, and restore-point exclusions | Automated/static evidence present; owner round-trip gate required |
| Atomic replacement/recovery | Verified restore point, journaled Room transaction and Preferences replacement, startup convergence, swap Restore | Automated and current-target manual evidence reported passing |
| Settings workflow | Scoped pickers, confirmation dialogs, persistent status, timer lockout, non-dismissible replacement barrier | Compose/instrumentation and current-target manual evidence reported passing |
| Google ownership | Origin-scoped hidden row identity, legacy adoption guard, disconnect/sign-out cleanup | Automated and prior owner Google/device evidence reported passing |
| Export compatibility | Existing immutable schema 6 and 14 visible columns | Automated cross-destination evidence present; final owner smoke required |
| API/device coverage | API 26 and current connected suites; current-target recovery/stability matrix | Passed by owner report; manual API 26 and physical tests deferred under D-122 |
| Release identity | Owner approved and source now uses `versionName = 0.6.0`, `versionCode = 6` | Final-main and independently downloaded release verification passed by owner report |

## Owner-run gate

Run from a fresh PowerShell session. These commands are supplied for the owner; Codex must not run
them or claim their results.

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

& .\gradlew.bat "-Duser.home=$projectUserHome" --stop
git diff --check
if ($LASTEXITCODE -ne 0) {
    throw 'Whitespace/formatting check failed; stop here.'
}

& .\gradlew.bat "-Duser.home=$projectUserHome" `
    --offline --no-daemon `
    :app:lintDebug :app:lintRelease `
    :app:testDebugUnitTest `
    :app:assembleDebug :app:assembleRelease

if ($LASTEXITCODE -ne 0) {
    throw 'Milestone 55A offline audit gate failed; stop here.'
}
```

Then run the complete connected suite separately on API 26 and the current target emulator. Use the
project-local `-Duser.home` setup and record only owner-observed results. Do not install a debug APK
over a populated signed installation without first selecting a disposable emulator.

No dedicated formatter task is configured in this repository. `git diff --check` is the available
formatting/whitespace gate; Android lint remains the static-analysis gate.

## Required handoff

After the owner reports the offline and connected gates, Task 55B (Terra) reconciles only confirmed
documentation or release blockers. Task 55A stops here; it does not select release version numbers,
change signing identity, commit, merge, tag, upload, or publish.

Deferred manual API 26 and physical-device procedures remain in [DEFERRED_TESTS.md](DEFERRED_TESTS.md)
and are not silently converted into passing evidence.
