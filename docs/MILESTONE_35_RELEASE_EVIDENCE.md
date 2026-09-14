# Milestone 35 Release Evidence

Status: **`0.3.0` publicly released; owner-confirmed public artifact and physical-device upgrade**
Branch reviewed: `milestone35`  
Integration branch: `v0.3.0-development`  
Reviewed: 2026-09-13

This inventory preserves the pre-publication gate history. The owner subsequently confirmed the
public release, downloaded APK checksum, and physical-device update over retained data. Existing
Milestone 30–34 results are identified separately from the owner-signed `0.3.0` artifact checks.
The owner accepted limited resource profiling and minor maximum-scale visual imperfections.
Public-download checksum and install checks can occur only after publication; test-APK retention
is a test-harness convenience still unverified, not a runtime release gate.
No result is considered passed merely because a command is planned.

## 1. Repository preflight

| Check | Evidence | Status |
| --- | --- | --- |
| Active branch | `git branch --show-current` returned `milestone35` | Pass |
| Milestones 30–34 integrated | Every milestone branch is an ancestor of `v0.3.0-development` | Pass |
| Correct starting point | `v0.3.0-development` and `milestone35` both point to `1acb712` | Pass |
| Integration separation | `main...v0.3.0-development` is 0 behind / 11 ahead | Pass |
| Clean worktree | `git status --short` returned no entries at Task 35A preflight | Pass at preflight; intended Task 35 documentation changes now await review |

## 2. Identity and release artifact

| Requirement | Evidence | Status |
| --- | --- | --- |
| Application ID/namespace | Source, output metadata, and `aapt2`: `worq.order` | Pass |
| SDK compatibility | `aapt2`: min 26, target 36, compile 36; Gradle compile SDK 36.1 | Pass |
| v0.3 version identity | Owner explicitly approved; source, output metadata, and `aapt2` contain `versionName = "0.3.0"`, `versionCode = 3` | Pass |
| Release signing configuration | `apksigner` verifies APK Signature Scheme v2 and one permanent release certificate; v2 supports the full API 26+ range | Pass |
| Release APK checksum/certificate | Current post-Google-fix `app-release.apk`: 16,494,849 bytes, SHA-256 `CBF04232B810BAC9BC2A64952E31D28FE5E6A51BF664762228904973AFB54D08`; release metadata reports `worq.order`, `0.3.0`, code 3. On 2026-09-13 the owner reported that `apksigner`, `aapt2`, and SHA-256 checks on this exact candidate returned every expected result, including permanent signer SHA-1 `57:51:0C:CB:30:01:A7:E7:0C:43:91:C9:16:A8:0A:0C:C6:03:BB:19` | Pass by owner report for local candidate; public asset comparison follows publication |
| No signing material in Git | Only `keystore.properties.example` is tracked | Pass for tracked-file scan; repeat before release |

## 3. Room, migration, and persistence

| Requirement | Evidence | Status |
| --- | --- | --- |
| Schema exports 1–5 present | `app/schemas/.../1.json` through `5.json` | Pass |
| Lossless 4→5 migration | `Schema5MigrationCoreTest` covers archived rows, nullable metadata, zero/one/many legacy intervals, active non-first interval, IDs, endpoints, metadata, FKs, and reopen | Pass through M30/M34; repeat full gate |
| 1→5 and 2→5 routes | Explicit migration chain and `WorqOrderDatabaseTest` | Pass by prior automated evidence; repeat full gate |
| Schema-5 one-interval guard | Unique `work_intervals.task_id`, cascade, and active-slot tests | Pass through M30/M33 |
| One global active timer | Room transaction and concurrency tests | Pass through M30–M34 |
| Populated signed v0.2→v0.3 update | Owner reports every Section 8 upgrade/idempotency check passed on a disposable API 36.1 emulator. A legacy task with two completed and one active interval became three one-interval tasks with metadata and running state retained | Pass by owner manual report |
| No destructive fallback | No `fallbackToDestructiveMigration` in production; explicit migrations 1→2→3→4→5 registered | Pass static |

## 4. Timer, date, lifecycle, and accessibility

