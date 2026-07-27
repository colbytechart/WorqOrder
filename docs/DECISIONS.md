# WorqOrder Decisions

Status terms: **Accepted** is a fixed product/architecture decision; **Proposed** needs owner confirmation before its affected implementation; **Deferred** is intentionally out of MVP.

## Accepted decisions

### D-001 — Native stable Android stack

Use Kotlin-only native Android, Jetpack Compose/Material 3, Compose Navigation, ViewModel, coroutines/Flow, Room, Preferences DataStore, `java.time`, Gradle Kotlin DSL/version catalog, KSP where supported, and minimum API 26. Use stable dependency/tool releases only. The scaffold has proven the installed API 36.1 compile platform with target API 36.

Consequences: no Java application code, XML layouts, Flutter, React Native, web wrapper, or preview dependency without a separately documented/approved exception.

### D-002 — Room is authoritative

Room is the source of truth for clients, daily tasks, intervals, and the active-timer pointer. It is created once when needed and reused. DataStore contains settings/hints only.

Consequences: exports never drive local state, production startup never clears/reseeds the database, and no durability claim extends through uninstall/clear storage.

### D-003 — No cross-device synchronization or backend

WorqOrder is local/offline-first. There is no task import, multi-device merge, Firebase, custom backend, or account requirement for core use.

Consequences: Google Sheets is an export copy, and edits made there never update Room.

### D-004 — Exactly three one-way export destinations

CSV, XLSX, and Google Sheets are the only destinations. All three project the same
schema-versioned logical snapshot from Room and never import or synchronize data. XLSX was added
by the owner after the original CSV/Google-only decision.

Consequences: XLSX uses a focused implementation after the Google Sheets milestones. Apache POI
and another broad Excel stack remain prohibited without a separately documented owner decision.

### D-005 — Displayed date is export date

The main date selector filters daily tasks and defines the export date. Export labels identify both the displayed date and destination; “current day” is never implicit.

### D-006 — Live timing is limited to today

Historical/future tasks can be created and manually edited, but Start requires a selected task belonging to today in the effective application zone.

### D-007 — Clients are archived

Removing a client means archive/deactivate. Archived rows remain to satisfy historical relationships and exports and can be restored. Active canonical names are unique.

### D-008 — Daily tasks and normalized intervals

Each daily task has a stable task ID and stable cross-date series ID. Work intervals are normalized child rows with start/nullable stop; there are no repeated `start1/stop1` columns or stored total counters.

### D-009 — One global active timer

At most one interval is open globally. A singleton active-timer record plus Room transactions/internal write APIs enforce the invariant. UI state is not sufficient enforcement.

### D-010 — Timestamp and zone model

Persist absolute boundaries as UTC epoch milliseconds, work dates as epoch days, and the geographical ZoneId used to assign/split the date. Historical dates are not recomputed after settings changes.

### D-011 — Derived timer and no foreground service

Do not persist a changing stopwatch or write on display ticks. Use wall-clock UTC instants for persisted history, monotonic elapsed time for a live in-process display, and wall reconstruction after process death. The initial MVP has no foreground service merely to keep a counter running.

### D-012 — Midnight normalization

Split at every local-date boundary using `ZoneId` rules, including missed/multiple boundaries. The app may normalize lazily on foreground/resume/stop; the active segment ultimately belongs to the current session-local date.

### D-013 — Selection rollover

Persist preferred series/concrete-task hints. On actual configured-date rollover, find/create the same-series daily task with current metadata under `(seriesId, workDate, assignmentZoneId)` uniqueness and select it. Browsing a date alone does not create a copy. Including the zone refines the suggested series/date key so a future task created in another zone is not silently repurposed or made inconsistent with interval boundaries.

### D-014 — One row per interval

All three exports use one row per interval with repeated task metadata. A zero-interval task emits
one blank-interval row. Schema version 1 and exact current columns are defined in
`EXPORT_SPEC.md`; a later owner-directed field reduction must update the schema and all three
destinations together.

### D-015 — One connected spreadsheet and one tab per date

Connect exactly one existing Google spreadsheet. Never create a new spreadsheet on each export. Each exported date owns `WorqOrder_YYYY-MM-DD` inside that document with a predictable marker/schema.

### D-016 — Google re-export is idempotent replacement

