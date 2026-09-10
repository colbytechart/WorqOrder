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

### D-013 — Selection rollover (v0.2 historical behavior)

Persist preferred series/concrete-task hints. On actual configured-date rollover, find/create the same-series daily task with current metadata under `(seriesId, workDate, assignmentZoneId)` uniqueness and select it. Browsing a date alone does not create a copy. Including the zone refines the suggested series/date key so a future task created in another zone is not silently repurposed or made inconsistent with interval boundaries.

### D-014 — One row per interval (v0.2 historical behavior)

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

Retain the dark/high-contrast vertical visual direction where Material 3/accessibility allow it.
Written behavior wins for the WorqOrder title, Settings icon, labeled date, explicit task
metadata/state, Create/Cancel, unambiguous export label, geographical ZoneId, and explicit client
actions. D-065 later adopts the concept's cleaner no-fraction duration presentation.

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
only while Google Sheets is the selected destination. The conditional Google content belongs in
the Export Destination card; its final row is the **Auto Export** switch described by D-071.
Section and screen headers use title
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
scrolls the Settings list to the newly revealed conditional connection content. The **Auto Export**
row follows all sign-in and spreadsheet-connection options at the bottom of the Export Destination
card and is absent for CSV/XLSX.

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

Milestones 10 and 11 use the debug signing SHA-1 and debug Android OAuth client. A permanent
direct-release key/fingerprint and matching Android OAuth client are created only through the
secure Milestone 17 release process. If Google
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
canonical values are literal cells (nine in `0.1.0`, 14 in planned `0.2.0`); no macros, formulas,
external links, hidden worksheets,
credentials, or app-private keys are written. Apache POI remains disallowed absent a new explicit
owner decision. Cancellation writes nothing; output failure uses best-effort partial-document
cleanup and leaves Room unchanged.

### D-050 — At-rest encryption is deferred to optional Milestone E

The owner superseded the earlier required Milestone 14 encryption decision after its experimental
work was removed from the production branch. The required production sequence does not add
WorqOrder-managed encryption to app-private Room or DataStore files and must not claim otherwise.
Android's application sandbox remains the current local access boundary.

The complete Data Protection and Encryption Hardening scope is retained only as optional
Milestone E. It requires separate explicit owner permission. If
authorized, it must cover versioned Keystore-backed authenticated encryption, database auxiliary
files, backup/data extraction, caches, temporary artifacts, logs, crash output, non-destructive
plaintext migration, key rotation, key loss/invalidation, corruption, performance, and full
regression testing. It must add no mandatory WorqOrder login, account, biometric prompt,
device-credential prompt, or app PIN, and it must never silently delete or reseed Room.

User-selected CSV and XLSX documents remain unencrypted external files. Readable Google Sheets
cells use platform TLS and Google access controls but are not end-to-end encrypted by WorqOrder.

### D-051 — Optional Milestone E requires separate authorization

Optional Milestone E is release-agnostic, outside `0.2.0` and every other release scope, and is not
scheduled. It remains on the backburner until the owner explicitly assigns it. It is the only home for
WorqOrder-managed at-rest encryption, opt-in biometric/device-credential/approved-PIN app access,
and separately reviewed screenshot/Recents privacy controls. Do not begin any part without a new
explicit owner instruction.

If authorized, keep access control local/offline and opt-in. It must not introduce a WorqOrder
cloud account, custom backend, password server, Google-account requirement, destructive recovery,
or silent key/data loss. Its own gates cover cryptographic migration/key failures,
prompt/lockout/cancellation, background timeout, screen/reboot/process events,
deep-link/navigation bypass, sensitive previews, accessibility, running-timer integrity,
performance, security, and full regression testing.

Persistent-XLSX mode is obsolete rather than deferred. CSV/XLSX remain manual one-off document
flows. The About, interval-clock, handed landscape, lock-screen surface, and Google-only automatic
export requirements moved into the approved `0.2.0` production roadmap and no longer belong to an
optional milestone.

### D-052 — One reduced canonical export dataset (`0.1.0` baseline)

For `0.1.0`, all destinations consume one immutable schema-version-2 `ExportSnapshot` built after the export
action from one Room transaction and one internal snapshot instant. The visible columns are
exactly Work Date, Client Name, Description, Hardware / Software Purchases, Interval Number, Start
Local, Stop Local, Interval Duration Formatted, and Task Total Duration Formatted. D-069 supersedes
the external columns together for `0.2.0`; the single-builder rule remains unchanged.

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

The UI ticker remains a shared presentation flow and stops when Main is no longer collected. D-064
sets its final release cadence to 200 ms after physical performance profiling.
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

Milestone 17 lint review extends this decision for Android 12+: the manifest references explicit
data-extraction rules that exclude every app-private credential/device-protected domain from both
cloud backup and device transfer. This does not create a backup/export feature and does not change
the plaintext external-export boundary.

