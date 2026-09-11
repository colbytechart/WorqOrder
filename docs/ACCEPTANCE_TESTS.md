# WorqOrder Acceptance Tests

Version scope: sections that explicitly identify released `0.1.0`/`0.2.0` behavior preserve
historical acceptance evidence. Section 15 is the authoritative current `0.3.0` acceptance set;
its one-interval, no-rollover, exact-boundary, and schema-5 rules supersede earlier expectations.

## 1. Test policy

These scenarios define observable MVP behavior. Automated coverage may combine pure unit, coroutine, Room instrumentation, ViewModel, and Compose UI tests, but a requirement is not complete merely because one layer was tested. Use fake UTC clock, fake monotonic time, fake effective-zone provider, fake document destination, and fake Google Sheets gateway to make time/failure cases deterministic.

Unless stated otherwise, examples use `America/New_York`, a healthy database at the latest
implemented Room version, and no active timer. Migration scenarios name their source and target
versions explicitly. Duration comparisons use exact milliseconds even when the UI ticker renders
less frequently.

## 2. Main screen and selection

### UI-01 Initial empty state

Given the database has no tasks for today, when the main screen opens, then it shows WorqOrder,
today's labeled date, `00:00:00`, disabled Start, Add Task, a useful empty state, Settings, and an
export action naming today's date/destination.

### UI-02 Date filtering

Given tasks exist on two dates, when the displayed date changes with arrows or picker, then only tasks whose stored work date equals the new displayed date appear and the export label names that date.

### UI-03 Row content

Given a daily task has client, description, and intervals, then its row exposes those fields, total duration, overflow actions, selected semantics, and running semantics without relying only on color.

### UI-03a Pinned timer/date controls and task ordering

Given more tasks than fit in the lower task region, scrolling to the oldest task leaves both the
timer card and complete date-selector bar at their original on-screen positions and exposes a
visual scroll-position indicator. Tasks are ordered newest-created first with a stable ID
tie-breaker; adding a task places it first without changing persisted or export order.

### UI-04 Selection

Given two stopped tasks on the displayed date, when the user taps the second, then it alone is selected and the timer shows its total.

### UI-05 Start without selection

Given no task is selected, then Start is disabled and an attempted programmatic Start creates no interval.

### UI-06 Non-today Start

Given a task is selected on a historical or future displayed date, then Start is disabled; task creation/manual editing remain available.

### UI-07 Running lock

Given task A is running, when the user tries to select/edit/delete task B or A as applicable, then selection remains A, switching is disabled, A cannot be edited/deleted, and no extra interval is created.

### UI-07a Browse while running

Given task A is running for today, when another date is displayed, then A remains the identified
global timing selection, Stop remains available, rows on the viewed date cannot replace A, and the
export action remains disabled with **Stop Timer to Export**. Creating/editing a non-running task
does not alter A or its interval.

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

Given today is displayed and the client, short description, and optional
hardware/software-purchases text are valid, when Create is tapped once, then one Room task is
written with a new task/series ID, today's epoch day/current ZoneId/timestamps, it becomes selected,
and navigation returns to main.

### TASK-02 Create other date

Given a historical/future displayed date, a valid task is stored for that date/zone and shown after return, but live timing remains disabled and the preferred live selection is not changed solely by creation.

### TASK-03 Cancel creation

Given edited form fields, when Cancel/back is confirmed according to normal navigation behavior, then no client/task is implicitly written except a client explicitly completed through the separate inline Add Client action.

### TASK-04 Description validation

Blank/whitespace-only and over-400-character short descriptions fail; valid descriptions are
trimmed and saved.

### TASK-04a Hardware/software-purchases validation

The field labeled `Hardware / Software Purchases` may be blank. Nonblank text is trimmed and saved;
over-400-character text is rejected with field-level validation.

### TASK-05 Edit metadata

Given a stopped task, changing client, short description, or hardware/software-purchases text
affects that daily task only. Existing sibling dates remain unchanged; a later rollover copies the
edited current metadata.

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

### INT-07 Manual add

Given a valid stopped task interval, Add assigns `maxOrdinal + 1`, a stable ID, manual flag, and
updates totals. The editor exposes Add Interval and applies the same date-boundary, overlap, and DST
validation used by interval edits.

### INT-08 DST gap editor

On a spring-forward date, a nonexistent local time is rejected and no instant is guessed silently.

### INT-09 DST overlap editor

On a fall-back date, the earlier/later occurrence is distinguishable by offset, the chosen instant persists, and reopening the editor shows the same occurrence.

## 5. Timer and recovery (v0.2 historical compatibility)

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

Given one active interval, Stop writes one UTC stop, clears active state, preserves the interval,
and leaves the displayed total derived from completed intervals. With a valid process-local anchor,
the endpoint is Start plus measured monotonic active duration so the final total equals the
immediately preceding live total.

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

Given wall time moves forward or backward while a valid live anchor exists, Stop and normalization
use the monotonic projected UTC instant. The final displayed/persisted total does not jump and no
false midnight boundary is created. If process recovery has no trustworthy anchor and wall now
would make Stop at/before Start, the interval remains recoverable and a clock-change state is
shown instead of writing an invalid boundary.

### TMR-12 Destination-independent resume recovery

Given a timer is active while Main, Create Task, Edit Task, Settings, or Client Management is
visible, every Activity resume invokes the same application-scoped recovery sequence. Returning to
Main shows the Room-owned running task and recovered duration without requiring that Main was
visible during resume.

### TMR-13 Concurrent recovery idempotence

Given repeated or concurrent Activity/Main resume signals after one or several missed midnights,
exactly one continuation exists for each crossed boundary, one interval remains open, and the
active pointer and selection identify the final daily task.