If a date tab is absent, create it. If its exact marker/schema/date are valid, replace all application-owned content with the current authoritative sorted snapshot. If the same name lacks the marker or is incompatible, do not overwrite.

Rationale: this later, precise rule resolves the earlier append-only wording and correctly reflects local edits/deletions as well as additions. Re-export does not create duplicate rows.

### D-017 — Google is a gateway, not DI/core storage

Use a fakeable Google gateway, current stable Google-supported Android identity/authorization APIs, Sheets API v4, and typed retryable failures. No Firebase/service account/API-key authorization/raw token storage.

### D-018 — Per-file Google Sheets authorization for pasted IDs

Request only `https://www.googleapis.com/auth/drive.file` for Google data access. After the user
pastes a spreadsheet URL/ID, launch the official Android Google Picker authorization flow filtered
to that exact ID and the Google Sheets MIME type. A nonempty `picked_file_ids` result must contain
exactly that ID. Because Disconnect deliberately retains the existing per-file grant, Google may
return no Picker IDs when that grant is reused during reconnect; in that case only an exact-ID
Drive capability check plus Sheets metadata validation may establish the connection. A nonempty
mismatch is always rejected.

This supersedes the provisional `spreadsheets`-scope plan after the Milestone 9 official-doc
review found Google's current Android Picker resource-parameter flow. The chosen scope is
non-sensitive and per-file; it preserves pasted-ID input at the cost of one explicit Google Picker
confirmation. The sensitive all-spreadsheets scope is not an implementation fallback because it
conflicts with the no-cost/no-domain policy. Do not request Drive-wide, profile, or email data
scopes for Sheets authorization. If the selected flow becomes unavailable, stop for a new owner
decision and preserve CSV.

### D-019 — Restrained architecture/manual DI

Use Compose → ViewModels → repositories/domain services → storage/gateways and one small application container. Do not create trivial use-case classes or adopt a DI framework without a compelling documented need.

### D-020 — Non-destructive schema evolution

Export/commit Room schemas from version 1, write explicit migrations and instrumentation tests, and never enable destructive fallback in release builds.

### D-021 — Validation limits

Client names are maximum 100 characters and canonicalized with trim, repeated-whitespace collapse,
and locale-independent case normalization. A task's required short description and optional
hardware/software-purchases text are each maximum 400 characters. Both are trimmed; the short
description cannot be blank, while purchases text may be blank.

### D-022 — Client rename semantics

A client is a retained referenced entity, not a name snapshot on each task. Renaming it changes the current name shown/exported for historical tasks; task/interval records themselves are not changed. Archiving retains the name. If immutable historical naming is later required, that is a schema/product change.

### D-023 — Exporting a running interval

Export remains available while timing. Normalize date boundaries, capture one internal export
instant, emit blank Stop Local, and calculate interval/task duration to that snapshot without
stopping the timer. Running state remains internal and is not a visible schema-version-2 column.

### D-024 — Active session pins its zone

Capture the effective geographical zone at Start. Settings are locked while active, and even an external device-zone change does not alter this session's splitting rules. Adopt a new device zone after Stop.

### D-025 — Concept images are direction, not an alternate contract

Retain the dark/high-contrast vertical visual direction where Material 3/accessibility allow it. Written behavior wins: WorqOrder title, Settings icon, milliseconds, labeled date, explicit task metadata/state, Create/Cancel, unambiguous export label, geographical ZoneId, and explicit client actions.

### D-026 — Initial theme

Default to System on first launch so WorqOrder follows the device appearance. Light and Dark remain equally supported explicit radio choices and all three settings apply immediately without recreating navigation or timer state.

### D-027 — Stable ordinals and row order

Interval ordinals are stable and not renumbered after deletion/edit. UI is chronological; export rows use deterministic task creation/ID and interval time/ordinal ordering as specified. This prevents identities changing accidentally.

### D-028 — Spreadsheet-owned date tab

In a correctly marked WorqOrder date tab, row 1 is the exact shared header and row 2 onward is the
application-owned table that can be fully replaced. Marker/schema/date ownership metadata is
non-visible: sheet-scoped Google developer metadata or reviewed XLSX package metadata. Other tabs
and same-named unmarked conflicts are never changed. Use raw/literal string values.

## Additional accepted decisions

### D-029 — Android application identity

Use `worq.order` as both the Android `applicationId` and Kotlin namespace. Treat it as durable from the first scaffolding milestone and use the exact value for Google OAuth client registration.

