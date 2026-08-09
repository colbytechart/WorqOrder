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
| One/multiple midnight | Boundary, timer, recovery, and Room continuation tests |
| Concurrent resume | Eight concurrent recovery calls produce one split chain |
| Concurrent resume/Stop and Start | Recovery/Stop and global concurrent-Start tests |
| Device-zone change while active | Pinned-zone recovery test and settings-zone provider tests |
| Manual ZoneId/non-DST | Settings/provider and `Asia/Kolkata` boundary tests |
| Spring-forward/fall-back | Real `America/New_York` 23/25-hour boundary and interval-validation tests |
| Wall-clock anomaly | Negative recovery, false-midnight veto, safe Stop, and corrected re-anchor tests |
| Orphan/inconsistent state | Room orphan-open snapshot test and active-pair validation |
| Transaction interruption | Invalid second continuation proves the entire Room chain rolls back |
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
   - Expected: Room reconstructs the timer; no duplicate interval.
3. Start a timer, reboot the device, and launch WorqOrder after boot.
   - Expected: the one-shot post-unlock receiver may restore the running-timer notification from
     Room. On launch, elapsed includes downtime and missed local midnights are split. No
     foreground service or continuous boot-time loop is started.

### C. Date boundaries

1. In a controlled test build or existing migrated installation with a stored manual geographical
   ZoneId, choose a zone whose local midnight is near enough to wait naturally. The production
   `0.2.0` Settings UI intentionally exposes device-zone mode only. Start before midnight and keep
   Main visible across the boundary.
   - Expected: the first post-boundary refresh creates one continuation on the new daily task.
2. Repeat but lock/background through midnight and resume afterward.
   - Expected: resume performs the same single split.
3. Stop exactly near the boundary if practical.
   - Expected: no zero-duration continuation is displayed. Exact-boundary behavior is primarily
     deterministic automated coverage.
4. Multiple-midnight recovery is automated; a multi-day real-device run is optional, not required
   for every milestone verification.

### D. Zone and wall-clock changes

1. In device-zone mode, Start, then change Android's device ZoneId while the timer runs.
   - Expected: the active task remains pinned to the Start zone. Stop, then confirm Settings/Main
     adopt the new device zone without moving historical tasks.
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
- CSV/XLSX/Google still receive the same 15 schema-version-4 headers, order, and canonical values.

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