### D-061 — Initial direct-release identity and optimization policy

The initial public release is `0.1.0` with `versionCode = 1`, application ID `worq.order`, and a
single APK signed by the owner's permanent external RSA-4096 release key. The key and passwords
never enter Git. The ignored `keystore.properties` points to the external key; the tracked
`keystore.properties.example` contains placeholders only. AGP's built-in release-signing
validation must fail when a usable release key is not configured.

The release variant is non-debuggable. R8 code shrinking and resource shrinking remain disabled
for `0.1.0`: enabling them for the first time at the final release boundary would create
unnecessary reflection/serialization/Google/Room/OOXML risk without an established download-size
requirement. A future release may enable shrinking only with explicit owner approval and complete
release regression testing.

The supported public delivery is the owner-signed APK attached to a GitHub Release with its
SHA-256. Existing users install an update over the current app using the same signing identity;
uninstalling first is neither required nor safe for local data.

### D-062 — Production Google audience and no-intervention release

For `0.1.0`, Google Auth Platform is External and In Production, with no app logo or active
verification requirement. Google Sheets, Drive, and Picker APIs are enabled; `drive.file` is the
only file-data scope. The release Android OAuth client binds `worq.order` to the permanent release
SHA-1, and the public Web client identifier is injected from ignored local build configuration.

This state removes the owner-managed test-user-list gate. Ordinary users of the owner-signed APK
may authorize their own Google Account without owner intervention or payment. User refusal,
spreadsheet permissions, organization/Advanced-Protection policy, service availability, quota,
and future Google policy can still deny a particular request. No billing account, paid quota,
custom domain, or Workspace organization is introduced.

### D-063 — Short-landscape Main actions and pinned-header bound

The owner-approved phone-landscape layout moves Export and Add task from the bottom bar into the
top application bar. This removes the persistent bottom region that otherwise combines with the
pinned timer/date header to hide the task list under large text and display scaling. The landscape
title is left-aligned, Export and Add task are centered as a pair with 144 dp minimum widths and
16 dp separation on standard phone-landscape widths, and Settings remains at the far right.
Constrained landscape/multi-window widths use the earlier 120 dp action widths and an ellipsized
title slot so those elements cannot overlap. The landscape export button uses the compact visible
labels **Export CSV**, **Export XLSX**, or **Export Sheets**; its semantics retain the full
displayed date and destination required by D-059 and the export-date rule. Portrait retains the
approved bottom action area.

Short landscape continues to pin the timer and complete date-navigation controls above the
independently scrolling task list. Its compact timer/date row has a bounded height, omits only the
redundant visible **Work Date** label, keeps 48 dp actions, and preserves full timer/date semantics.
This is a presentation-only change: Main state, Room, timing, task ordering, and all three export
implementations are unchanged.

D-063 documents the released `0.1.0` layout only. For `0.2.0`, D-070 and Milestone 24 supersede
its top-bar actions and single-column compact-header arrangement with the handed two-column
landscape composition; its portrait and non-behavioral guarantees remain in force.

### D-064 — Five-Hz visible timer refresh

Physical release profiling with the timer visible for one hour found 22–26% instantaneous app CPU
and about ten minutes of accumulated process CPU under the original 50 ms presentation cadence.
Memory returned from roughly 154 MB foreground PSS to 45 MB in the background and back to 157 MB
when visible, and device temperature rose only 2.1°C, so the evidence did not indicate a memory
leak or thermal defect. The recomposition cadence itself was nevertheless too expensive.

The visible timer now refreshes every 200 ms (five times per second). Its value still derives from
the application-scoped monotonic anchor, persisted UTC boundaries remain unchanged, Stop persists
the precise projected instant, and no tick writes Room or DataStore. The change trades
unnecessary animation frequency for materially lower CPU while retaining responsive stopwatch
feedback.

The owner reran the signed release on a physical Pixel after this change. Over 20 minutes with the
timer visible, cumulative process CPU increased from 1.02 to 31.29 seconds (about 2.5% average),
battery declined by one percentage point, and temperature decreased from 30.1°C to 29.1°C. During
the following locked/background interval Android reclaimed the process, which is expected and
exercises persisted recovery rather than background execution. This evidence closes the release
performance gate.

### D-065 — Hide fractional seconds at presentation boundaries

User-visible accumulated durations use `HH:MM:SS`; exported Start/Stop remain `HH:mm`, and exported
durations remain `HH:MM:SS`. Positive sub-second remainders are truncated rather than rounded.
Room UTC boundaries, monotonic anchors, interval arithmetic, and in-memory export snapshots retain
their existing precision. This is a presentation-only decision and requires no Room migration or
export schema-version change.

