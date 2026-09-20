# WorqOrder Release QA Report

Audit date: 2026-07-31
Branch: `milestone17` (historical release evidence)
Scope: required production behavior through Milestone 17. This report is retained as historical
`0.2.0` evidence; current `0.3.0` status is tracked in `REQUIREMENTS_TRACEABILITY.md` Section 10
and the Milestone 34 inventory.

## 1. Outcome

The audited implementation contains all required local workflows and all three approved one-way
export destinations. No required behavior is known to fail. Permanent signing, production OAuth,
privacy/handoff documentation, a signed release artifact, the accessibility/runtime matrix,
physical performance profiling, and the fresh-account signed-Google exercise are now present.

## 2. Defects found and fixed

1. Main's former single scrolling surface let long task lists move primary controls off screen. The
   timer and complete date-selector bar are now pinned, while messages/tasks scroll independently
   with a quiet visual position indicator.
2. Daily tasks were presented oldest first. Main now presents newest-created first with stable task
   ID tie-breaking. Room and canonical export ordering did not change.
3. The first implementation of newest-first ordering sorted on every live display refresh. Sorting
   was moved to the Room observation boundary, avoiding repeated work during long timers.
4. `GOOGLE_SHEETS_SETUP.md` still described the superseded running-interval snapshot export. It now
   requires Stop before export, matching every implemented destination.
5. Android platform backup remained enabled. `allowBackup=false` now prevents the app from
   advertising platform backup/restore for unencrypted app-private client/task data.
6. Android 12+ lint identified `allowBackup` as insufficiently explicit for modern cloud-backup
   and device-transfer extraction policy. `data_extraction_rules.xml` now excludes every
   credential-protected/device-protected app-private domain from both channels while retaining
   `allowBackup=false` for older releases.
7. The first release-signing preflight task captured a Gradle script object and broke configuration
   cache storage. Release signing now uses AGP's built-in `validateSigningRelease` with a
   deliberately invalid fallback path when local signing configuration is absent.
8. On an API 26 Pixel 1 in landscape, the pinned portrait-sized timer/date header consumed the
   short viewport and left no usable task-list height. Short landscape windows now use a compact
   side-by-side timer/date header while keeping the task list independently scrollable and bottom
   actions visible. Portrait and backend behavior are unchanged.
9. With large text and display scaling, the remaining landscape bottom action bar combined with
   the pinned header and again starved the task list. Landscape now moves Export and Add task into
   the top application bar, shortens only the visible export label, and retains the full date and
   destination in semantics. The compact pinned timer/date row is bounded to guarantee the lower
   task list a nonzero independently scrollable viewport. The focused 200%-font, 640 × 360 dp
   regression passes.

10. The 200 ms presentation cadence made the former fractional-second timer visibly step between
    values and left unnecessary fractional detail in task totals. Main, task rows, and edit-task
    duration labels now use `HH:MM:SS`, truncating only the presentation remainder. Room instants,
    monotonic timing, Stop precision, duration arithmetic, and the canonical export snapshot retain
    their existing precision. CSV/XLSX/Sheets were already `HH:mm`/`HH:MM:SS`.

No XLSX implementation, schema, writer, coordinator, or tests were changed.

## 3. Runtime-efficiency review

- The visible timer uses a 200 ms refresh cadence only while an interval is active and Main state is
  lifecycle-collected. `SharingStarted.WhileSubscribed(5_000)` stops the ticker after Main has no
  collector.
- A ViewModel owns at most one ticker chain. `flatMapLatest` cancels the prior interval loop when
  active interval identity changes.
- Refresh ticks calculate from one process-local monotonic anchor and do not write Room, DataStore,
  disk, or network.
- Background, screen-off, process-death, and reboot behavior has no continuous loop, foreground
  service, wake lock, exact alarm, or WorkManager stopwatch job.
- Room and DataStore flows are lifecycle/ViewModel/application scoped; receiver registration has a
  matching unregister path. No unbounded Activity reference is held by the application container.
- Room operations use Room's coroutine execution; document output and Google HTTPS calls use IO
  dispatchers. No `allowMainThreadQueries` occurs in production.
- The task region is a stable-key `LazyColumn`; off-screen rows are not composed. Newest-first
  sorting occurs only on task-list emission.
- Google uses one bounded metadata read and one explicit atomic batch per export, with no automatic
  retry loop. Response-body reads are bounded.
- CSV strings and XLSX packages are prepared in memory to keep picker snapshots consistent and
  avoid app-private plaintext staging. Extremely large exports can still create transient memory
  pressure.

The owner profiled the signed release on a physical Pixel. The initial 50 ms build accumulated
about ten CPU minutes during one visible timer hour, so the cadence was reduced to 200 ms. In the
post-fix run, cumulative process CPU advanced from 1.02 to 31.29 seconds over approximately
20 minutes of foreground timing—about 2.5% average CPU. Battery moved from 87% to 86%, and device
temperature decreased from 30.1°C to 29.1°C. Android reclaimed the process during the subsequent
locked/background interval; battery remained 86% and temperature was 29.5°C. Reclamation is
expected, creates no background loop, and recovery uses Room's persisted active interval.
Together with the earlier foreground/background memory samples, this closes the release
CPU/memory/thermal gate without evidence of a leak, overheating, or background drain defect.

## 4. Commands and exact results

Every Gradle invocation explicitly used Android Studio JBR 21, the configured Android SDK, the
project-local Gradle user home, and `--offline`.

### Targeted change verification

```text
gradlew --offline :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin
BUILD SUCCESSFUL in 1m 17s
37 actionable tasks: 9 executed, 4 from cache, 24 up-to-date
```

### Full device-independent regression

```text
gradlew --offline :app:lintDebug :app:lintRelease :app:testDebugUnitTest
  :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
BUILD SUCCESSFUL in 29s
138 actionable tasks: 23 executed, 115 up-to-date
```

- JVM suites: 27
- JVM tests: 153
- Failures: 0
- Skipped: 0
- Debug lint: 0 errors, 19 warnings
- Release lint: 0 errors, 19 warnings
- Debug APK: built
- Debug test APK: built
- Unsigned release APK: built

The warnings are version-availability, intentional round/standard icon duplication, the API-26
adaptive-icon folder naming observation, and target/compile-SDK availability. No baseline hides
lint errors.

### Connected regression

```text
gradlew --offline :app:connectedDebugAndroidTest
BUILD SUCCESSFUL
78 tests; 0 failures; 0 skipped; 1m27.70s test duration
```