### TMR-14 Recovery-anchor correction

Given process recovery occurs while wall time is before the persisted Start, visible active
contribution is clamped to zero and a clock anomaly is shown. After wall time is corrected and the
app resumes, the provisional anchor may be rebuilt without changing any persisted start/stop
boundary.

## 6. Midnight, rollover, and time zones (v0.2 historical compatibility)

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

Given selected prior task metadata was edited before rollover, the new copy uses its current
client, short description, and hardware/software-purchases text, same series ID, new stable task
ID, new date, and zone used for assignment.

### DATE-07 Spring forward

Given a timer spans a spring-forward transition, elapsed duration follows instants (missing local hour is not counted), date segments fit 23-hour boundaries where applicable, and totals are exact.

### DATE-08 Fall back

Given a timer spans the repeated fall-back hour, elapsed duration counts both occurrences,
persisted instants and the interval editor preserve/identify the chosen occurrence, and date
segments fit 25-hour boundaries where applicable. The intentionally reduced external export
schema still emits task-zone `hh:mm a` only; its duration remains the correct instant-based value.

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

### SET-01 Immediate appearance modes

Client Management is the first Settings item and Consultant Management is second, followed by the
bare Consultant selector/warning and Appearance. No Time Zone card, controls, effective-ID text,
or selector dialog is exposed. Device zone remains the first-launch/corrupt-value default in typed
settings. Export Destination follows Appearance, and Google Sheets Connection is visible only when
Google Sheets is selected. Its conditional controls end with an **Auto Export** switch row inside
the Export Destination card. The row follows the sign-in/connection controls, displays supporting
text **Automatically export tasks at the end of each day.**, and is absent for CSV/XLSX. System is
selected by default and follows device Light/Dark configuration changes. Selecting explicit Light
or Dark overrides the device while all three radio choices remain enabled. Screen, section, card,
empty-state, and dialog headers use title capitalization without rewriting body/action copy. Every
change updates the whole Compose tree immediately and persists through
recreation/process/device restart.

### SET-02 Settings durability

Theme, zone mode/manual ID, export default, spreadsheet metadata, and valid selection hints survive supported restarts through DataStore; no token/task data is stored there. First launch defaults to System theme, device-zone mode, and CSV.

### SET-03 Database durability

Created clients/tasks/intervals survive activity recreation, backgrounding, process death, device restart, and ordinary subsequent launches. Normal startup does not recreate, clear, or seed production data.

### SET-04 No uninstall promise

Help/release claims do not state that local data survives uninstall or clearing app storage.

### SET-05 Invalid preference fallback

Given an unknown stored theme, zone-mode, or export enum, the app uses System, device-zone mode, or
CSV respectively. A missing, invalid, fixed-offset, or legacy-alias manual zone cannot activate
manual mode. A malformed selection hint is treated as no selection rather than becoming task or
timer truth.

### SET-06 Startup zone and displayed date

Given persisted manual-zone selection and a stale eligible task selection, startup waits for the
persisted effective zone before rollover and creates/reuses only the exact series/date/zone copy.
Normal startup displays today and does not restore an old browsed date. If an effective-zone change
occurs while Main displays today, Main follows the new today; an intentionally browsed date remains
unchanged.

## 8. CSV

Run these after the destination-flow decision in `EXPORT_SPEC.md` is approved.

### CSV-01 Displayed date and filename

Given Jul 22 is displayed while today is Jul 23, CSV uses only Jul 22 rows and suggests `worqorder_2026-07-22.csv`.

### CSV-02 Exact schema

Header and each row use current schema version 5's exact 13 visible columns and canonical strings
in the documented order. Internal schema/snapshot metadata is not a visible column. A date with no
tasks has only a header; an untimed task has one row with blank Start/Stop, `00:00:00` Time spent,
and `0` Billing minutes.

### CSV-03 One row per task

A task produces exactly one row with its task/client metadata and optional sole interval values,
including hardware/software-purchases text. A task with no interval still produces one row.

### CSV-04 Escaping/Unicode

Description and hardware/software-purchases fields containing comma, quote, CR, LF, emoji,
accented and non-Latin characters round-trip under UTF-8 and RFC-style quoting; quotes double and
records use CRLF.

### CSV-05 Durations/timestamps

Longer-than-23-hour task totals are not wrapped. Start/Stop are converted through the task's stored
ZoneId and exported only as strict 12-hour `hh:mm a`; complete instants remain internal. Durations are
`HH:MM:SS`, truncate rather than round sub-second remainder, and are locale independent.

### CSV-06 Running-timer lockout

Given any globally active interval, the export action is disabled and labeled **Stop Timer to
Export**. Dispatching `MainEvent.Export` while running launches no CSV picker. After Stop, CSV
contains the completed interval's final Stop Local and duration.

### CSV-07 Picker cancellation

Canceling before a destination URI produces no output, no Main-screen status/banner, and no
success or error claim; Room and prior files remain unchanged. A non-sensitive diagnostic
`Canceled` attempt may still be retained.

### CSV-08 Repeat and failure

Repeating export is allowed and never changes Room. A serialization/provider failure does not claim success; any partial document is deleted when the provider supports it or clearly reported otherwise.

### CSV-09 No broad permission or unintended Excel stack

The merged manifest requests no broad storage permission for CSV. Before Milestone 12 there is no
XLSX implementation; after Milestone 12, dependency/build inspection finds no Apache POI or other
unapproved broad Excel stack.

### CSV-10 Immutable picker payload and progress

