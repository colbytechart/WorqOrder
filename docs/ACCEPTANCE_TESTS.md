# WorqOrder Acceptance Tests

## 1. Test policy

These scenarios define observable MVP behavior. Automated coverage may combine pure unit, coroutine, Room instrumentation, ViewModel, and Compose UI tests, but a requirement is not complete merely because one layer was tested. Use fake UTC clock, fake monotonic time, fake effective-zone provider, fake document destination, and fake Google Sheets gateway to make time/failure cases deterministic.

Unless stated otherwise, examples use `America/New_York`, a healthy version 1 Room database, and no active timer. Duration comparisons use exact milliseconds even when the UI ticker renders less frequently.

## 2. Main screen and selection

### UI-01 Initial empty state

Given the database has no tasks for today, when the main screen opens, then it shows WorqOrder, today's labeled date, `00:00:00.000`, disabled Start, Add Task, a useful empty state, Settings, and an export action naming today's date/destination.

### UI-02 Date filtering

Given tasks exist on two dates, when the displayed date changes with arrows or picker, then only tasks whose stored work date equals the new displayed date appear and the export label names that date.

### UI-03 Row content

Given a daily task has client, description, and intervals, then its row exposes those fields, total duration, overflow actions, selected semantics, and running semantics without relying only on color.

### UI-04 Selection

Given two stopped tasks on the displayed date, when the user taps the second, then it alone is selected and the timer shows its total.

### UI-05 Start without selection

Given no task is selected, then Start is disabled and an attempted programmatic Start creates no interval.

### UI-06 Non-today Start

Given a task is selected on a historical or future displayed date, then Start is disabled; task creation/manual editing remain available.

### UI-07 Running lock

Given task A is running, when the user tries to select/edit/delete task B or A as applicable, then selection remains A, switching is disabled, A cannot be edited/deleted, and no extra interval is created.

### UI-07a Browse while running

Given task A is running for today, when another date is displayed, then A remains the identified global timing selection, Stop remains available, rows on the viewed date cannot replace A, and exporting uses the viewed date. Creating/editing a non-running task does not alter A or its interval.

### UI-08 Date picker cancellation

Given a displayed date, when the date picker is canceled, then date, selection, and database remain unchanged.

### UI-09 Export label routing

Given default CSV, label reads equivalent to `Export Jul 22 as CSV`; given Google default, it reads equivalent to `Submit Jul 22 to Google Sheets`. If Google is not ready, tapping routes to/focuses connection settings with explanation.

### UI-10 Recreation

Given a selected or running task, when the Activity rotates/recreates, then displayed persisted/navigation state is restored appropriately, Room remains authoritative, and no extra start/stop/database tick occurs.

## 3. Clients

### CL-01 Add and sort

Given active clients `Zulu` and `Alpha`, when `Beta` is added, then active selectors/settings show Alpha, Beta, Zulu and the same repository path is used from Settings and inline task creation.

### CL-02 Name validation

Blank/whitespace-only and over-100-character names are rejected. A valid name is trimmed and repeated whitespace normalized before storage/display per the canonicalization contract.

### CL-03 Duplicate active names

Given active `Acme Corp`, attempts to add or rename another active client to ` acme   corp ` or another case variation fail without modifying either client.

### CL-04 Archive preservation

Given Client A has historical tasks, when archived, then it disappears from new-task selectors, remains available to joined historical views/exports, and no task/interval is altered.

### CL-05 Restore

Given archived Client A has no active canonical conflict, when restored, then it returns to sorted active lists. If a conflict exists, restore is rejected with a useful message and both records remain intact.

### CL-06 Rename

Given retained Client A is renamed validly, then all referencing task views/future exports show the retained client's current name, task IDs/dates/intervals remain unchanged, and the client stays in its prior active/archive state.

### CL-07 Client delete defense

Given historical references, no UI exposes destructive client deletion, and a direct database deletion is restricted.