The owner ran the suite on the active emulator after the agent-side UTP process was blocked by its
external temporary-directory boundary. The generated Gradle HTML report confirms the result above.
An earlier inventory incorrectly reported 81 tests because it counted three non-test helpers
(`createDatabase`, `closeDatabase`, and `setRunning`); the source contains exactly 78 `@Test`
annotations.

After the owner clarified that the complete date-selector bar belongs in the pinned region, the
focused Main-screen connected suite was rerun:

```text
gradlew --offline :app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=worq.order.ui.main.MainScreenTest
BUILD SUCCESSFUL in 49s
16 tests; 0 failures; 0 skipped
```

The updated long-list assertion verifies that both the timer and date-selector bounds remain
unchanged while the task region scrolls.

### Milestone 17 Release Evidence

The owner ran the complete device-independent gate after stopping an agent-started restricted
Gradle daemon:

```text
gradlew --offline --no-daemon
  :app:testDebugUnitTest
  :app:lintDebug :app:lintRelease
  :app:assembleDebug :app:assembleDebugAndroidTest
  :app:assembleRelease
BUILD SUCCESSFUL
```

After explicit Android 12+ no-backup extraction rules were added, the agent ran:

```text
gradlew --offline --no-daemon
  :app:testDebugUnitTest
  :app:lintDebug :app:lintRelease
  :app:compileDebugAndroidTestKotlin
  :app:assembleRelease
BUILD SUCCESSFUL in 2m31s
98 actionable tasks: 49 executed, 1 from cache, 48 up-to-date
```

- JVM suites: 27
- JVM tests: 153
- Failures/errors/skipped: 0/0/0
- Debug lint: 0 errors
- Release lint: 0 errors
- Debug Android-test Kotlin: compiled
- Signed release APK: built
- `apksigner`: one RSA-4096 signer; APK Signature Scheme v2 verified
- Tracked/intended-file secret scan: no credential, token, private-key, OAuth-client-ID, or
  personal-email pattern match
- Prohibited technology/permission/migration scan: no match
- `git diff --check`: no whitespace error

Remaining lint warnings are informational: target/compile availability, one newer compatible
dependency family that requires the deferred SDK move, Android Studio launcher-icon duplication,
and the now-redundant `mipmap-anydpi-v26` qualifier. No lint baseline hides an error.

The owner then ran the complete post-backup-rule connected suite:

```text
gradlew --offline --no-daemon :app:connectedDebugAndroidTest
BUILD SUCCESSFUL in 2m 1s
73 actionable tasks: 4 executed, 69 up-to-date
```

The generated JUnit XML confirms 78 tests, zero failures, zero errors, zero skipped, and 90.699
seconds of test execution on the Pixel 10 Android 17/API 37.1 emulator.

After the final five-Hz/no-fraction duration presentation change, the device-independent release
gate ran again:

```text
gradlew --offline --no-daemon
  :app:testDebugUnitTest
  :app:lintDebug :app:lintRelease
  :app:compileDebugAndroidTestKotlin
  :app:assembleRelease
BUILD SUCCESSFUL in 2m 8s
98 actionable tasks: 27 executed, 71 up-to-date
```

- JVM suites/tests: 27/154
- JVM failures/errors/skipped: 0/0/0
- Debug/release lint: passed
- Debug Android-test Kotlin: compiled
- Signed release APK: built

The owner then ran the complete API 36.1 connected suite. Generated XML records 79 tests, zero
failures/errors/skips, and 99.826 seconds. The owner also confirmed the updated no-fraction
presentation.

`0.1.0` final signed artifact:

- path: `app/build/outputs/apk/release/app-release.apk`
- size: 16,059,788 bytes
- SHA-256: `93F83CB4A089425A739AED92C8817BD6A93D210190A97F57D1085B51E6999995`
- signature: APK Signature Scheme v2, one RSA-4096 signer
- signing certificate SHA-1:
  `57:51:0C:CB:30:01:A7:E7:0C:43:91:C9:16:A8:0A:0C:C6:03:BB:19`

### Dependency and repository audits

- `dependencyInsight` for Credential Manager's biometric transitive dependency: successful.
- `dependencyInsight` for `material-icons-extended`: successful.
- With explicit owner-approved internet access, OSV's version-aware batch API checked 29 exact
  resolved runtime Maven coordinates and seven build/test coordinates on 2026-07-29. All 36
  package/version results contained zero known vulnerability records.
- Official release sources confirmed that the selected Compose BOM, Activity, Credentials,
  DataStore, Navigation, Room, AGP, KSP, and Google authentication versions are stable/current.
  Kotlin 2.3.20 and coroutines 1.11.0 are newer stable releases, but no known advisory requires a
  late release-boundary upgrade from the proven 2.3.10/1.10.2 matrix.
- Core 1.19 and Lifecycle 2.11 require compile SDK 37. Their upgrade remains coupled to a future,
  explicitly tested SDK/toolchain move; the current compile SDK 36.1 build retains stable Core
  1.18 and Lifecycle 2.10.
- AGP 9.2 documents Gradle 9.4.1 as its required/default version. The wrapper distribution now
  pins Gradle's published binary ZIP SHA-256, and the checked-in wrapper JAR matches Gradle's
  published 9.4.1 SHA-256.
- Task inventory found lint tasks but no configured formatter, ktlint, Spotless, Detekt, or
  dependency-vulnerability task.
- Tracked-file credential-pattern scans returned zero API key, OAuth client ID, access/refresh
  token, client-secret, or private-key matches.
- Static prohibited-technology, permission, logging, migration, and lifecycle-loop scans completed.

## 5. Existing manual evidence

The owner previously completed navigation, rotation, Don't Keep Activities, background, screen
lock, Recents removal, explicit process death, reboot, device/manual zone change, wall-clock
correction, DST, running-export lockout, physical Pixel workflows, live Google connection/export/
re-export, CSV, and XLSX checks. Exact lifecycle evidence is retained in
`LIFECYCLE_TEST_PLAN.md`.

The API 26 minimum-SDK release matrix was completed on a Pixel 1 Android 8.0 AVD. Two initial
viewport-dependent test assertions were corrected to scroll before asserting display. A genuine
short-landscape Main defect was then found and fixed. The focused 17-test Main suite and complete
79-test connected suite passed; generated XML records 79 tests, zero failures/errors/skips, and
34.758 seconds. Manual API 26 release smoke and the corrected landscape timer/date/task/action
layout also passed.