The full Room snapshot and CSV string exist before the create-document picker opens. Editing the
task after that point does not change the pending file. Main disables repeat submission while
preparing/choosing/writing, identifies the displayed export date, and distinguishes success,
retryable failure, and possible partial output. Cancellation restores the ordinary Main state
without visible feedback.

### CSV-11 Shared snapshot boundary

`ExportSnapshotCoordinator` performs normalization/read/build once and returns the immutable
current schema-5 dataset of 13 columns. Released `0.1.0` and `0.2.0` schema versions remain
historical compatibility fixtures. CSV serialization changes no field/order/value. XLSX/Google adapters
consume the same object rather than rebuilding destination-specific rows.

## 9. Google Sheets

### GS-01 Connection validation

Given a valid URL/ID, the app filters the official Android Google Picker authorization flow to that
exact ID and the Google Sheets MIME type. A nonempty returned `picked_file_ids` set must exactly
match. If Disconnect retained a prior per-file grant and Google returns an empty Picker-ID set,
reconnect may continue only through successful exact-ID Drive edit-capability and Sheets metadata
validation. A nonempty mismatch is rejected before API validation. Only successful validation
stores/displays the spreadsheet ID/title. No spreadsheet or test cell is created. Malformed,
Picker-mismatched, missing, read-only, trashed, or unauthorized sheets give distinct safe errors.

### GS-02 One connection

Connecting B replaces/disconnects local association to A only after confirmation/success; exports
use B, and neither external document is deleted. While a spreadsheet is connected, the URL/ID
field and Validate and Connect action are absent; Disconnect must occur before choosing another
spreadsheet. Changing Export Destination from CSV or XLSX to Google Sheets automatically scrolls
the Settings list until the newly revealed Google Sheets Connection section is visible.

### GS-03 Missing connection routing

With Google default but missing/invalid authorization or spreadsheet, main export routes to the relevant settings state and makes no network write.

### GS-04 New date tab

If a connected spreadsheet is confirmed completely blank, its original first sheet is renamed to
`WorqOrder_2026-07-22`, right-sized, and populated; no unused blank default tab remains. If any
user-entered cell/sheet content exists, or blankness is indeterminate, every existing tab is
preserved and export creates the absent date tab. In either case the date tab has exactly nine
columns and the required row count, writes the exact `PROJECT`-visible sheet metadata
`worqorder_export_marker=WORQORDER_EXPORT`, `worqorder_export_schema=2`, and
`worqorder_export_work_date=2026-07-22` plus the exact visible header/data, and creates no new
spreadsheet document. One atomic batch contains either first-sheet rename/resize or Add Sheet,
all marker requests, and the complete literal cell table.

### GS-05 Idempotent re-export

Given a correctly marked date tab, unchanged re-export replaces application-owned content and
creates no duplicate rows. The visible table remains identical. The production response parser
recognizes metadata returned under `sheets[].developerMetadata`; it does not misclassify its own
previously exported tab as unowned.

### GS-06 Authoritative replacement

Given local edit/deletion/addition after first export, re-export removes obsolete application-owned rows and exactly reflects current Room snapshot in stable sorted order.

### GS-07 Tab-name conflict

Given same-named tab without the exact marker, export fails with conflict, leaves that tab untouched, makes no alternate/duplicate tab, and changes no local data.

### GS-08 Schema/date conflict

Given developer metadata with unsupported schema or mismatched date, export does not overwrite and
reports compatibility conflict.

### GS-09 Other content untouched

Export/re-export never modifies other tabs. Within a marked tab, documented app-owned contents may be replaced.

### GS-10 Raw values

Client, description, and hardware/software-purchases values starting with formula characters are
written as `UpdateCellsRequest.userEnteredValue.stringValue`, not executable formulas.

### GS-10A Shared visible table and capacity

Row 1 and every visible row exactly match CSV/XLSX headers, order, and canonical strings. Marker
metadata is not visible. Re-export clears stale rows and right-sizes the grid to 13 columns and
required rows so unused allocation does not unnecessarily consume the official 10-million-cell
spreadsheet limit.

### GS-11 Offline

Given no network, export reports retryable offline failure, does not claim success, retains useful error state, and leaves Room unchanged.

### GS-12 Authorization expiration

Given expired/revoked authorization, export prompts reauthorization and supports retry without losing spreadsheet metadata or changing Room.

### GS-13 User cancellation

Canceling account selection/consent does not claim sign-in, connection, or export success. An
export cancellation clears in-progress presentation without showing a cancellation banner.

### GS-14 Ambiguous/remote failure

Rate limit/server/ambiguous response yields a typed retryable state. Retrying converges to the authoritative content without duplicates.

### GS-14A Confirmed success and diagnostic history

The app claims success only after a 2xx batch response confirms the connected spreadsheet ID.
The generic typed `last_export_*` DataStore entry may record destination/date/time/outcome/safe
category, but no export-history Room table, spreadsheet ID, row data, token, or API response is
persisted.

### GS-14B Main-screen state

While an explicit export is active, the date-specific Google button is disabled and shows progress.
Success, ownership/schema conflict, authorization, offline, timeout, permission, not-found, quota,
server, malformed, and ambiguous-result states remain usable and actionable. Retry is offered only
where repeating the idempotent operation is useful; repeated taps cannot start parallel exports.

### GS-15 Sign-out/disconnect

Disconnect clears local spreadsheet metadata but leaves account/grant state intact. Sign-out makes
Google export unavailable, attempts grant revocation, clears Credential Manager/in-memory state,
clears account and connected-spreadsheet metadata, and reports incomplete remote revocation if it
fails. Local metadata is still cleared after incomplete remote revocation. Neither operation
changes Room or deletes external content.

