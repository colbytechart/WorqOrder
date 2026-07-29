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

Do not persist a changing stopwatch or write on display ticks. Persist absolute UTC boundaries,
use monotonic elapsed time for live in-process display and the projected Stop endpoint, and use
wall reconstruction after process death. The initial MVP has no foreground service merely to keep
a counter running.

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

The initial export design allowed a one-instant snapshot while timing. Manual Milestone 13 device
testing showed that the resulting blank Stop Local was not useful or reliably understood as a
completed record. D-056 supersedes this behavior: the user must Stop before exporting.

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
non-visible sheet-scoped Google developer metadata. Other tabs and same-named unmarked conflicts
are never changed. One-off XLSX contains only its newly generated date tab and needs no ownership
metadata. Use raw/literal string values.

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
Google connection settings; Sheets export; focused XLSX; lifecycle hardening;
accessibility/usability; full audit; and release/handoff. User-presence/application-access work and
at-rest encryption exist only as separately authorized optional post-project milestones.

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
clamps a negative provisional wall contribution to zero and surfaces an anomaly. D-057 supersedes
the initial two-minute live wall/monotonic rejection policy: a valid live anchor now projects the
UTC Stop endpoint directly, while an untrustworthy negative recovery still returns `ClockChanged`.

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
XLSX and Google adapters consume the same object. The later D-056 UI policy requires Stop before
this workflow is dispatched. Picker cancellation is neutral. Output failure
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
Testing during development, then In Production for unrestricted external-account eligibility. Do not
seek verified name/logo branding when it would introduce a domain requirement. This intentionally
accepts less-polished consent presentation.

Google Play distribution and Play App Signing are out of scope. Milestones 10 and 11 use the debug
signing SHA-1 and debug Android OAuth client. A permanent direct-release key/fingerprint and matching
Android OAuth client are created only through the secure Milestone 17 release process. If Google
changes the free API, quota, OAuth, or Picker policy, do not silently add cost, broader access, or a
backend; stop for a new owner decision.

### D-048a — Production audience requires no per-user owner intervention

The official Google Auth Platform and Drive scope documentation was rechecked on 2026-07-29.
External projects in **Testing** are limited to listed test users. External projects set to
**In Production** are available to any Google Account, so the test-user list no longer gates
authorization. WorqOrder requests only non-sensitive `drive.file`; mandatory
sensitive/restricted-scope verification and its unverified-app 100-new-user cap do not apply.
Brand verification remains optional unless the owner wants a verified WorqOrder name/logo on the
consent screen, and is not a release dependency.

Milestone 17 must create/register the direct-release signing identity, switch the audience to
External/In Production, and prove the release-signed APK with a Google Account that has never been
listed as a test user. After that one-time project/release setup, the owner does not add or approve
individual users. Authorization can still be blocked by the user's own refusal, missing
spreadsheet permission, Google Workspace administrator policy, Advanced Protection, account
eligibility, outage/quota, or a future Google policy change; WorqOrder cannot override those
external controls.

### D-049 — Focused XLSX export is production scope

Add a complete XLSX export milestone directly after Google Sheets export. Production creates one
new user-selected workbook for every export through `ACTION_CREATE_DOCUMENT`, parallel to CSV. It
uses the displayed date and the same immutable Room snapshot, nine columns, row order,
one-row-per-interval rule, stopped-timer export policy, and no-local-mutation behavior as CSV and
Google Sheets.

Each workbook contains exactly one visible `WorqOrder_YYYY-MM-DD` worksheet with the complete
canonical table. WorqOrder does not open, read, connect, or update an existing workbook, and it
stores no XLSX document URI or connection metadata. Repeated exports intentionally create
independent files.

Use a focused internal Android-compatible OOXML writer with no new production dependency. All
nine canonical values are literal cells; no macros, formulas, external links, hidden worksheets,
credentials, or app-private keys are written. Apache POI remains disallowed absent a new explicit
owner decision. Cancellation writes nothing; output failure uses best-effort partial-document
cleanup and leaves Room unchanged.

### D-050 — At-rest encryption is deferred to optional Milestone 19

The owner superseded the earlier required Milestone 14 encryption decision after its experimental
work was removed from the production branch. The required production sequence does not add
WorqOrder-managed encryption to app-private Room or DataStore files and must not claim otherwise.
Android's application sandbox remains the current local access boundary.

The complete Data Protection and Encryption Hardening scope is retained only as optional
Milestone 19, after optional Milestone 18. It requires separate explicit owner permission. If
authorized, it must cover versioned Keystore-backed authenticated encryption, database auxiliary
files, backup/data extraction, caches, temporary artifacts, logs, crash output, non-destructive
plaintext migration, key rotation, key loss/invalidation, corruption, performance, and full
regression testing. It must add no mandatory WorqOrder login, account, biometric prompt,
device-credential prompt, or app PIN, and it must never silently delete or reseed Room.