| Requirement | Evidence | Status |
| --- | --- | --- |
| First/repeated Start behavior | M30/M31 tests and owner checks | Pass through M31 |
| No date/ZoneId rollover creation | M31 selection/recovery tests and owner checks | Pass through M31 |
| Exact first-boundary close, no continuation | M32 tests, M34 Room test, 23/25-hour/non-DST boundary tests, and owner-observed midnight export with no next-day duplicate task | Pass for observed current-release scenario |
| Monotonic/DST/wall-clock handling | Existing timer/recovery suites and lifecycle evidence | Owner reports current force-stop and running-timer reboot recovery passed; other current-release clock cases rely on prior automated/manual evidence |
| Notification authority/cleanup | M28 tests and owner checks | Pass through M28; repeat current artifact |
| API-26/current connected behavior | API 36.1 full suite passed in 3m 10s; after the API-26-only test-query correction, the focused schema-5 migration class passed in 53s and the complete 106-test API 26 suite passed in 1m 22s | Pass for current debug/test APKs; signed release smoke remains |
| Large text, layouts, TalkBack, semantics, contrast | M15/M24/M33 evidence exists | Owner reports current maximum text/display-scale functional checks passed with minor visual imperfections accepted as non-blocking; prior automated accessibility evidence remains applicable |
| No app-owned service/alarm/wake-lock/tick persistence | Static source and merged release-manifest review | Pass. WorkManager contributes library scheduling permissions/components, but WorqOrder never promotes automatic export to foreground work and has no foreground stopwatch service, exact alarm, app-owned wake lock, or tick persistence |
| Extended CPU/memory profile | One current-release owner sample: 36,544 KiB total PSS, 150,832 KiB RSS, 3.5% instantaneous CPU, 25.70 seconds cumulative CPU | Limited observation accepted by owner on 2026-09-13; no extended trend or meaningful emulator thermal conclusion claimed |

## 5. Export, Google, permissions, and security

| Requirement | Evidence | Status |
| --- | --- | --- |
| Shared immutable schema-5 snapshot | `ExportRowBuilder` and adapter tests | Pass through M33 |
| CSV/XLSX/Google schema-5 equivalence | Serializer/writer/gateway tests and prior smoke | Pass through M33; repeat current artifact |
| Google auth, one spreadsheet, `drive.file` | Authorization/connection tests and prior fresh-account evidence | Pass by prior evidence; release-client recheck required |
| Automatic Google captured-date/pending behavior | M32 manager/worker tests and owner-observed live midnight Google export of the captured date with the expected 13-column schema and no rogue next-day task | Pass for observed current-release scenario; pending/permission-error device paths retain prior evidence |
| Running-timer export lockout/no local mutation | Snapshot and coordinator tests | Pass through M32/M33 |
| Backup disabled | Source manifest plus extraction rules | Pass static: `allowBackup=false`, pre-Android-12 `fullBackupContent=false`, and Android 12+ cloud/device-transfer rules excluding all app-private domains; repeat merged manifest after rebuild |
| No broad storage permission | Manifest and SAF/scoped APIs | Pass static |
| Merged library permissions/components | Credential Manager contributes biometric/fingerprint normal permissions; WorkManager contributes wake-lock/network-state/foreground-service permissions, a `BIND_JOB_SERVICE`-protected job service, and a `DUMP`-protected diagnostics receiver | Pass static; none creates an app access gate, foreground stopwatch, or unprotected WorqOrder data endpoint |
| Stable compatible dependencies | Stable direct pins; D-080 retains the approved transitive Google identity preview exception. Owner-approved OSV review covered all 400 exact cached coordinates and the exact 175-coordinate release runtime graph | Pass for shipped graph: no known OSV match. Kotlin Gradle plugin 2.3.10 has build-cache-only GHSA-r937-wjx7-w2jp; remote cache is absent and Gradle build-output caching is now disabled pending a stable, compatible toolchain fix |
| No Firebase/backend/service account/POI/secrets | Source/config and tracked-file scans; only `keystore.properties.example` matched the tracked filename scan and strict credential-value patterns returned no matches | Pass static; repeat after artifact generation |
| Encryption/app lock | Explicitly optional Milestone E | N/A; prohibited in M35 |
| Free/direct/GPLv3 distribution | Repository policy and release docs | Pass as policy; no marketplace publication |