### GS-16 Credentials/scopes

Static/release inspection finds no password, raw token persistence, OAuth client secret,
service-account key, Firebase, Drive-wide/all-spreadsheets/profile/email scope, or unrestricted
credential. Consent requests only `drive.file`, and every connected ID was explicitly returned by
the Picker resource grant.

### GS-17 Free/open-source and distribution policy

Static configuration and release review find no Google Cloud billing account dependency, paid
quota path, subscription, Workspace/organization requirement, custom-domain requirement, Google
marketplace-signing dependency, or owner-managed tester-list dependency. Debug and direct-release
builds use Android OAuth clients bound to their exact package/signing SHA-1. The repository remains
GPLv3.

### GS-18 Quota and future-policy failure

Google requests occur only during explicit user operations and do not retry automatically.
Standard-quota exhaustion does not trigger paid capacity or background retry, does not mutate Room,
and leaves CSV available. If `drive.file`/Picker policy no longer supports the workflow, the app
does not silently request a broader scope.

## 10. XLSX

### XLSX-01 One-off create-document flow and default

Settings accepts exactly CSV, XLSX, and Google Sheets after migration; CSV remains the
default/fallback. XLSX has no connection UI or retained URI metadata. Every export launches
`ACTION_CREATE_DOCUMENT` with the official XLSX MIME type and suggested
`worqorder_YYYY-MM-DD.xlsx` filename.

### XLSX-02 Shared schema and ordering

Exporting Jul 22 creates a new workbook containing exactly one visible
`WorqOrder_2026-07-22` worksheet. It has the exact current canonical header at row 1 and rows identical
in content/order to CSV and Google Sheets for the same captured snapshot. Exporting Jul 23 creates
another independent workbook containing only `WorqOrder_2026-07-23`. An empty date has only the
header; a zero-interval task has one blank-interval row.

### XLSX-03 Cell safety and fidelity

All canonical values are literal text cells. Formula-prefixed text is not executable.
Unicode, commas, quotes, CR/LF, task-zone `hh:mm a`, and accumulated `HH:MM:SS` survive an
independent-reader round trip and open correctly in Microsoft Excel and LibreOffice.

### XLSX-04 Package safety

The OOXML ZIP contains only required reviewed package, workbook, relationship, style, and
single-worksheet parts. It has no ownership marker, macro project, formula, external link, hidden
worksheet/content, credential, key material, path traversal entry, or unnecessary identifying
metadata.

### XLSX-05 Running lockout/repeat/no mutation

A running interval disables XLSX export under the same policy as the other destinations. After
Stop, each re-export creates another independent file containing one complete current snapshot; it
never appends within a workbook. No success, cancellation, or failure changes Room or timer state.

### XLSX-06 SAF cancellation and failure

The official spreadsheet MIME type and user-mediated SAF create-document flow are used without
broad storage permission. No open-document flow or persistable URI grant exists. Cancellation
writes nothing and never changes Room. Output failure attempts to delete a partial provider
document and reports when a partial document might remain.
Cancellation returns to the ordinary Main state without visible feedback.

### XLSX-07 Compatibility and efficiency

Representative generated workbooks parse with an independent reader and open in current Excel and
LibreOffice. The minimal package contains one visible sheet and no formulas, macros, external
links, credentials, hidden content, or unnecessary metadata. Large-snapshot tests stay within
recorded memory/time limits on minimum and target API devices. Dependency inspection proves the
focused writer adds no XLSX library and no Apache POI.

## 11. Room and build quality

### DB-01 Fresh creation/reuse

The database is created only when first needed and reopening the app reuses rows instead of reseeding.

### DB-02 Constraints

Instrumentation tests prove client active-name uniqueness, series/date/zone uniqueness, foreign-key restrict/cascade, interval ordinal uniqueness, and singleton active-timer cardinality.

### DB-03 Migration

For every schema version, migrate a populated prior database to latest and verify every client/task/interval/active invariant and ID/timestamp. Release database construction has no destructive fallback.

### DB-04 Schema export

Versioned Room schema JSON is committed and changes are reviewed with migration/acceptance updates.

### DB-05 Task-metadata migration

Migrating a populated version-1 database to version 2 adds non-null
`hardware_software_purchases` with an empty value on existing tasks, preserves every existing
client/task/interval/active-timer relationship, and accepts independently validated 400-character
description and purchases values after migration.

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

## 12. Accessibility and resilience

### A11Y-01 Semantics/touch

Settings, date arrows/calendar, Start/Stop, Add, export, overflow, and client actions have labels/roles, at least 48 dp targets, logical traversal, and keyboard/switch accessibility.

### A11Y-02 Font/contrast

At supported large font scales and narrow screens, timer/date/actions remain understandable, content scrolls rather than clips critical controls, and Light/Dark contrast meets Material accessibility guidance.

### A11Y-03 State communication

Selected, running, disabled, validation, success, and failure are conveyed through semantics/text/icons in addition to color; transient messages needed to recover are not snackbar-only.

### A11Y-04 Destructive Google actions

Disconnecting a spreadsheet requires confirmation that the Google account remains signed in.
Signing out while a spreadsheet is connected requires confirmation that the spreadsheet will
also disconnect. Both state that local tasks remain untouched. Signing out with no connected
spreadsheet remains a direct action.

### A11Y-05 Adaptive Main layout

At 200% font scale, portrait Main stacks Export and Add task without overlap. Landscape Main moves
those actions into its top application bar, uses a compact destination-specific visible export
label, and retains the full export date/destination in semantics. The timer retains its complete
semantic value and can scroll horizontally if necessary. The bounded pinned timer/date region
leaves a usable independently scrollable lower message/task region on a 640 × 360 dp short
landscape surface. At ordinary portrait phone width, the two bottom actions remain side by side.

