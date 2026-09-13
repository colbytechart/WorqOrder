# Lifecycle and Timer Test Plan

## 1. Purpose

This plan verifies that Room's singleton active-timer state remains authoritative across Android
lifecycle and clock/date changes. It separates deterministic automated coverage from scenarios
that require a real Activity, process, device sleep, or reboot.

No test should infer correctness from a continuously running background counter. Expected elapsed
time comes from persisted UTC boundaries plus an in-process Android elapsed-realtime anchor.

## 2. Automated coverage matrix

| Scenario | Automated evidence |
| --- | --- |
| Recomposition/multiple collectors | `MainViewModelTest.sharedTickerRefreshesDoNotWriteChangingDurationToRepository` |
| Navigation and Activity recreation | Main ViewModel recreation test plus Compose navigation tests |
| Process/reboot reconstruction model | `TimerRecoveryCoordinatorTest.processRecoveryUsesRoomTimerAndRebuildsMonotonicDisplay` |
| Background/screen sleep elapsed | Monotonic recovery test advances fake elapsed realtime without Room writes |
| One/multiple midnight | Boundary, timer, recovery, and Room exact-close tests with no continuation |
| Concurrent resume | Eight concurrent recovery calls produce one exact boundary close |
| Concurrent resume/Stop and Start | Recovery/Stop and global concurrent-Start tests |
| Device-zone change while active | Pinned-zone recovery test and settings-zone provider tests |
| Manual ZoneId/non-DST | Settings/provider and `Asia/Kolkata` boundary tests |
| Spring-forward/fall-back | Real `America/New_York` 23/25-hour boundary and interval-validation tests |
| Wall-clock anomaly | Negative recovery, false-midnight veto, safe Stop, and corrected re-anchor tests |
| Orphan/inconsistent state | Room orphan-open snapshot test and active-pair validation |
| Transaction interruption | Invalid boundary-close input proves the Room transaction fails closed |
| Export while running | Main ViewModel and Compose tests require Stop for every destination |

## 3. Manual device checklist

Use a debug build with non-sensitive test data. Before each case, record the selected app ZoneId,
task total, interval count, and wall time. Stop and clean up the timer after the case unless the
next step explicitly tests continued recovery.

### A. Navigation, recreation, and ordinary backgrounding

1. Create/select today's task, Start, wait at least 15 seconds, open Settings, then return.
   - Expected: the same task is running; time includes the Settings visit; no extra interval.
2. With the timer running, rotate the device twice.
   - Expected: one running interval; display continues without resetting or jumping.
3. Press Home for at least one minute and reopen WorqOrder.
   - Expected: elapsed includes the background minute; the optional running-timer notification may
     remain visible, but no service or app-owned background tick loop is running.
4. Lock the screen for at least one minute, unlock, and reopen.
   - Expected: elapsed includes screen-off sleep; one interval remains open.
5. Start a timer, open another WorqOrder destination, background and resume there, then return Main.
   - Expected: recovery already occurred even though Main was not the visible resume destination.

### B. Recents, process death, and reboot

1. Start a timer, note its start/time, press Home, swipe WorqOrder from Recents, and relaunch.
   - Expected: timer remains running from its original UTC start. OEMs may or may not kill the
     process; either path must look correct.
2. Start/background a timer, then use Android Studio's **Terminate Application** or, from a
   project PowerShell with the known SDK path:
   `& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am kill worq.order`
   Relaunch from the launcher.
   - Expected: Room reconstructs the timer; no duplicate interval. If a pinned local midnight was
     crossed, the existing interval closes exactly at that boundary and no continuation is made.
3. Start a timer, reboot the device, and launch WorqOrder after boot.
   - Expected: the one-shot post-unlock receiver may restore the running-timer notification from
     Room. On launch, elapsed includes downtime; a missed pinned local midnight closes the
     existing interval exactly at the first boundary and creates no continuation. No foreground
     service or continuous boot-time loop is started.

### C. Date boundaries (v0.3 current policy)

1. In a controlled test build or existing installation with a stored geographical ZoneId, start
   shortly before a pinned local midnight and keep Main visible across the boundary.
   - Expected: the first post-boundary observation closes the existing interval exactly at the
     boundary, clears active state and stale selection, and creates no task or continuation.
2. Repeat while locked or backgrounded, then resume after the boundary.
   - Expected: recovery stores the same exact boundary rather than the later wake time; no task or
     interval is created for the new date.
