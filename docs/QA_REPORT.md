# WorqOrder Milestone 16 QA Report

Audit date: 2026-07-29  
Branch: `milestone16`  
Scope: required production behavior through Milestone 16

## 1. Outcome

The audited implementation contains all required local workflows and all three approved one-way
export destinations. No required behavior is known to fail. It is not yet declared release-ready:
the connected instrumentation suite passed, while final release-device, human accessibility,
signing, and production OAuth checks remain Milestone 17.

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

No XLSX implementation, schema, writer, coordinator, or tests were changed.

## 3. Runtime-efficiency review

- The visible timer uses a 50 ms refresh cadence only while an interval is active and Main state is
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
  pressure; representative device profiling remains a Milestone 17 release check.
- Built artifact sizes in this audit were 22,958,751 bytes for debug and 16,029,128 bytes for the
  unsigned, unminified release APK. Release shrinking remains a deliberate Milestone 17 decision.

Static design supports hours-long operation without sustained background work or per-tick
persistence. A fresh multi-hour CPU/heap/thermal profiler run was not possible without a connected
device and remains part of the release matrix.

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

### Dependency and repository audits

- `dependencyInsight` for Credential Manager's biometric transitive dependency: successful.
- `dependencyInsight` for `material-icons-extended`: successful.
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

Reopening on the next real day, a natural one-midnight crossing, and multiple-midnight device
recovery were moved by owner to optional Milestone 18. Automated multi-boundary and real-zone
evidence remains active.

## 6. Release blockers and pending checks

1. Complete Milestone 17's API 26/target-API/populated-upgrade device matrix and multi-hour
   CPU/heap/thermal observation.
2. Complete a human TalkBack listening/order pass and representative OEM display-scaling pass.
3. Create the permanent direct-release key and matching release OAuth Android client, switch the
   audience to External/In Production, and prove an account never listed as a tester can
   connect/export without owner intervention or committed signing material.
4. Decide and test release shrinking/optimization rules.
5. Confirm current dependency advisories under the owner's internet-access guardrail; no
   vulnerability scanner is configured and no live advisory database was queried here.

## 7. Formatting

Kotlin's official style is configured, but the project has no formatting-check task. Compilation
and lint enforce syntax/static correctness; introducing a formatter would be a dependency/tooling
decision rather than a hidden audit change.