### D-066 — `0.2.0` identity and roadmap

The next release is `versionName = 0.2.0`, `versionCode = 2`. Milestone 18 is planning-only and
changes no implementation code; consecutive implementation milestones begin at Milestone 19.
Released `0.1.0` evidence remains history;
planning documents label future behavior explicitly and must not claim it is already implemented.
The application ID, permanent signing identity, Room authority, disabled-backup policy, GPLv3
license, direct GitHub distribution, Google `drive.file` scope, and free/open-source policy do not
change.

### D-067 — Client CSV import appends transactionally

Client Management may import names only from a user-selected CSV document. Parse every nonblank
cell across all rows/columns with no special header. Apply the existing trim, whitespace collapse,
100-character maximum, and canonical duplicate rule. The import never replaces the client list:
active matches are skipped, archived matches are restored, in-file duplicates collapse, and new
valid names append. Commit the complete planned import atomically and keep the final active list
sorted A–Z. Unsupported MIME/extension, malformed quoting/encoding, invalid cells, excessive size,
or I/O failure produces no partial mutations and an actionable non-crashing result.

Milestone 20 implements this with Android `OpenDocument`, no persisted URI grant, and a strict
`.csv` filename plus CSV MIME allowlist (`text/csv`, `text/comma-separated-values`,
`application/csv`, or `application/vnd.ms-excel`). Input is strict UTF-8, with a leading UTF-8 BOM
accepted. Bounds are 1 MiB per document, 10,000 records, 20,000 cells, and 1,024 UTF-16 code units
per raw cell; normalized names remain limited to 100 Unicode code points. Parsing and all
validation finish before the single Room transaction begins. File-picker cancellation is silent,
and a completed attempt reports added/restored/skipped counts.

### D-068 — Consultants use internal Employee identity plus task snapshots

Add a user-facing **Consultant** directory directly below Client Management. Its internal Room
entity remains named Employee; this is an implementation term, not a GUI label. Consultant validation, normalization,
active/archive/restore semantics, and alphabetical ordering mirror clients. Preferences stores the
currently selected active consultant for future tasks.

Each daily task stores the optional employee ID and an assignment-time employee-name snapshot.
Renaming or archiving a Consultant entry does not rewrite existing tasks or exports. Explicitly
editing a task may replace its assignment/snapshot. New task creation requires an active selected
consultant; migrated `0.1.0` tasks use null ID/blank snapshot until edited. Rollover/midnight copies
carry the source task's snapshot rather than re-resolving the current directory name.

Archiving the currently selected directory employee clears the future-task selection; it does not
change existing tasks. The user must select/restore an active employee before creating another
task.

Milestone 21 implements this with a Consultant-labeled Settings section directly below Client
Management, an A-Z active selector, active/archived management, and a coordinator spanning the
Room directory and typed DataStore preference. The task-creation transaction rechecks the selected
Employee is active and reads its current Room name into `employee_name_snapshot`; stale selection
cannot create a newly assigned task. Complete Consultant correction, Work Type, and Mileage task
controls remain Milestone 22.

### D-069 — Work metadata, derived billing, and canonical schema 3

New tasks default Work Type to `On-Site`; `In-Office` is the alternative. Migrated tasks use
`Unspecified`/blank export until edited. Mileage is optional canonical non-negative decimal text,
entered through a decimal numeric keyboard and validated without floating-point or locale
conversion. Migrated tasks use blank Mileage.

Billing Minutes is derived from the task's exact interval total and is not persisted:
`0` for zero duration; otherwise `ceil(totalMilliseconds / 900000) * 15`. Any positive total below
15 minutes therefore yields `15`.

All destinations advance together to schema version 3 with these exact 14 columns:

1. Start date
2. End date
3. Consultant
4. Client
5. Description
6. Expense
7. Work type
8. Mileage
9. Interval number
10. Start time
11. Stop time
12. Interval duration
13. Time spent
14. Billing minutes

Start date and End date repeat the same stored task date as `MM/DD/YYYY`; this is only an export
projection and does not create a date range in Room. Export Start/Stop use task-zone `HH:mm` only.
Duration values remain accumulated `HH:MM:SS`; **Time spent** is the renamed task-total value.
Precise internal instants and milliseconds remain
unchanged. Known owned Google schema-2 tabs are atomically upgraded/replaced as schema 3 on
re-export; unowned same-name tabs and unknown/newer schemas remain protected conflicts.

Milestone 22 completes the task-facing integration without another Room migration. Create Task
uses the selected active Consultant, defaults to On-Site, and accepts optional bounded decimal
Mileage. Edit Task can reassign an active Consultant and update Client, text metadata, Work Type,
and canonical Mileage in one stopped-task transaction. Room rechecks both referenced directory
rows and refreshes the assignment-time Consultant snapshot; a stale/archive race produces no
partial update. Billing Minutes is calculated from observed completed intervals and is never a
stored counter. All destinations continue consuming the one schema-3 snapshot implemented in
Milestone 19; no adapter gained independent field selection or formatting.