### D-030 — CSV save flow

Use the standard `ACTION_CREATE_DOCUMENT` flow for every CSV export, with the suggested filename and an initial-location hint when available. Request no storage permission. The system picker/user controls the final location, so WorqOrder does not claim it can force or automatically create `Downloads/WorqOrder`. See `EXPORT_SPEC.md`.

### D-031 — Milestone 1 is scaffold-only

The owner explicitly narrowed Milestone 1 to the initial buildable Android scaffold: project/toolchain configuration, package structure, dependency preparation, navigation placeholders, static main-screen regions, Light/Dark theme foundations, manual application-container boundary, and smoke-test foundations.

Consequences: Milestone 1 does not create the production Room database, entities, DAOs, migrations, typed DataStore preferences, repositories, timer rules, task behavior, or exporters. Those remain later milestones even though an earlier implementation-plan draft grouped persistence into Milestone 1.

### D-032 — Proven initial toolchain and dependency matrix

The scaffold uses Gradle 9.4.1, Android Gradle Plugin 9.2.1, Kotlin/Compose compiler plugin 2.3.10, and KSP 2.3.8 with Android Studio's Embedded JDK/JBR 21. It compiles against installed API 36.1, targets API 36, and has minimum API 26.

AndroidX Core 1.18.0 and Lifecycle 2.10.0 are the newest selected stable lines compatible with compile SDK 36.1 in this environment. Core 1.19.0 and Lifecycle 2.11.0 require compile SDK 37 and are therefore intentionally not selected. All remaining initial versions are recorded in `IMPLEMENTATION_PLAN.md` and the Gradle version catalog.

### D-033 — Repository license

GNU GPL version 3 is the intended repository license. The existing `LICENSE` file is retained.

### D-034 — Room version-1 active-slot enforcement

Room version 1 contains `clients`, `daily_tasks`, `work_intervals`, and `active_timer`. A completed interval has nullable `active_slot = NULL`; the sole open interval has `active_slot = 1` under a unique index. This is the selected schema-native replacement for a custom partial index or trigger.

The fixed-key active-timer row records both interval and task IDs. A composite foreign key to unique `(work_intervals.id, work_intervals.task_id)` proves that they match and restricts deletion while running. Room transactions atomically create the interval/pointer pair and atomically close/release/clear it. The database file is `worqorder.db`, version 1 schema export is enabled, and production construction has no destructive fallback.

Consequences: the one-open-interval cardinality rule is visible in Room’s exported schema and directly testable. Repository APIs do not expose a general open-interval insert. Timer eligibility, midnight splitting, overlap/date validation, recovery normalization, and the same-process mutex remain later domain work.

### D-035 — Owner-directed Milestone 2 is Room-only

The detailed Milestone 2 prompt limits implementation to Room entities, DAOs, local models, database transactions, and repository foundations. It supersedes the older implementation-plan draft that also placed typed Preferences DataStore models/defaults and formatting helpers in Milestone 2.

Consequences: the existing stable DataStore dependency remains prepared but unused. Typed settings, selection hints, and their persistence tests move to the settings/selection milestones. This scope correction does not change product behavior or permit later UI/timer/export work.

### D-036 — Extended production delivery sequence

The owner replaced the earlier broad sequence with the numbered plan recorded in
`IMPLEMENTATION_PLAN.md`: scaffold; Room persistence; timer/date domain; Main/live presentation;
clients; task/interval CRUD; persistent settings; CSV; current Google integration planning;
Google connection settings; Sheets export; focused XLSX; lifecycle hardening; local data
protection/encryption; accessibility/usability; full audit; and release/handoff. A final
user-presence/application-access milestone exists only as optional post-project work.

Consequences: feature ownership and deferred work follow that sequence. In particular, Milestone 3
contains no final UI, Google, or CSV behavior; Google documentation/dependency discovery is
Milestone 9.

### D-037 — Timing selection distinguishes rollover from historical browsing

Milestone 3 persists task ID, series ID, the effective local date on which the timing selection was
made, and its effective ZoneId. A selection carried through a real date change is reconciled to the
exact `(series ID, today, effective ZoneId)` copy. A historical/future task intentionally selected
while browsing today remains viewable but is not silently rolled forward and cannot Start.

