# Timer and Date Rules

## 1. Invariants

At every committed database state:

1. There is zero or one `active_timer` row.
2. If present, it references the sole interval whose stop is null.
3. The referenced interval belongs to one daily task and uses the `boundaryZoneId` captured at Start.
4. Every completed interval has `start < stop` and lies within its task's stored local-date boundaries.
5. Intervals for a task do not overlap.
6. Released `0.2.0` used at most one daily task for a `(work date, assignment ZoneId)` pair.
   In `0.3.0`, `series_id` is lineage only and no reconciliation path creates another date copy.
7. The visible timer is derived state, never a persisted counter.

All algorithms use injected time sources and execute rule-dependent database changes in Room transactions.

## 2. Date and boundary definitions

For a task with `workDate` and stored `zoneId`:

```text
dayStart = workDate.atStartOfDay(zoneId).toInstant()
dayEnd   = workDate.plusDays(1).atStartOfDay(zoneId).toInstant()
```

The valid instant range is `[dayStart, dayEnd]`; non-zero intervals satisfy `start >= dayStart`, `stop <= dayEnd`, and `start < stop`. `atStartOfDay` is intentional: it applies real zone rules when a transition shifts or removes local midnight. A day may be shorter or longer than 24 hours.

Today is `LocalDate.ofInstant(clock.now(), effectiveZoneId)`. Stored task dates are never recomputed when settings change.

## 3. Duration and formatting

Completed interval duration is `Duration.between(start, stop)`. The open contribution at an evaluation instant is non-negative `Duration.between(start, evaluationInstant)`, subject to the live monotonic rule below. Task total is the exact sum; it is not a wall-clock time-of-day.

Duration calculation retains the exact `Duration`. User-visible accumulated formatting truncates
the positive sub-second remainder without rounding:

```text
totalSeconds = duration.seconds
hours        = totalSeconds / 3_600
minutes      = (totalSeconds / 60) % 60
seconds      = totalSeconds % 60
```

Render at least two hour digits, but never truncate larger hours: `07:03:09`, `125:00:00`.
Negative duration is an invariant error, not displayable time. Persisted start/stop instants and
all duration arithmetic remain millisecond-precise.

Version `0.2.0` derives Billing Minutes from the exact task total before presentation truncation:

```text
0 ms      -> 0
positive  -> ceil(totalMilliseconds / 900_000) * 15
```

It is never stored as a ticking/denormalized counter. Live task presentation may include the open
interval contribution; exports still require Stop and calculate from completed intervals only.
Routine task detail keeps Task Total above the interval list and Billing Minutes directly below.
Individual interval cards omit their Duration field and render Start Time/Stop Time as task-zone
12-hour `hh:mm a`; interval editing uses the same strict clock presentation. The canonical export
also uses `hh:mm a`, while all persisted/calculation precision remains unchanged.

## 4. Starting

`TimerCoordinator.start()` obtains one `nowInstant` under the application-scoped timer mutex:

1. Resolve the effective zone and `today`.
2. Resolve the persistent timing selection and verify its task/series relationship.
3. Require that task to be the exact `(series, today, effectiveZoneId)` daily copy. An
   intentionally selected historical/future task is rejected and is not silently converted into
   today's task.
4. In one Room transaction, recheck that `active_timer` is empty and no open candidate exists,
   allocate the next stable ordinal, insert `start=nowInstant`/`stop=null`, and insert singleton
   active state with the effective geographical zone captured as `boundaryZoneId`.
5. Establish a process-local monotonic anchor using the task's completed total before Start.

Actual-date carryover reconciliation is a separate `SelectionCoordinator` operation and may run on
resume/date-change normalization before Start is offered. It does not reinterpret a task that the
user intentionally selected while browsing a historical date today.

A failed precondition creates no interval. Concurrent Start calls yield one success at most.

## 5. Live display

At a successful in-process Start, record:

- persisted wall start instant;
- monotonic start reading; and
- completed total before the open interval.

While the same process session remains valid:

```text
displayTotal = completedTotalAtAnchor + (monotonicNow - monotonicAnchor)
```

Android elapsed realtime, not uptime, is the planned monotonic source so device sleep counts as elapsed work time. UI refresh events do not write Room.

After activity recreation, reuse the application-scoped live anchor when available. After process death or an anchor mismatch, reconstruct the open contribution once from `clock.now() - persistedStart`, clamp only the visual provisional contribution to zero if the device clock is anomalous, then establish a new monotonic anchor. Historical data remains based on persisted wall instants.

When the app stays foreground across a date boundary, the ticker/date observer requests normalization on the first refresh after the detected local-date change. The app need not wake exactly at midnight.

