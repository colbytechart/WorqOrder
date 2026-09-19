# WorqOrder `0.2.0` Release Checklist (Historical)

Status: historical `0.2.0` pre-publication record. Do not use its artifact identity, branch names,
or version values for later releases. The completed `0.3.0` release gate is archived in
`MILESTONE_35_RELEASE_EVIDENCE.md`; the `0.4.0` pre-publication release gate belongs to Milestone 41.
Release: `0.2.0` (`versionCode = 2`)
Upgrade baseline: public `0.1.0` (`versionCode = 1`)
Application ID: `worq.order`
Distribution: owner-signed APK attached to a GitHub Release

## `0.4.0` completed handoff status

At the 2026-09-15 QA source freeze, the `0.4.0` implementation was present on the
`v0.4.0-development` branch but was not yet published. This file remains a historical `0.2.0`
checklist and must not be reused as `0.4.0` release evidence. Before any `0.4.0` publication,
Milestone 41C must verify the approved `0.4.0`/code 4 signed candidate,
the complete non-destructive migration chain and schema-6 exports, current/API-26 device gates,
live automatic Google behavior, and the last rebuilt artifact checksum, then provide final GitHub
release instructions. Some gates are now recorded in Section 10 and `QA_REPORT.md`; no release
was approved merely by that documentation update. The owner later completed merge/tag/publication,
downloaded-asset verification, and physical-device installation. `0.5.0` release work belongs to
Milestone 48; the checklist below remains historical evidence rather than reusable version values.

## 1. Permanent policies

- WorqOrder remains free and open source under GPLv3.
- Distribution remains a directly signed APK from the public GitHub repository; there is no
  Google Play release, Play App Signing, Play Console, or owner-managed tester list.
- Android backup remains disabled.
- Room remains authoritative. CSV, XLSX, and Google Sheets are one-way plaintext exports.
- CSV and XLSX remain manual; only the connected Google spreadsheet can use opt-in automatic
  export.
- No signing key, password, token, client secret, `local.properties`, real
  `keystore.properties`, APK, or AAB enters Git.
- Google support remains `drive.file` only, External/In Production, no billing, no paid quota,
  and no Workspace/organization/custom-domain requirement.

## 2. Identity and signing preflight

Confirm in `app/build.gradle.kts`:

- `applicationId = "worq.order"`
- `namespace = "worq.order"`
- `versionName = "0.2.0"`
- `versionCode = 2`
- `minSdk = 26`, `targetSdk = 36`, compile SDK `36.1`
- release is not debuggable and uses the permanent release signing configuration

The permanent keystore remains outside the repository. The ignored root `keystore.properties`
contains its path and credentials. Verify without printing secrets:

```powershell
git check-ignore -v .\keystore.properties
git status --short --untracked-files=all
```

Neither the properties file nor keystore may appear in Git status. Do not replace the permanent
key: `0.2.0` must use the same signer as `0.1.0` so Android accepts an in-place update.

## 3. Google production preflight

The owner-confirmed production configuration must remain:

- Audience **External**, publishing status **In Production**.
- No active branding/verification requirement.
- Google Sheets API, Drive API, and Picker API enabled.
- Only `https://www.googleapis.com/auth/drive.file` configured/requested.
- Direct-release Android OAuth client bound to `worq.order` and the permanent release SHA-1.
- Web client ID supplied only through ignored local/CI configuration.
- No billing account, paid service, custom domain, or test-user intervention.

Before release, install the signed APK and repeat sign-in/connection/export with an eligible
Google account that was never manually added as a tester.

## 4. Clean automated gate

In a new PowerShell session:

```powershell
Set-Location 'T:\_SC Video\PROJECTS\2026\DNA Work Order App\app\WorqOrder'

$env:JAVA_HOME = 'S:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = 'C:\Users\colby\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = (Resolve-Path -LiteralPath '.gradle').Path
$projectUserHome = (Resolve-Path -LiteralPath '.android-user').Path

& .\gradlew.bat "-Duser.home=$projectUserHome" --stop
& .\gradlew.bat "-Duser.home=$projectUserHome" --offline --no-daemon clean
& .\gradlew.bat "-Duser.home=$projectUserHome" --offline --no-daemon `
    :app:testDebugUnitTest `
    :app:lintDebug `
    :app:lintRelease `
    :app:assembleDebug `
    :app:assembleDebugAndroidTest `
    :app:assembleRelease
```

With one unlocked emulator/device attached:

Use a disposable debug-test device, not an emulator containing the owner-signed release app:
debug and release signatures cannot update one another. The current project setting
`android.injected.androidTest.leaveApksInstalledAfterRun=true` is intended to keep the debug app
and test APK installed after this command, but post-run package presence must be checked. It does
not prevent test cases from changing their own test data.

```powershell
& .\gradlew.bat "-Duser.home=$projectUserHome" --offline --no-daemon `
    :app:connectedDebugAndroidTest
```

There is no configured formatter task. Run:

```powershell
git diff --check
```

All failures must be fixed or explicitly classified as release blockers. Do not use a lint
baseline or skip failing tests to manufacture a green gate.

## 5. Runtime and upgrade matrix

Required release evidence:

1. API 26 emulator: complete connected suite plus create/edit/timer/CSV or XLSX smoke.
2. Current target environment (API 36.1): complete connected suite and primary workflow smoke.
3. Available next API (for example API 37.x): install/start/create/timer/orientation smoke.
4. Portrait and both Right-/Left-handed landscape layouts, including large text/display scaling.
5. Running Timer notification permission, private/redacted content, swipe, Stop, process, reboot,
   and force-stop behavior.
6. Auto Export switch visibility/eligibility, pending notification, and at least one live
   captured-date Google export or accepted documented best-effort limitation.
7. Client CSV import with valid, duplicate/archived, invalid, and cancel samples.
8. CSV, XLSX, and current Google schema-5 output with representative task metadata. On two
   devices connected to one test spreadsheet, export different tasks for the same work date,
   verify both remain, then re-export an edited task from the first device and verify the second
   device's row remains unchanged. Verify the visible table still has exactly 13 columns.

### Signed `0.1.0` to `0.2.0` update

On a disposable release-test device/profile:

1. Install the public owner-signed `WorqOrder-0.1.0.apk`.
2. Create active and archived clients, multiple dated tasks, repeated intervals, purchases text,
   selection, theme/export settings, and one running timer.
3. Do not uninstall or clear storage.
4. Install the owner-signed `0.2.0` APK over it.
5. Confirm every old client, task, interval, stored date/zone, selection, setting, and running
   timer remains intact with no duplicate rows or intervals.
6. Confirm migrated tasks honestly show blank Consultant/Billing Status/Mileage and Unspecified
   Work Type until explicitly edited.
7. Create a new Consultant and new task; confirm On-Site and Billable defaults and normal
   `0.2.0` operation.
8. Stop the migrated timer and verify its accumulated total and interval endpoint.

Automated Room instrumentation separately proves populated `1→4` and released `2→4` migrations,
including an open timer. The signed replacement test proves package/signing/DataStore/runtime
integration and cannot be replaced solely by the database fixture.

## 6. Security and repository gate

- Inspect the merged debug/release manifests. Expected direct permissions are `INTERNET`,
  `POST_NOTIFICATIONS`, and `RECEIVE_BOOT_COMPLETED`; WorkManager may contribute its documented
  normal scheduling components/permissions.
- Confirm both WorqOrder receivers are non-exported.
- Confirm `allowBackup=false` and full cloud/device-transfer exclusions.
- Search tracked files and generated release resources for private keys, OAuth secrets, access or
  refresh tokens, real user emails, private spreadsheet IDs, authorization headers, and sensitive
  logs.
- Confirm no Firebase, backend, broad storage permission, destructive Room fallback, Apache POI,
  foreground timer service, exact alarm, or app-owned wake lock.
- Perform a current dependency/advisory review with explicitly approved network access; record the
  date and results in `SECURITY_REVIEW.md` and `QA_REPORT.md`.

## 7. Artifact verification

After the clean release build:

```powershell
$apk = (Resolve-Path '.\app\build\outputs\apk\release\app-release.apk').Path
$buildTools = Get-ChildItem "$env:ANDROID_HOME\build-tools" -Directory |
    Sort-Object { [version]$_.Name } -Descending |
    Select-Object -First 1

