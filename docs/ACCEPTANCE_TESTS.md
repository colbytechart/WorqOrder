# WorqOrder Acceptance Tests

## 1. Test policy

These scenarios define observable MVP behavior. Automated coverage may combine pure unit, coroutine, Room instrumentation, ViewModel, and Compose UI tests, but a requirement is not complete merely because one layer was tested. Use fake UTC clock, fake monotonic time, fake effective-zone provider, fake document destination, and fake Google Sheets gateway to make time/failure cases deterministic.

Unless stated otherwise, examples use `America/New_York`, a healthy database at the latest
implemented Room version, and no active timer. Migration scenarios name their source and target
versions explicitly. Duration comparisons use exact milliseconds even when the UI ticker renders
less frequently.

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

Given selected prior task metadata was edited before rollover, the new copy uses its current
client, short description, and hardware/software-purchases text, same series ID, new stable task
ID, new date, and zone used for assignment.

### DATE-07 Spring forward

Given a timer spans a spring-forward transition, elapsed duration follows instants (missing local hour is not counted), date segments fit 23-hour boundaries where applicable, and totals are exact.

### DATE-08 Fall back

Given a timer spans the repeated fall-back hour, elapsed duration counts both occurrences,
persisted instants and the interval editor preserve/identify the chosen occurrence, and date
segments fit 25-hour boundaries where applicable. The intentionally reduced external export
schema still emits task-zone `HH:mm` only; its duration remains the correct instant-based value.

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

Client Management is the first normal Settings item, followed by Appearance, Time Zone, and Export
Destination. Google Sheets Connection is visible only when Google Sheets is selected. System is
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

Header and each row use schema version 2's exact nine visible columns and canonical strings in the
documented order. Internal schema/snapshot metadata is not a visible column. A date with no tasks
has only a header; a zero-interval task has one row with blank interval number/start/stop/duration
and `00:00:00` task total.

### CSV-03 One row per interval

A task with three intervals produces exactly three rows with repeated task/client metadata,
including hardware/software-purchases text, and stable interval ordinals.

### CSV-04 Escaping/Unicode

Description and hardware/software-purchases fields containing comma, quote, CR, LF, emoji,
accented and non-Latin characters round-trip under UTF-8 and RFC-style quoting; quotes double and
records use CRLF.

### CSV-05 Durations/timestamps

Longer-than-23-hour task totals are not wrapped. Start/Stop are converted through the task's stored
ZoneId and exported only as 24-hour `HH:mm`; complete instants remain internal. Durations are
`HH:MM:SS`, truncate rather than round sub-second remainder, and are locale independent.

### CSV-06 Running-timer lockout

Given any globally active interval, the export action is disabled and labeled **Stop Timer to
Export**. Dispatching `MainEvent.Export` while running launches no CSV picker. After Stop, CSV
contains the completed interval's final Stop Local and duration.

### CSV-07 Picker cancellation

Canceling before destination URI produces no output and a non-error canceled state; Room and prior files remain unchanged.

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
neutral cancellation, retryable failure, and possible partial output.

### CSV-11 Shared snapshot boundary

`ExportSnapshotCoordinator` performs normalization/read/build once and returns the immutable
nine-column dataset. CSV serialization changes no field/order/value. XLSX/Google adapters
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
metadata is not visible. Re-export clears stale rows and right-sizes the grid to nine columns and
required rows so unused allocation does not unnecessarily consume the official 10-million-cell
spreadsheet limit.

### GS-11 Offline

Given no network, export reports retryable offline failure, does not claim success, retains useful error state, and leaves Room unchanged.

### GS-12 Authorization expiration

Given expired/revoked authorization, export prompts reauthorization and supports retry without losing spreadsheet metadata or changing Room.

### GS-13 User cancellation

Canceling account selection/consent does not claim sign-in, connection, or export success.

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
Play client, or Play App Signing configuration. Debug builds use the registered debug SHA-1; the
direct-release identity is added only in Milestone 17. The repository remains GPLv3.

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
`WorqOrder_2026-07-22` worksheet. It has the exact nine-column header at row 1 and rows identical
in content/order to CSV and Google Sheets for the same captured snapshot. Exporting Jul 23 creates
another independent workbook containing only `WorqOrder_2026-07-23`. An empty date has only the
header; a zero-interval task has one blank-interval row.

### XLSX-03 Cell safety and fidelity

All nine canonical values are literal text cells. Formula-prefixed text is not executable.
Unicode, commas, quotes, CR/LF, task-zone `HH:mm`, and accumulated `HH:MM:SS` survive an
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