## 6. Owner-run clean gate

Run with an active emulator. There is no standalone formatter task; `git diff --check` is the source
whitespace check.

```powershell
Set-Location 'T:\_SC Video\PROJECTS\2026\DNA Work Order App\app\WorqOrder'
$env:JAVA_HOME = 'S:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = 'C:\Users\colby\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = (Resolve-Path -LiteralPath '.gradle').Path
$projectUserHome = (Resolve-Path -LiteralPath '.android-user').Path

& .\gradlew.bat "-Duser.home=$projectUserHome" --offline --no-daemon `
    :app:clean `
    :app:testDebugUnitTest `
    :app:lintDebug `
    :app:lintRelease `
    :app:assembleDebug `
    :app:assembleDebugAndroidTest `
    :app:assembleRelease

& .\gradlew.bat "-Duser.home=$projectUserHome" --offline --no-daemon `
    :app:connectedDebugAndroidTest
```

Repeat the connected suite with an API-26 emulator and the current-target emulator. Do not run the
nonexistent `:app:testReleaseUnitTest` task.

After release assembly:

```powershell
$releaseApk = (Resolve-Path -LiteralPath '.\app\build\outputs\apk\release\app-release.apk').Path
$latestBuildTools = Get-ChildItem -LiteralPath (Join-Path $env:ANDROID_HOME 'build-tools') -Directory |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
$apkSigner = Join-Path $latestBuildTools.FullName 'apksigner.bat'
$aapt2 = Join-Path $latestBuildTools.FullName 'aapt2.exe'
Get-Item -LiteralPath $releaseApk | Select-Object FullName,Length,LastWriteTime
Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256
& $apkSigner verify --verbose --print-certs $releaseApk
& $aapt2 dump badging $releaseApk | Select-String 'package:|application-label:'
```

Record path, size, SHA-256, package, version, and signer SHA-1. Compare the SHA-1 with the permanent
release certificate and registered OAuth client; never print or commit keystore passwords.

## 7. Repository/security scan

```powershell
git diff --check
git status --short
git ls-files | Select-String -Pattern '(?i)(keystore|\.jks$|\.p12$|google-services\.json|local\.properties|credentials|secret|token|private.?key)'
git grep -n -I -E 'AIza[0-9A-Za-z_-]{20,}|client_secret|private_key|BEGIN (RSA|EC|OPENSSH) PRIVATE|ya29\.[0-9A-Za-z_-]+|1/[0-9A-Za-z_-]{20,}' -- . ':!docs/*' ':!*.md'
```

## 8. Populated upgrade and fresh install

1. On a disposable profile, install the owner-signed v0.2 APK and create archived/active clients,
   zero/one/many-interval tasks, all metadata, preferences, selection, Google connection/auto-export
   state, and an active timer whose open interval is not the first historical interval.
2. Install the owner-signed v0.3 APK over it without uninstalling or clearing storage.
3. Confirm schema 5 migration preserves every client, archived reference, task, interval, metadata,
   preference, Google connection/pending state, selection, and active timer without duplicates.
4. Exercise Start/Stop, no-rollover date changes, exact-boundary closure, and schema-5 CSV/XLSX/
   Google output after the update.
5. Install v0.3 fresh on a separate disposable profile and verify first launch, default settings,
   task creation, timing, export controls, and Google setup guidance.

On 2026-09-12 the owner reported that every preceding manual upgrade/idempotency check passed on
the disposable API 36.1 emulator. The old task with two completed intervals and a third active
interval split into three daily tasks, each carrying one preserved interval; metadata and the
running timer appeared retained. This is the deliberate, lossless schema-5 behavior, not a
duplicate-task defect. The owner also performed a real midnight Auto Export: the captured work
date reached Google Sheets with the expected 13-column schema, and no task was created on the new
date. These are owner-observed outcomes, not independently captured device logs. Uninstall/Clear
Storage is data-removing behavior and is not an upgrade test.

## 9. Release blockers and handoff