The ticker is a single `StateFlow` pipeline shared by all Main collectors. It runs only while Main
state is collected and an active interval exists, stops after the collection grace period, and
uses a 200 ms refresh cadence. Physical release profiling found the previous 50 ms cadence caused
unnecessarily high foreground CPU. Navigation/backgrounding therefore stops UI refresh work while
the persisted interval continues logically. Android elapsed realtime includes device sleep.

## 6. Stopping

`TimerCoordinator.stop()` obtains a wall-clock sample, then under the timer mutex:

1. Load the singleton active state and referenced interval; if absent, return `NotRunning` idempotently.
2. If a valid process-local anchor exists, calculate
   `logicalStop = currentSegmentStart + activeDurationAtAnchor + monotonicDelta`. Otherwise use
   the wall-clock sample reconstructed after process death/reboot.
3. Normalize the interval through every boundary strictly before `logicalStop` using the active
   session's pinned zone.
4. If `logicalStop` equals a boundary, close the preceding segment at that boundary without
   creating a zero-length next interval.
5. Close the final open interval at `logicalStop`.
6. Validate positive duration and the owning task boundary.
7. Delete the singleton active row and update affected timestamps.
8. Select the task owning the final closed segment. If Stop was exactly at midnight, ordinary
   selection reconciliation may subsequently select/create the new day's task without creating a
   zero-length interval.
9. Clear the process-local monotonic anchor after commit.

The database still stores a UTC stop instant. While a valid in-process anchor exists, that instant
is projected from the persisted segment start and Android elapsed realtime so the final persisted
total matches the live display even if wall time moves. This deliberately favors accurate elapsed
work over copying a corrected wall-clock label. After process death or reboot, the monotonic
reference is gone and wall-clock reconstruction remains the only available source.

## 7. Midnight splitting

Given an open segment starting at `segmentStart` and a normalization endpoint `now`, use `active_timer.boundaryZoneId`:

1. Determine `segmentDate = LocalDate.ofInstant(segmentStart, zone)`; at an exact boundary, associate it with the new local date unless it is already the prior segment's stop.
2. Compute `nextBoundary = segmentDate.plusDays(1).atStartOfDay(zone).toInstant()`.
3. While `nextBoundary < now` (or `<= now` when normalizing without immediately stopping):
   - close the current segment at `nextBoundary`;
   - find/create the next date's task with the same series ID, copied employee ID/name snapshot,
     client, short description, hardware/software-purchases text, Work Type, Billing Status
     (including blank), Mileage, and the
     pinned boundary ZoneId;
   - create the next interval at `nextBoundary`, open unless another boundary/end is known;
   - assign a new stable interval ID and next ordinal on that daily task;
   - retarget `active_timer` to the new open interval;
   - continue from the new task/date.
4. Select the currently open daily task.

For a known Stop endpoint exactly equal to a boundary, do not emit the next zero-duration segment. For recovery/continued running at or after a boundary, the active interval must ultimately be on the current session-local date. The entire chain commits atomically.

Example in `America/New_York`:

```text
Jul 22 task: 2026-07-22T23:30-04:00 → 2026-07-23T00:00-04:00
Jul 23 task: 2026-07-23T00:00-04:00 → 2026-07-23T01:00-04:00
```

For multiple missed dates, repeat at each `atStartOfDay` boundary. Never add fixed 24-hour durations to find midnight.

## 8. Daily selection rollover

The rules in this section describe the released `0.2.0` behavior and remain as historical
compatibility documentation. The current `0.3.0` branch does not perform this find-or-create
rollover; Section 15 is authoritative for current selection reconciliation.

Rollover is based on an actual change of today or the effective geographical zone, not ordinary browsing with the date selector.
Selection preferences therefore retain the selected task ID, preferred series ID, effective date
on which the timing selection was made, and its effective ZoneId.

1. Read the preferred series and its last concrete daily task.
2. Calculate today with the effective zone.
3. Query `(seriesId, todayEpochDay, effectiveZoneId)`.
4. If missing, insert a task that copies the source daily task's employee ID/name snapshot, client,
   short description, hardware/software-purchases text, Work Type, Billing Status (including
   blank), and Mileage, stores today's
   epoch day and effective ZoneId, and keeps the series ID. Do not re-resolve a renamed Employee or
   repurpose a same-date copy whose stored assignment zone differs.
5. On a uniqueness race, query and use the already-inserted row.
6. Persist the new concrete task selection.

This can run on startup/resume, actual-date normalization, or an effective-zone change before the
UI enables Start. Before any persisted selection is reconciled, wait for the first DataStore-backed
effective-zone value so a temporary device-zone default cannot create the wrong daily copy. A
selection is eligible for rollover only when the source task's stored date/zone matches the
date/zone context recorded when it was selected. If it was intentionally selected from a
historical/future or prior-zone row, preserve it for viewing and keep Start ineligible. Rollover
must not create duplicates and does not move/edit the source task. If the source was deleted, the
selected series does not match it, or its client relationship is invalid, clear selection and
require the user to choose rather than fabricate metadata.