& (Join-Path $buildTools.FullName 'apksigner.bat') verify --verbose --print-certs $apk
Get-FileHash -Algorithm SHA256 -LiteralPath $apk
Get-Item -LiteralPath $apk | Select-Object FullName, Length, LastWriteTimeUtc
```

Confirm exactly one signer and that its certificate SHA-1 matches both `0.1.0` and the release
Android OAuth client. Record the APK size and SHA-256. Copy it without changing bytes:

```powershell
Copy-Item -LiteralPath $apk -Destination '.\WorqOrder-0.2.0.apk'
Get-FileHash -Algorithm SHA256 -LiteralPath '.\WorqOrder-0.2.0.apk'
```

The copied asset remains ignored and must not be committed.

Verified final `0.2.0` artifact:

- source path: `app/build/outputs/apk/release/app-release.apk`
- GitHub asset name: `WorqOrder-0.2.0.apk`
- size: `16,477,861` bytes
- SHA-256: `92F8F92D9327B2089D8FAE23DF181CF74CD606BE20C6531296182CE7711B305A`
- signer: one RSA-4096 signer using APK Signature Scheme v2
- certificate SHA-1:
  `57:51:0C:CB:30:01:A7:E7:0C:43:91:C9:16:A8:0A:0C:C6:03:BB:19`

The package identity, permanent signer match, signed `0.1.0` upgrade, release installation, and
functional smoke checks passed. Do not rebuild or modify the APK before uploading it; doing so
would change the checksum.

## 8. Integration-branch and GitHub release sequence

Do not merge until every required gate above is green.

1. Commit Milestone 29 on `milestone29`:

   ```powershell
   git status --short
   git add <reviewed-files>
   git commit -m "chore: prepare WorqOrder 0.2.0 release"
   git push -u origin milestone29
   ```

2. Open and merge a pull request from `milestone29` into `v0.2.0-development`.
3. Update local integration state and verify ancestry:

   ```powershell
   git switch v0.2.0-development
   git pull --ff-only origin v0.2.0-development
   git merge-base --is-ancestor milestone29 v0.2.0-development
   git status --short --branch
   ```

4. Open the final reviewed pull request from `v0.2.0-development` into `main`. The base must be
   `main`; the compare branch must be `v0.2.0-development`.
5. Review the complete `0.1.0...0.2.0` diff and checks, then merge without deleting `main` history.
6. Update local `main`, verify the integration branch is an ancestor, and tag the exact verified
   release commit:

   ```powershell
   git switch main
   git pull --ff-only origin main
   git merge-base --is-ancestor v0.2.0-development main
   git tag -a v0.2.0 -m "WorqOrder 0.2.0"
   git push origin v0.2.0
   ```

7. On GitHub, open **Releases → Draft a new release**, select existing tag `v0.2.0`, title it
   **WorqOrder 0.2.0**, and attach `WorqOrder-0.2.0.apk`.
8. Put the exact SHA-256 in the release notes with the major changes and known limitations. Do not
   mark it as a pre-release if this is the accepted stable build.
9. Publish, download the public APK asset, recompute its SHA-256, verify its signature, and install
   it over a retained `0.1.0` or release-candidate installation.
10. Confirm the public source archives are generated from `v0.2.0` and that no secret/artifact was
    committed.

## 9. Release evidence record

All pre-publication evidence is recorded in `QA_REPORT.md`: commands and test counts, API/device
configurations, manual results, signed update, fresh-account Google behavior, dependency/advisory
review, accepted limitations, and the final artifact identity, size, signer, and SHA-256.

After publishing, append confirmation that the downloaded public asset reproduces the recorded
SHA-256, verifies with the same signer, installs, and launches. That post-publication check is not a
reason to delay the reviewed commit, integration merge, tag, or GitHub Release.

## 10. `0.4.0` Milestone 41 pre-publication release gate

This addendum, not the historical `0.2.0` command/artifact examples above, governs `0.4.0`.
The owner approved `versionName = 0.4.0` and `versionCode = 4`; package `worq.order`, the
permanent signer, minimum API 26, disabled Android backup, `drive.file`, GPLv3, and direct
no-cost GitHub distribution are unchanged.

The latest clean offline gate and signed candidate are recorded in `QA_REPORT.md`, Section 17.
The owner reports a populated signed `0.3.0` install-over and Notes, Create/Edit, legacy Google,
CSV, and XLSX visual/runtime checks passed. The pre-publication evidence is:

1. The owner reports a populated signed `0.3.0` update retained data and confirms a fresh API-37
   installation of the exact signed `0.4.0` candidate. Neither check uninstalled or cleared the
   owner's populated upgrade fixture. Recheck the *post-merge* APK if it is rebuilt.
2. A complete post-footer/icon connected suite on a **disposable debug-test emulator**, not the
   owner-signed populated release fixture. This gate passed 119/119 on API 26 after the final
   bounded presentation change; the earlier current-API suite passed 118/118 before it.
3. A live opt-in `0.4.0` automatic Google export occurred by owner report, with all 14 visible
   columns and no duplicate row. WorkManager remains best-effort; report any pending
   authorization/recovery state rather than assuming a guaranteed daily execution time.
4. The owner explicitly confirms sign-in, connect, and 14-column export from the owner-signed
   candidate with a real Google account never added as a tester. The owned schema-5 tab upgrade,
   repeated export, and cross-device append also passed by owner report.
5. The owner reports API-37 client/Consultant/task-Notes/timer/orientation smoke and 14-column
   cross-device Google append passed from a fresh install of the exact signed candidate.
   Short/large-scale layouts, notification privacy/recovery, and accepted unchanged
   limitations are recorded in `QA_REPORT.md` and earlier release evidence.
6. Recompute the artifact's size/SHA-256 after the **last** source build. Verify one permanent
   signer and `worq.order`/`0.4.0`/code 4. After commit/merge/tag, never upload an APK whose bytes
   differ from that reviewed artifact; compare the downloaded GitHub asset hash and signer.

The source/candidate pre-publication gate is green; the final post-merge artifact hash and public
download integrity are owner-controlled publication checks, not reasons to overstate an already
published release. No commit, integration merge, tag, or GitHub Release is performed by Codex.

## 11. Owner-only `0.4.0` GitHub integration and publication

The reviewed, owner-signed candidate is
`app/build/outputs/apk/release/app-release.apk`, 16,511,217 bytes, SHA-256
`B944CA0C8244179DFDBB0E899584A2CC4BB1E9A5A93DA0B4F9700020589E14C4`. Its signer,
package, version, migration, Notes/export behavior, fresh installation, and non-tester Google
access passed the pre-publication gates in `QA_REPORT.md`. These exact bytes may be published
**only if** the final `main` source tree is identical to the reviewed `milestone41` tree and the
APK hash stays unchanged. Otherwise stop, review the differing source, rebuild, rehash, and
repeat the relevant signed-install/export checks before publication. Never force-update a tag.

1. On `milestone41`, review `git status --short`, `git diff --check`, and the full diff. Stage
   only the intended tracked code/docs (`git add -u` after review), then inspect
   `git diff --cached --check`, `git diff --cached --stat`, and `git status --short`.
   Commit with `chore(release): prepare WorqOrder 0.4.0`, then push `milestone41`.
2. On GitHub create a pull request with **base** `v0.4.0-development` and **compare**
   `milestone41`. Review it and use **Create a merge commit**, not squash or force-push, so the
   milestone branch remains an ancestor. Locally update `v0.4.0-development` with
   `git pull --ff-only origin v0.4.0-development` and require
   `git merge-base --is-ancestor milestone41 v0.4.0-development` to succeed.
3. Create the final pull request with **base** `main` and **compare**
   `v0.4.0-development`. Review and use **Create a merge commit**. Locally update `main` with
   `git pull --ff-only origin main`; require
   `git merge-base --is-ancestor v0.4.0-development main` and
   `git diff --exit-code milestone41 main -- .` to succeed. If the source-tree check fails,
   do not tag or upload the old APK.
4. With local `main` clean, recompute the existing candidate's SHA-256, inspect `apksigner`
   (one v2 signer, permanent SHA-1 `57510ccb3001a7e70c4391c916a80a0cc603bb19`) and
   `aapt2` (`worq.order`, `0.4.0`/code 4, min API 26). Copy the unchanged APK to ignored
   `WorqOrder-0.4.0.apk`; compare source and copied hashes. Do **not** rebuild after this
   evidence unless willing to treat the new binary as a new candidate and repeat checks.
5. Verify local and remote `v0.4.0` tags do not already exist. Annotate the exact `main`
   commit (`git tag -a v0.4.0 -m "WorqOrder 0.4.0"`), verify its peeled commit is local
   `main`, then `git push origin v0.4.0`. Never rewrite an existing release tag.
6. On GitHub select the existing `v0.4.0` tag in **Releases → Draft a new release**, title
   it **WorqOrder 0.4.0**, upload only the checked `WorqOrder-0.4.0.apk`, and paste the exact
   SHA-256 into release notes. Summarize Notes/schema 6 and the non-destructive upgrade;
   link the GPLv3 license and known limitations. Publish as a normal release, not a prerelease,
   only after the artifact and source checks above are green. No Google Play action is involved.
7. Download the public APK asset independently. Compare its SHA-256 to the published value,
   verify its permanent signer/package/version, and install it **over** an existing signed
   WorqOrder installation with `adb install -r` or through Android's normal package installer.
   Confirm old data remains and the app launches. Record that post-publication result in QA/
   handoff docs in a later reviewed documentation commit; never alter the published tag or APK
   bytes to do so.

## 12. `0.5.0` Milestone 48 owner handoff (released)

The `0.5.0` Tag implementation is present through Milestone 47 on the development branch. Do not
reuse the historical `0.2.0`/`0.4.0` artifact values above for this release. On 2026-09-19 the owner
explicitly approved `versionName = 0.5.0` and `versionCode = 5`. Keep the permanent `worq.order`
signer. The completed artifact/publication evidence is recorded in `QA_REPORT.md` and
`REQUIREMENTS_TRACEABILITY.md`.

Required owner evidence included clean offline formatting/lint/JVM/debug/release builds,
API-26/current connected suites, schema-1-through-7 migration and populated `0.4.0` install-over
checks, Tag CRUD/search/import/picker/manual task checks, all three export paths plus automatic
Google, accessibility/lifecycle/performance/security checks, signer/package/version/hash checks,
and public-download install-over verification. The owner reported them passed. No merge, tag,
upload, publication, signing-key change, Google-scope change, or environment teardown was performed
by Codex.

## 13. `0.5.0` Milestone 48C owner-run commands and checklist

The owner runs these commands from a new PowerShell session. They use the project-local Gradle and
Android user homes and do not uninstall or clear application data. Run the connected suite once per
active disposable emulator: first API 26, then the current target emulator.

```powershell
Set-Location 'T:\_SC Video\PROJECTS\2026\DNA Work Order App\app\WorqOrder'

