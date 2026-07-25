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

### D-004 — Only CSV and Google Sheets exports

CSV and Google Sheets are the only destinations. There is no native XLSX export and no Apache POI/Excel-generation dependency.

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

Both exports use one row per interval with repeated task metadata. A zero-interval task emits one blank-interval row. Schema version 1 and exact columns are defined in `EXPORT_SPEC.md`.

### D-015 — One connected spreadsheet and one tab per date

Connect exactly one existing Google spreadsheet. Never create a new spreadsheet on each export. Each exported date owns `WorqOrder_YYYY-MM-DD` inside that document with a predictable marker/schema.

### D-016 — Google re-export is idempotent replacement

If a date tab is absent, create it. If its exact marker/schema/date are valid, replace all application-owned content with the current authoritative sorted snapshot. If the same name lacks the marker or is incompatible, do not overwrite.

Rationale: this later, precise rule resolves the earlier append-only wording and correctly reflects local edits/deletions as well as additions. Re-export does not create duplicate rows.

### D-017 — Google is a gateway, not DI/core storage

Use a fakeable Google gateway, current stable Google-supported Android identity/authorization APIs, Sheets API v4, and typed retryable failures. No Firebase/service account/API-key authorization/raw token storage.

### D-018 — Google Sheets scope for arbitrary IDs

Plan to request only `https://www.googleapis.com/auth/spreadsheets`. It is broader than per-file `drive.file` but is needed for the required arbitrary existing spreadsheet URL/ID workflow; no Drive-wide/profile/email scopes are planned. Revalidate official guidance immediately before the Google milestone.

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

Export remains available while timing. Normalize date boundaries, capture one export instant, emit blank Stop plus `RUNNING`, and calculate running duration/task total to that snapshot without stopping the timer.

### D-024 — Active session pins its zone

Capture the effective geographical zone at Start. Settings are locked while active, and even an external device-zone change does not alter this session's splitting rules. Adopt a new device zone after Stop.

### D-025 — Concept images are direction, not an alternate contract

Retain the dark/high-contrast vertical visual direction where Material 3/accessibility allow it. Written behavior wins: WorqOrder title, Settings icon, milliseconds, labeled date, explicit task metadata/state, Create/Cancel, unambiguous export label, geographical ZoneId, and explicit client actions.

### D-026 — Initial theme

Default to System on first launch so WorqOrder follows the device appearance. Light and Dark remain equally supported explicit radio choices and all three settings apply immediately without recreating navigation or timer state.

### D-027 — Stable ordinals and row order

Interval ordinals are stable and not renumbered after deletion/edit. UI is chronological; export rows use deterministic task creation/ID and interval time/ordinal ordering as specified. This prevents identities changing accidentally.

### D-028 — Spreadsheet-owned range

Rows 1–3 and the table in a correctly marked WorqOrder tab are application-owned and can be fully replaced. Other tabs and unmarked conflicts are never changed. Use raw Sheets values for user text.

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

### D-036 — Fifteen-milestone delivery sequence

The owner replaced the earlier eight broad implementation milestones with the exact
fifteen-milestone sequence recorded in `IMPLEMENTATION_PLAN.md`: scaffold; Room persistence;
timer/date domain; Main/live presentation; clients; task/interval CRUD; persistent settings; CSV;
current Google integration planning; Google connection settings; Sheets export; lifecycle
hardening; accessibility/usability; full audit; release/handoff.

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
selection rollover and midnight continuation, and included in both export destinations.

Consequences: Milestone 6 owns the form/repository/domain integration and an explicit Room
version-1-to-version-2 migration that gives existing tasks an empty purchases value. Milestones 8
and 11 include the field in their shared logical export schema. Milestone 6 implemented the field,
migration, daily rollover/midnight copying, and create/edit validation.

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
Appearance, Time zone, export default, and Google connection status.

## Deferred decisions

- A secondary one-time export destination chooser; omit unless usability testing shows need.
- Restored-client UI placement; restoration capability is planned, but it may be an Archived subsection or separate route.
- Multiple connected spreadsheets, background automatic export, imports, synchronization, XLSX, and foreground timer service are outside MVP.

## Implementation inputs still needed

- Publisher-owned Google Cloud project, consent/verification details, and release signing fingerprints before Google integration testing. These are external setup inputs, not reasons to delay the offline core.