## 9. Time-zone setting changes

- Time-zone mode/manual ID controls are disabled and repository writes reject changes whenever `active_timer` exists.
- In device mode, read the current geographical system zone. Do not store only its current offset.
- Validate manual IDs against the supported geographical IANA region namespaces; exclude raw fixed-offset and legacy alias IDs from the selector. Store and display the selected canonical ID.
- On an allowed change, future calculations of today, rollover, and newly assigned tasks use the new zone.
- Existing `work_date_epoch_day` and `zone_id` remain untouched.
- A task manually created for a displayed date stores the effective zone at creation.
- If Main was displaying the previous value of today, an effective-zone change moves it to the new today. If the user was browsing another date, that displayed date remains unchanged.

If the device zone changes externally during an active device-mode timer, keep using the Start-captured boundary zone until Stop. Show the pinned session zone if relevant. Adopt the device's new zone afterward and perform selection rollover. This explicit policy avoids silently changing an already-running interval's calendar rules.

## 10. DST rules and manual editing

Boundary and duration logic is instant-based; local editor input must resolve through the task's stored zone:

- **Gap/nonexistent local time:** reject and explain that the local time does not exist because of a clock change.
- **Overlap/repeated local time:** require or visibly preserve the earlier/later offset choice. Show the UTC offset with ambiguous values.
- **Existing interval edit:** default to the interval's current resolved offset when unchanged.
- **Date membership:** resolve start/stop to instants, then validate against the task's `[dayStart, dayEnd]` and against each other.

Spring-forward dates can total 23 hours and fall-back dates 25. Duration is elapsed instant duration, not a subtraction of local clock labels.

## 11. Interval validation algorithm

For create/edit/delete requests:

1. Load task, all its intervals, and active state inside the write transaction.
2. Reject if the task/target interval is running or if the operation materially edits the running task.
3. Resolve local inputs with explicit DST handling.
4. Require closed manual intervals; open intervals can only be created by Start.
5. Require `start < stop` and both within stored date boundaries.
6. Compare half-open ranges `[start, stop)` against every other interval except the edited one. Adjacent endpoints are allowed; any positive intersection is rejected.
7. Preserve an existing ordinal on edit; use `maxOrdinal + 1` on manual add.
8. Set `wasManuallyEdited=true` and update timestamps.

Deleting an interval recalculates totals by observation; it does not renumber other intervals.

The Milestone 6 Material time picker accepts hour-and-minute input on the task's fixed work date.
Confirming a changed endpoint sets seconds and milliseconds to zero. Reopening an existing interval
derives its displayed local time and any fall-back offset choice from the persisted instant; an
endpoint left unchanged retains that instant. Spring-forward gaps remain invalid, and fall-back
overlaps require an explicit earlier/later offset selection before saving.

## 12. Clock anomalies

The product stores UTC boundaries and uses a monotonic live display. Policy:

- Ordinary wall-clock changes do not make the live in-process display jump.
- While a valid process-local anchor exists, Stop and normalization use its monotonic projection
  instead of the changed wall clock. Forward/backward wall changes therefore cannot make the final
  total jump or invent false midnight segments.
- The persisted projected endpoint remains an absolute UTC `Instant`; it represents Start plus
  measured elapsed realtime. Its local clock label may intentionally differ from a manually
  corrected device clock.
- If process death removed the monotonic reference, wall-clock reconstruction is the only source; show anomaly state for negative duration and never write an invalid stop.
- If a process-recovery anchor was created while wall time was before the persisted Start, a later
  successful resume after the user corrects the clock may rebuild that provisional anchor from
  valid UTC time. No interval boundary is changed by this re-anchoring.
- Tests inject forward/backward jumps and prove the live total, final persisted duration, and
  midnight plan remain consistent.

## 13. Lifecycle triggers

Normalize/check on:

- `MainActivity.onResume`, regardless of which navigation destination is visible;
- Main ViewModel initialization and Main destination resume as idempotent presentation retries;
- detection of foreground date change;
- Start and Stop;
- before changing a rule-sensitive task/interval;
- after Stop, before exporting the displayed date; export remains disabled while active; and
- after device boot through the one-shot post-unlock running-notification receiver, and again when
  the user next launches the app. The receiver invokes the same idempotent Room recovery path and
  does not run a stopwatch loop.

The application-scoped `TimerRecoveryCoordinator` performs each recovery in this order:

1. Wait for the first DataStore-backed effective ZoneId.
2. Capture one UTC evaluation instant.
3. Read and validate Room's singleton/open-interval state.
4. Split every crossed local-date boundary transactionally with the active session's pinned zone.
5. Rebuild the process-local monotonic anchor if it is absent or was a negative provisional
   recovery anchor.
6. Reconcile persistent selection, with an active Room timer taking precedence.
7. Let Room/DataStore flows rebuild ViewModel and Compose presentation.

Concurrent resume calls are serialized and the timer-operation mutex serializes them with
Start/Stop/export normalization. The Room continuation transaction and three-part task uniqueness
remain the final duplicate-prevention boundary.

An open interval without the singleton pointer, a singleton with zero/multiple open candidates,
or a mismatched/closed referenced interval is an explicit persistence-invariant error. It is not
silently discarded or treated as stopped, and a new Start remains blocked.

No alarm, application-owned wake lock, foreground service, continuous background loop,
WorkManager tick, or per-tick persistence is used. The sole manifest boot receiver performs one
post-unlock recovery/reconciliation for the running notification and then finishes.

## 14. v0.2 automatic-export target dates

Automatic Google export does not change timer/date authority. Near the end of a local date, the
scheduler captures `(targetEpochDay, effectiveZoneId)` as immutable job input. If Android executes
after midnight, export still reads that captured prior date. A device/manual zone change after
scheduling does not reinterpret the target.

If a timer is open at execution, preserve the target as pending and export nothing. Normal
Stop/midnight normalization first closes/splits every interval under the pinned timer zone. Only
after the Stop transaction succeeds may a content-free notification offer the preserved-date
Google export. This retains the global Stop-before-export rule and avoids blank Stop values.

This scheduler is not a timer wake-up mechanism, does not split intervals itself, and cannot use
an exact alarm, foreground stopwatch service, or tick loop merely to approach 11:59 PM. The exact
stable Android scheduler/auth mechanism is chosen only after current official research and owner
approval.

## 15. Planned v0.3.0 timer and date rules

Milestone 31 has implemented the Section 15 selection and repetition rules. The midnight closure
and automatic-export ordering rules remain planned for Milestone 32, so this section distinguishes
the currently active selection behavior from later boundary behavior.

The following rules supersede Sections 4, 6, 7, 8, and 14 where they describe repeated intervals,
midnight continuation, or selection rollover. All UTC, monotonic-clock, pinned-ZoneId, DST,
non-negative-duration, and one-global-timer rules remain.

### Start and repetition

1. Start still requires a concrete selected task assigned to today in the effective ZoneId and no
   globally active timer.
2. If the selected task has no interval, atomically create its sole open interval and active-timer
   row.
3. If it has one completed interval, atomically create a new same-day task carrying the source
   lineage and current user metadata, select it, and create its sole open interval and active-timer
   row. Its displayed accumulated time begins at zero. The source task remains unchanged.
4. If its interval is open, the global active-timer rule rejects another Start.
5. Concurrent Start calls must never create more than one repetition or open interval.

### Stop

Ordinary Stop closes the sole interval at the monotonic projection already defined by the clock
anomaly policy and clears `active_timer`. Task total equals that one interval's non-negative
duration. Stop with no active timer remains an expected no-op result.

### Midnight closure

For an open interval, calculate the first `LocalDate.plusDays(1).atStartOfDay(pinnedZone)` boundary
after its start. When the evaluated/projected instant reaches or exceeds that boundary:

1. close the existing interval exactly at the boundary;
2. clear the singleton active timer and live monotonic session;
3. cancel/reconcile the running notification;
4. clear the stale timing selection; and
5. create no task, interval, or continuation for the new date.

The operation is transactional, idempotent, and safe under concurrent resume, worker, Start, and
Stop entry points. It never iterates over missed days because there is no continuation. A device
that does not execute WorqOrder at midnight may temporarily retain an open row/chronometer, but the
next legitimate execution writes the exact historical boundary rather than its later wake time.
This is the strongest correct behavior compatible with the prohibition on exact alarms, app-owned
wake locks, and a foreground stopwatch service.

### Selection and dates

Date-selector browsing never creates data. When actual today or effective ZoneId no longer matches
the selected task's stored context, clear timing selection; never find or create a series copy.
Historical/future tasks remain viewable and editable, but Start is unavailable. A user must select
or create an eligible task for the new day.

### Automatic Google ordering

Capture the intended work date and pinned ZoneId before its boundary, but schedule the earliest
best-effort automatic execution after that boundary. The worker first applies midnight closure for
any stale interval belonging to the captured date, then prepares schema-5 rows and exports. A
delay still exports the captured date. Offline, authorization, quota, or Android scheduling
limitations retain the existing typed pending state. No successful Main-screen notification is
added, and CSV/XLSX remain manual.