## 13. v0.2.0 acceptance additions

These tests become active with their owning `0.2.0` milestones and do not rewrite the accepted
`0.1.0` results above.

### V2-DATA-01 Non-destructive update

A populated version-1, version-2, or version-3 database upgrades without losing clients, tasks,
intervals, selections, or an open timer. Existing tasks receive null/blank Employee,
`Unspecified` Work Type, blank Billing Status, and blank Mileage. The new schema is exported; no
destructive fallback exists.

### V2-CLIENT-01 CSV import appends safely

The picker accepts CSV only. Every nonblank cell is parsed with RFC-style quote/line-break support
and existing client validation. Active duplicates are skipped, archived matches restored, in-file
duplicates collapsed, and new names appended. Existing clients are never overwritten or removed;
the final active list is A–Z. Unsupported, malformed, corrupt, over-limit, I/O-failed, or excessive
input causes no partial mutation or crash and reports an actionable error.

Automated coverage must prove strict UTF-8/BOM handling; comma, doubled-quote, CR/LF, Unicode, and
quoted-multiline handling; the 1 MiB/10,000-record/20,000-cell/1,024-UTF-16-unit limits; extension
and MIME rejection; blank-cell omission; normalization; active and in-file duplicate skipping;
archived restoration; A–Z observation; accessible progress/result states; silent picker cancel;
and rollback when a later database mutation fails after an earlier restoration.
With a long client directory scrolled away from the actions, an import error returns the list to
the top and displays the error between **Import From CSV** and **Active Clients**. Dismissing the
error also leaves the list at the top with both import actions visible.

### V2-CONSULTANT-01 Directory and historical snapshots

The user-facing Consultant directory (internally Employee persistence) supports
add/rename/archive/restore and A–Z active selection under the approved validation rules. New task
creation requires an active selected Consultant. Rename/archive never changes earlier task
consultant snapshots or exports; explicit task edit may correct the assignment. Rollover copies the
source snapshot. Migrated unassigned tasks remain legible/editable.

Standalone **Client Management** and **Consultant Management** rows are the first and second
Settings options. The bare current-Consultant dropdown and missing-selection warning follow those
rows with no Consultant or Clients card. The dedicated Consultant destination contains Add
Consultant plus active and archived lists with the same rename/archive/restore behavior and visual
structure as Client Management. Empty active and archived sections each show an indented title and
supporting explanation. Back navigation returns to Settings without changing selection.

The persisted selection recovers only when its Employee row still exists and is active. Archiving
the selected Consultant clears future selection while leaving every daily-task Employee ID/name
snapshot unchanged. Add offers restore for a canonical archived match; rename/restore conflicts do
not partially mutate either row. Create Task displays an actionable Consultant Settings route and
keeps Create disabled while no valid selection exists. At save time Room rechecks both the active
Client and active Employee and captures the Employee's current name in the same transaction that
inserts the daily task.

### V2-TASK-01 Work Type, Billing Status, Mileage, and Billing Minutes

New tasks default to On-Site and can choose In-Office. Mileage opens a decimal numeric keyboard,
accepts only the approved canonical non-negative decimal syntax, and round-trips through edit.
Billing Status is an exclusive `Billable`/`Do not bill`/`Do not charge` choice between Work Type
and Mileage; new tasks default to Billable while migrated tasks remain blank until edited.
Billing Minutes equals `0` at zero duration and otherwise rounds the exact combined interval total
up to a 15-minute multiple, including boundary and long-duration cases. It is derived rather than
persisted and never changes timestamp precision.

### V2-EXPORT-01 Canonical schema 4

CSV, one-off XLSX, and Google Sheets expose exactly these headers/values in order: Start date, End
date, Consultant, Client, Description, Expense, Work type, Billing Status, Mileage, Interval number, Start time,
Stop time, Interval duration, Time spent, Billing minutes. Both date fields equal the same stored
task date formatted `MM/DD/YYYY`. Export Start/Stop are `hh:mm a`; durations are `HH:MM:SS`; Billing
minutes follows V2-TASK-01. Known owned schema-2/schema-3 Google tabs upgrade atomically;
unowned or newer/unknown tabs remain untouched conflicts.

### V2-TASK-02 Interval presentation and text-entry capitalization

Routine interval cards omit an interval Duration field, label values Start Time/Stop Time, and use
task-zone 12-hour `hh:mm a`. Task Total remains above the interval list and Billing Minutes is
directly below it. Text fields request sentence capitalization from the keyboard without changing
stored text automatically. Exact instants, editor precision, DST occurrence handling, and export
formatting remain unchanged.

### V2-EXPORT-02 Automatic eligibility and captured date

The switch is labeled **Auto Export**, displays **Automatically export tasks at the end of each
day.**, defaults off, and appears at the bottom of the Export Destination card after all Google
sign-in and Sheets connection options only while Google is selected. It is absent for CSV/XLSX;
without authorization/connection or required notification capability it is disabled with setup
guidance. Turning it on enables scheduling and turning it off cancels future automatic work safely.
CSV and XLSX never auto-run. A stable WorkManager `2.11.2` unique, non-expedited,
network-constrained one-time request captures target date, ZoneId, and connection association near
11:59 PM. An inexact run shortly after midnight exports that captured prior date, never
execution-time `today`. Recalculated one-time work stays aligned with geographical DST/zone rules;
no exact alarm or fixed 24-hour periodic worker exists.

### V2-EXPORT-03 Pending timer and notification