3. Stop exactly near the boundary if practical.
   - Expected: the result is one closed interval ending at the boundary, with no zero-duration
     continuation. Exact-boundary behavior is primarily deterministic automated coverage.
4. Multiple missed midnights are automated; recovery closes once at the first crossed boundary,
   not once per missed date. A multi-day real-device run is optional.

### D. Zone and wall-clock changes

1. In device-zone mode, Start, then change Android's device ZoneId while the timer runs.
   - Expected: the active task remains pinned to the Start zone. If a boundary is crossed, the
     interval closes using that pinned zone. After recovery/Stop, confirm Settings/Main adopt the
     new device zone without moving historical tasks.
2. In a controlled test build or existing installation that already stores a manual geographical
   ZoneId, start a task assigned in that zone, background, and resume.
   - Expected: today/boundaries use the manual zone, independent of the device zone.
3. Start and keep WorqOrder alive, then move wall time forward and backward while elapsed realtime
   advances normally.
   - Expected: visible time does not jump; Stop succeeds at the monotonic projected UTC instant;
     the final total matches the immediately preceding live total; no false midnight split exists.
4. Repeat with wall time moved before the persisted Start and process termination before relaunch.
   - Expected: recovered active contribution never becomes negative; a clock warning appears.
     Restore correct time and resume; the provisional display re-anchors without rewriting Start.

### E. Running export lockout

For each destination—CSV, XLSX, and a connected test Google Sheet—Start a task and inspect the
bottom action.

- Expected: the action is disabled and reads **Stop Timer to Export**.
- Attempting to dispatch the export event programmatically launches no picker or Google request.
- After Stop, export is enabled and contains the authoritative completed Stop Local and duration.
- CSV/XLSX/Google still receive the same 13 schema-version-5 headers, order, and canonical values.

## 4. Evidence to record

For each manual case record device/API, app build/commit, ZoneId, start/end wall times, result,
unexpected UI message, and whether Room shows one active pointer/open interval. Do not claim a
device-only case passed unless it was actually performed.

## 5. Manual execution record — 2026-07-27

The owner completed these emulator/device checks successfully:

- navigation through Settings and back;
- rotation;
- Activity reconstruction with **Don't keep activities**;
- Home/background recovery;
- screen lock recovery;
- Recents removal;
- explicit process death;
- reboot recovery, with an accepted approximately one-second observation difference over
  3 minutes 45 seconds;
- external device-ZoneId change and manual application ZoneId; the expected exact
  series/date/ZoneId selection copy was created without moving historical data; and
- spring-forward and fall-back transitions; local labels reflected the discontinuous/repeated wall
  clock while elapsed duration remained correct.

Corrections implemented and manually retested successfully:

- Forward and backward wall-clock changes previously left the live monotonic display accurate but
  made Stop differ by approximately 10–20 seconds. D-057 now uses the valid monotonic projection
  for Stop and normalization; both forward and backward correction tests passed on the updated
  build.
- Running export under the superseded snapshot policy produced blank Stop Local values and
  unusable duration snapshots. D-056 replaces that workflow with a global running-timer lockout;
  the manual lockout check passed on the updated build.

Milestone 15 resolved the formerly deferred cancellation presentation under D-058. JVM tests
prove CSV/XLSX/Google cancellation clears progress, writes nothing, retains only the optional
diagnostic outcome, and exposes no Main-screen feedback.

## 6. v0.3.0 exact-boundary lifecycle matrix

When v0.3 is implemented, retain every ordinary recreation/background/process/reboot test above
but replace split/rollover expectations with these checks:

1. **Foreground boundary:** start shortly before a simulated pinned-zone midnight; verify the
   first post-boundary observation closes exactly at midnight, clears notification/selection, and
   creates no task or interval.
2. **Background/locked boundary:** background and lock before the simulated boundary; allow the
   scheduled worker or resume to run later; verify stored Stop is the boundary rather than wake
   time and no continuation exists.
3. **Killed-process boundary:** kill the process before the boundary, advance time, relaunch, and
   verify the same exact retrospective close and no generated task.
4. **Reboot boundary:** reboot across the simulated boundary and verify post-unlock recovery closes
   once, reconciles the running surface, and does not duplicate data.
5. **Several missed dates:** recover several days later; verify one close at the first boundary,
   not one task/interval per day.