This resolves the interaction between automatic daily carryover and the explicit rule that
historical tasks are inactive. Start itself requires an exact today/date/zone task; lifecycle/Main
integration will call reconciliation for real date rollover.

### D-038 — Milestone 3 timer and clock-anomaly contracts

Start and Stop return typed expected outcomes. One application-scoped mutex reduces same-process
races, while the Room singleton, unique active slot, and transactions remain authoritative.
Midnight plans are calculated in Kotlin with the Start-pinned ZoneId and applied as one transaction
that can create/find daily copies, close segments, insert continuations, and retarget/clear the
active pointer.

The visible live contribution uses Android elapsed realtime and is never persisted. Recovery
clamps a negative provisional wall contribution to zero and surfaces an anomaly. Stop returns
`ClockChanged` without writing when `stop <= start` or when a live wall/monotonic comparison differs
by more than the initial two-minute diagnostic tolerance.

Repeated timing always creates separate intervals. The clarified example is authoritative:
Task 1 has 9:00–10:00 AM and 1:00–2:00 PM intervals (two total hours); Task 2 has a
10:30–11:00 AM interval (thirty minutes). Reselecting Task 1 displays its existing one-hour total
before its second Start.

### D-039 — Client changes while timing

Renaming or archiving a client is allowed while a related task is running. Both operations retain
the same client row and stable client ID, so the daily task foreign key, active interval, and timer
identity remain intact. Renaming changes the referenced display name; archiving only removes the
client from future new-task selectors while historical and running-task resolution continues.

Consequences: client management does not need to stop or rewrite a timer. Restoring remains subject
to active canonical-name uniqueness, and no client-management action hard-deletes a client.

### D-040 — Hardware and software purchases task metadata

Each daily task has a second free-form text field labeled **Hardware / Software Purchases** in
addition to its required short description. The purchases field is optional and has no structured
currency, quantity, attachment, or inventory behavior. It is stored on the daily task, editable
through the same task create/edit workflow, copied with the other current metadata during
selection rollover and midnight continuation, and included in all three export destinations under
the current schema.

Consequences: Milestone 6 owns the form/repository/domain integration and an explicit Room
version-1-to-version-2 migration that gives existing tasks an empty purchases value. Milestones 8,
11, and 12 use the shared logical export schema. Milestone 6 implemented the field, migration,
daily rollover/midnight copying, and create/edit validation.

### D-041 — Manual interval entry precision and DST choice

Milestone 6 includes manual interval addition as well as editing and deletion. The Material time
picker accepts hour-and-minute input on the task's fixed stored work date. Confirming a changed
endpoint sets that endpoint's seconds and milliseconds to zero; an untouched endpoint retains its
persisted instant. Interval display continues to show seconds and the resolved UTC offset.

Spring-forward gap times are rejected. A fall-back time with two valid offsets requires the user to
choose the earlier or later occurrence explicitly; editing an existing ambiguous boundary
preselects the occurrence represented by its persisted instant.

Consequences: add and edit share the same domain validator and transactional overlap/running-task
guards. Manual intervals remain millisecond-precise in storage even though new picker input is
minute-granular.

### D-042 — Milestone 7 typed settings and effective-zone readiness

Preferences DataStore stores `theme_mode`, `time_zone_mode`, `manual_zone_id`, and
`default_export_destination` through one typed `SettingsRepository`. First-launch and unknown-value
fallbacks are System theme, device-zone mode, and CSV; manual selection accepts only geographical
IANA region namespaces and preserves the selected canonical ID.

Time-zone writes share the timer-operation mutex and recheck Room's singleton active timer before
committing. The effective-zone provider observes both DataStore and Android time-zone broadcasts.
An active timer's captured boundary zone overrides both until Stop, after which the current device
or manual zone becomes effective. Domain operations that can create a daily copy or interval wait
for the first DataStore-backed effective-zone value, preventing an incorrect startup rollover.

Selection rollover now distinguishes context explicitly: a source task eligible when selected is
rolled when either effective date or zone changes, including a same-date zone change; a task
intentionally selected outside its own stored date/zone context remains view-only. No historical
task row is rewritten. Main normally starts on today, follows a changed today only when it was
already showing today, and preserves an intentionally browsed date.

The root Compose theme observes the typed setting without Activity recreation. The Main export
button names the displayed date and stored destination; CSV remains disabled until Milestone 8,
while Google Sheets without a connection routes to an explanatory Settings state and performs no
network call. Google connection state and APIs remain Milestones 10 and 11.

