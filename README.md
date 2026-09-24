# WorqOrder

WorqOrder is a free, open-source Android work tracker for recording client tasks and their tracked
work time. Its core workflow is local and offline: Room is the authoritative data store, while
CSV, XLSX, and Google Sheets are optional one-way exports.

## Development Status

The owner published and physically verified `0.5.0`/code 5. It adds reusable Description and
Hardware / Software Purchase Tags, bounded CSV Tag import, searchable task Tag pickers, historical
task-owned Tag snapshots, task-form refinements, and exact-boundary automatic Google export for a
running timer. Check the
[Releases page](https://github.com/colbytechart/WorqOrder/releases) for the authoritative APK.
The [v0.5.0 roadmap](docs/V0_5_MILESTONE_PROMPTS.md) is retained as delivery evidence. The
[v0.4.0 roadmap](docs/V0_4_MILESTONE_PROMPTS.md) is retained as historical delivery evidence.

`0.6.0` is in development. Milestones 49–53 have delivered the versioned compressed backup format,
strict validation, atomic replacement/recovery engine, one app-private rolling restore point, and
the **Backup & Restore** Settings workflow on the development branch. These changes are not present
in the released `0.5.0` app and still require compatibility hardening and release audit. See the
[v0.6.0 roadmap](docs/V0_6_MILESTONE_PROMPTS.md). The prior environment-teardown work is deferred
to Milestones 56–57 after the eventual public `0.6.0` release.
Owner-deferred, unexecuted release tests are tracked transparently in
[Deferred Tests](docs/DEFERRED_TESTS.md); they must not be described as passing evidence.

## Install WorqOrder From GitHub

WorqOrder supports Android 8.0 (API 26) and newer. Install only APKs published by the repository
owner on this repository's official [Releases page](https://github.com/colbytechart/WorqOrder/releases).

1. On the Android phone, open this repository's **Releases** page.
2. Open the latest release and download the APK asset, such as
   `WorqOrder-<version>.apk`. Do not download the source-code archive when you want to install the app.
3. Optionally compare the release's published SHA-256 value with the downloaded APK. On a
   computer with PowerShell:

   ```powershell
   Get-FileHash -Algorithm SHA256 -LiteralPath '.\WorqOrder-<version>.apk'
   ```

4. Open the downloaded APK on the phone.
5. If Android blocks the installation, open the offered settings page and temporarily allow
   **Install unknown apps** for the browser or file-manager app that opened the APK.
6. Return to the APK and choose **Install**.
7. Open WorqOrder. You may turn **Install unknown apps** back off afterward.

Android may warn that a sideloaded app was not obtained from an app marketplace. Verify that the
APK came from this repository and that its SHA-256 matches the release notes before proceeding.
Managed phones may prohibit sideloading through an administrator policy.

Future updates are installed by downloading the newer owner-signed APK and installing it over the
existing app. Do not uninstall first: uninstalling or clearing app storage removes WorqOrder's
local database and preferences. A legitimate update retains the application ID and release
signing identity.

## Features

- Create daily client tasks with Consultant, Work Type, Billing Status, Mileage, description, and
  hardware/software-purchase metadata.
- Run one globally authoritative timer. Each task owns zero or one interval; starting a completed
  task creates a new same-day task so prior records remain unchanged.
- See the active timer in a silent, dismissible Android notification while platform/user settings
  permit it.
- Recover active timing after backgrounding, activity recreation, process death, and reboot.
- Browse dates and edit completed tasks and their single interval using stored geographical
  time-zone rules. Date changes never create rollover tasks.
- Add, rename, archive, restore, and CSV-import clients without damaging historical task
  relationships.
- Manage active/archived Consultants while preserving each task's assignment-time name.
- Maintain separate Description and Hardware / Software Purchase Tag catalogs, import Tags from
  user-selected CSV files, and compose ordered task-owned Tag snapshots into existing exports.
- View derived Billing Minutes rounded upward to 15-minute increments.
- Follow the system appearance or explicitly choose Light or Dark.
- Use the device's geographical time zone while preserving every task's assigned historical zone.
- Choose Right- or Left-handed two-column landscape layouts.
- Export the displayed date as UTF-8 CSV or a new XLSX workbook.
- Connect one editable Google spreadsheet and merge task rows into one WorqOrder-owned worksheet
  per date, preserving rows exported from other devices.
- Optionally schedule a best-effort captured-date Google export near each day's end.

See the [User Guide](docs/USER_GUIDE.md) for complete operating instructions.

## Data and Privacy Summary

- Room is the source of truth for clients, tasks, intervals, and the active timer.
- Preferences DataStore holds application preferences, selection hints, and non-secret Google
  connection metadata.
- Core tracking does not require a network, WorqOrder account, Firebase, or custom server.
- Android application backup is disabled.
- Local data is not promised to survive uninstalling the app or clearing its storage.
- CSV and XLSX files and readable Google Sheets cells are external plaintext copies.
- WorqOrder does not import from exports or synchronize changes back from Google Sheets.
- Google access requests only the non-sensitive per-file `drive.file` scope.

Read [Privacy and Data](docs/PRIVACY_AND_DATA.md) before using WorqOrder with sensitive client
information.

## Export Behavior

All destinations consume one immutable canonical snapshot and the same 14 columns:

1. Start date
2. End date
3. Consultant
4. Client
5. Description
6. Expense
7. Work type
8. Billing Status
9. Mileage
10. Start time
11. Stop time
12. Time spent
13. Billing minutes
14. Notes

CSV and XLSX use Android's create-document interface, so the user chooses each output location.
Every XLSX export creates a new workbook. Google Sheets writes to one connected spreadsheet and
uses a marked `WorqOrder_YYYY-MM-DD` worksheet for each exported date. Re-exporting merges into that
owned date table. Export is disabled while a timer is running.

## Architecture and Technology

WorqOrder is a single-module native Android application:

```text
Jetpack Compose UI
    -> ViewModels and StateFlow
    -> repositories and domain services
    -> Room / Preferences DataStore / export gateways
```

The project uses Kotlin, Jetpack Compose, Material 3, Compose Navigation, ViewModel,
coroutines/Flow, Room, Preferences DataStore, `java.time`, KSP, Gradle Kotlin DSL, and a version
catalog. Dependency construction uses a small manual application container.

Important source areas:

```text
app/src/main/kotlin/worq/order/
  app/             application container and navigation root
  data/            Room and Preferences DataStore implementations
  domain/          timer, date, validation, and coordination rules
  export/          shared snapshot plus CSV, XLSX, and Google adapters
  timer/           clock and live-timer abstractions
  ui/              Compose screens and ViewModels
app/src/test/       JVM tests
app/src/androidTest/ Room, migration, Compose, and integration tests
app/schemas/        committed Room schema exports
docs/               specifications, QA evidence, setup, and handoff
```

## Build From Source

### Prerequisites

- Windows development environment matching the current project, or an equivalent supported host
- Android Studio with Embedded JBR/JDK 21
- Android SDK platform 36.1 and target API 36
- Git
- Android device or emulator for instrumentation tests
- A developer-owned Google Cloud configuration when building Google functionality

The repository uses a project-local Gradle user home in the documented Windows workflow. Do not
commit `local.properties`, signing keys, passwords, or tokens.

### Google Build Configuration

Follow [Google Sheets Integration Setup](docs/GOOGLE_SHEETS_SETUP.md). Put the public Web OAuth
client ID in ignored `local.properties`:

```properties
worqorder.google.webClientId=REPLACE_WITH_WEB_CLIENT_ID.apps.googleusercontent.com
```

Forks signed with another certificate must create their own no-cost Google Cloud project and
matching Android OAuth clients. The public identifier is not a client secret; no OAuth secret
belongs in the APK.

### Debug Build and Tests

From PowerShell in the repository root:

```powershell
Set-Location 'T:\_SC Video\PROJECTS\2026\DNA Work Order App\app\WorqOrder'
$env:JAVA_HOME = 'S:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = 'C:\Users\colby\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = (Resolve-Path -LiteralPath '.gradle').Path
$projectUserHome = (Resolve-Path -LiteralPath '.android-user').Path

.\gradlew.bat "-Duser.home=$projectUserHome" --offline --no-daemon :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat "-Duser.home=$projectUserHome" --offline --no-daemon :app:lintDebug :app:lintRelease
.\gradlew.bat "-Duser.home=$projectUserHome" --offline --no-daemon :app:connectedDebugAndroidTest
```

The connected test command requires an unlocked emulator or device. See
[Developer Handoff](docs/DEVELOPER_HANDOFF.md) for the complete release procedure.

## Release Builds

Release signing uses an owner-controlled keystore stored outside the repository. Copy
`keystore.properties.example` to ignored `keystore.properties` and supply the local path and
passwords. Then run:

```powershell
.\gradlew.bat "-Duser.home=$projectUserHome" --offline --no-daemon clean :app:assembleRelease
```

Never create a new signing key for an update. Losing the permanent key prevents existing users
from installing future updates over the app. The `0.2.0` checklist and
[Milestone 35 release evidence](docs/MILESTONE_35_RELEASE_EVIDENCE.md) are historical.
`0.4.0` pre-publication gates are tracked historically under Milestone 41. Future releases must
likewise upload only an owner-signed APK whose final SHA-256 and signer have been checked and must
compare the public download afterward.

## Troubleshooting

- **No connected devices:** start an emulator or enable USB debugging and authorize the computer.
- **Google sign-in fails:** confirm package `worq.order`, the exact signing SHA-1, the Web client
  ID, External/In Production audience, and enabled APIs.
- **Spreadsheet cannot connect:** use an editable Google Sheets file owned by or shared with the
  signed-in account, then approve access to that exact file.
- **Worksheet conflict:** rename the existing unmarked `WorqOrder_YYYY-MM-DD` worksheet and retry.
- **Release signing fails:** confirm ignored `keystore.properties`, its external `storeFile`, alias,
  and passwords.
- **Offline dependency failure:** populate the documented Gradle cache while online, stop the
  daemon, then retry with the project-local cache.

See [Known Limitations](docs/KNOWN_LIMITATIONS.md) and the
[Google setup guide](docs/GOOGLE_SHEETS_SETUP.md) for more detail.

## Contributing

Contributions and forks are welcome.

```powershell
git clone <repository-url>
Set-Location '.\WorqOrder'
git switch -c feature/short-description
```

Before proposing a change:

1. Read `AGENTS.md`, the product specification, architecture, decisions, and relevant acceptance
   tests.
2. Do not commit credentials, signing material, personal spreadsheet IDs, or generated build
   outputs.
3. Add or update tests for every behavior change.
4. Run unit tests, lint, applicable builds, and relevant instrumentation tests.
5. Update specifications and decisions when intentionally changing product behavior.
6. Commit the change and open a pull request explaining the behavior, tests, and migration impact.

## License

WorqOrder is licensed under the [GNU General Public License version 3](LICENSE).

GPLv3 permits anyone to use, study, modify, share, fork, and charge for copies of the software.
When distributing a modified or unmodified version, distributors must follow GPLv3—including
providing the corresponding source, preserving required notices and license terms, identifying
modifications where required, and not imposing additional restrictions that contradict the
license. The license provides the controlling terms and includes warranty and liability
disclaimers; this summary is not legal advice.