### XLSX-07 Compatibility and efficiency

Representative generated workbooks parse with an independent reader and open in current Excel and
LibreOffice. The minimal package contains one visible sheet and no formulas, macros, external
links, credentials, hidden content, or unnecessary metadata. Large-snapshot tests stay within
recorded memory/time limits on minimum and target API devices. Dependency inspection proves the
focused writer adds no XLSX library and no Apache POI.

## 11. Local data protection and encryption

### ENC-01 Fresh encrypted storage

A fresh production install creates protected app-private Room and sensitive DataStore storage
anchored by non-exportable Android Keystore material. Seeded sensitive canaries do not appear in
database, WAL, SHM, DataStore, cache, backup artifacts, logs, or crash output at rest.

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
wrong-purpose key use are rejected/tested.

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
this required milestone.

### ENC-08 Sensitive-data handling

Static/runtime inspection finds no protected task/client/export content in logs, exceptions,
analytics, notifications, clipboard, recent temporary files, or credentials. Plaintext export
snapshots exist only as needed in process memory and are not staged to app-private disk.

## 12. Room and build quality

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

## 13. Accessibility and resilience

### A11Y-01 Semantics/touch

Settings, date arrows/calendar, Start/Stop, Add, export, overflow, and client actions have labels/roles, at least 48 dp targets, logical traversal, and keyboard/switch accessibility.

### A11Y-02 Font/contrast

At supported large font scales and narrow screens, timer/date/actions remain understandable, content scrolls rather than clips critical controls, and Light/Dark contrast meets Material accessibility guidance.

### A11Y-03 State communication

Selected, running, disabled, validation, success, and failure are conveyed through semantics/text/icons in addition to color; transient messages needed to recover are not snackbar-only.

## 14. Optional post-project application-access security

These tests are inactive unless the owner explicitly authorizes optional Milestone 18 after the
required project is complete.

### ACCESS-01 Opt-in and no remote account

App locking is disabled by default and can be enabled/disabled only through an authenticated local
flow. It adds no WorqOrder cloud account, backend, remote password store, or Google-account
requirement.

### ACCESS-02 Prompt and recovery states

Successful, failed, canceled, locked-out, biometric-enrollment-changed, and device-credential
fallback paths are deterministic, accessible, and do not expose protected content or silently
delete/replace encryption keys or Room data.

### ACCESS-03 Lifecycle locking

Configured lock-on-launch/background-timeout/screen-off behavior survives rotation, navigation,
process death, and reboot. Timing continues authoritatively while UI access is locked; unlocking
does not stop, duplicate, or lose an interval or midnight continuation.

### ACCESS-04 Bypass and privacy review

Deep links, exported components, restored navigation state, notifications, screenshots, and
recent-app previews cannot reveal or bypass protected screens under the approved policy.

### ACCESS-05 Full regression

Room/encryption migrations, key failure handling, timer/date/recovery, all three exports, Google
authorization, accessibility, and debug/release suites pass with app locking both disabled and
enabled.

### OPTIONAL-EXPORT-01 XLSX mode choice

If separately authorized, users can choose the production one-off XLSX mode or a future connected
persistent-workbook mode. Both consume the same nine-column snapshot and produce equivalent
date-tab content; switching/migration, create-document cancellation, missing persistent URI, and
unrelated tabs are safe.

### OPTIONAL-EXPORT-02 Midnight automation eligibility

Automatic local-midnight export can be enabled only for Google Sheets or a valid persistent XLSX
workbook. It never runs for CSV or one-off XLSX and disabling it cancels future scheduled work.

### OPTIONAL-EXPORT-03 Midnight correctness and idempotence

Ordinary, spring-forward, fall-back, manual/device-zone-change, Doze, reboot, missed-run,
offline/auth-expired, invalid-XLSX-URI, and concurrent manual-export tests prove that each completed
date converges to one duplicate-free tab without changing Room or using unbounded retry.

### OPTIONAL-EXPORT-04 Background safety

Automation uses approved Android background work rather than a foreground service waiting for
midnight. A background run cannot launch a destination picker or silently choose storage; it
records a safe pending failure until the user can repair the destination. No notification/log
exposes task content.

### OPTIONAL-UI-01 Interval clock-time display

If separately authorized, routine Task interval cards display completed Start and Stop values as
task-zone `HH:mm` without seconds. Persisted UTC instants, stored task ZoneId, duration and
date-boundary behavior, edit precision, and explicit fall-back occurrence disambiguation remain
unchanged.