$env:JAVA_HOME = 'S:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = 'C:\Users\colby\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = (Resolve-Path -LiteralPath '.gradle').Path
$projectUserHome = (Resolve-Path -LiteralPath '.android-user').Path

git diff --check
if ($LASTEXITCODE -ne 0) { throw 'Whitespace/conflict check failed.' }

& .\gradlew.bat "-Duser.home=$projectUserHome" --offline --no-daemon clean
if ($LASTEXITCODE -ne 0) { throw 'Clean failed.' }

& .\gradlew.bat "-Duser.home=$projectUserHome" --offline --no-daemon :app:testDebugUnitTest
if ($LASTEXITCODE -ne 0) { throw 'JVM unit tests failed.' }

& .\gradlew.bat "-Duser.home=$projectUserHome" --offline --no-daemon :app:lintDebug :app:lintRelease
if ($LASTEXITCODE -ne 0) { throw 'Lint failed.' }

& .\gradlew.bat "-Duser.home=$projectUserHome" --offline --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest
if ($LASTEXITCODE -ne 0) { throw 'Debug/test APK assembly failed.' }

& .\gradlew.bat "-Duser.home=$projectUserHome" --offline --no-daemon :app:assembleRelease
if ($LASTEXITCODE -ne 0) { throw 'Release assembly failed.' }