### D-043 — Settings appearance and information hierarchy

System, Light, and Dark are always-enabled radio choices. System is the first-launch and
unknown-value fallback; while selected, it follows the device configuration without disabling the
two explicit overrides. Client Management is the first normal Settings item, followed by
Appearance, Time Zone, and Export Destination. The Google Sheets Connection section is rendered
only while Google Sheets is the selected destination. Section and screen headers use title
capitalization; body, field, and action copy retains its existing sentence-style wording.

### D-044 — Milestone 8 CSV snapshot and document delivery

CSV now uses the exact nine-column schema-version 2 contract in `EXPORT_SPEC.md`. Under the shared
timer-operation lock, one UTC instant is captured, the running timer is normalized through that
instant, and a date-filtered Room transaction reads retained clients, daily tasks, and ordered
intervals. The entire UTF-8/CRLF CSV payload is serialized before the standard
`CreateDocument("text/csv")` picker opens.

`ExportSnapshotCoordinator` owns capture/normalization/read/build and produces the immutable
destination-neutral nine-column dataset. `CsvExportCoordinator` only serializes/delivers it; future
XLSX and Google adapters consume the same object. Running export follows D-023 and uses the one
captured instant without stopping the timer. Picker cancellation is neutral. Output failure
attempts to delete only the returned provider URI and warns when a partial document may remain.
CSV delivery and repeat attempts never mark, delete, or rewrite Room task data.

### D-045 — Edit-task interval display and successful Save behavior

Routine interval cards show local start/stop clock times without appending their UTC offsets. DST
overlap occurrence controls remain explicit inside the interval editor where the distinction is
needed. A successful **Save task changes** operation persists client, description, and purchases
metadata and immediately navigates back to Main; validation or persistence failure remains on the
editor with an actionable state.

### D-046 — Google identity and authorization stack

Authentication and Google-data authorization are separate. Use stable AndroidX Credential Manager
`1.6.0` plus Google ID `1.2.0` for account choice, and Google Identity Services
`AuthorizationClient` from `play-services-auth:21.6.0` for `drive.file` consent, Picker, token
acquisition, token clearing, and grant revocation. `kotlinx-coroutines-play-services:1.10.2` may
bridge Google Task results.

WorqOrder has no backend, so it cannot treat Credential Manager's Google ID token as a verified
application identity. Discard the raw token after the account-choice result; any locally retained
display hint is non-secret and non-authoritative. Request a fresh short-lived access token for each
explicit connection/validation/export operation, hold it only in memory, and never request or
manually persist a refresh token.

Use small, fakeable HTTPS/JSON Drive v3 and Sheets v4 gateways rather than the generated Google API
Java client, whose documented Android support remains Beta. No Firebase, Google services Gradle
plugin, API-key authorization, backend, or service account is introduced.

### D-047 — Google connection, disconnect, and sign-out semantics

Connecting stores only non-secret spreadsheet ID, title, validation status/time, and an optional
account display hint in DataStore. It does not store access/refresh tokens and does not write a test
cell. An initial/nonempty Picker result must exactly match the parsed input. A reconnect may reuse
the retained grant and return no Picker IDs; exact-ID Drive edit/content-modification capability
and a successful narrow Sheets metadata read remain mandatory before anything is saved.

**Disconnect** clears the local spreadsheet association but leaves the selected Google account and
grant unchanged. **Sign out** immediately makes Google export unavailable, attempts to revoke the
app's Google data grant, clears Credential Manager state and in-memory credentials, and clears the
account hint and all connected-spreadsheet metadata. A later sign-in must explicitly connect,
Picker-grant, and revalidate a spreadsheet before export. A failed remote revocation is reported
instead of falsely claiming complete revocation; local account and spreadsheet metadata are still
cleared so Google export remains unavailable.

While a connection exists, Settings shows its title/ID and Disconnect/Sign Out actions but hides
the spreadsheet URL/ID and Validate and Connect controls. The entire connection card is hidden
unless Google Sheets is the selected Export Destination. Selecting Google Sheets automatically
scrolls the Settings list to the newly revealed connection card.

Milestone 10 implements this decision with DataStore keys for the account ID/display
hint, spreadsheet ID/title, validation timestamp, current-account validation flag, and
connection-state version. Unknown or incomplete combinations fail closed as not
connected. No access, refresh, or ID token is stored.

