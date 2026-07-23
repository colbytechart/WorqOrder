# Timer and Date Rules

## 1. Invariants

At every committed database state:

1. There is zero or one `active_timer` row.
2. If present, it references the sole interval whose stop is null.
3. The referenced interval belongs to one daily task and uses the `boundaryZoneId` captured at Start.
4. Every completed interval has `start < stop` and lies within its task's stored local-date boundaries.
5. Intervals for a task do not overlap.
6. A series has at most one daily task for a work date.
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

Formatting uses total milliseconds:

```text
hours       = totalMs / 3_600_000
minutes     = (totalMs / 60_000) % 60
seconds     = (totalMs / 1_000) % 60
milliseconds= totalMs % 1_000
```

Render at least two hour digits, but never truncate larger hours: `07:03:09.004`, `125:00:00.000`. Negative duration is an invariant error, not displayable time.

## 4. Starting

`start(selectedTaskId)` obtains one `nowInstant`, then under the timer mutex and one Room transaction:

1. Resolve the effective zone and `today`.
2. Normalize any existing active interval through `nowInstant`. If one remains active, reject with `AlreadyRunning`.
3. Resolve the selected task and preferred series. If it is not the exact `(series, today, effectiveZoneId)` copy, find/create that copy using the rollover algorithm and select it; this includes a same-date task retained under another assignment zone.
4. Require the displayed date and resolved task work date to equal today and its stored zone to equal the effective Start zone.
5. Recheck that `active_timer` is empty and there is no unreferenced null-stop interval.
6. Allocate the next stable ordinal and create an interval with `start=nowInstant`, `stop=null`.
7. Insert singleton active state with the resolved effective geographical zone captured as `boundaryZoneId`.
8. Update selection hints after the transaction succeeds.
9. Establish a process-local monotonic anchor for display.

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

## 6. Stopping

`stop()` obtains `nowInstant`, then under the timer mutex and one Room transaction:

1. Load the singleton active state and referenced interval; if absent, return `NotRunning` idempotently.
2. Normalize the interval through every boundary strictly before `nowInstant` using the active session's pinned zone.
3. If `nowInstant` equals a boundary, close the preceding segment at that boundary without creating a zero-length next interval.
4. Close the final open interval at `nowInstant`.
5. Validate positive duration and the owning task boundary.
6. Delete the singleton active row and update affected timestamps.
7. If the effective current date is now later, find/create/select the series copy for current today without creating an interval.
8. Clear the process-local monotonic anchor after commit.

The database stop instant is the injected wall-clock UTC instant, as required for historical reconstruction. The final persisted total may differ from the pre-stop monotonic display if the user/network adjusted wall time during the interval; see anomaly handling.

## 7. Midnight splitting

Given an open segment starting at `segmentStart` and a normalization endpoint `now`, use `active_timer.boundaryZoneId`:

1. Determine `segmentDate = LocalDate.ofInstant(segmentStart, zone)`; at an exact boundary, associate it with the new local date unless it is already the prior segment's stop.
2. Compute `nextBoundary = segmentDate.plusDays(1).atStartOfDay(zone).toInstant()`.
3. While `nextBoundary < now` (or `<= now` when normalizing without immediately stopping):
   - close the current segment at `nextBoundary`;
   - find/create the next date's task with the same series ID, copied client/description, and the pinned boundary ZoneId;
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

Rollover is based on an actual change of today, not ordinary browsing with the date selector.

1. Read the preferred series and its last concrete daily task.
2. Calculate today with the effective zone.
3. Query `(seriesId, todayEpochDay, effectiveZoneId)`.
4. If missing, insert a task that copies the source daily task's current client and description, stores today's epoch day and the effective ZoneId, and keeps the series ID. Do not repurpose a same-date copy whose stored assignment zone differs.
5. On a uniqueness race, query and use the already-inserted row.
6. Persist the new concrete task selection.

This can run on resume, normalization, or Start. It must not create duplicates and does not move/edit the source task. If the source was deleted or its client relationship is invalid, clear selection and require the user to choose rather than fabricate metadata.

## 9. Time-zone setting changes

- Time-zone mode/manual ID controls are disabled and repository writes reject changes whenever `active_timer` exists.
- In device mode, read the current geographical system zone. Do not store only its current offset.
- Validate manual IDs against available `ZoneId` values; exclude raw fixed-offset IDs from the primary selector.
- On an allowed change, future calculations of today, rollover, and newly assigned tasks use the new zone.
- Existing `work_date_epoch_day` and `zone_id` remain untouched.
- A task manually created for a displayed date stores the effective zone at creation.

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

## 12. Clock anomalies

The product requires wall-clock UTC persistence and monotonic live display, which cannot both hide every manual/system clock correction. Policy:

- Ordinary wall-clock changes do not make the live in-process display jump.
- At Stop/recovery/export, compare wall-derived and available monotonic elapsed values.
- If wall time makes `stop <= start` or differs beyond an implementation-defined diagnostic tolerance, do not silently synthesize a historical wall instant. Keep the timer recoverable, show a `ClockChanged` error, and guide the user to correct device time or stop and manually correct the interval.
- If process death removed the monotonic reference, wall-clock reconstruction is the only source; show anomaly state for negative duration and never write an invalid stop.
- Tests inject jumps forward/backward and prove no database invariant is broken.

The exact user copy and tolerance are implementation UX choices, but the no-silent-corruption rule is fixed.

## 13. Lifecycle triggers

Normalize/check on:

- application/main ViewModel initialization;
- activity/process resume;
- detection of foreground date change;
- Start and Stop;
- before changing a rule-sensitive task/interval;
- before exporting a date that could contain the active interval; and
- after device boot only when the user next launches the app (no boot receiver required).

No alarm, wake lock, foreground service, or per-tick persistence is needed.