If any timer is running, the scheduled operation exports nothing and persists the target date as
pending. After successful Stop, a system notification containing no client/task data appears.
Tapping resumes/performs or confirms that target export. Dismissal does not mark success or delete
pending state. A successful automatic export produces no Main-screen status and no success
notification. API-33+ enablement requests `POST_NOTIFICATIONS` in context; denial leaves the switch
off. API-26+ channel-disabled and later permission-revocation tests prove pending state remains
recoverable in Google Settings even when Android suppresses the notification.

### V2-EXPORT-04 Background correctness and idempotence

Ordinary, DST, device/manual-zone-change, Doze, reboot, force-stop/reopen, delayed/missed run,
offline/auth-expired, returned Google authorization resolution, disconnect,
notification/worksheet permission, quota/ambiguous response, and concurrent manual-export tests
prove each captured date converges to one duplicate-free owned tab without Room mutation or
automatic retry. Multiple missed days preserve the oldest target and advance one date per worker
until caught up; no target is overwritten and no worker contains an unbounded loop. Disabling
automation, selecting CSV/XLSX, disconnecting, or signing out cancels future unique work and clears
automatic pending state safely.

The silent connected-spreadsheet request targets the persisted Google account explicitly. If
Google instead requires interactive authorization, the connection metadata, enabled switch, and
captured target survive restart; Settings exposes the pending action, and an Activity-backed retry
can authorize and export without forcing spreadsheet reconnection.

Unit and WorkManager integration tests additionally prove unique-work replacement rules,
calculated initial delay, network constraint, one-date-per-worker execution, persisted target/ZoneId
recovery, no interactive `PendingIntent` launch from a worker, one bounded 401 token-clear attempt,
terminal pending results instead of `Result.retry()`, content-free notification text/stable ID,
and exact merged-manifest permission expectations. Manual API-26 and current-API checks cover
notification channels, API-33+ permission grant/deny/revoke, Doze delay, reboot, force-stop/reopen,
Google grant retained versus resolution required, offline recovery, and silent success.

Milestone 26 maps these requirements to `AutomaticGoogleExportManager`,
`WorkManagerAutomaticGoogleExportScheduler`, `AutomaticGoogleExportWorker`, the DataStore target
tuple, and the conditional Settings controls. Its final close gate must distinguish automated
coverage from the remaining Android/Google timing and notification device checks.

A rejected Auto Export toggle does not move the Settings list. A red inline message names the
exact blocker and, for disabled notifications, directs the user to WorqOrder's notification
settings.

### V2-UI-01 Interval clock-time display

Routine Task interval cards display completed Start Time and Stop Time values as
task-zone `hh:mm a` without seconds. Persisted UTC instants, stored task ZoneId, duration and
date-boundary behavior, edit precision, and explicit fall-back occurrence disambiguation remain
unchanged. CSV, XLSX, and Google Sheets emit the same canonical `hh:mm a` Start/Stop
values, with no destination-specific formatter or schema discrepancy.

### V2-UI-02 Version-aware Settings footer

The final Settings content is bare footer text over the screen background, outside any card and
without an About title. It displays exactly the Gradle-derived version/stability text, for example
`WorqOrder v0.2.0 - stable`, and has no repository/release link, click action, or second manually
maintained version constant.

### V2-UI-03 Handed two-column landscape

On first install and corrupt-value fallback, **Landscape Orientation** is **Right-handed**.
Landscape keeps a spanning top bar with WorqOrder far left and Settings far right. Below it, a
full-height independently scrolling task list occupies approximately the left half while the
right half stacks timer, complete date controls, then Export/Add task. Selecting **Left-handed**
persists immediately and mirrors only those content columns without changing task order,
selection, timer, date, or export state. Portrait is unchanged.

The task-list scrollbar remains attached to that list's right edge in either handed mode, and the
visible list extends to the same bottom margin respected by Export/Add task. Mirroring changes
column placement, not chronological order or accessibility traversal meaning.

Both modes remain usable on API 26/current target, short and standard landscape, 200% font, large
display scale, TalkBack, and narrow multi-window bounds. Task rows stay readable and reachable;
the control half does not overlap, clip, or remove any required action.

### V2-UI-04 Running-timer lock-screen surface

Under the owner-approved `LOCK_SCREEN_SURFACE_ADR.md`, starting a timer posts one silent, dismissible,
standard notification containing WorqOrder identity, active Client plus task Description, and a
system-rendered accumulated elapsed timer. It appears in the shade and is eligible for the lock
screen; it is not described as a lock-screen-only widget. Its private supporting line contains
Client plus Description; its public supporting line is blank. It appears only while an interval
is open. Stop removes it. Swiping it away records that active interval ID and hides the surface
without stopping, closing, duplicating, or changing the Room interval; a later Start may show a new
surface.

API-33+ permission denial, channel disablement, OS lock-screen privacy suppression, process death,
reboot/first unlock, force-stop/relaunch, screen lock/unlock, and task metadata constraints fail
safely. Start remains usable without notification permission. A system-killed process needs no tick
repost; app resume reconciles against Room. The approved one-shot boot receiver reposts only after
first unlock and only when Room still has an undismissed active interval. Force-stop cannot recover
until the user relaunches the package. The app never claims visibility when user/device policy hides
it, never writes tick values to Room/DataStore, and never adds a foreground service, custom
notification layout, exact alarm, wake lock, or continuous app-owned background loop solely to
update elapsed text.