## 4. Task and interval CRUD

### TASK-01 Create today

Given valid client/description and displayed today, when Create is tapped once, then one Room task is written with a new task/series ID, today's epoch day/current ZoneId/timestamps, it becomes selected, and navigation returns to main.

### TASK-02 Create other date

Given a historical/future displayed date, a valid task is stored for that date/zone and shown after return, but live timing remains disabled and the preferred live selection is not changed solely by creation.

### TASK-03 Cancel creation

Given edited form fields, when Cancel/back is confirmed according to normal navigation behavior, then no client/task is implicitly written except a client explicitly completed through the separate inline Add Client action.

### TASK-04 Description validation

Blank/whitespace-only and over-200-character descriptions fail; valid descriptions are trimmed and saved.

### TASK-05 Edit metadata

Given a stopped task, changing client/description affects that daily task only. Existing sibling dates remain unchanged; a later rollover copies the edited current metadata.

### TASK-06 Delete task

Given a stopped task with intervals, cancellation leaves it intact; confirmation deletes that task and its intervals transactionally, retains its client and same-series tasks on other dates, and leaves external exports untouched. If it was selected, selection/series hints clear so rollover does not recreate it automatically.

### TASK-07 Delete running task

Given a running task, both UI and repository reject deletion and the active interval remains recoverable.

### INT-01 Chronological history

Given intervals whose stable ordinals differ from chronological order after edits, the task editor lists them by start instant with a deterministic tie breaker while preserving ordinals for export.

### INT-02 Valid edit

Given a stopped interval, editing to a non-overlapping in-day range with start before stop succeeds, keeps ID/ordinal, sets manual-edit flag, and recalculates derived totals.

### INT-03 Invalid order

Start equal to or after stop is rejected with no partial update.

### INT-04 Overlap and adjacency

An edit/add whose half-open range overlaps another interval is rejected. An interval whose start equals another's stop is accepted if all other rules pass.

### INT-05 Work-date boundary

Inputs before task day start, after day end, or spanning the boundary are rejected. A split interval ending exactly at day end is valid; manual creation of a cross-midnight interval is not.

### INT-06 Running interval lock

Manual edit/delete of the active interval and material edit of its task are rejected at both UI and transaction layers.

### INT-07 Manual add (if included)

Given a valid stopped task interval, Add assigns `maxOrdinal + 1`, a stable ID, manual flag, and updates totals. If manual add is deferred, no misleading action is shown.

### INT-08 DST gap editor

On a spring-forward date, a nonexistent local time is rejected and no instant is guessed silently.

### INT-09 DST overlap editor

On a fall-back date, the earlier/later occurrence is distinguishable by offset, the chosen instant persists, and reopening the editor shows the same occurrence.

## 5. Timer and recovery

### TMR-01 Start transaction

Given selected today's task and no active interval, Start creates exactly one null-stop interval and singleton active record with captured ZoneId. Timer/row switch to running.

### TMR-02 Multiple cycles

Given the same task, three Start/Stop cycles create three intervals with increasing stable ordinals; the total equals their exact sum and no counter column exists.

### TMR-02a Reselect and continue accumulated total

Given Task 1 runs 9:00–10:00 AM, Task 2 runs 10:30–11:00 AM, and Task 1 is reselected at
1:00 PM, then Task 1 initially displays one accumulated hour. Starting and stopping it from
1:00–2:00 PM creates a second Task 1 interval rather than extending the first. Final authoritative
history is Task 1 `09:00–10:00` plus `13:00–14:00` (two hours total) and Task 2
`10:30–11:00` (thirty minutes total).

### TMR-03 Stop transaction

Given one active interval, Stop writes one wall-clock UTC stop, clears active state, preserves the interval, and leaves the displayed total derived from completed intervals.

### TMR-04 One global timer

Given simultaneous Start requests for two tasks, at most one succeeds; the database ends with one active row and one null-stop interval. Retrying Start while active is rejected.