User-selected CSV and XLSX documents remain unencrypted external files. Readable Google Sheets
cells use platform TLS and Google access controls but are not end-to-end encrypted by WorqOrder.

### D-051 — Post-project options require separate authorization

Biometric, device-credential, local-PIN, or comparable user-presence/app-lock behavior belongs
only to optional Milestone 18 after the required project through release/handoff is fully
complete. Do not begin it without a new explicit owner instruction, and remind the owner about it
only after Milestone 17 is finished.

If authorized, keep the feature local/offline and opt-in. It must not introduce a WorqOrder cloud
account, custom backend, password server, Google-account requirement, or silent key/data loss.
Its own gate covers prompt/lockout/cancellation, background timeout, screen/reboot/process events,
deep-link/navigation bypass, sensitive previews, accessibility, running-timer integrity, and full
security/regression testing. Encryption interactions apply only if optional Milestone 19 is also
separately authorized.

The same optional milestone also revisits two export enhancements that are intentionally excluded
from production scope until separately authorized:

1. let the user choose between the production one-off XLSX file and a connected persistent XLSX
   workbook; and
2. optionally export the just-completed day automatically at its local midnight when the default
   destination is Google Sheets or a valid persistent XLSX workbook.

It also defers a Task interval-card presentation refinement: show completed Start and Stop clock
values as task-zone `HH:mm` only. This is display-only. Persisted UTC instants, the task's stored
ZoneId, edit precision, duration calculations, and explicit fall-back occurrence handling remain
unchanged.

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

The single connected Google spreadsheet uses exactly one marked
`WorqOrder_YYYY-MM-DD` worksheet per exported date. Row 1 is the shared nine-column header and row
2 onward contains the canonical rows. Re-export replaces the entire owned table and removes stale
rows; it never appends or merges, so unchanged or repeated exports create no duplicates.

Google uses sheet-scoped developer metadata for marker/schema/date and right-sizes each sheet to
nine columns and required rows to respect the spreadsheet's 10-million-cell limit. A same-named
tab without the marker is a conflict, not permission to overwrite.

Before the first WorqOrder Google export, a confirmed completely blank spreadsheet reuses and
renames its original first worksheet instead of leaving an empty default tab. If any user content
exists, or blankness cannot be established safely, all existing tabs remain untouched and Google
export adds the requested date tab.

One-off XLSX does not participate in this ownership protocol. Every export creates a new workbook
containing exactly one date-named worksheet, so it needs no marker, replacement, or conflict rule.

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
same stopped-timer precondition as CSV and XLSX under D-056.

### D-055 — Lifecycle recovery is Activity-triggered and Room-authoritative

Milestone 13 adds one application-scoped `TimerRecoveryCoordinator`. `MainActivity.onResume`
invokes it regardless of the visible navigation destination; Main initialization/resume and its
foreground date-change observer remain idempotent retries. Recovery waits for the effective
ZoneId, captures one UTC instant, normalizes all crossed boundaries with the Start-pinned zone,
rebuilds a missing process-local monotonic anchor, and reconciles selection. Concurrent recovery
signals are serialized and timer writes remain protected by the existing timer-operation mutex
and Room transactions.

The UI ticker remains a shared 50 ms presentation flow and stops when Main is no longer collected.
No background ticking, foreground service, alarm, wake lock, boot receiver, or WorkManager timer
job is added. Background, screen-off, process-death, Recents-removal, and reboot correctness comes
from the persisted UTC open interval and singleton pointer when the user next resumes the app.

Room snapshot reads now reject orphan open intervals and any singleton/open-candidate mismatch
instead of presenting inconsistent data as stopped or silently deleting it. Negative
process-recovery duration is clamped only for provisional display, remains a visible clock anomaly,
and can be re-anchored after wall time is corrected without rewriting interval history. When a
valid live monotonic anchor exists, normalization checks it before persisting wall-derived
midnight splits, so a manual wall jump cannot invent daily segments.

### D-056 — Stop is required before every export

Owner-directed Milestone 13 device testing supersedes D-023's running-snapshot workflow. Whenever
Room-derived active-timer state is present, Main disables its single export action for CSV, XLSX,
and Google Sheets and labels it **Stop Timer to Export**. The event handler repeats the guard so a
programmatic event while active state is loaded cannot launch a picker or network export.

After Stop commits the final UTC endpoint and clears active state, export proceeds normally from
the immutable canonical dataset. This avoids blank Stop Local values and gives every destination
the same authoritative completed-interval semantics. The destination-neutral builder may retain
defensive running-row capability internally, but it is not an approved user workflow.

