# WorqOrder Release QA Report

Audit date: 2026-07-31
Branch: `milestone17`
Scope: required production behavior through Milestone 17

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

Final signed artifact:

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
recovery were moved by owner to optional Milestone 18. Automated multi-boundary and real-zone
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
