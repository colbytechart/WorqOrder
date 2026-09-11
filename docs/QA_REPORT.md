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