### TMR-05 No tick writes

Given a timer runs while 1,000 UI refresh signals occur, then interval/task/active rows are unchanged except the original Start; only a later Stop/normalization writes.

### TMR-06 Monotonic live display

Given a running in-process timer, when wall clock jumps forward/back but monotonic time advances normally, the visible running contribution advances monotonically without that wall-clock jump.

### TMR-07 Process recovery

Given an open interval is committed and the process dies, when relaunched later, then active state is recovered from Room, running task remains selected, elapsed is reconstructed from persisted start/current UTC once, and no duplicate interval appears.

### TMR-08 Device restart

Given an open interval before device restart, when the app is next launched, it recovers as running from Room without requiring a service/boot receiver and normalizes missed boundaries.

### TMR-09 Backgrounding

Given a running interval, background/foreground preserves persisted state; elapsed includes device sleep and resumes accurately without per-tick persistence.

### TMR-10 Orphan/inconsistent state

Given a deliberately inconsistent test fixture (active pointer to closed/missing interval or unreferenced open interval), startup returns a defined invariant error/approved repair path, does not start another timer, and never silently drops known time.

### TMR-11 Wall-clock anomaly

Given wall now would make Stop at/before Start, Stop does not write an invalid interval or silently invent a boundary; it preserves recoverability and shows the clock-change resolution state.

## 6. Midnight, rollover, and time zones

### DATE-01 Single midnight

Given an interval starts 11:30 PM and stops 1:00 AM in the pinned zone, then one original-day interval ends exactly at midnight, a same-series next-day task exists with copied metadata, and its interval begins at midnight and ends 1:00 AM.

### DATE-02 Multiple midnights

Given a timer is recovered/stopped after crossing three local-date boundaries, one segment exists within each affected daily task and every boundary comes from `atStartOfDay(zone)`, with no duplicates.

### DATE-03 Exact midnight stop

Given Stop occurs exactly at a boundary, the prior segment ends at midnight and no zero-duration next-day interval is created; selected-series rollover can still select/create today's daily task.

### DATE-04 Lazy foreground split

Given the app stays foreground across midnight, the first date-change refresh normalizes and retargets active state to today's daily copy. Given the app sleeps through midnight, resume does the same.

### DATE-05 Rollover uniqueness

Given repeated/concurrent resume/date-change reconciliation attempts in one effective zone,
exactly one `(seriesId, workDate, ZoneId)` task exists and it is selected. A pre-existing
same-series/date task assigned under another zone is retained separately and is not rewritten.

### DATE-05a Historical selection is not rollover

Given the effective date has not changed and the user intentionally selects a task from a
historical displayed date, reconciliation preserves that task for viewing and Start remains
ineligible. Given instead that a timing selection made yesterday is restored after the effective
date advances, reconciliation finds or creates and selects the exact series/today/ZoneId copy.

### DATE-06 Rollover metadata

Given selected prior task metadata was edited before rollover, the new copy uses its current client/description, same series ID, new stable task ID, new date, and zone used for assignment.

### DATE-07 Spring forward

Given a timer spans a spring-forward transition, elapsed duration follows instants (missing local hour is not counted), date segments fit 23-hour boundaries where applicable, and totals are exact.

### DATE-08 Fall back

Given a timer spans repeated fall-back hour, elapsed duration counts both occurrences, local export values contain offsets, and date segments fit 25-hour boundaries where applicable.

### DATE-09 Device zone mode

Given device mode, when no timer runs and system zone changes, today/future assignment follows the new geographical ZoneId while existing stored dates/zones remain unchanged.

### DATE-10 Manual zone mode

Given manual `America/Los_Angeles`, today/boundaries use that zone regardless of device zone. Invalid/fixed-offset-only selector values are not accepted as the configured geographical ID.

### DATE-11 Locked zone setting

Given a timer runs, time-zone mode/manual-zone controls and repository mutations are blocked until Stop.