### D-070 — v0.2 presentation additions

Routine interval cards omit their redundant Duration row and display **Start Time**/**Stop Time**
as task-zone 12-hour `hh:mm a` without changing editor or storage precision. Task Total remains
above the list and the derived Billing Minutes counter appears directly below it, never per
interval. Text-entry fields request sentence capitalization from the Android keyboard without
silently rewriting stored user text. The final bare Settings footer displays only
`WorqOrder v0.2.0 - stable`, derived from build
metadata, and has no GitHub repository/release link.

Landscape uses a global top bar with WorqOrder far left and Settings far right, then approximately
equal functional columns below. Right-handed is the default: the full-height independently
scrolling task list occupies the left side with its scrollbar attached to the list's right edge
and its bottom aligned to the action-button margin; the right side stacks timer, date controls,
and Export/Add actions. Left-handed mirrors only the content columns. Portrait remains unchanged.

### D-071 — Automatic export is Google-only and target-date-captured

CSV and one-off XLSX remain manual. When Google Sheets is selected, its conditional Settings
content ends with a switch labeled exactly **Auto Export** and supporting text exactly
**Automatically export tasks at the end of each day.** The row is inside the Export Destination
card below Google sign-in and Sheets connection controls, is hidden for CSV/XLSX, and defaults off;
enabling requires authorization, a valid connected spreadsheet, and required notification
capability. Schedule approximately near 11:59 PM
using the approved Android inexact mechanism. Capture the intended work date and its effective
ZoneId when scheduling. Execution may occur shortly after midnight but must still export that
captured prior date, never a newly blank `today`.

Automatic export uses the same schema-3 snapshot and idempotent owned-tab replacement as manual
Google export. A successful background run produces no Main success status or success
notification. If any timer is active, preserve the target date as pending and do not export an
open interval. After Stop, show a system notification containing no task/client data; tapping it
resumes/performs or confirms export for the preserved date. Dismissal does not claim success or
discard the recoverable pending state. Offline/auth/permission/quota/ambiguous results remain safe
and bounded. Exact scheduler and unattended-authorization feasibility requires current official
research before implementation.

### D-072 — Lock-screen mechanism is research-gated

Before lock-screen code, review current official Android guidance and native Clock timer/alarm
behavior, explain stable notification/widget alternatives and permission/privacy implications,
and pause for owner approval. Any accepted surface is present only for an open Room interval,
shows WorqOrder identity, active task, and elapsed timer, and scopes swipe dismissal to that active
interval without stopping it. It may not become timer authority or justify a foreground service,
wake lock, exact alarm, or app-owned background tick loop solely for display.

Milestone 27 completed that official review on 2026-08-08. The recommended API-26-through-target
contract is a silent, low-importance, non-ongoing standard notification with Android's own
chronometer, `CATEGORY_STOPWATCH`, private full content, and a redacted public version. It is a
notification-shade surface eligible for lock-screen display, not a guaranteed lock-screen-only
widget. A `deleteIntent` persists only the dismissed active interval ID; Room remains authoritative.
API-33+ permission denial, channel/user/OEM privacy controls, and force-stop can suppress it. A
one-shot `BOOT_COMPLETED` receiver may restore it after first unlock without a foreground service,
alarm, wake lock, or tick loop. AppWidget, custom `RemoteViews`, full-screen intent, foreground
service, and API-36-only promoted Live Update approaches are rejected as the baseline. The detailed
evidence and tradeoffs are in `LOCK_SCREEN_SURFACE_ADR.md`. The owner approved all four tradeoffs on
2026-08-08. The owner then explicitly started Milestone 28. The implementation uses the selected
standard notification, typed per-interval dismissal state, contextual permission request,
application/resume/date reconciliation, and a non-exported post-unlock boot receiver. It adds no
timer service, alarm, wake lock, custom layout, or background tick. Device verification remains.

Manual verification showed that granting notification permission in Android Settings did not
always repost until a later Stop/Start, so Activity window-focus recovery now reconciles the open
Room interval when WorqOrder returns from system Settings. The owner then finalized the standard
layout: **WorqOrder** is the main title beside Android's platform-controlled compact chronometer;
the private supporting line contains **Client · Description**, and the public supporting line is
blank. Custom `RemoteViews` remains rejected.

### D-073 — Strict 12-hour clock presentation

All human-readable task clock values use task-zone `hh:mm a` with uppercase AM/PM. This includes
routine interval cards, interval-editor values, and canonical Start time/Stop time export strings
consumed unchanged by CSV, XLSX, and Google Sheets. Accumulated durations remain `HH:MM:SS` and do
not use AM/PM. Room continues to store precise UTC epoch boundaries plus the task ZoneId; DST
occurrence choices and calculation precision are unchanged. Because schema version 3 is still an
unreleased v0.2.0 format, this finalizes its clock representation without another schema-version
or Room migration. D-073 supersedes earlier 24-hour export-format wording in D-052, D-065, and
D-069 without changing their persistence or single-builder decisions.

### D-074 — Consultant selection stays in Settings; directory management has its own route

Settings begins with standalone **Client Management** and **Consultant Management** navigation rows
in that order. The active-Consultant dropdown, `Choose a Consultant` empty value, and red
missing-selection guidance follow directly over the Settings background; there is no Consultant or
Clients card and no inline Consultant description. Add, rename, archive, active-list,
archived-list, and restore Consultant controls
move unchanged to the dedicated `settings/consultants` screen, whose Material layout mirrors
Client Management. Empty Consultant lists use the same indented title/supporting-text layout as
empty Client lists.

This is presentation and navigation only. Both destinations continue through the same
`ConsultantSettingsViewModel`, selection coordinator, Employee repository, typed DataStore
selection, validation, and historical task-snapshot rules. This decision supersedes only the
inline-directory placement described in D-068; it does not change Consultant behavior or data.
The final version/stability text is a bare item at the bottom of Settings; there is no About card or
About title.

### D-075 — Time-zone settings are retained internally but hidden from Settings

The Settings UI does not compose the Time Zone card, effective ZoneId, device/manual choices,
manual ZoneId picker action, or zone selector dialog. The underlying ViewModel events, repository,
typed preferences, effective-zone provider, timer guards, and historical ZoneId rules remain in
the codebase. Missing, first-launch, and corrupt time-zone-mode values continue to default to
`DEVICE`; this presentation decision does not rewrite an existing stored manual preference or any
historical task/date/interval data.

### D-076 — Automatic Google export uses unique one-time WorkManager jobs

Milestone 25's 2026-08-05 official review selects stable WorkManager `2.11.2`. Use a unique,
non-expedited, network-constrained `OneTimeWorkRequest` calculated for each effective-zone
near-end-of-day target, then recalculate the following target. Do not use a fixed 24-hour periodic
worker, AlarmManager, an exact alarm, a foreground service, or app-owned stopwatch/background
ticks. Execution is intentionally inexact and may occur after midnight; the durable captured epoch
day and canonical ZoneId remain authoritative.

The oldest unresolved target cannot be overwritten. It records only non-sensitive target ZoneId,
date, connection association, and typed pending state. After success, due later dates advance one
at a time through separate bounded workers. Timer-running, offline, authorization-resolution,
permission, quota, timeout, server, or ambiguous states fail closed and remain recoverable; they do
not use WorkManager automatic retry. Manual and automatic Google operations share the same export
coordinator, canonical snapshot, and idempotent owned-tab replacement.

A worker may call `AuthorizationClient.authorize()` using application context and proceed only
when Google returns an already-granted short-lived `drive.file` token without interaction. A
returned `PendingIntent` is never launched in background; it becomes a user-action-required state.
No token is persisted and no backend, service account, broader scope, billing, or OAuth
verification workflow is added.

Blocked work uses one content-free **Pending Google Export** notification channel. On API 33+,
enabling automation requires the user-driven `POST_NOTIFICATIONS` runtime grant; API 26+ requires
the channel. Notification dismissal never clears pending work, success remains silent, and disabled
notifications leave the pending target visible in Settings. WorkManager's library-managed normal
network/reboot/wake-lock support must be visible in the merged-manifest audit, but WorqOrder adds no
app-owned receiver or wake lock. Google Play services remains an installed-device API dependency;
this decision does not add Google Play Store distribution, Play App Signing, Play Console setup, or
a Play release.

### D-077 — Milestone 26 implements one durable automatic target

Milestone 26 implements D-076 with stable WorkManager `2.11.2`. Preferences DataStore stores the
enabled flag plus one oldest target tuple: epoch day, geographical ZoneId, account/spreadsheet
association, and optional typed pending reason. It stores no token. Enabling captures today's date
in the effective ZoneId and schedules one uniquely named, non-expedited, connected-network worker
for 23:59. Delayed execution uses the captured date; each successful worker advances exactly one
date and schedules the next one-shot target.

The worker uses a background authorizer that accepts only an immediately returned access token.
Any Google resolution, active timer, or bounded failure becomes durable pending state and returns a
terminal WorkManager result; operation failures never call `Result.retry()`. Timer-pending work is
announced only after Stop commits. All other user-action-required states use the same content-free
notification, whose explicit action opens the pending control in Settings. A user-confirmed retry
uses the activity authorizer. Manual and automatic Google exports share one application mutex in
addition to the owned-tab idempotency contract.

The Auto Export switch is hidden unless Google Sheets is selected, defaults off, and appears after
the Google sign-in/connection controls inside Export Destination. API 33+ requests notification
permission only after the user tries to enable it. Selecting CSV/XLSX, disabling the switch,
disconnecting the sheet, or signing out disables and cancels future automatic work without
touching Room or remote data. Successful background export remains silent.

### D-078 — Auto Export enablement failures remain inline and specific

A rejected Auto Export toggle must not insert content above the Export Destination card or change
the user's Settings scroll position. The switch remains off and a red accessible message appears
directly below it. Notification permission or app/channel disablement, missing spreadsheet
connection, wrong export destination, and local-settings failure each use distinct recovery text.
When Android can present the runtime notification permission request, WorqOrder requests it; a
denial or device-setting block directs the user to the app's notification settings.

### D-079 — Billing Status is nullable history and required-default new metadata

Milestone 27 first adds daily-task **Billing Status** with exactly three user/export values:
`Billable`, `Do not bill`, and `Do not charge`. Create Task defaults to `Billable`; Edit Task uses
the same exclusive three-choice radio presentation between Work Type and Mileage. The Room value
is nullable so every task migrated through the explicit non-destructive `MIGRATION_3_4` remains
blank rather than receiving invented billing history. Rollover and midnight continuation copy the
source value, including null.

All export destinations advance together from canonical schema 3 to schema 4. The exact new
15-column order inserts **Billing Status** between **Work type** and **Mileage**. The shared
`ExportRowBuilder` remains the only projection boundary; CSV, one-off XLSX, manual Google export,
and automatic Google export cannot diverge. Owned Google tabs marked with known schema 2 or 3 may
be atomically replaced and upgraded to schema 4; unknown/newer and unowned tabs remain protected.

### D-080 — Approved Credential Manager transitive preview exception for `0.2.0`

The declared identity stack remains stable: `androidx.credentials:credentials:1.6.0`,
`androidx.credentials:credentials-play-services-auth:1.6.0`, Google ID `1.2.0`, and Google Play
services Auth `21.6.0`. The resolved stable AndroidX Google-provider adapter transitively includes
`com.google.android.gms:play-services-identity-credentials:16.0.0-alpha08`. WorqOrder neither
declares nor directly calls that preview artifact.

The owner explicitly approved this one transitive exception for the official `0.2.0` release after
Milestone 29 identified it in both resolved runtime graphs. Excluding it, forcing a separately
versioned provider artifact, or replacing the proven identity stack at the release boundary would
risk breaking Credential Manager and Google sign-in. The clean debug/release gate passes and the
2026-08-09 OSV query returned no vulnerability record for this artifact or any of the 180 resolved
debug/release Maven coordinates. This decision does not authorize any direct preview dependency or
future transitive preview change; either requires a new documented review and owner decision.

### D-081 — v0.3.0 is a narrow behavioral release

`0.3.0` contains only two product changes: eliminate automatic task rollover/midnight
continuation, and replace task multi-interval history with one task per interval. Supporting Room
migration, selection, automatic-export ordering, UI, export-schema, documentation, and regression
work are required consequences rather than additional product features. Every unrelated `0.2.0`
behavior and permanent policy remains.

### D-082 — Schema 5 preserves every interval as one task

Room advances explicitly from version 4 to 5. `work_intervals` remains a separate table but gains
a unique `task_id` and loses `ordinal`, enforcing zero or one interval per task. A populated task
with several intervals is split deterministically: the original task retains its earliest ordered
interval and each later interval moves to a newly identified task carrying all user metadata. No
interval endpoint, task metadata, client/Consultant relationship, manual-edit state, active timer,
date, ZoneId, or identity that can remain stable is discarded.

The active-timer composite reference is repointed transactionally if its open interval moves.
Zero-/one-interval tasks remain semantically unchanged. Migration failure is explicit and
non-destructive. This supersedes the multi-interval portion of D-008 and ordinal assumptions in
D-027; it does not weaken D-009, D-010, or D-020.

### D-083 — Series IDs are lineage, not rollover uniqueness

Schema 5 replaces the unique `(seriesId, workDate, ZoneId)` index with a normal lookup index.
`seriesId` remains stable, non-user-visible lineage shared by task repetitions and any historical
v0.2 daily copies, but no code may use it to create a task for another date. A repeated Start
creates a new task and interval ID while retaining the source lineage and current user metadata.
This supersedes the uniqueness/copy behavior in D-013 and D-037.

### D-084 — Repeated Start duplicates the task atomically

Start on an eligible task with no interval uses that task. Start on an eligible task with one
completed interval atomically creates one same-day metadata copy, selects it, and creates its sole
open interval plus singleton active-timer state. The new timer begins at zero; the source task and
completed interval never change. Concurrent Start operations remain serialized and cannot create
duplicate repetitions. Manual Add Interval is available only to an untimed task; Edit/Delete owns
the singular completed interval.

### D-085 — Midnight is an exact logical stop without a wake guarantee

At the first next local-day boundary calculated in the active timer's pinned ZoneId, the sole open
interval ends, active state and stale timing selection clear, and no continuation task/interval is
created. Foreground observation may apply this promptly. If Android has suspended the process,
WorkManager, resume, launch, or post-unlock recovery applies the exact boundary retrospectively at
its next legitimate execution opportunity. The app will not add an exact alarm, app-owned wake
lock, or foreground stopwatch service merely to execute at the physical instant of midnight.

Automatic Google work targets the preceding captured date and is scheduled no earlier than its
local boundary. It applies this close before snapshot/export. Other failures retain typed pending
state. This supersedes D-012 and the timer-running scheduling portions of D-071/D-076/D-077 while
retaining best-effort scheduling, idempotency, no billing/backend, and content-free recovery.

### D-086 — Date changes clear selection and never create tasks

Actual date or effective-zone reconciliation clears a selected task that is no longer eligible for
today. Startup, resume, normalization, Start eligibility checks, and historical browsing never
find or create a daily series copy. The user must select or create today's task. This supersedes
selection-rollover creation in D-013, D-037, and D-042 without changing historical stored dates or
ZoneIds.

### D-087 — Canonical schema 5 has 13 task rows

All export destinations move together to internal schema version 5. The exact visible columns are
Start date, End date, Consultant, Client, Description, Expense, Work type, Billing Status,
Mileage, Start time, Stop time, Time spent, and Billing minutes. Every task emits exactly one row.
`Interval number` and redundant `Interval duration` are removed. Owned Google tabs using known
schemas 2–4 may be replaced and upgraded; unknown/newer and unowned tabs remain protected. This
supersedes D-014 and the schema-4 visible projection in D-079 while preserving the shared immutable
projection requirement.

### D-088 — v0.3.0 task-level model delegation and teardown milestone

Every v0.3 milestone is divided into explicitly ordered task phases in
`V0_3_MILESTONE_PROMPTS.md`; whole-milestone model ownership is superseded. Each phase is assigned
to Luna, Terra, or Sol at Extra High reasoning according to the narrowest capable model, with token
efficiency prioritized before equivalent-quality implementation. Luna owns highly specified
inventory, mechanical fixtures, documentation, and evidence work. Terra owns bounded application,
UI, adapter, and integration work. Sol owns migration/invariant design, concurrency, security,
release judgment, destructive-system guidance, and the final quality review for every milestone.

The owner supplies the current weekly-token-budget percentage and explicit permission once at the
start of a milestone; the first phase estimates the whole milestone and verifies its branch. Later
phases inspect the existing diff and prior handoff instead of repeating broad discovery. At every
model transition, work stops until the owner confirms the requested model is active. No phase may
perform another model's assigned work, and a milestone cannot close until its Sol review accepts
the combined work and required tests.

The final teardown milestone produces conservative, inventory-first instructions only. It never
removes software or changes BIOS/firmware itself, must distinguish project-exclusive components
from tools shared by other projects, protects Git history/release keys/backups first, and requires
explicit confirmation before every destructive user action.

### D-089 — Schema 5 migration evidence baseline

Milestone 30C records the first executable schema-5 evidence baseline. The populated migration
fixture includes archived Client and Consultant rows, nullable metadata, zero-/one-/many-interval
tasks, and an active non-first interval; assertions verify deterministic split IDs, preserved
metadata/endpoints/relationships, active-pointer repointing, foreign-key integrity, reopen
persistence, and removal of the persisted ordinal. Room's packaged schema-5 JSON is checked in
alongside version 1–4 inputs. The compatibility `WorkInterval.ordinal` value is derived as `1`
for legacy presentation callers only and is not persisted.

### D-090 — Milestone 31 no-rollover regression evidence

The current `0.3.0` selection contract is covered by explicit regression cases for stale
date/ZoneId clearing, persisted selection and process reconstruction, Room active-timer authority,
date browsing without writes, repeated-Start selection and metadata/null copying, singular
Add/Edit/Delete, selected-task deletion, and repeated reconciliation without idle duplication.
These tests do not alter the legacy migration fixtures or the midnight continuation compatibility
surface; those remain inputs to Milestones 32 and 34.

The final Milestone 31 Sol audit found no hidden non-midnight rollover writer, stale DataStore
authority, duplicate repeated-Start path, transaction race violating the one-interval/global-timer
constraints, or accidental midnight-policy change. The complete owner-run Gradle gate passed in
10 seconds with 40 actionable tasks (1 executed, 39 up-to-date), followed by successful manual
date/ZoneId, browsing, singular interval, repeated Start, deletion, and Recents recovery checks.

### D-091 - Automatic Google authorization recovery preserves its target

Connected-spreadsheet authorization explicitly targets the persisted Google account hint. A
background `AuthorizationClient` result that requires UI marks the authorization stale but keeps
the account/spreadsheet metadata, enabled Auto Export preference, and captured work date. It does
not disable automation or make the stored destination indistinguishable from a disconnect. The
pending action remains visible after restart, and an Activity-backed retry may invoke Google's
resolution even while validation is stale. A successful complete export reaffirms the stored
connection validation. Passive next-day selection cleanup is silent; explicit attempts to start
an ineligible historical task retain their validation message.

### D-092 - Main visible date has an independent boundary signal

The active-timer stream is not authoritative for the Main screen's calendar day. A lifecycle-
collected signal calculates the next real midnight in the effective ZoneId and suspends directly
until it. This closes the race where WorkManager or another recovery caller clears the active timer
and cancels the 200 ms presentation tick before that tick can advance the visible date. A screen
following Today advances; a deliberately browsed date remains unchanged. The signal does not poll,
write Room, schedule exact alarms, or depend on Google export completion.

### D-093 - Milestone 32 quality gate accepts layered boundary execution

Exact boundary closure has one Room compare-and-close transaction and several legitimate bounded
entry points: foreground date observation, Activity/application recovery, post-unlock boot recovery,
scheduled WorkManager execution, and export preparation. All entry points converge through the same
operation lock and idempotent Room predicate. Automatic Google execution retains one captured date,
ZoneId, and connection key; foreground/startup execution can handle an overdue target while
WorkManager remains its durable background fallback. A later stale delivery cannot duplicate the
completed export. No exact alarm, continuation record, app-owned wake lock, foreground stopwatch
service, continuous background loop, or UI-tick database write was added. The final owner-run gate
passed 235 JVM tests, 108 connected tests, debug lint, and debug/release assembly.

### D-094 - Milestone 33 activates the singular interval and schema-5 projection

The current `0.3.0` UI exposes at most one optional interval per task: untimed tasks offer Add
Interval, while a completed interval offers Edit/Delete and a running interval remains locked.
Starting a task that already has a completed interval creates a new same-day metadata-preserving
task before opening its sole interval. The shared immutable export projection is schema 5 with
exactly 13 visible columns and one row per task; CSV, one-off XLSX, manual Google, and automatic
Google consume the same values. Known-owned Google schema-2/3/4 tabs may be upgraded by replacing
the complete owned range, while unowned or unknown/newer tabs remain protected. The released
`0.2.0` schema-4 projection remains a migration/compatibility fixture only.

The owner-run Milestone 33 final gate passed 235 JVM tests and 109 connected tests with zero
failures, errors, or skips. Debug/release lint and debug, Android-test, and release assembly also
passed. The Sol review found no Room mutation, destination-specific projection, unsafe Google-tab
overwrite, or singular-interval availability defect.

## Deferred decisions

- A secondary one-time export destination chooser; omit unless usability testing shows need.
- Restored-client UI placement; restoration capability is planned, but it may be an Archived subsection or separate route.
- Multiple connected spreadsheets, task/interval import, synchronization, and a foreground timer
  service remain outside production scope. Client-name-only CSV import is the explicit exception.
- Persistent XLSX mode is obsolete. CSV and XLSX remain manual one-off document exports.
- At-rest encryption, app-access login/biometric/device-credential/PIN gating, and optional
  screenshot/Recents privacy controls are deferred only to optional Milestone E under D-050/D-051.
- The running-timer surface is implemented under D-072 and `LOCK_SCREEN_SURFACE_ADR.md`; Android
  permission/channel/privacy/OEM policy still controls whether it appears or is redacted. The automatic
  Google scheduling/auth mechanism selected by D-076 is implemented in Milestone 26; its remaining
  limitations are Android-controlled timing, authorization, and notification behavior.

## Implementation inputs still needed

- None for the implemented `0.1.0` identity. The owner has created/backed up the permanent release
  key, registered its SHA-1 for `worq.order`, created the release Android OAuth client, enabled the
  three required Google APIs, configured only `drive.file`, moved the External audience to In
  Production, removed the logo/verification gate, and confirmed that no billing account is
  required.
- Final validation still uses the prepared fresh non-test Google account and a disposable editable
  spreadsheet; these are test inputs, not application credentials or repository files.
- Before Milestone 35 changes release identity, the owner must explicitly confirm the `0.3.0`
  `versionCode`. The plan does not infer it merely from `versionName`.