### D-048 — Permanent free/open-source and no-paid-Google policy

WorqOrder remains free and open source under GPLv3. Supported workflows must not require a Google
Workspace subscription, Cloud/Workspace organization, custom domain, billing account, paid API
tier, quota purchase, or another paid service. Google export uses only standard no-additional-cost
quota and explicit foreground user actions. Do not enable billing or request increased paid quota;
when standard quota is unavailable, return a useful failure and retain CSV.

The Google Auth project uses an individual developer-controlled account and External audience:
Testing during development, then In Production for small ongoing personal/open-source use. Do not
seek verified name/logo branding when it would introduce a domain requirement. This intentionally
accepts less-polished or unverified consent presentation and a small-user policy boundary.

Google Play distribution and Play App Signing are out of scope. Milestones 10 and 11 use the debug
signing SHA-1 and debug Android OAuth client. A permanent direct-release key/fingerprint and matching
Android OAuth client are created only through the secure Milestone 17 release process. If Google
changes the free API, quota, OAuth, or Picker policy, do not silently add cost, broader access, or a
backend; stop for a new owner decision.

### D-049 — Focused XLSX export is production scope

Add a complete XLSX export milestone directly after Google Sheets export. Production connects
exactly one persistent XLSX workbook through a user-granted SAF URI. It uses the displayed date
and the same immutable Room snapshot, nine columns, row order, one-row-per-interval rule, running
snapshot policy, and no-local-mutation behavior as CSV and Google Sheets.

Each exported date owns one visible `WorqOrder_YYYY-MM-DD` worksheet. A missing date adds a tab;
re-export replaces the marked tab's entire table and removes obsolete rows, so duplicate rows are
not possible. Preserve unrelated tabs and reject same-named unmarked conflicts. User text and all
nine canonical values are literal cells; no macros, external links, hidden worksheets,
credentials, or app-private keys are written.

If the connected document is deleted, moved, revoked, malformed, or unwritable, invalidate it
without crashing and launch the standard create-document flow. After the user selects a location,
create a fresh `worqorder.xlsx` containing the requested date. Android cannot silently select an
arbitrary replacement location; cancellation leaves XLSX disconnected and Room unchanged.

The implementation must be bounded and Android-compatible. A stable, maintained,
GPLv3-compatible lightweight writer may be proposed through the dependency gate, but Apache POI
remains disallowed absent a new explicit owner decision.

### D-050 — Transparent local data encryption is required production scope

After lifecycle hardening, add a dedicated Data Protection and Encryption Hardening milestone.
Protect sensitive app-private Room and DataStore content at rest with versioned,
Keystore-backed, authenticated encryption. Include database auxiliary files, backup/data
extraction, caches, temporary artifacts, logs, crash output, non-destructive plaintext migration,
key rotation, key loss/invalidation, corruption, performance, and full regression testing.

This protection is invisible during normal use: no WorqOrder login, account, biometric prompt,
device-credential prompt, or app PIN is required. Never silently delete/reseed Room or replace
authoritative data after a key or migration failure. Plaintext necessarily exists transiently in
memory while the unlocked app uses it.

User-selected CSV and XLSX documents are unencrypted external files. Readable Google Sheets cells
use platform TLS and Google access controls but are not end-to-end encrypted by WorqOrder. The app
must communicate these boundaries rather than imply that local encryption follows exported data.

### D-051 — Post-project options require separate authorization

Biometric, device-credential, local-PIN, or comparable user-presence/app-lock behavior belongs
only to optional Milestone 18 after the required project through release/handoff is fully
complete. Do not begin it without a new explicit owner instruction, and remind the owner about it
only after Milestone 17 is finished.

If authorized, keep the feature local/offline and opt-in. It must not introduce a WorqOrder cloud
account, custom backend, password server, Google-account requirement, or silent key/data loss.
Its own gate covers prompt/lockout/cancellation, background timeout, screen/reboot/process events,
deep-link/navigation bypass, sensitive previews, accessibility, running-timer integrity,
encryption interactions, and full security/regression testing.

The same optional milestone also revisits two export enhancements that are intentionally excluded
from production scope until separately authorized:

1. let the user choose between the production persistent XLSX workbook and a one-off XLSX file for
   each export; and