### DATE-12 External zone change while running

Given device mode and a system zone change during timing, splitting remains pinned to the Start zone until Stop; afterward the new device zone becomes effective without moving historical tasks.

### DATE-13 Browse without creation

Given a preferred series lacks a task on a browsed historical/future date, merely displaying that date does not create a daily task.

### DATE-14 Same date in different zones

Given a same-series/today task was assigned under a prior ZoneId, actual-date/zone reconciliation
resolves or creates the exact current-zone copy, selects it, and leaves the prior-zone task
untouched before Start is enabled. A direct Start against the prior-zone copy is rejected. If both
appear on the displayed date, the UI distinguishes their zones and export includes their stored
ZoneIds.

## 7. Theme and persistence

### SET-01 Immediate Light/Dark

Changing between Light and Dark updates the whole Compose tree immediately and persists through recreation/process/device restart. If System exists, explicit choices remain available and deterministic.

### SET-02 Settings durability

Zone mode/manual ID, export default, spreadsheet metadata, and valid selection hints survive supported restarts through DataStore; no token/task data is stored there.

### SET-03 Database durability

Created clients/tasks/intervals survive activity recreation, backgrounding, process death, device restart, and ordinary subsequent launches. Normal startup does not recreate, clear, or seed production data.

### SET-04 No uninstall promise

Help/release claims do not state that local data survives uninstall or clearing app storage.

## 8. CSV

Run these after the destination-flow decision in `EXPORT_SPEC.md` is approved.

### CSV-01 Displayed date and filename

Given Jul 22 is displayed while today is Jul 23, CSV uses only Jul 22 rows and suggests `worqorder_2026-07-22.csv`.

### CSV-02 Exact schema

Header and each row use schema version 1 columns in the exact documented order. A date with no tasks has only a header; a zero-interval task has one row with blank interval fields and zero task total.

### CSV-03 One row per interval

A task with three intervals produces exactly three rows with repeated task/client metadata and stable interval ordinals.

### CSV-04 Escaping/Unicode

Fields containing comma, quote, CR, LF, emoji, accented and non-Latin characters round-trip under UTF-8 and RFC-style quoting; quotes double and records use CRLF.

### CSV-05 Durations/timestamps

Longer-than-23-hour task totals are not wrapped; UTC/local timestamps and DST offsets follow schema; numeric fields are locale independent.

### CSV-06 Running snapshot

Given today's active interval, all rows share one export instant, its stop is blank/state RUNNING, and interval/task duration uses that instant without stopping the timer.

### CSV-07 Picker cancellation

Canceling before destination URI produces no output and a non-error canceled state; Room and prior files remain unchanged.

### CSV-08 Repeat and failure

Repeating export is allowed and never changes Room. A serialization/provider failure does not claim success; any partial document is deleted when the provider supports it or clearly reported otherwise.

### CSV-09 No broad permission/XLSX

The merged manifest requests no broad storage permission for CSV; dependency/build inspection finds no XLSX/POI generation.

## 9. Google Sheets

### GS-01 Connection validation

Given authorized access and valid URL/ID, validation stores/displays the exact spreadsheet ID/title and does not create a spreadsheet. Malformed, missing, read-only, or unauthorized sheets give distinct safe errors.

### GS-02 One connection

Connecting B replaces/disconnects local association to A only after confirmation/success; exports use B, and neither external document is deleted.

### GS-03 Missing connection routing

With Google default but missing/invalid authorization or spreadsheet, main export routes to the relevant settings state and makes no network write.

### GS-04 New date tab

If `WorqOrder_2026-07-22` is absent, export creates it inside the connected spreadsheet, writes exact marker/schema/date/header/data, and creates no new spreadsheet document.

### GS-05 Idempotent re-export

Given a correctly marked date tab, unchanged re-export replaces application-owned content and creates no duplicate rows. Export timestamp may update.

### GS-06 Authoritative replacement