& .\gradlew.bat "-Duser.home=$projectUserHome" --offline --no-daemon :app:connectedDebugAndroidTest
if ($LASTEXITCODE -ne 0) { throw 'Connected instrumentation tests failed.' }
```

The connected suite includes Room schema 1-through-7 migration, active-timer, Compose, import,
Tag, picker, task-form, and export regression tests. If a debug app is already installed with a
different signer, use a disposable emulator or remove only that disposable test installation;
never uninstall a populated release installation used for the upgrade check.

### Manual owner matrix

1. **Migration and persistence:** On a populated signed `0.4.0` installation, create clients,
   Consultants, tasks, Notes, Tags, completed timing data, an active timer, settings, and an export
   connection. Install the signed `0.5.0` APK over it without uninstalling. Confirm all records,
   the active timer, preferences, and connection metadata remain. Reopen after force-stop/reboot.
2. **Tag management:** Verify both catalogs independently support Add, Edit, confirmed Delete,
   case-insensitive search, clear search, alphabetical ordering, and empty/no-result states.
3. **Tag CSV import:** Use valid quoted CSV with Unicode, commas, quotes, line breaks, duplicates,
   and header-looking cells. Confirm nonblank cells append, duplicates skip, lists stay sorted, and
   malformed/non-CSV/over-1 MiB/over-10,000-cell/over-400-code-point files make no partial change.
4. **Create/Edit picker:** Verify Add/Edit tags, ordered multi-select, Cancel/Apply, live search,
   clear search, Select All/Deselect All for filtered results, inline creation, no direct chip
   removal, and the 999-code-point composed limit. Confirm over-limit bulk selection is atomic.
5. **Task history:** Save a task with manual text and ordered Tags, then edit/delete catalog Tags.
   Confirm the task's saved text and export remain unchanged. Repeat Start and confirm snapshots
   copy in order with new identities; Notes remains blank on the new task.
6. **Form polish:** Check live `N / 999` and `Character limit: N / 999` states, live Tag-editor
   `N / 400` states, selector-local Client/Consultant errors, fixed action-only footers, and Edit
   Task inline Add Client recovery for archived assignments.
7. **Exports:** With the same task data, manually export CSV and one-off XLSX, then manually export
   Google Sheets. Confirm identical schema-6 14 columns, composed Description/Expense values,
   punctuation, no Tag column, and no Room mutation. Re-export from a second device and confirm
   keyed append/update preserves the other device's rows.
8. **Automatic Google:** Enable Auto Export, schedule near the local day boundary, and confirm the
   captured date exports silently. With a running timer, confirm midnight automatically closes the
   interval at the exact boundary, exports the completed captured date, leaves no active timer, and
   creates no continuation or duplicate next-day task. If a forced fallback leaves
   `TIMER_RUNNING`, confirm later successful Stop/reconciliation resumes automatically without a
   success notification. Reserve the attention notification/Settings action for authorization,
   connectivity, permission, remote, or local-storage failures that require user recovery.
9. **Accessibility/layout:** Check TalkBack, large font/display scale, light/dark themes, portrait,
   landscape handedness, keyboard navigation, picker scrolling, and error announcement. Confirm no
   critical Tag/task information depends on color or transient animation.
10. **Lifecycle/performance:** Check background, screen lock, activity recreation, process death,
    force-stop/reopen, reboot, and timer reconstruction. Observe a long foreground/background run
    for unbounded memory growth, excessive CPU, overheating, or duplicate refresh work.
11. **Release/security:** Confirm no secrets are tracked, backup remains disabled, package is
    `worq.order`, the permanent signer SHA-1 is unchanged, the explicitly approved version identity
    is present, and the final APK SHA-256 is recorded. Download the GitHub asset independently,
    verify its checksum/signature/package, and install it over the populated signed installation.

Record only results actually observed by the owner in `QA_REPORT.md`; failures remain release
blockers until resolved or explicitly accepted by the owner.

### Milestone 48 owner evidence — 2026-09-19

The owner reports Steps 1–10 passed on the required migration/device workflows. Step 8 initially
exposed the running-timer pending-export defect; D-106 corrected it, and the repeated real-boundary
check passed with exact timer closure, silent captured-date Google export, and no continuation or
duplicate task. The latest generated reports show 292/292 JVM and 151/151 connected tests passing;
the owner separately reports API-26 and current-target suites passed. Preliminary Step 11 source,
permission, backup, tracked-secret/artifact, prohibited-dependency, and destructive-migration scans
pass. The release identity is owner-approved and present in source. The owner-signed artifact,
permanent-signer comparison, package/version inspection, and checksum gate now pass. The exact
candidate SHA-256 is
`AB9AAD5FC34A0AD3677C8F3860BE1012DD104488D276816EA904B45A4C06F247`; its certificate SHA-1 is
`57510ccb3001a7e70c4391c916a80a0cc603bb19`, and `aapt2` reports `worq.order`/`0.5.0`/code 5,
minSdk 26, targetSdk 36. Install-over/fresh smoke, integration merge, tag/publication, independent
download, and physical install-over were the remaining gates at that point. Do not rebuild the
candidate after install verification unless all artifact checks are repeated and the recorded hash
is replaced.

The owner subsequently verified the official `0.4.0` GitHub APK baseline (SHA-256
`B944CA0C8244179DFDBB0E899584A2CC4BB1E9A5A93DA0B4F9700020589E14C4`, permanent signer), populated
it, and installed the unchanged `0.5.0` candidate over it. Data/migration, launch, Tag/task/timer,
version-display, and force-stop/reopen checks passed. A separate fresh installation on a disposable
emulator also passed. The APK was not rebuilt before publication. At that stage, integration merge,
tag/publication, independent public-asset verification, and physical-device installation remained.

The owner then merged Milestone 48 through `v0.5.0-development` into `main`, verified the annotated
`v0.5.0` tag points to the same `main` commit, published the unchanged APK on GitHub, independently
downloaded it, and repeated checksum/signature/package and physical-device installation checks.
All passed. `0.5.0` is publicly released; Milestone 48 is complete. Milestones 49 and 50 did not
start and still require separate explicit owner authorization.