After adding the landscape regression, the owner ran the complete 79-test suite on the Pixel 10
Android 17/API 37.1 AVD. It passed in 2m21s with zero failures/errors/skips; generated XML records
124.535 seconds. This is forward-compatibility evidence. Because the app targets API 36, a
separate API 36.1 run was then used for the exact current-target gate.

The API 36.1 Pixel 10 AVD complete suite passed with 79 tests, zero failures/errors/skips, and
102.053 seconds recorded by generated XML. After the final centered landscape top-action and
large-scale pinned-header refinements, the complete API 36.1 suite passed again with 79 tests, zero
failures/errors/skips, and 77.344 seconds recorded by generated XML. Manual portrait, landscape,
large-text, and display-scale checks passed task creation, timer Start/Stop, fixed controls, and
independent task-list scrolling. Minimum, target, and next-API runtime gates are complete.

Reopening on the next real day, a natural one-midnight crossing, and multiple-midnight device
recovery were not performed during the `0.1.0` device run and were later removed from the future
backlog by owner decision. Automated multi-boundary and real-zone
evidence remains active.

## 6. Release blockers and pending checks

No known implementation or release-evidence blocker remains. API 26, API 36.1, API 37.1,
accessibility/large-scale, signed populated-update, physical performance, production Google, and
fresh-account runtime gates are complete.

The owner-signed `0.1.0` release passed the production Google gate with a fresh account that had
never appeared on an OAuth tester list. Sign-in required no owner intervention; exact-spreadsheet
connection, first export, same-date idempotent re-export, connection restoration after restart,
and sign-out-driven disconnect all passed without local or remote data loss.

Release shrinking is resolved rather than blocked: R8/resource shrinking remain disabled for
`0.1.0` to avoid first-use reflection/serialization risk at the release boundary.

## 7. Formatting

Kotlin's official style is configured, but the project has no formatting-check task. Compilation
and lint enforce syntax/static correctness; introducing a formatter would be a dependency/tooling
decision rather than a hidden audit change.

## 8. v0.2.0 incremental evidence through Milestone 22

Milestone 22 completed the task-facing Consultant, Work Type, Mileage, and derived Billing Minutes
integration without changing Room schema version 3. Create and Edit share bounded metadata
validation; stopped-task edits recheck active Client and Consultant references and commit the
complete metadata change transactionally. Billing Minutes remains derived from exact interval
totals rather than persisted state. The one canonical schema-3 snapshot remains the input to CSV,
XLSX, and Google Sheets.

The project-local offline gate passed debug/release compilation, debug assembly, all 186 JVM tests
across 36 suites, lint with zero errors (14 informational warnings), and Android-test Kotlin
compilation. The owner then ran:

```text
gradlew -Duser.home=<project>/.android-user --offline --no-daemon
  :app:connectedDebugAndroidTest
BUILD SUCCESSFUL
```

Generated JUnit XML records 93 tests across 17 suites, with zero failures, zero errors, and zero
skipped tests. Two test-fixture defects found during the gate were corrected: the Room metadata
test now creates the active Consultant it assigns, and the Edit Task Compose test scrolls Billing
Minutes into the emulator viewport before asserting visibility. Neither correction changes
production behavior.

## 9. v0.2.0 incremental evidence through Milestone 23

Milestone 23 completed the final no-link About text, routine interval-card simplification,
sentence-capitalization keyboard requests, and strict task-zone 12-hour clock presentation. One
shared `ClockTimeFormatter` now owns UI and canonical-export Start/Stop formatting as `hh:mm a`.
This presentation change does not truncate Room UTC instants, alter ZoneId or DST resolution,
change duration formatting, or rewrite user-entered text. CSV, one-off XLSX, and Google Sheets
continue to consume the same immutable canonical snapshot.

The project-local offline gate passed debug assembly, release Kotlin compilation, debug and
release lint, all 190 JVM tests across 38 suites, and Android-test Kotlin compilation. The owner
then ran the project-local offline connected gate on `emulator-5554 - 16`; it completed
successfully. Generated JUnit XML records 95 connected tests with zero failures, zero errors, and
zero skipped tests. The connected run took 113.4 seconds. After the approved final Settings
refinement, the project-local offline debug/release, lint, JVM, and Android-test compilation gate
passed again. The owner then ran the refreshed connected suite and completed the manual Settings
inspection successfully. Final generated reports record 190 JVM tests across 38 suites and 97
connected tests, all with zero failures, zero errors, and zero skipped tests; the final connected
run took 118.33 seconds. No Room schema or DataStore migration was required for this
presentation/export-format milestone.

## 10. v0.2.0 incremental evidence through Milestone 24

Milestone 24 replaces only the `0.1.0` landscape Main composition. A spanning WorqOrder/Settings
bar remains global. Below it, the default Right-handed mode places the independently scrolling
task list in the left approximate half and timer/date/Export/Add controls in the right; Left-handed
mirrors only those content columns. The list retains its right-edge scrollbar and reaches the same
bottom margin as the action row. Portrait, task ordering, selection/running state, Room, timer
authority, date behavior, and all export behavior are unchanged.

The existing typed DataStore preference now has Settings radio controls after Appearance and is
projected immediately through Settings and Main immutable state. First-install and corrupt values
still fall back to Right-handed. The offline gate passed debug assembly, release Kotlin
compilation, debug/release lint with zero errors, all 191 JVM tests across 38 suites, and
Android-test Kotlin compilation. The first connected run exposed one genuine 200%-text,
640 × 360 dp defect: the redundant compact **Tracked Time** label and vertical padding could push
the bottom action row outside the control pane. Constrained landscape now tightens vertical
padding and omits only that redundant visible label while retaining the full timer value and
screen-reader semantics. The refreshed connected report records 98 tests, zero failures, zero
errors, zero skips, and 121.476 seconds. The owner then manually verified the intended handed,
portrait, persistence, task-scroll, timer/date/action, and responsive behavior. Milestone 24 is
complete; no Room/DataStore migration or dependency change was required.

## 11. Milestone 26 implementation evidence

Milestone 26 adds stable WorkManager `2.11.2`, the Google-only Auto Export switch, a durable
DataStore target tuple, silent background authorization, the one-date worker/coordinator,
content-free pending notification, API-33+ permission request, post-Stop notification hook, and an
application-wide Google-export mutex. It adds no Room migration, foreground service, exact alarm,
app-owned wake lock/boot receiver, token storage, broader scope, backend, CSV/XLSX automation, or
Google Play release dependency.

