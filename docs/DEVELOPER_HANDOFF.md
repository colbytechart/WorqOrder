# Developer Handoff

## 1. Release Identity

- Product: WorqOrder
- Application ID and namespace: `worq.order`
- Minimum SDK: 26
- Target SDK: 36
- Compile SDK: 36.1
- Initial version: `0.1.0`
- Version code: `1`
- License: GPLv3
- Distribution artifact: owner-signed APK attached to a GitHub Release

Android application backup is disabled. Room is authoritative. CSV, XLSX, and Google Sheets are
one-way exports.

## 2. Required Local Toolchain

The proven Windows environment uses:

- Android Studio Embedded JBR 21;
- the installed Android SDK with platform 36.1;
- the repository Gradle wrapper;
- project-local `GRADLE_USER_HOME=.gradle`; and
- the dependency versions in `gradle/libs.versions.toml`.

Declare the environment on every new PowerShell session:

```powershell
Set-Location 'T:\_SC Video\PROJECTS\2026\DNA Work Order App\app\WorqOrder'
$env:JAVA_HOME = 'S:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = 'C:\Users\colby\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = (Resolve-Path -LiteralPath '.gradle').Path
```

Host-specific paths are examples and are not portable repository configuration.

The wrapper's Gradle 9.4.1 binary distribution is pinned with Gradle's published SHA-256 in
`gradle/wrapper/gradle-wrapper.properties`. When intentionally changing Gradle, update the URL and
checksum together from Gradle's official checksum reference, then verify the checked-in wrapper
JAR separately.

## 3. Local Configuration

Ignored `local.properties` must contain the public Web OAuth client ID:

```properties
sdk.dir=C\:\\Users\\developer\\AppData\\Local\\Android\\Sdk
worqorder.google.webClientId=REPLACE_WITH_WEB_CLIENT_ID.apps.googleusercontent.com
```

Ignored `keystore.properties` is created from `keystore.properties.example`:

```properties
storeFile=X:/secure/external/path/worqorder-release.jks
storePassword=LOCAL_KEYSTORE_PASSWORD
keyAlias=worqorder-release
keyPassword=LOCAL_KEY_PASSWORD
```

The keystore remains outside the repository and must be backed up securely. Never send the
keystore or passwords through Git, an issue, build log, or chat. Every update must use the same
permanent key.

## 4. Google Configuration

The owner configuration for `0.1.0` is:

- Google Auth Platform audience: External;
- publishing status: In Production;
- no app logo or active verification requirement;
- Google Sheets API, Google Drive API, and Google Picker API enabled;
- only `drive.file` configured/requested;
- debug Android client bound to `worq.order` plus debug SHA-1;
- direct-release Android client bound to `worq.order` plus permanent release SHA-1;
- Web application client ID supplied through ignored local configuration; and
- no billing account or paid service.

The release gate includes testing the signed APK with a fresh account never listed as a tester.
Forks signed by another key require their own no-cost Cloud project, Android clients, and Web
client ID. See `docs/GOOGLE_SHEETS_SETUP.md`.

## 5. Architecture Ownership

```text
Compose screens
  -> ViewModels / immutable StateFlow state
  -> repositories and coordination/domain services
  -> Room / DataStore / clock-zone abstractions / export gateways
```

Important invariants:

- only Room's singleton active-timer state authorizes a running timer;
- timer display ticks never write Room;
- UTC interval boundaries plus stored geographical ZoneId determine history;
- daily rollover uniqueness includes series, work date, and assignment ZoneId;
- migrations are explicit and non-destructive;
- all exports consume one immutable canonical snapshot;
- no destination imports or synchronizes back into Room; and
- running timers block all export destinations.

## 6. Build and Test Commands

With the environment from section 2:

```powershell
.\gradlew.bat --offline clean
.\gradlew.bat --offline :app:testDebugUnitTest
.\gradlew.bat --offline :app:lintDebug :app:lintRelease
.\gradlew.bat --offline :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat --offline :app:connectedDebugAndroidTest
.\gradlew.bat --offline :app:assembleRelease
```

Connected tests require an unlocked emulator/device. The Room schema files in `app/schemas` must
remain packaged as Android-test assets for migration verification.

The project has no configured formatter task. Use `git diff --check`, Kotlin compilation, and lint;
do not silently add a formatter dependency during a release.

## 7. Verify a Release APK

Locate the newest installed Android SDK Build Tools and verify:

```powershell
$apk = (Resolve-Path '.\app\build\outputs\apk\release\app-release.apk').Path
$buildTools = Get-ChildItem "$env:ANDROID_HOME\build-tools" -Directory |
    Sort-Object { [version]$_.Name } -Descending |
    Select-Object -First 1

& (Join-Path $buildTools.FullName 'apksigner.bat') verify --verbose --print-certs $apk
Get-FileHash -Algorithm SHA256 -LiteralPath $apk
```

Confirm exactly one signer and compare its SHA-1 to the registered release OAuth client. Publish
the SHA-256 alongside the APK. Do not publish the keystore or passwords.

## 8. Release Procedure

1. Confirm the working tree contains only intended tracked release changes.
2. Run the complete clean verification suite.
3. Install the release APK on a supported device.
4. With the fresh non-tester Google account, sign in, connect an editable spreadsheet, export a
   date, re-export it, disconnect, reconnect, and sign out.
5. Complete the manual API/accessibility/performance checks in `docs/RELEASE_CHECKLIST.md`.
6. Verify the APK signature and SHA-256.
7. Copy/rename the artifact to `WorqOrder-0.1.0.apk` without modifying its bytes.
8. Commit with `chore: prepare WorqOrder 0.1.0 release`.
9. Create annotated tag `v0.1.0` on the verified commit.
10. Create a GitHub Release from that tag; attach the APK and publish its SHA-256 in the notes.
11. Test the public download and installation instructions on another supported phone if
    available.

Build outputs are intentionally ignored and are not committed to Git.

## 9. Database Evolution

Room schema exports are committed from version 1. For every schema change:

1. increment the database version;
2. export the new schema into `app/schemas`;
3. write an explicit forward migration;
4. add fresh, populated, and open-timer migration tests;
5. never add destructive fallback to production; and
6. prove a signed update preserves clients, tasks, intervals, preferences, and active timer.

Never delete/reseed production data to recover a migration or storage failure.

## 10. Maintenance and Security

- Keep dependencies stable; review official release notes and advisories before upgrades.
- Query exact resolved package versions against a current advisory source. The 2026-07-29 release
  audit checked 36 runtime/build/test Maven package-version pairs with OSV and found no known
  affected versions.
- Re-run the full timer/date/DST/migration/export regression suite after behavior changes.
- Preserve `drive.file`; broader scopes require a new decision and consent/security review.
- Never add Firebase, a backend, analytics, billing, paid quota, embedded credentials, broad
  storage permissions, or an unnecessary background service.
- Keep logs free of client/task contents, tokens, spreadsheet IDs, and raw Google responses.
- Update `README.md`, `CHANGELOG.md`, privacy documentation, decisions, and tests for releases.

Optional Milestones 18 and 19 are not production prerequisites and must not begin without explicit
owner authorization.