Automated and device tests cover the separate Running Timer channel, concise permission/channel
recovery guidance, private/public content, direct Main tap, swipe `deleteIntent`, per-interval
dismissal across recreation/process death/reboot, Stop cleanup, later-Start reappearance, midnight
continuation, wall-clock changes, API 26/current target, TalkBack, notification-disabled states, and
CPU/memory/battery behavior. Platform typography and compact chronometer formatting are not asserted
beyond displaying an advancing elapsed value. A process-absent midnight test confirms the surface
continues without an application wake and then reconciles to the new daily task total on resume;
the documented visible reset is accepted and no duplicate split is created.

Milestone 28 verification passed 208 JVM tests and 102 connected tests with zero failures, errors,
or skips. The owner also passed the focused permission denial/re-enable, channel, private/redacted
content, accumulated chronometer, direct tap, swipe dismissal, Stop cleanup, new-interval,
background/lock, Recents/process, reboot, force-stop, and short resource checks. Final private
content is **Client · Description**; the redacted supporting line is blank.

## 14. Optional Milestone E — data protection, app access, and privacy

The `ENC-*` tests are inactive and are not part of current production acceptance. Run them only
if the owner separately authorizes optional Milestone E. Until then,
the production build must not claim WorqOrder-managed at-rest encryption for Room or DataStore.

Milestone E also owns inactive opt-in app-lock tests for successful/failed/canceled authentication,
lockout, biometric/device-credential/approved-PIN policy, screen/background/process/reboot timing,
navigation/deep-link bypass, running-timer integrity, and no destructive recovery. Separately
approved screenshot and Recents-preview controls must protect content without making the app
inaccessible. None of these gates belongs to `0.2.0` or any other release scope unless the owner
later assigns Milestone E explicitly.

### ENC-01 Fresh encrypted storage

A fresh optional-Milestone-E install creates protected app-private Room and sensitive DataStore
storage anchored by non-exportable Android Keystore material. Seeded sensitive canaries do not
appear in database, WAL, SHM, DataStore, cache, backup artifacts, logs, or crash output at rest.

### ENC-02 Non-destructive plaintext upgrade

A populated pre-encryption installation upgrades with every client/task/interval/active-timer row,
ID, timestamp, relationship, selection hint, and setting intact. Reopen after process death and
reboot preserves behavior and the prior plaintext artifacts are not left recoverable.

### ENC-03 Interrupted migration and resource failures

Interruption at each durable migration phase plus low-storage/write failure is recoverable and
non-destructive. The app never clears, reseeds, or partially substitutes authoritative data and
does not run against an ambiguous mixed plaintext/encrypted state.

### ENC-04 Key and ciphertext failures

Missing, invalidated, wrong-version, or rotated keys and corrupted ciphertext fail closed with a
safe actionable state. Key loss never triggers destructive database creation. Nonce reuse and
wrong-purpose key use are rejected and tested.

### ENC-05 Backup and extraction boundary

Release manifest/data-extraction rules disable backup for protected app data or exclude it under
the approved encrypted-backup design. A backup cannot expose plaintext or restore ciphertext
without its usable key.

### ENC-06 Regression and performance

Room constraints/migrations, Start/Stop/midnight/recovery, selection/settings, CSV/XLSX/Google
snapshots, account authorization, and UI workflows pass through the encrypted storage adapters.
Startup, query, timer mutation, migration, file growth, memory, and battery stay within recorded
approved regressions on API 26 and the current target API.

### ENC-07 Explicit external boundary

The app states that user-selected CSV/XLSX files are unencrypted external documents and readable
Google Sheets cells rely on TLS/Google access controls rather than WorqOrder end-to-end
encryption. No biometric, device-credential, app PIN, or WorqOrder login prompt is introduced by
this optional milestone.

### ENC-08 Sensitive-data handling

Static/runtime inspection finds no protected task/client/export content in logs, exceptions,
analytics, notifications, clipboard, recent temporary files, or credentials. Plaintext export
snapshots exist only as needed in process memory and are not staged to app-private disk.

## 15. v0.3.0 acceptance tests

These cases supersede multi-interval, midnight-splitting, selection-rollover, and schema-4 row
cardinality expectations for current `0.3.0` behavior. Prior-version migration fixtures remain
mandatory precisely because released data may contain those older shapes.

Milestone 31 provides the current evidence for V3-TMR-01 through V3-TMR-03's selection and
singular-interval portions, and for V3-DATE-01's no-rollover behavior. Milestone 32 implements the
exact pinned-zone boundary close, foreground/process/boot recovery integration, notification
reconciliation, and automatic-Google ordering described by V3-DATE-02, V3-DATE-03, and
V3-EXPORT-03. Its deterministic clock/ZoneId, typed-pending, authorization-recovery, late-worker,
visible-date, and no-duplicate-export coverage passed the final Sol quality gate. The owner-run
project-local gate passed 235 JVM tests and 108 connected tests with no failures; debug lint and
both debug/release builds also passed.

Milestone 33 activates V3-EXPORT-01/V3-EXPORT-02 and the singular Edit Task presentation in
V3-TMR-03. Its owner-run final gate passed 235 JVM tests and 109 connected tests with zero failures,
errors, or skips; debug/release lint and debug, Android-test, and release assembly also passed.

### V3-DB-01 Non-destructive schema 4-to-5 migration

Given a populated schema-4 database containing clients, Consultants, zero-/one-/many-interval
tasks, archived directory rows, all task metadata, repeated dates/zones, and an active non-first
interval, migration preserves every user value and interval endpoint. Each post-migration task has
at most one interval, the total task and interval counts reflect one generated task for every
additional interval, foreign keys pass, and the active timer points to the generated owner of the
same open interval. Closing/reopening remains stable and schema 5 is exported and committed.