The Task 35C repository-only review confirmed the branch ancestry, schema exports 1–5, explicit
migration chain, schema-5 structural constraints, one-row canonical export projection, exact
`drive.file` scope, narrow source permissions, merged-manifest attribution, disabled backup,
absence of destructive fallback, and absence of tracked credential values. It also replaced one
hardcoded `0.2.0` About-screen assertion with `BuildConfig.VERSION_NAME`; no product behavior was
changed.

The first clean owner gate passed in 7m 15s with 140 actionable tasks (95 executed, 44 from cache,
1 up-to-date): 234 JVM tests passed with zero failures/errors/skips; debug and release lint each
completed with zero errors and 22 warnings; and debug, debug-test, and release APKs assembled. The
two native symbol-stripping messages packaged unchanged third-party AndroidX/DataStore libraries
and were non-fatal. Lint identified that the Android 12+ extraction rules did not also state the
legacy API 26–30 policy through `fullBackupContent`; Task 35C added `fullBackupContent=false` and
required the post-fix clean build/lint gate recorded below.

The post-fix clean gate passed in 2m 1s with 140 actionable tasks (70 executed, 67 from cache,
3 up-to-date). It again produced 234 passing JVM tests with zero failures/errors/skips and
successful debug, debug-test, and release APKs. Debug and release lint each completed with zero
errors and 21 warnings; the `DataExtractionRules` warning is gone. The remaining warning groups
are deliberate SDK/version compatibility, retained hidden time-zone resources, identical round/
standard launcher art, and non-blocking Compose/KTX style suggestions. The current release APK is
16,478,421 bytes with SHA-256
`F9FFCE361C1D87F8360B3F6EF495FF498947FD08E7B206FD34FF9A62A6B24F8E`; its output metadata reports
application ID `worq.order`, version `0.3.0`/code 3, release variant, and minimum API 26. Certificate
and package inspection then passed from owner-supplied Android SDK tool results: `aapt2` reports
`worq.order`, `0.3.0`/code 3, min API 26, target API 36, and label `WorqOrder`; `apksigner` verifies
APK Signature Scheme v2 with certificate SHA-1
`57:51:0C:CB:30:01:A7:E7:0C:43:91:C9:16:A8:0A:0C:C6:03:BB:19`, exactly matching the permanent
v0.1/v0.2 signer recorded in the repository. V2-only signing is valid across WorqOrder's API 26+
support range; v3/v3.1/v4 are not release requirements.

The first current-emulator connected gate passed on API 36.1 in 3m 10s with 73 actionable tasks
(6 executed, 67 up-to-date). The first API 26 run executed all 106 tests and found one test-only
compatibility defect: `Schema5MigrationCoreTest` queried the newer table-valued
`pragma_foreign_key_check` form, which Android 8 SQLite does not provide. Production migration
code already used the compatible `PRAGMA foreign_key_check` command. The test now uses that same
command and treats any returned row as a violation; the corrected reruns are recorded below.

The corrected API 26 focused migration class passed in 53s with 73 actionable tasks (6 executed,
67 up-to-date). The complete API 26 connected suite then passed in 1m 22s with 73 actionable tasks
(1 executed, 72 up-to-date), all 106 tests completing without failure. Together with the earlier
API 36.1 full-suite pass, the connected API matrix is complete for the current debug/test APKs.

After the dependency review disabled Gradle build-output caching, the owner ran the final clean
gate with `--no-build-cache`. It passed in 2m 43s with 140 actionable tasks (137 executed,
3 up-to-date): all 234 JVM tests passed with zero failures/errors/skips; debug and release lint
each reported zero errors and 21 accepted warnings; and debug, debug-test, and owner-signed release
APKs assembled. The regenerated release APK is 16,478,421 bytes with SHA-256
`27DD6814CB4AEFC922275253301C3372769E9C2F19E4C1C4CFBA986EAFECDA99`. The AndroidX/DataStore
native symbol-strip notices remained non-fatal. The owner subsequently reported that the requested
package and certificate checks on the final build passed; the detailed tool output was not pasted
into this audit.