2. optionally export the just-completed day automatically at its local midnight when the default
   destination is Google Sheets or a valid persistent XLSX workbook.

Automatic midnight export must define Android background-execution behavior, offline/auth/URI
failure and retry policy, zone changes, reboot/catch-up, duplicate prevention, user controls,
battery impact, and privacy/security tests. It must never apply to CSV, create a foreground
service merely to wait for midnight, or silently replace a missing XLSX document without
user-mediated destination selection.

### D-052 — One reduced canonical export dataset

All destinations consume one immutable schema-version-2 `ExportSnapshot` built after the export
action from one Room transaction and one internal snapshot instant. The visible columns are
exactly Work Date, Client Name, Description, Hardware / Software Purchases, Interval Number, Start
Local, Stop Local, Interval Duration Formatted, and Task Total Duration Formatted.

Start/Stop are task-zone `HH:mm`; durations are accumulated `HH:MM:SS` with sub-second remainder
truncated. All nine values are canonical strings, so CSV, XLSX, and Google Sheets expose equivalent
headers, order, values, and row positions. Destination adapters only escape/package/transport the
snapshot. Every previously exportable ID, UTC instant, ZoneId, millisecond duration, timestamp,
state, and edit flag remains stored internally and is merely excluded from external projection.

### D-053 — One marked worksheet per date without duplicates

Persistent XLSX and the single connected Google spreadsheet use exactly one marked
`WorqOrder_YYYY-MM-DD` worksheet per exported date. Row 1 is the shared nine-column header and row
2 onward contains the canonical rows. Re-export replaces the entire owned table and removes stale
rows; it never appends or merges, so unchanged or repeated exports create no duplicates.

Google uses sheet-scoped developer metadata for marker/schema/date and right-sizes each sheet to
nine columns and required rows to respect the spreadsheet's 10-million-cell limit. XLSX uses
reviewed non-visible workbook/package metadata. A same-named tab without the marker is a conflict,
not permission to overwrite.

Before the first WorqOrder export to either persistent destination, a confirmed completely blank
spreadsheet/workbook reuses and renames its original first worksheet instead of leaving an empty
default tab. If any user content exists, or blankness cannot be established safely, all existing
tabs remain untouched and WorqOrder adds the requested date tab.

### D-054 — Atomic Google Sheets batch with invisible exact ownership keys

Milestone 11 reads spreadsheet structure and developer metadata nested on the sheet that owns it
immediately before each write, then uses one `spreadsheets.batchUpdate`. A first export also
inspects workbook content when needed: it renames/right-sizes the original first sheet only when
the whole spreadsheet is confirmed blank; otherwise a new date batch adds the exact-size sheet.
The batch creates the three `PROJECT`-visible metadata entries
`worqorder_export_marker=WORQORDER_EXPORT`, `worqorder_export_schema=2`, and
`worqorder_export_work_date=YYYY-MM-DD`, and writes the canonical table. An existing owned date
batch resizes then completely replaces that table. Success requires a 2xx batch response that
confirms the connected spreadsheet ID.

Cells use `UpdateCellsRequest.userEnteredValue.stringValue`, not destination-specific value
interpretation, so all canonical strings—including formula-prefixed user text—remain literal. No
automatic network retry occurs. A mutation response interrupted after transmission is ambiguous,
never success; explicit retry is safe because replacement is idempotent. Google export uses the
same running-interval one-instant snapshot policy as CSV and does not require Stop.

## Deferred decisions

- A secondary one-time export destination chooser; omit unless usability testing shows need.
- Restored-client UI placement; restoration capability is planned, but it may be an Archived subsection or separate route.
- Multiple connected spreadsheets, background automatic export, imports, synchronization, and a
  foreground timer service remain outside production scope.
- One-off XLSX mode and opt-in automatic local-midnight export to Google/persistent XLSX are
  deferred to optional Milestone 18 under D-051.
- App-access login/biometric/device-credential/PIN gating remains optional post-project scope
  governed by D-051.

## Implementation inputs still needed

- Developer-controlled personal Google Cloud project, External test audience, debug SHA-1/debug
  Android OAuth client, Web OAuth client ID, and disposable test spreadsheets before Milestone 10
  integration testing.
- Permanent direct-release key/fingerprint and release Android OAuth client are deferred to
  Milestone 17. No Google Play signing input is required.