6. **DST/ZoneId:** repeat for spring-forward, fall-back, a non-DST zone, and a zone whose
   `atStartOfDay` is unusual; expected instant comes from ZoneId rules, never fixed 24 hours.
7. **No idle rollover:** with no timer, cross a date and change device ZoneId; verify no task is
   inserted and stale timing selection clears.
8. **Automatic Google ordering:** with Auto Export enabled and a pre-boundary timer, allow the
   foreground boundary callback or post-boundary worker to run; verify Room closes first, then the
   captured prior date exports one current canonical row per task. Verify an overdue target also
   runs during startup/resume reconciliation without waiting for an `ENQUEUED` WorkManager request,
   and verify later worker delivery does not duplicate it. Repeat delayed/offline/auth-required
   recovery without duplication. Milestone 33 owns the separate schema-5 projection change.
9. **Notification limitation:** if Android does not run the process at midnight, record any
   temporary system chronometer continuation; verify the next app execution corrects the surface
   and stored endpoint. This visible scheduling delay is accepted and must not be hidden with an
   exact alarm or foreground stopwatch service.

## 7. Milestone 32C deterministic evidence map

The following tests are the repeatable local evidence for the current boundary policy:

| Evidence | Test location | Expected proof |
| --- | --- | --- |
| Exact boundary, just before, and just after | `TimerCoordinatorTest`, `TimerRecoveryCoordinatorTest` | Before leaves the interval open; at/after closes at the first pinned boundary |
| Repeated and concurrent normalization | `TimerCoordinatorTest`, `TimerRecoveryCoordinatorTest` | One close, one closed interval, no continuation or duplicate task |
| Several missed dates | `TimerCoordinatorTest`, `TimerRecoveryCoordinatorTest` | The first boundary is persisted; later missed dates are not materialized |
| Non-DST, spring-forward, fall-back, and skipped local-date rules | `MidnightBoundaryCalculatorTest` | `ZoneId.atStartOfDay` supplies the boundary; no fixed 24-hour assumption |
| Wall-clock and process reconstruction | `TimerCoordinatorTest`, `TimerRecoveryCoordinatorTest`, `MainViewModelTest` | Monotonic projection/anomaly policy is retained and foreground recovery closes exactly |
| Foreground and late automatic execution | `MainViewModelTest`, `AutomaticGoogleExportManagerTest` | A boundary close cannot cancel its own export callback; captured date is not exported early; stale intervals normalize before export |
| Stale worker identity | `AutomaticGoogleExportManagerTest` | An obsolete worker cannot replace the durable current target |
| Authorization/network/quota/permission pending | `AutomaticGoogleExportManagerTest`, `GoogleSheetsExportCoordinatorTest` | Typed pending state is retained; no automatic retry or Room mutation |
| Export race and notification cleanup | `CsvExportCoordinatorTest`, `XlsxExportCoordinatorTest`, Main/notification tests | Open timers cannot produce unstable snapshots; stale running surfaces are reconciled |

Device-only evidence must still record API level, pinned ZoneId, boundary instant, actual recovery
instant, Room interval/active counts, notification state, and export result. A deferred or
unperformed device case must remain marked pending rather than being reported as passed.

Record device/API, build commit, pinned ZoneId, boundary instant, actual execution time, Room task/
interval/active counts, notification state, export target/result, and whether any new task appeared.

## 8. Milestone 32 final execution record

The owner exercised repeated emulator midnight transitions while the implementation was refined.
Those checks confirmed exact timer closure without a continuation task or interval, preserved
captured-date export state, successful interactive completion after an authorization-required
result, and a subsequent automatic Google export without duplicate rows. The observed defects—a
stale authorization flag disabling automation, a passive stale-selection warning, an overdue
WorkManager target remaining queued, and Main remaining on yesterday after another recovery caller
closed the timer—were corrected and covered by deterministic regressions.

The final project-local evidence contains 235 passing JVM tests and 108 passing connected tests,
with no failures, errors, or skips. The isolated CSV presentation test and complete connected suite
were rerun after an Android emulator EGL/RenderThread crash; both passed, confirming that native
emulator failure was not a WorqOrder assertion. Debug lint and current debug/release assembly also
passed. A single combined real-midnight rerun of every corrected path was not performed after the
last visible-date signal change; its foreground/external-close race is covered deterministically by
`MainViewModelTest` and remains an appropriate later release-candidate smoke check.