Given local edit/deletion/addition after first export, re-export removes obsolete application-owned rows and exactly reflects current Room snapshot in stable sorted order.

### GS-07 Tab-name conflict

Given same-named tab without the exact marker, export fails with conflict, leaves that tab untouched, makes no alternate/duplicate tab, and changes no local data.

### GS-08 Schema/date conflict

Given marker with unsupported schema or mismatched date, export does not overwrite and reports compatibility conflict.

### GS-09 Other content untouched

Export/re-export never modifies other tabs. Within a marked tab, documented app-owned contents may be replaced.

### GS-10 Raw values

Client/description values starting with formula characters are written as literal/raw strings, not executable formulas.

### GS-11 Offline

Given no network, export reports retryable offline failure, does not claim success, retains useful error state, and leaves Room unchanged.

### GS-12 Authorization expiration

Given expired/revoked authorization, export prompts reauthorization and supports retry without losing spreadsheet metadata or changing Room.

### GS-13 User cancellation

Canceling account selection/consent does not claim sign-in, connection, or export success.

### GS-14 Ambiguous/remote failure

Rate limit/server/ambiguous response yields a typed retryable state. Retrying converges to the authoritative content without duplicates.

### GS-15 Sign-out/disconnect

Sign-out uses supported identity APIs and makes Google export unavailable; Disconnect clears local spreadsheet metadata. Neither changes Room or deletes external content.

### GS-16 Credentials/scopes

Static/release inspection finds no password, raw token persistence, OAuth client secret, service-account key, Firebase, Drive-wide scope, or unrestricted credential. The documented Sheets scope matches the implemented consent.

## 10. Room and build quality

### DB-01 Fresh creation/reuse

The database is created only when first needed and reopening the app reuses rows instead of reseeding.

### DB-02 Constraints

Instrumentation tests prove client active-name uniqueness, series/date/zone uniqueness, foreign-key restrict/cascade, interval ordinal uniqueness, and singleton active-timer cardinality.

### DB-03 Migration

For every schema version, migrate a populated prior database to latest and verify every client/task/interval/active invariant and ID/timestamp. Release database construction has no destructive fallback.

### DB-04 Schema export

Versioned Room schema JSON is committed and changes are reviewed with migration/acceptance updates.

### BUILD-01 Technology guardrails

Source/build inspection confirms Kotlin application code, Compose rather than XML layouts, Gradle Kotlin DSL/version catalog, KSP where supported, min API 26, stable dependencies, manual DI, and no prohibited framework/backend.

### BUILD-02 Quality gate

Formatting, lint, unit tests, coroutine tests, relevant Room/Compose instrumentation tests, and applicable debug/release builds pass before each implementation milestone is declared complete.

### M3-01 Domain test evidence

Milestone 3 JVM tests use fake UTC clocks, elapsed-realtime sources, effective ZoneIds, selection
storage, Room-facing repositories, and multiple real geographical zones. They cover typed
Start/Stop failures, repeated totals, exact date/zone eligibility, rollover/reuse, duration
formatting, recovery/anomaly clamping, manual interval/DST validation, and real midnight
boundaries. Instrumentation tests cover DataStore recreation, atomic Room continuation/Stop
chains, idempotence, and concurrent global Start protection. Lifecycle/UI presentation remains
deferred to its owning milestones.

## 11. Accessibility and resilience

### A11Y-01 Semantics/touch

Settings, date arrows/calendar, Start/Stop, Add, export, overflow, and client actions have labels/roles, at least 48 dp targets, logical traversal, and keyboard/switch accessibility.

### A11Y-02 Font/contrast

At supported large font scales and narrow screens, timer/date/actions remain understandable, content scrolls rather than clips critical controls, and Light/Dark contrast meets Material accessibility guidance.

### A11Y-03 State communication

Selected, running, disabled, validation, success, and failure are conveyed through semantics/text/icons in addition to color; transient messages needed to recover are not snackbar-only.