### D-057 — A valid monotonic session determines Stop and normalization

Manual Milestone 13 testing found that persisting the current wall-clock sample at Stop made the
completed total jump by roughly 10–20 seconds after ordinary forward/backward clock corrections,
even though the live monotonic display stayed accurate. The owner rejected that user experience.

While `LiveTimerSession` retains a valid anchor, Stop and active-date normalization now project:

`logicalInstant = currentSegmentStart + activeDurationAtAnchor + elapsedRealtimeDelta`

The projected value is persisted as an absolute UTC `Instant`, drives ZoneId-aware midnight
boundaries, and makes the completed total match the final live total. A manual wall-clock change
therefore cannot create a false split or replace measured elapsed work. The local Stop clock label
may intentionally differ from the device's newly corrected wall clock.

After process death or reboot, the old monotonic reading is unusable. Recovery still derives its
initial contribution from persisted Start/current UTC, then establishes a fresh monotonic anchor.
A negative/untrustworthy recovery remains fail-closed with `ClockChanged`; WorqOrder does not
invent elapsed time that cannot be reconstructed.

### D-058 — Milestone 15 adaptive and accessible interaction policy

Owner approval resolves the previously deferred export-cancellation presentation: canceling a
CSV/XLSX create-document picker or Google export authorization clears progress and returns to
unchanged Main content without a banner, snackbar, success, or error. A non-sensitive
`Canceled` last-attempt diagnostic remains permitted.

Main uses one vertically scrollable content surface so timer, date, recovery messages, and task
rows remain reachable in landscape, on short displays, and at large font scales. At narrow widths
or at least 130% font scale, Export and Add task stack vertically; ordinary phone layouts retain
the approved side-by-side bottom actions. The full timer value remains a single accumulated value,
is horizontally scrollable when enlarged, and retains its complete screen-reader description.

Expected errors use assertive live-region semantics, progress/status and success use polite
announcements, field validation exposes error semantics, and selected/running/locked task states
remain explicit text and semantics rather than color-only. Disconnecting a spreadsheet always
requires confirmation; signing out requires confirmation when connected metadata will also be
cleared. Neither action changes Room task data.

### D-059 — Pinned timer and newest-first Main task presentation

The owner supersedes only D-058's single-scroll-surface choice. The timer card and complete
date-selector bar now remain pinned below the top app bar while messages and the task list scroll
in an independent lower region with a minimalist visual position indicator. This keeps the primary
timing state, Start/Stop control, displayed date, navigation arrows, and calendar action
continuously visible. Large-font and short-screen tests must still prove the lower region remains
reachable and the pinned controls do not starve it of usable space.

Main presents daily tasks by descending creation instant with stable task ID as a deterministic
tie-breaker. Sorting occurs when the observed Room list changes, not on the live display ticker.
Room queries, interval chronology, and canonical CSV/XLSX/Google export ordering are unchanged.

### D-060 — Platform backup disabled for the production application

Milestone 16 found Android backup enabled in the manifest. WorqOrder stores potentially sensitive
client/task data in app-private Room and DataStore files and does not promise restoration after
uninstall or storage clearing. The production application therefore sets `allowBackup=false`.
This reduces unintended platform/cloud/device-transfer copies; it is not WorqOrder-managed
encryption and does not protect user-directed plaintext CSV/XLSX files or Google Sheets.

## Deferred decisions

- A secondary one-time export destination chooser; omit unless usability testing shows need.
- Restored-client UI placement; restoration capability is planned, but it may be an Archived subsection or separate route.
- Multiple connected spreadsheets, background automatic export, imports, synchronization, and a
  foreground timer service remain outside production scope.
- Persistent XLSX mode and opt-in automatic local-midnight export to Google/future persistent
  XLSX are deferred to optional Milestone 18 under D-051.
- Task interval-card Start/Stop values changing from their current second-level display to
  task-zone `HH:mm` are deferred to optional Milestone 18 under D-051; full stored time metadata
  remains unchanged.
- At-rest encryption of app-private Room and DataStore files is deferred to optional Milestone 19
  under D-050 and requires separate explicit owner authorization after optional Milestone 18.
- App-access login/biometric/device-credential/PIN gating remains optional post-project scope
  governed by D-051.

## Implementation inputs still needed

- Developer-controlled personal Google Cloud project, External test audience, debug SHA-1/debug
  Android OAuth client, Web OAuth client ID, and disposable test spreadsheets before Milestone 10
  integration testing.
- Permanent direct-release key/fingerprint and release Android OAuth client are deferred to
  Milestone 17. No Google Play signing input is required.