The pre-Google-fix signed artifact, clean JVM/lint/debug/release/API-26/current connected gates,
and owner-reported populated v0.2→v0.3 migration and live midnight Google export were evidenced.
The artifact checksum from that gate has since been superseded. Other current
release manual lifecycle, accessibility, performance, fresh-install, and three-destination export
checks remain to be explicitly recorded before public release. Task 35A is complete when
this inventory is accepted. Task 35B documentation polish is complete; no version, signing,
dependency, OAuth, migration, concurrency, or application behavior was changed in that phase.
The owner explicitly approved `versionName = 0.3.0` and `versionCode = 3`, and Task 35C applied
that identity. Task 35C must still withhold release if a required gate fails or remains
unperformed.

### Cross-device Google export regression discovered during final manual checks

The owner exported the same date from two emulators connected to one spreadsheet and found that
the second device erased the first device's rows. That invalidates the prior Google re-export
release gate and makes every APK checksum above **superseded** once the fix is built. The current
code changes Google-only export planning to read the existing date tab through at most column P,
preserve remote rows, update by hidden task
ID, and append unseen tasks. CSV/XLSX are unchanged. Planner and encoder regressions have been
added. The owner-run offline no-build-cache lint, JVM, debug/debug-test/release build gate passed
in 2m 49s with 139 actionable tasks (50 executed, 89 up-to-date). The first connected run executed
zero tests because an already-installed app on that emulator had a different signing certificate.
After moving to a compatible test emulator, the owner reported `connectedDebugAndroidTest`
**BUILD SUCCESSFUL** in 2m 30s (73 actionable tasks, 1 executed, 72 up-to-date). The current
generated connected XML records 108 passing tests. The owner subsequently reported the supplied
Step 4 manual checks passed, including the post-fix same-date Google retry. The renewed artifact
checksum is recorded below. The owner subsequently confirmed the exact candidate's signer,
package, version, and checksum. The public-download comparison follows publication. Do not
release this worktree before the reviewed commit and integration merge.

### Connected-test APK retention

The owner observed that a successful connected test run removed the debug app from its disposable
emulator. The project sets AGP's
`android.injected.androidTest.leaveApksInstalledAfterRun=true` option with the intent to leave
test-installed APKs in place. This affects only test-device cleanup, not release signing,
installation, Room, or runtime behavior. The post-run package-presence check has not yet been
reported, so retention is not claimed as verified.

### 2026-09-13 owner follow-up

The owner reports that the supplied Step 4 manual checks passed after the Google merge change.
The current project-local test XML records 237 JVM tests and 108 connected tests, all with zero
failures, errors, or skips. The owner also reports that force-stop recovery and running-timer
reboot recovery passed. Maximum accessibility text and display scaling produced minor visual
imperfections, but the owner accepted them as non-blocking after functional checks. One resource sample recorded
36,544 KiB total PSS, 150,832 KiB RSS, and 3.5% instantaneous CPU; the owner explicitly accepts
this limited evidence and elects to revisit CPU, temperature, or memory only if a problem appears.
No extended resource trend or physical-device thermal result is inferred from that sample.

Repository-local checks show `git diff --check` exits 0 with informational CRLF-to-LF warnings,
no tracked keystore/local-configuration/APK filenames from the specified scan, and no strict
credential-pattern match in tracked application files. The current in-project signed-release APK
is 16,494,849 bytes with SHA-256
`CBF04232B810BAC9BC2A64952E31D28FE5E6A51BF664762228904973AFB54D08` and release output
metadata reports `worq.order`, `0.3.0`, code 3. This hash supersedes every earlier candidate in
this document. The owner confirms `apksigner`, `aapt2`, and checksum checks on this exact APK
returned the expected permanent identity. Confirmation that the public upload preserves these
bytes follows publication. The connected-test APK-retention check remains unverified; it concerns
test-device cleanup only and does not change release behavior.

### Public-release owner confirmation

The owner reports that `0.3.0` is officially published. The downloaded APK's SHA-256 matched
`CBF04232B810BAC9BC2A64952E31D28FE5E6A51BF664762228904973AFB54D08`, and its inspected
package was `worq.order`, versionName `0.3.0`, versionCode `3`, minimum API 26. The owner installed
it on a physical device over an existing WorqOrder installation without uninstalling; prior data
remained intact, and new task Start/Stop and interval accuracy passed. These are owner-reported
results, not new tests executed during `0.4.0` planning.
