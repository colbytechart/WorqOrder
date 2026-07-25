# Timer and Date Rules

## 1. Invariants

At every committed database state:

1. There is zero or one `active_timer` row.
2. If present, it references the sole interval whose stop is null.
3. The referenced interval belongs to one daily task and uses the `boundaryZoneId` captured at Start.
4. Every completed interval has `start < stop` and lies within its task's stored local-date boundaries.
5. Intervals for a task do not overlap.
6. A series has at most one daily task for a `(work date, assignment ZoneId)` pair.
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

## 6. Stopping

`TimerCoordinator.stop()` obtains `nowInstant`, then under the timer mutex:

1. Load the singleton active state and referenced interval; if absent, return `NotRunning` idempotently.
2. Normalize the interval through every boundary strictly before `nowInstant` using the active session's pinned zone.
3. If `nowInstant` equals a boundary, close the preceding segment at that boundary without creating a zero-length next interval.
4. Close the final open interval at `nowInstant`.
5. Validate positive duration and the owning task boundary.
6. Delete the singleton active row and update affected timestamps.
7. Select the task owning the final closed segment. If Stop was exactly at midnight, ordinary
   selection reconciliation may subsequently select/create the new day's task without creating a
   zero-length interval.
8. Clear the process-local monotonic anchor after commit.

The database stop instant is the injected wall-clock UTC instant, as required for historical reconstruction. The final persisted total may differ from the pre-stop monotonic display if the user/network adjusted wall time during the interval; see anomaly handling.

## 7. Midnight splitting

Given an open segment starting at `segmentStart` and a normalization endpoint `now`, use `active_timer.boundaryZoneId`:

1. Determine `segmentDate = LocalDate.ofInstant(segmentStart, zone)`; at an exact boundary, associate it with the new local date unless it is already the prior segment's stop.
2. Compute `nextBoundary = segmentDate.plusDays(1).atStartOfDay(zone).toInstant()`.
3. While `nextBoundary < now` (or `<= now` when normalizing without immediately stopping):
   - close the current segment at `nextBoundary`;
   - find/create the next date's task with the same series ID, copied client, short description,
     and hardware/software-purchases text, and the pinned boundary ZoneId;
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
Selection preferences therefore retain the selected task ID, preferred series ID, effective date
on which the timing selection was made, and its effective ZoneId.

1. Read the preferred series and its last concrete daily task.
2. Calculate today with the effective zone.
3. Query `(seriesId, todayEpochDay, effectiveZoneId)`.
4. If missing, insert a task that copies the source daily task's current client, short description,
   and hardware/software-purchases text, stores today's epoch day and the effective ZoneId, and
   keeps the series ID. Do not repurpose a same-date copy whose stored assignment zone differs.
5. On a uniqueness race, query and use the already-inserted row.
6. Persist the new concrete task selection.

This can run on resume or actual-date normalization before the UI enables Start. When the
selection was made on today's effective date but points to a historical/future task, preserve it
for viewing and keep Start ineligible. Rollover must not create duplicates and does not move/edit
the source task. If the source was deleted, the selected series does not match it, or its client
relationship is invalid, clear selection and require the user to choose rather than fabricate
metadata.

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

The Milestone 6 Material time picker accepts hour-and-minute input on the task's fixed work date.
Confirming a changed endpoint sets seconds and milliseconds to zero. Reopening an existing interval
derives its displayed local time and any fall-back offset choice from the persisted instant; an
endpoint left unchanged retains that instant. Spring-forward gaps remain invalid, and fall-back
overlaps require an explicit earlier/later offset selection before saving.

## 12. Clock anomalies

The product requires wall-clock UTC persistence and monotonic live display, which cannot both hide every manual/system clock correction. Policy:

- Ordinary wall-clock changes do not make the live in-process display jump.
- At Stop/recovery/export, compare wall-derived and available monotonic elapsed values.
- If wall time makes `stop <= start` or differs from the live monotonic estimate by more than the
  initial diagnostic tolerance of two minutes, do not silently synthesize a historical wall
  instant. Keep the timer recoverable, return `ClockChanged`, and later UI must guide the user to
  correct device time or manually correct the interval.
- If process death removed the monotonic reference, wall-clock reconstruction is the only source; show anomaly state for negative duration and never write an invalid stop.
- Tests inject jumps forward/backward and prove no database invariant is broken.

The exact user copy remains a later UI choice. Changing the two-minute diagnostic tolerance is a
reviewed behavior change; the no-silent-corruption rule is fixed.

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

Milestone 3 implements the pure/coordinating services and transaction operations. Android
lifecycle trigger wiring, the collected UI ticker, process-launch normalization, and user-facing
clock-anomaly recovery remain Milestones 4 and 12.