### V3-DB-02 Structural one-interval rule

Room rejects a second interval for one task even if a caller bypasses UI validation. It still
rejects a second global open interval, cascades the sole interval on task deletion, and never uses
a destructive migration fallback.

### V3-TMR-01 First Start uses an untimed task

Given an eligible selected task with no interval, Start creates one open interval on that task and
the singleton active timer in one transaction. No additional task is created and the display begins
at zero before advancing monotonically.

### V3-TMR-02 Repeated Start creates one task

Given today's selected task has one completed interval, Start atomically creates and selects one
new task with copied user metadata and lineage, creates its sole open interval, and begins its
display at zero. The source task/interval and every exported value remain unchanged. Two concurrent
Start calls create only one repetition and one active timer.

### V3-TMR-03 Singular manual interval

Edit Task labels the section **Interval**. An untimed task permits one valid manual interval. Once
present, only Edit/Delete actions are available; Add is absent or disabled with a clear reason. A
running interval remains non-editable. Deleting the sole completed interval returns the same task
to the untimed state, after which Start uses it rather than duplicating it.

### V3-DATE-01 No idle rollover

Given no timer is active and the effective date or device ZoneId changes, resume/recovery/startup
creates no task. A previous-day timing selection is cleared, today shows no selected task, and
historical date browsing remains read-only for live timing.

### V3-DATE-02 Midnight closes without continuation

Given a timer started before local midnight in its pinned ZoneId, evaluation at or after the first
next-day boundary closes that same interval exactly at the boundary, clears active state and stale
selection, and creates no next-day task or interval. Repeating normalization is a no-op. Spring
forward, fall back, unusual `atStartOfDay` rules, several missed days, resume, reboot, and process
recovery produce the same result.

### V3-DATE-03 Android may execute late without changing stored time

Given the process does not run at midnight, its notification may remain temporarily platform-
rendered, but the next legitimate execution stores the stop at the exact pinned-zone boundary—not
the later wake time—and cancels/reconciles the running surface. No exact alarm, foreground timer
service, app-owned wake lock, or tick persistence exists.

### V3-EXPORT-01 Canonical schema 5

CSV, XLSX, manual Google, and automatic Google expose exactly these 13 headers in order: Start
date, End date, Consultant, Client, Description, Expense, Work type, Billing Status, Mileage,
Start time, Stop time, Time spent, Billing minutes. Each task produces exactly one equivalent row.
Untimed tasks have blank Start/Stop, `00:00:00` Time spent, and `0` Billing minutes. No Interval
number or Interval duration exists.

### V3-EXPORT-02 Owned Google schema upgrade

Re-exporting a date whose WorqOrder-owned tab uses schema 2, 3, or 4 atomically replaces it with
schema 5, removes obsolete columns/rows, and produces no duplicates. Unknown/newer and unowned tabs
remain untouched. CSV/XLSX and Google values remain byte/logically equivalent as applicable.

### V3-EXPORT-03 Midnight precedes automatic export

Automatic Google work targets the captured preceding date and cannot export its open pre-midnight
interval. At the first allowed execution after the boundary, WorqOrder first performs the
idempotent exact-boundary close, then snapshots and exports the active canonical projection.
Foreground boundary handling and startup/resume reconciliation may execute an overdue target
without waiting for inexact WorkManager dispatch; WorkManager remains the durable background
fallback. Delays, offline access, authorization resolution, quota failure, process death, reboot,
and repeated worker delivery never lose the captured date, duplicate a row, or mutate Room beyond
the required timer close. Milestone 33 changes that shared projection to schema 5 without changing
this ordering policy.

If another recovery caller closes the timer before the Main screen's active-timer tick, the Main
screen still advances from yesterday to the new Today through an independent ZoneId-aware
date-boundary signal. It does not advance an intentionally browsed historical date and does not
poll, write Room, or depend on Google success.

### V3-REG-01 Unchanged product behavior

Client/Consultant management, metadata validation, Billing Minutes, date browsing, task editing and
deletion, themes, handed landscape, CSV/XLSX document flows, Google connection/re-export,
notifications, backup-disabled policy, GPLv3 distribution, and release signing retain their
accepted `0.2.0` behavior except where the cases above explicitly supersede it.

### V3-DB-04 Milestone 30C executable evidence

`Schema5MigrationCoreTest` is the populated schema-4 evidence fixture. It includes archived Client
and Consultant directory rows, nullable task metadata, a zero-interval task, a one-interval task,
a multi-interval task, and an open non-first interval. The test verifies that every interval and
metadata value survives deterministic task splitting, the active pointer follows the open interval,
foreign keys remain valid, the reopened file is stable, and schema 5 has no persisted `ordinal`.
`WorqOrderDatabaseTest` verifies the packaged version-5 schema, the task-to-interval uniqueness
guard, same-series lineage rows, and populated version 1/2-to-5 migration paths.

### V3-DB-05 Milestone 30 Sol quality gate

The generated version-5 Room schema uses identity hash
`01298237042e1a0bb2998ab5f1522ec8`, a non-unique lineage/date/zone index, a unique
`work_intervals.task_id` index, and no persisted `ordinal`. The Sol audit corrected stale
multi-interval test assumptions, added a true one-interval schema-4 fixture, and strengthened
fresh-schema, deterministic timestamp, rejected-write, and reopen assertions. The project-local
offline JVM/lint/debug/release gate passed, and the complete connected API-36 instrumentation
suite passed all 105 tests. The signed populated-install upgrade walkthrough remains a final
release gate in Milestone 35; schema migration behavior is currently verified by populated
instrumentation fixtures rather than a user-data-bearing release installation.
