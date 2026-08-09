# WorqOrder `0.2.0` Release Checklist

Status: all pre-publication gates passed; ready for reviewed integration, tag, and GitHub Release
Release: `0.2.0` (`versionCode = 2`)
Upgrade baseline: public `0.1.0` (`versionCode = 1`)
Application ID: `worq.order`
Distribution: owner-signed APK attached to a GitHub Release

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
8. CSV, XLSX, and Google schema-4 output with representative task metadata.

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