The generated debug merged manifest was inspected after compilation. WorkManager contributes its
documented library-owned `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, and `RECEIVE_BOOT_COMPLETED`
permissions plus Startup initializer, `SystemJobService`, and reschedule receiver. WorqOrder adds
only `POST_NOTIFICATIONS`; it declares no app-owned wake-lock or boot receiver, exact-alarm
permission, foreground export service, storage permission, or notification service.

Focused JVM coverage verifies captured target scheduling, distinct enablement blockers,
active-timer pending behavior, post-Stop notification, silent one-date success/advancement, and
cancellation/target clearing. Connected coverage verifies the durable target ZoneId/connection
association, conditional accessible Settings switch, and specific inline notification recovery.
Rejected Auto Export enablement no longer inserts a message above the Export card, so it does not
shift the Settings scroll position. Android-wide notification disablement, API-33+ denial, and a
disabled WorqOrder notification channel are all treated as the notification-specific blocker.

The final project-local offline gate passed: 39 JVM suites contain 200 tests with zero failures,
errors, or skips; debug and release lint each contain zero errors and 21 existing warnings; and both
debug and release APK assembly passed. The final connected report contains 100 tests with zero
failures, errors, or skips in 123.435 seconds. `git diff --check` passed apart from informational
line-ending conversion warnings.

The owner completed the first half of the supplied manual Auto Export checklist and then verified
the final notification-denial behavior: the switch remained off, the Settings position did not
move, and the specific red recovery text appeared. The remaining manual background/error scenarios
were explicitly deferred for lack of time and are not claimed as passed. Milestone 26 is complete
with those checks retained as known unverified device coverage.

## 12. Milestone 28 implementation evidence

Milestone 28 implements the approved Room-backed running-timer notification. One silent,
low-importance standard notification uses Android's system chronometer and no application tick.
Its private form shows **WorqOrder** with **Client · Description**; its public/redacted supporting
line is blank. Swipe dismissal persists only the active interval ID in typed Preferences DataStore.
Start, Stop, application startup, Activity resume/window focus, date normalization, and a one-shot
post-unlock boot receiver reconcile presentation against Room. No foreground service, exact alarm,
wake lock, custom notification layout, or WorkManager timer loop was added.

The final project-local offline gate passed debug lint, 208 JVM tests, 102 connected tests, and
debug/release assembly with zero test failures, errors, or skips. A transient Windows incremental
release-packaging failure was rerun in isolation and `:app:packageRelease` passed; signing
validation remained intact. The owner passed the supplied notification permission/channel,
private/redacted content, accumulated timer, tap, dismissal, Stop/new Start, background/lock,
Recents/process, reboot, force-stop, and short resource checklist. Milestone 28 is complete.

## 13. Milestone 29 release-readiness audit

Status: pre-publication release gate passed. The repository-local preflight confirms a
`milestone29` branch based on
the accepted `v0.2.0-development` integration tip through Milestone 28. `main` remains the public
`0.1.0` line and the integration branch is 22 commits ahead.

The local implementation/configuration audit confirms `worq.order`, version `0.2.0`/code 2,
minimum API 26, target API 36, compile SDK 36.1, Room schema 4, committed schemas 1–4, explicit
`1→2→3→4` migrations, canonical export schema 4 with 15 columns, release signing attached and
fail-closed when local signing inputs are absent, disabled Android backup, and exact stable
version-catalog pins. Tracked-file scans found no credentials, private keys, APK/AAB artifacts,
Firebase, Apache POI, destructive migration fallback, broad storage permission, foreground timer
service, exact alarm, app-owned wake lock, sensitive production logging, or OAuth scope beyond
`drive.file`. The only `allowMainThreadQueries()` calls are instrumentation fixtures.

The audit corrected stale 0.1.0 documentation that still described nine-column exports, Room
version 2/3, no notification/boot/background scheduling, and the prior artifact/version names.
No production behavior or dependency changed during this documentation pass.

All pre-publication evidence required for release readiness is now recorded below. Downloading and
re-verifying the public GitHub asset remains a post-publication integrity check and cannot be
completed before the Release exists.

The first clean Milestone 29 gate passed on 2026-08-09. Gradle produced the debug,
instrumentation-test, and signed release APKs; all 208 JVM tests across 40 suites passed with zero
failures, errors, or skips; debug and release lint each reported zero errors and 22 warnings; and
`git diff --check` reported no whitespace errors. The line-ending messages are informational Git
LF-to-CRLF working-copy notices. Resolved debug/release dependency reports contained 180 unique
Maven coordinates. The owner-approved, read-only OSV batch query returned zero vulnerability
records. D-080 records the one explicit transitive preview exception supplied by stable AndroidX
Credentials 1.6.0.

The owner then ran the complete `connectedDebugAndroidTest` suite separately on the Android 8/API
26 emulator and the Android 16/API 36.1 emulator. Both builds passed, followed by successful manual
smoke checks for Consultant/Client setup, full task metadata including Billing Status, timer and
notification behavior, task editing, interval presentation, landscape reachability, export,
notification permission/privacy, handed layouts, large text/display scaling, and conditional Auto
Export presentation. The retained final API-36.1 XML records 102 tests, zero failures, zero errors,
zero skips, and 149.758 seconds. The API-26 pass is owner-confirmed; its generated report was
replaced by the subsequent connected run.

The owner completed the real signed-update gate on a disposable emulator. Android accepted the
new owner-signed `0.2.0` release APK over the public owner-signed `0.1.0` APK without uninstalling
or clearing data. Active/archived Clients, dated tasks, repeated intervals, selection, preferences,
and an open timer survived; no task or interval duplicated; migrated metadata retained its honest
blank/Unspecified defaults; the open timer stopped with the correct accumulated value; and a newly
created `0.2.0` task used the expected Consultant, On-Site, and Billable behavior. The follow-up
schema-4 export smoke also passed.

The owner then completed the release-signed production Google/export gate with a non-test Google
account and disposable editable spreadsheet. Sign-in required no tester-list or owner intervention;
exact-spreadsheet validation/connection passed; first export reused the blank default worksheet;
the exact 15-column schema-4 headers and values matched CSV and XLSX; same-date re-export remained
idempotent; edited local data authoritatively replaced obsolete rows; running-timer export stayed
locked; disconnect/reconnect and sign-out-driven disconnect preserved Room; and the conditional
Auto Export switch enabled, disabled, and hid correctly without a false success notification. All
requested live Google, CSV, XLSX, recovery, and equivalence checks passed.

The owner completed the final identity, signature, artifact, install, and smoke checks. The APK
reports package `worq.order`, version name `0.2.0`, version code `2`, application label
`WorqOrder`, compile SDK 36, and target SDK 36. `apksigner` verified APK Signature Scheme v2 with
one RSA-4096 signer. The certificate SHA-1 matches the public `0.1.0` APK, the permanent release
keystore, and the production Android OAuth client. The absent v1/v3/v4/SourceStamp signatures are
expected for the API-26+ direct-GitHub APK workflow; v4 would be a separate incremental-install
sidecar and is not required for ordinary installation.

Final `0.2.0` signed artifact:

- path: `app/build/outputs/apk/release/app-release.apk`
- intended GitHub asset name: `WorqOrder-0.2.0.apk`
- size: 16,477,861 bytes
- SHA-256: `92F8F92D9327B2089D8FAE23DF181CF74CD606BE20C6531296182CE7711B305A`
- signature: APK Signature Scheme v2, one RSA-4096 signer
- signing certificate SHA-1:
  `57:51:0C:CB:30:01:A7:E7:0C:43:91:C9:16:A8:0A:0C:C6:03:BB:19`

The owner confirmed all remaining release-candidate installation, functional, and smoke checks
passed. No known pre-publication release blocker remains. After publication, the downloaded GitHub
asset must reproduce the SHA-256 above, verify with the same signer, install, and launch.

Generated debug and release merged manifests were audited. Direct WorqOrder permissions remain
`INTERNET`, `POST_NOTIFICATIONS`, and `RECEIVE_BOOT_COMPLETED`; backup is disabled; release is not
debuggable; and both WorqOrder receivers are non-exported. WorkManager contributes its normal
network, boot, wake-lock, `FOREGROUND_SERVICE`, job service, system foreground service, and
reschedule components. WorqOrder never promotes the automatic-export worker to foreground work and
does not use that library service for timer display. Other exported library components are guarded
by `DUMP`, `BIND_JOB_SERVICE`, or Google revocation permissions.

## 14. Milestone 32 midnight and automatic-export quality gate

Milestone 32 replaces the released continuation behavior with one idempotent exact-boundary close
in the active timer's pinned geographical ZoneId. The transaction closes the expected sole open
interval, clears the singleton timer, and creates no task or interval. Foreground, startup,
Activity, boot, export, and WorkManager callers converge on this operation; an independent
lifecycle-collected boundary signal advances Main from yesterday when another caller wins the
close race. Automatic Google work exports its preserved prior date only after closure and retains
typed pending state through authorization, offline, permission, quota, and process-recovery paths.

The final repository audit found no new continuation caller, exact alarm, app-owned wake lock,
foreground stopwatch service, background tick loop, or UI-refresh persistence. The legacy split
methods remain isolated compatibility material for the later classified cleanup milestone.
`git diff --check` reported no whitespace errors; its CRLF/LF messages are informational working-
copy notices.

Final owner-run results:

- `testDebugUnitTest`: 235 tests, zero failures/errors/skips.
- `connectedDebugAndroidTest`: 108 tests, zero failures/errors/skips, 149.206 seconds.
- Isolated `MainScreenTest.csvExportShowsDateProgressAndSuccessState`: passed before the full
  connected rerun. Its earlier empty failure was a native emulator EGL `RenderThread` crash.
- `lintDebug`: passed with zero errors.
- `assembleDebug` and `assembleRelease`: passed.
- `installDebug`: passed; the Android Gradle Plugin's connected-test cleanup intentionally removes
  its temporary app/test packages, so visual inspection uses a subsequent debug install.

The owner manually confirmed exact midnight closure without a duplicate continuation, preserved
pending export completion, and successful automatic captured-date Google export while defects were
being corrected. The final combined real-midnight visible-date scenario remains a later
release-candidate smoke check; deterministic ViewModel coverage proves the corrected race. No
Milestone 32 release blocker remains.

## 15. Milestone 35 `0.3.0` final-gate status

Task 35C's repository-only review on 2026-09-11 confirmed the expected milestone ancestry,
explicit Room 1→2→3→4→5 route, committed schema exports 1–5, schema-5 one-interval constraints,
one-row/13-column shared export projection, exact Google `drive.file` scope, disabled backup,
narrow source permissions, and absence of destructive migration fallback or tracked credential
values. Merged release-manifest library permissions were attributed to AndroidX Credentials and
WorkManager; they do not add an app-owned foreground stopwatch, exact alarm, wake lock, or app
access gate.

One test-readiness defect was corrected: `SettingsScreenTest` no longer hardcodes the published
`0.2.0` About label and instead asserts against `BuildConfig.VERSION_NAME`. This is test-only and
does not change application behavior.

The owner's first clean gate passed in 7m 15s: 140 actionable tasks (95 executed, 44 from cache,
1 up-to-date), 234 JVM tests with zero failures/errors/skips, zero lint errors in both variants,
and successful debug/debug-test/release assembly. Both lint variants initially reported 22
warnings. One warning led to a bounded manifest hardening fix: `fullBackupContent=false` now makes
the no-backup policy explicit for API 26–30 alongside `allowBackup=false` and the Android 12+
extraction rules. The post-fix gate recorded below verifies that change; the remaining warnings are
non-blocking SDK/version availability, hidden-but-retained time-zone resources, intentional icon
duplication, and style suggestions. Third-party AndroidX/DataStore libraries that AGP could not
strip were packaged unchanged, as reported by the successful build.

The current API 36.1 connected suite passed in 3m 10s with 73 actionable tasks (6 executed,
67 up-to-date). The first API 26 suite completed 106 tests with one failure caused by a test query,
not the migration: Android 8 SQLite does not support selecting from the newer table-valued
`pragma_foreign_key_check` form. Production already uses `PRAGMA foreign_key_check`; the test was
changed to use the same API-26-compatible command and fail when it returns any row. The corrected
reruns are recorded below.

The corrected schema-5 migration class passed on API 26 in 53s with 73 actionable tasks
(6 executed, 67 up-to-date). The complete API 26 connected suite then passed in 1m 22s with
73 actionable tasks (1 executed, 72 up-to-date); all 106 instrumentation tests completed without
failure. The API 26 and API 36.1 connected gates now pass for the current debug/test APKs. A signed
release smoke remains separate from these instrumentation results.

The post-fix clean gate passed in 2m 1s with 140 actionable tasks (70 executed, 67 from cache,
3 up-to-date). It produced 234 passing JVM tests, zero failures/errors/skips, successful debug/
debug-test/release assembly, and zero lint errors in both variants. Each lint variant now reports
21 warnings; the backup/extraction warning is gone. The release output metadata reports
`worq.order`, `0.3.0`/code 3, and minimum API 26. The 16,478,421-byte release candidate has SHA-256
`F9FFCE361C1D87F8360B3F6EF495FF498947FD08E7B206FD34FF9A62A6B24F8E`.

Owner-run `aapt2` inspection reports package `worq.order`, version `0.3.0`/code 3, min API 26,
target API 36, and label `WorqOrder`. `apksigner` verifies APK Signature Scheme v2 with certificate
SHA-1 `57:51:0C:CB:30:01:A7:E7:0C:43:91:C9:16:A8:0A:0C:C6:03:BB:19`, exactly matching the
permanent v0.1/v0.2 release signer already recorded in this report. V2 signing covers the entire
API 26+ support range; v3/v3.1/v4 are not required.

The owner then generated the exact `releaseRuntimeClasspath` and build-environment reports from
the offline project. The release graph contains 175 resolved Maven coordinates. A narrow,
owner-approved OSV review on 2026-09-11 queried 400 exact coordinates present in the project-local
Gradle cache; 87 vulnerability records mapped to 19 stale/cache-only or build-tool coordinates,
and none mapped to the resolved release runtime graph. Kotlin Gradle plugin 2.3.10 is separately
affected by build-cache metadata advisory `GHSA-r937-wjx7-w2jp`. The plugin is not packaged in the
APK, no remote/shared build cache is configured, and Milestone 35 disabled Gradle build-output
caching while retaining dependency and configuration caches.

The owner then ran a clean `--no-build-cache` gate. It passed in 2m 43s with 140 actionable tasks
(137 executed, 3 up-to-date): all 234 JVM tests passed with zero failures/errors/skips, debug and
release lint each reported zero errors and the same 21 accepted warnings, and debug, debug-test,
and owner-signed release APKs assembled. The regenerated 16,478,421-byte release candidate has
SHA-256 `27DD6814CB4AEFC922275253301C3372769E9C2F19E4C1C4CFBA986EAFECDA99`.

On 2026-09-12 the owner reported a successful signed v0.2→v0.3 install-over on a disposable
API 36.1 emulator, including the Section 8 populated-data and reinstall/idempotency checks in
`MILESTONE_35_RELEASE_EVIDENCE.md`. A legacy task with two completed and one running interval became
three one-interval tasks with metadata retained, as required by schema 5. The owner also observed
a real midnight automatic Google export: the captured date was exported with the expected
13-column schema and no next-day task was created. These are owner-reported manual results, not
device logs independently collected by the reviewer.

The owner subsequently found that Google re-export from a second device replaced the first
device's same-date rows. A non-destructive, hidden-task-ID merge fix and regression tests were
added. The owner-run offline lint/JVM/debug/debug-test/release gate passed in 2m 49s with 139
actionable tasks (50 executed, 89 up-to-date). An initial connected run performed zero tests
because the installed app had a different signing certificate; the subsequent compatible-emulator
connected run passed in 2m 30s with 73 actionable tasks (1 executed, 72 up-to-date). The current
project-local test XML records 237 JVM and 108 connected tests with zero failures, errors, or
skips. On 2026-09-13 the
owner reported that the supplied Step 4 manual checks passed after this fix, including the
same-date Google re-export checks. The owner separately confirmed force-stop and running-timer
reboot recovery. Maximum text/display scaling had minor visual imperfections, which the owner
accepted after functional checks. One sample showed 36,544 KiB total PSS, 150,832 KiB RSS, and
3.5% instantaneous CPU; the owner accepted this limited resource observation rather than an
extended profile, so no long-run memory or thermal pass is claimed. The current 16,494,849-byte
release APK has SHA-256
`CBF04232B810BAC9BC2A64952E31D28FE5E6A51BF664762228904973AFB54D08`; older candidate
hashes are superseded. The owner reports that the current APK's `apksigner`, `aapt2`, and checksum
results exactly match the permanent signer, `worq.order`, `0.3.0`/code 3, and this hash. Public
asset comparison follows publication. `MILESTONE_35_RELEASE_EVIDENCE.md` tracks the accepted
resource/accessibility limits and the still-unverified test-APK-retention convenience setting.
After this gate, the owner reported that the public `0.3.0` APK matched the published checksum and
installed on a physical device over existing data; new task timing and intervals passed. This
does not constitute `0.4.0` implementation or test evidence.

## 16. Milestone 41A (`0.4.0`) evidence snapshot

Luna's repository audit was performed on 2026-09-14 at `milestone41`. `main` is an ancestor of
`milestone41`; the branch contains the merged Milestones 37–40 and remains ahead of the released
line. The following gates were completed during the preceding implementation phases and are
referenced here as evidence, not rerun or newly claimed as a final release gate:

- Offline debug/release lint, JVM tests, and debug/release assembly passed in 2m34s (107 actionable
  tasks; 31 executed, 76 up-to-date). The JVM XML recorded 43 suites and 250 tests with zero
  failures, errors, or skips.
- The complete connected suite passed 117 tests with zero failures, errors, or skips. The focused
  Create Task suite passed 11/11 after short-viewport, large-text/display-scale, IME, pinned-footer,
  and Notes reachability coverage.
- `Schema6MigrationCoreTest`, Notes validation/ViewModel tests, shared schema-6 export tests, and
  Google legacy-tab planner/encoder tests are present in the repository and were included in the
  successful JVM/connected evidence above.

No physical-device visual check, new signed `0.4.0` artifact, fresh/populated upgrade exercise, or
live Google release check was independently performed in Task 41A. Those checks remain explicit
Milestone 41C gates. This section does not declare release readiness or publication.

## 17. Milestone 41C (`0.4.0`) clean gate and owner migration exercise

On 2026-09-15, after bounded Edit/Create footer polish and restoration of the Delete Task icon,
the project-local offline `--no-build-cache clean :app:lintDebug :app:lintRelease
:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease`
gate passed in 2m52s: 140 actionable tasks, 139 executed and one up-to-date. The generated JVM
XML records 43 suites, 250 tests, zero failures, zero errors, and zero skips. Debug and release
lint each report zero errors and 22 previously documented nonblocking warnings. Debug, test, and
release APKs assembled. `git diff --check` passed; Git separately printed an informational
line-ending conversion warning for `TaskConsultantSelector.kt`. No connected test, install,
uninstall, or data-clearing command was run during this clean gate.

Before the final visual polish, the current complete connected suite passed 118/118 on API 26 and
118/118 on API 36. The Edit Task testability correction was included in those complete reruns.
After the pinned-footer/icon polish and an added Delete-left/Save-right Compose assertion, the
owner ran the full connected suite on a disposable API 26 emulator. Its generated XML records
119 tests, zero failures, zero errors, and zero skips; Gradle reported `BUILD SUCCESSFUL in 1m29s`
with 73 actionable tasks (4 executed, 69 up-to-date). No connected suite was run on the populated
owner-signed API 36 migration fixture after the polish, so its retained app data was not exposed
to debug-test installation or clearing.

The clean owner-signed release candidate is `app/build/outputs/apk/release/app-release.apk`,
16,511,217 bytes, SHA-256
`B944CA0C8244179DFDBB0E899584A2CC4BB1E9A5A93DA0B4F9700020589E14C4`. Local SDK
`apksigner` verifies one signer and APK Signature Scheme v2; certificate SHA-1 is
`57510ccb3001a7e70c4391c916a80a0cc603bb19`, the permanent WorqOrder signer. `aapt2`
reports `worq.order`, versionName `0.4.0`, versionCode `4`, minSdk 26, targetSdk 36, and launcher
label `WorqOrder`. This is a candidate checksum, not a public-asset checksum: any later rebuild
must be rehashed before upload.

A focused project-local source/Git scan found no tracked real keystore, `local.properties`,
private key, APK/AAB, or service-account file; no source `Log.*` call, Firebase/Apache POI
dependency, destructive Room fallback, raw token/secret literal, or broader OAuth scope was
found. The only requested source OAuth scope is `drive.file`. This is a local repository scan,
not a new online vulnerability advisory check or a claim that plaintext exports are encrypted.

The owner reported that the signed in-place `0.3.0` to `0.4.0` emulator upgrade preserved the
populated clients, archived-client historical links, Consultant/selection, changed settings,
tasks/intervals, and running timer; no duplicate task or interval was created. Stopping the
recovered timer recorded its interval, and migrated tasks initially showed blank Notes. The
owner then reported all remaining manual Steps 6–9 passed: optional blank Notes creation,
populated Notes edit/reopen and length validation, blank Notes on repeated Start with source
Notes intact, Create/Edit footer/layout inspection, in-place owned Google schema-5-to-6 date-tab
upgrade and idempotent re-export, and exact shared 14-column CSV/XLSX output. These are owner-
reported visual/runtime observations, not instrumentation results independently collected by
the reviewer.

The owner subsequently reported a successful live `0.4.0` automatic Google export and then
explicitly confirmed all 14 visible columns were present with no duplicate row. On an API 37
emulator, the owner reported client/Consultant creation, task Notes, Start/Stop, portrait, and
landscape all passed. A manual 14-column Google export to the same spreadsheet used from API 36
appended the API-37 task without replacing the API-36 row. These are owner-reported manual
observations; the generated API-26 XML above is the separate automated result. The owner then
explicitly confirmed API 37 was a fresh installation of the
exact owner-signed candidate with SHA-256
`B944CA0C8244179DFDBB0E899584A2CC4BB1E9A5A93DA0B4F9700020589E14C4`, and its Google
account had never been added to the OAuth tester list. This validates the configured release
signer and External/In Production audience for that tested account without owner intervention.
The reviewed source/candidate is approved for the owner's integration merge and release handoff;
public release is not complete until the owner tags/uploads and independently checks the
downloaded asset. WorkManager's best-effort timing limitation remains; one successful device
run does not guarantee exact execution time on every Android device.

## 18. Milestone 46 (`0.5.0`) shared export-composition evidence

On 2026-09-18, the owner reported that the focused Milestone 46 JVM suite and complete connected
instrumentation suite passed after the shared Description/Expense composer was integrated into the
canonical export projection. The focused coverage includes manual-only, Tag-only, mixed ordered
snapshots, punctuation, surrounding whitespace, embedded CR/LF, Unicode, exact/over-limit Unicode
code-point counts, historical tasks without snapshots, CSV quoting, XLSX output, Google schema-6
planning, legacy compatibility, and exact cross-destination row equivalence.

The owner also reported that manual checks 1-8 and 10 passed: CSV/XLSX/Google retained the same 14
visible columns and composed values; repeated keyed Google export did not duplicate rows; remote
rows were preserved; catalog edits/deletes did not rewrite saved task snapshots; and automatic
Google export used the same composed projection without local task mutation. Manual check 9 for
formula-looking input was intentionally skipped by the owner. Its transport mechanics remain
covered by the passing XLSX literal-inline-string and Google `stringValue` encoder tests. CSV
continues the documented schema-6 policy of preserving exact user text with RFC-style quoting; it
does not promise to suppress a spreadsheet application's formula interpretation.

Sol's repository audit confirmed that CSV, one-off XLSX, manual Google, and automatic Google all
receive the same `ExportSnapshotCoordinator` projection. Room reads tasks, intervals, and task-owned
Tag snapshots in one transaction; the export builder orders snapshots by persisted selection order
and never reads the mutable Tag catalog. Google remains schema 6 with visible A:N, reserved O,
hidden identity P, ownership checks, keyed updates, and other-device row preservation. No schema-7
Google marker, fifteenth visible column, destination-specific Tag logic, or Room write was added.
No release-blocking Milestone 46 defect remains in the reported evidence.

## 19. Milestone 47 (`0.5.0`) task-form polish evidence

On 2026-09-18, the owner reported that all supplied Milestone 47 compilation, JVM, lint,
debug/release build, connected-instrumentation, and manual visual/behavior checks passed after the
final Edit Task inline Add Client recovery. Exact suite counts were not supplied for this final
rerun, so this section records only the owner's pass report and does not invent per-suite totals.

Verified behavior includes shared live Description, Hardware / software purchases, and Notes
`N / 999` counters; immediate red over-limit feedback; compact non-wrapping Add/Edit Tag controls;
picker-opening informational pills; action-only Create/Edit footers; selector-local Client and
Consultant errors; live 400-code-point Settings and inline Tag-editor counters; filter-scoped
Select All/Deselect All; and the archived/unavailable Edit Task client recovery flow. The recovery
uses the canonical inline client validation and archived-match restore confirmation, selects the
result as an unsaved task change, and remains blocked while the task timer is running.

Static closeout review confirmed branch `milestone47` descends from `v0.5.0-development`, found no
merge-conflict markers or diff whitespace errors, and reported only the existing informational
CRLF/LF conversion warning for `TaskFormComponents.kt`. No Room schema, export schema, timer rule,
task snapshot, or historical client relationship changed in Milestone 47. This closes Milestone
47 only; Milestone 48 Task 48A is now in progress on the owner-created `milestone48` branch.

## 20. Milestone 48 Task 48A audit baseline

On 2026-09-18, the `milestone48` branch was read-only audited before owner-run release work.
`main` is an ancestor of `v0.5.0-development`, which is an ancestor of `milestone48`; the
working tree was clean and `milestone48` pointed at the Milestone 47 integration commit. The
implemented schema-7 Tag catalogs, task snapshots, bounded CSV import, task pickers, centralized
composition, and Milestone 47 form polish are mapped in `REQUIREMENTS_TRACEABILITY.md` Section 12.

This section intentionally records no new test pass. The following remain owner-run gates for
Milestone 48: clean formatting/lint/JVM/debug/release builds, API-26/current connected suites,
all migration and populated `0.4.0` install-over checks, CSV/XLSX/manual and automatic Google
exports, accessibility/lifecycle/performance/security checks, release signer/package/version/hash
verification, and public-download install-over verification. Until those results are reported,
the `0.5.0` branch is not declared release-ready.

Task 48B then reconciled README, User Guide, product/data/architecture/privacy/security/handoff,
acceptance, traceability, changelog, and release-checklist wording with the implemented schema-7
Tag contract. That documentation-only work made no application identity, signer, OAuth scope,
dependency, backup-policy, migration, or export-layout change and records no new owner-run result.

Task 48C's complete project-local commands and manual verification matrix are now in
`RELEASE_CHECKLIST.md`, Section 13. No Task 48C result is recorded until the owner runs the
commands and reports each observed outcome.

On 2026-09-19, the owner reported checks 1–8 passed except the running-timer automatic-Google
midnight case: the preserved date did not complete automatically after the timer stopped. Static
diagnosis found that exact-boundary Room normalization already closes the interval without a
continuation, but `AutomaticGoogleExportManager` deliberately converted `TIMER_RUNNING` into a
post-Stop notification instead of resuming unattended. D-106 changes that timer-specific recovery:
successful boundary closure/Stop/reconciliation automatically runs the preserved date, while other
typed failures remain actionable pending states. Focused automated and repeated live-midnight
owner evidence were then reported passing. The owner confirmed the timer stopped at the boundary,
the completed captured date exported silently, and no continuation or duplicate next-day task was
created.

The owner subsequently reported all Milestone 48 Section 13 checks through Step 10 passing,
including populated migration/persistence, Tag management/import, Create/Edit picker/history/form
behavior, all manual export paths, corrected automatic Google behavior, accessibility/layout, and
lifecycle/performance. The latest locally generated reports independently show 292/292 debug JVM
tests and 151/151 connected tests with zero failures, errors, or skips; the owner separately reports
the required API-26 and current-target runs passed. Debug and release lint each contain zero errors
or fatals (26 non-blocking warnings), and the owner reports the supplied offline build gates passed.

The preliminary Step 11 repository/security checks also pass: `git diff --check` is clean; no
keystore, local properties, private-key container, APK, or AAB is tracked; no Firebase, Apache POI,
destructive Room fallback, or secret-value pattern was found; backup remains disabled; and the
manifest contains only Internet, notifications, and boot-completed permissions.

The owner then explicitly approved `versionName = 0.5.0` and `versionCode = 5`; source identity was
updated without changing application ID, signing configuration, SDK levels, backup policy, OAuth
scope, dependencies, Room schema, or export schema. The owner then reported the clean build, lint,
JVM, and connected gates passed for that exact identity. `apksigner` verified APK Signature Scheme
v2 and the permanent certificate SHA-1
`57510ccb3001a7e70c4391c916a80a0cc603bb19`; `aapt2` reported package `worq.order`, versionName
`0.5.0`, versionCode `5`, minSdk 26, targetSdk 36, and label `WorqOrder`. The exact release APK
SHA-256 is `AB9AAD5FC34A0AD3677C8F3860BE1012DD104488D276816EA904B45A4C06F247`.
Install-over/fresh smoke, merge/tag/publication, independent download, and physical-device
verification remain required. Do not rebuild after those install checks unless every artifact
identity and checksum check is repeated and this evidence is updated.

The owner then verified the official public `0.4.0` APK baseline with SHA-256
`B944CA0C8244179DFDBB0E899584A2CC4BB1E9A5A93DA0B4F9700020589E14C4` and the permanent signing
certificate, populated that installation, and installed the unchanged `0.5.0` candidate over it.
The upgrade, retained data, migration, launch, task/Tag/timer smoke, force-stop/reopen, and version
display checks passed. A separate disposable-emulator fresh installation and its persistence smoke
also passed. This completed the pre-publication signed-artifact gate. At that stage, integration
merge, tag, GitHub publication, independent download/checksum/signature verification, and
physical-device installation of the downloaded public asset remained unperformed.

The owner subsequently merged `milestone48` into `v0.5.0-development` and that integration branch
into `main`. The annotated `v0.5.0` tag was corrected before push so its peeled commit matched
`main`. The unchanged APK was published on GitHub, independently downloaded, and reverified; its
SHA-256, permanent signer, package, and version matched the candidate evidence above. The owner
installed the downloaded release on a physical device and reported all release smoke checks passed.
WorqOrder `0.5.0` is therefore publicly released and Milestone 48 is complete. The former teardown
Milestones 49–50 were not started and have moved to 56–57.

## 21. `0.6.0` Milestone 49 planning baseline

The approved `0.6.0` scope is documented in `V0_6_MILESTONE_PROMPTS.md`: bounded plaintext logical
ZIP backup, full preflight validation, sensitive/runtime exclusions, verified one-generation swap
restore point, durable Room/DataStore recovery journal, installation-scoped Google identity, and a
Settings experience. Milestone 49 changes documentation only. No application source, dependency,
database, manifest, Gradle, or test change and no automated/manual execution result is claimed.

Milestones 50–55 remain unstarted and individually require explicit owner instruction/model
handoffs. Teardown is deferred to 56–57 after public `0.6.0`.
