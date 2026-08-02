# WorqOrder Architecture

## 1. Architectural goals

The architecture keeps the offline core small, testable, and independent of Google services:

```text
Compose screens and navigation
        ↓ state / events
ViewModels
        ↓
Repositories and rule-heavy domain services
        ↓
Room | DataStore | clocks/zones | CSV/XLSX output | Google Sheets gateway
```

Milestone 10 implements the Google boundary with:

- a `GoogleConnectionRepository` that stores only account display hints and
  connected-spreadsheet metadata in Preferences DataStore;
- a lifecycle-scoped Android adapter for Credential Manager,
  `AuthorizationClient`, and the exact-file Picker resolution;
- a pure `GoogleConnectionCoordinator` that maps identity, authorization,
  validation, disconnect, and sign-out outcomes;
- a small fakeable HTTPS/JSON `GoogleSheetsGateway` for Drive v3 and Sheets v4
  metadata validation; and
- immutable Settings/Main UI state that contains no Google SDK types or tokens.

The access token remains inside `export.google` for one explicit operation and is
never returned to a ViewModel, persisted, or logged.

Room owns clients, tasks, intervals, and active-timer truth. DataStore owns preferences and selection hints. UI observes `Flow` state and sends intent-like events; it does not mutate DAOs directly or own authoritative timer state.

## 2. Technology baseline

- Kotlin only, native Android.
- Jetpack Compose, Material 3, Compose Navigation, lifecycle-aware ViewModels.
- Coroutines and Flow for asynchronous work/observation.
- Room with KSP, schema export, transactions, and explicit migrations.
- Preferences DataStore.
- `java.time` on minimum SDK 26; no legacy date/time library is needed.
- Gradle Kotlin DSL with `gradle/libs.versions.toml` in the implementation milestone.
- Manual dependency injection through a small `ApplicationContainer`.
- Stable releases only. Version selection is performed and recorded when scaffolding starts, not guessed in this planning milestone.

### SDK/toolchain observation

The planning inspection found one installed platform at `android-36.1` (`AndroidVersion.ApiLevel=36.1`, Android 16, extension level 20) and Build Tools `36.0.0`; `ANDROID_HOME`/`ANDROID_SDK_ROOT` were unset. The intended baseline is:

- `minSdk = 26`;
- `targetSdk = 36`; and
- the newest stable installed compile SDK, currently API 36.1, using the stable Android Gradle Plugin syntax that officially supports minor SDK releases.

Milestone 1 must set the SDK path locally, inspect stable installed/available packages, and prove the chosen stable AGP/Gradle/Kotlin/JDK matrix with a build. If API 36.1 cannot be consumed by a stable installed toolchain, install/use the latest mutually supported stable SDK rather than adding preview build tooling. Do not commit `local.properties`. Prefer the JDK version required by the selected stable AGP (normally Android Studio's bundled JDK), not the unrelated system Java 22 discovered during planning.

## 3. Initial project shape

Use a single Android application module for the MVP. Package boundaries provide separation without premature Gradle-module overhead:

```text
app                    application, container, activity, navigation root
ui                     theme and shared Compose components
ui.main                main screen and MainViewModel
ui.tasks               create/edit task UI and ViewModels
ui.clients             reusable client management UI and ViewModel
ui.settings            settings UI and ViewModel
data                   repository implementations and transaction coordinator
data.local             Room database, entities, DAOs, migrations
data.preferences       DataStore keys/models/repository
domain                 rule-heavy services and domain errors/results
timer                  normalization, display ticker, clocks
export                 export row model/builder and orchestration
export.csv             serializer and document destination
export.xlsx            focused OOXML workbook writer and document destination
export.google          authorization and Sheets gateway
security               reserved for separately authorized optional Milestone E
ui.employees           employee directory and current-assignment settings
import.csv             bounded client-name CSV parser and document input adapter
automation             captured-date Google scheduling/pending-state coordination
notification           lock-screen/pending-export presentation adapters after approval
model                   UI/domain models that are not persistence entities
util                    narrow formatting/parsing helpers
```

Do not add one “use case” class per repository getter. Add named domain services where multiple entities, clocks, transactions, or invariants are involved.

## 4. Application container

`WorqOrderApplication` owns one lazily constructed application-scoped container. As of Milestone
13, the container constructs one retained `WorqOrderDatabase`, Room-backed client/task/active-timer
repositories, typed settings and selection Preferences DataStore repositories, UTC/device/manual-zone/elapsed
realtime adapters, one shared timer-operation mutex, one process-local live timer session,
`SelectionCoordinator`, `TimerCoordinator`, `ActiveTimerNormalizer`,
`TimerRecoveryCoordinator`, and
`TaskMutationCoordinator`.

Later milestones extend the same boundary with:

- destination-neutral export snapshot coordinator/row builder and CSV serializer;
- document-output adapter;
- Google authorization coordinator and `GoogleSheetsGateway`; and
- focused XLSX writer; and
- dispatcher provider where tests require deterministic dispatchers.

ViewModel factories request only their direct dependencies. Android framework types stay in adapters/gateways, not pure domain services. A DI framework is not planned; reconsider only if container wiring becomes demonstrably unsafe or unmaintainable.

## 5. UI state and navigation

Proposed routes:

- `main`
- `task/create/{workDateEpochDay}`
- `task/{taskId}/edit`
- `settings`
- `settings/time-zone`
- optional `settings/clients/archived`

Dialog destinations may be Compose dialogs when state restoration and accessibility remain correct; otherwise use full screens.

Each ViewModel exposes an immutable `StateFlow<UiState>` and a separate bounded effect mechanism for one-time navigation, picker launches, snackbars, and authorization UI. Collect flows lifecycle-aware. Use `SavedStateHandle` for route/UI restoration only; persisted business state remains in Room/DataStore.

`MainUiState` combines:

- displayed date;
- effective/pinned zone;
- tasks with database-computed completed totals;
- preferred/selected task;
- active-timer record and active task;
- monotonic display contribution;
- export default/connection readiness; and
- operation/error state.

The visible ticker runs only while collected and an interval is active. It emits a display refresh
signal every 200 ms, never database mutations. The five-Hz cadence was selected from physical
release profiling to avoid sustained CPU use while remaining responsive. Accumulated presentation
formats the computed exact duration as `HH:MM:SS` and truncates its sub-second remainder; this does
not reduce persistence or calculation precision.

Main keeps the timer card and complete date-selector bar outside the lower `LazyColumn`, so the
timing identity, Start/Stop control, displayed date, date arrows, and calendar action remain visible
while messages and tasks scroll independently. Task rows are sorted newest-created first when a
Room date observation emits, not on each timer refresh. This is a presentation projection only:
DAO and canonical export ordering remain independently stable.

## 6. Repository and service responsibilities

### Repositories

- `ClientRepository`: implemented in Milestone 2 with active/all-client `Flow` observations and add, rename, archive, and restore operations using one canonical-name validator. It returns typed invalid-name, duplicate-active-name, and not-found outcomes.
- `TaskRepository`: observes dates/tasks, fetches joined/detail models, reads one transactional
  joined task/client/ordered-interval snapshot for a requested export date, and atomically finds or creates
  an exact `(series, date, zone)` copy from current source metadata (client, short description, and
  hardware/software-purchases text), creates/updates/deletes daily tasks, inserts completed
  intervals, and exposes ordered validation history and completed totals.
- `ActiveTimerRepository`: observes/reads the singleton and delegates atomic open, multi-boundary
  continuation, retarget, and final close operations to `ActiveTimerDao`. Eligibility and boundary
  calculation remain outside the DAO.
- `SettingsRepository`: provides typed Flow access to theme, zone mode/manual ID, default export,
  and the safe last export attempt (destination/date/instant/outcome/category). Google connection
  metadata uses its owning typed repository; one-off XLSX retains no document connection metadata.
- `SelectedTaskRepository`: implemented in Preferences DataStore with task/series hints plus the
  effective selection date/zone needed to distinguish real daily carryover from intentional
  historical browsing. It stores no task or interval truth.

### Domain services

- `TimerCoordinator`: typed Start/Stop entry point; validates exact-today eligibility, coordinates
  the shared mutex and Room transaction APIs, and owns clock-anomaly results/live-anchor changes.
- `ActiveTimerNormalizer`: calculates all crossed boundaries from the pinned session zone, applies
  one atomic continuation chain, follows the active task selection, and establishes/re-establishes
  process-local display state without changing persisted boundaries.
- `TimerRecoveryCoordinator`: application-scoped startup/resume entry point that waits for the
  effective zone, captures one UTC instant, normalizes Room state, and then reconciles selection.
  Its own mutex makes duplicate Activity/Main resume signals idempotent.
- `SelectionCoordinator`: select/clear/observe, active-timer switching lock, dangling repair, and
  exact three-part daily rollover using current source metadata.
- `ManualIntervalValidator`: pure boundary, ordering, overlap, open/running-state, and explicit
  DST gap/overlap validation.
- `TaskMutationCoordinator`: shared task metadata validation, active-client-at-commit enforcement,
  today-only automatic selection after creation, validated manual interval add/edit/delete, and
  selected-task cleanup after daily-task deletion.
- `DurationMath`, `MidnightBoundaryCalculator`, and `LiveTimerSession`: pure accumulated duration,
  real-zone boundary, and process-local monotonic/recovery models.
- `ExportSnapshotCoordinator`: implemented from the Milestone 8 foundation; captures one instant
  under the timer-operation lock, normalizes crossed boundaries, reads the transactional Room
  snapshot, and returns the one immutable destination-neutral dataset. Main permits this workflow
  only when Room-derived active state is loaded and empty.
- `ExportRowBuilder`: owns schema version 3 in `0.2.0`, the exact 14 visible columns, duplicated
  `MM/DD/YYYY` Start/End dates, task-zone export `HH:mm`,
  accumulated `HH:MM:SS`, zero-interval rows, and deterministic internal-key sorting.
- `CsvExportCoordinator`: consumes the prepared snapshot and only serializes/packages the pending
  UTF-8 CSV before the picker opens.
- XLSX and Google adapters receive the same `ExportSnapshot`; destination adapters may
  escape/package/transport but never select, reorder, or reformat task fields or become sources of
  task truth.

## 7. Concurrency and transaction model

- Milestone 3 adds one application-scoped `Mutex`, selection/date validation, normalization, and
  clock policy. The mutex reduces same-process races; database constraints and transactions remain
  the cross-coordinator protection.
- Open storage transaction: verify no active record/open candidate, allocate the next ordinal, insert one interval with `stop = null` and unique `active_slot = 1`, insert singleton active state with matching task/interval IDs, touch the task, and return one snapshot.
- Close storage transaction: load and validate the singleton and referenced open interval, write a valid later stop, release `active_slot`, delete active state, touch the task, and return one snapshot. Missing active state is an idempotent no-op.
- Midnight normalization receives a Kotlin-calculated ordered boundary plan. One Room transaction
  closes each segment, finds or creates the exact three-part daily copy, inserts the continuation,
  retargets the singleton, and either leaves the final interval open or closes/clears it for Stop.
- Snapshot reads verify that zero active rows means zero open candidates and that one active row
  means exactly one valid matching open candidate. Orphan/mismatched states fail explicitly; no
  startup repair discards an interval.
- Task/client edits use optimistic current-state validation inside their write transaction, not only form validation.
- Export reads use one Room transaction and one captured `exportInstant` so task totals and interval durations agree.
- Google/SAF I/O occurs after the Room read transaction ends. Network/file failures never roll back or mutate task records.
- DAO write methods capable of creating/opening/closing intervals remain internal to the data layer; ViewModels cannot bypass the coordinator.

## 8. Time architecture

Use three distinct concepts:

1. `UtcClock.now(): Instant` for Start boundaries, creation/update timestamps, process recovery,
   and export snapshots.
2. `MonotonicTimeSource.elapsedRealtimeNanos()` for the live contribution and projected Stop/date
   normalization while a valid process session remains alive. Android's elapsed realtime source
   includes device sleep.
3. `EffectiveZoneIdProvider.zoneId(): ZoneId` from device mode now and validated manual settings later.

At Start/recovery, the application establishes a base duration and a monotonic anchor. It derives
future frames from the monotonic delta. Stop and normalization project an absolute UTC instant from
the current segment start plus measured monotonic active duration, preventing a wall-clock change
from replacing the displayed total. After process death, it reconstructs once from the persisted
start instant and current UTC instant, then re-anchors monotonically.

The `ActiveTimer` captures the geographical boundary zone at Start. Time-zone settings are locked while active, and external device-zone changes do not change the session's splitting zone. Historical tasks always use their stored zone ID.

Detailed algorithms and anomaly policy are in `TIMER_AND_DATE_RULES.md`.

## 9. Storage architecture

### Room

- The stable production file is `worqorder.db`, opened normally through one application-container database instance and retained.
- Foreign keys are explicit and enabled by Room.
- Version 1 schema is exported to `app/schemas/worq.order.data.local.WorqOrderDatabase/1.json`.
- The four version-1 entities are `clients`, `daily_tasks`, `work_intervals`, and `active_timer`.
- Milestone 6 evolved the database to version 2 by adding non-null
  `daily_tasks.hardware_software_purchases` with an empty-string default for existing rows and
  widening short-description validation to 400 characters. Production registers the explicit
  `1 -> 2` migration; the version-2 schema and populated migration test are committed.
- A nullable unique `work_intervals.active_slot` is the structural one-open-interval guard. `active_timer` uses fixed singleton ID `1` plus a composite foreign key to the exact interval/task pair.
- Every version change supplies explicit forward migration(s), schema JSON, and migration instrumentation tests.
- Release builds never use destructive fallback. Destructive migration may be used only in isolated test fixtures if clearly scoped.

### Preferences DataStore

DataStore stores atomic timing-selection preferences (task ID, series ID, effective selection date,
and selection ZoneId) plus typed theme, time-zone mode/manual ID, and export-default values.
The safe last export attempt is implemented from Milestone 8 onward. Google metadata is implemented
in Milestone 10; one-off XLSX adds no URI/display/status preference. Domain operations that can
create daily copies or intervals wait for the first persisted effective-zone emission. DataStore
does not contain task rows, active-timer state, passwords, service-account material, raw access
tokens, or refresh tokens.

The required production sequence uses ordinary app-private Preferences DataStore files protected
by Android's application sandbox. Separately authorized optional Milestone E may add
Keystore-backed at-rest protection while preserving these typed repository APIs; ViewModels and
composables must never handle raw keys or cryptographic payloads.

### File output

Milestone 8 uses Android user-mediated/scoped storage. Compose launches
`ActivityResultContracts.CreateDocument("text/csv")`; no storage permission is requested. A
`DocumentOutputDestination` wraps `ContentResolver` operations so output failure, UTF-8 encoding,
best-effort deletion of partial provider documents, and unit tests remain isolated. Cancellation
occurs before this adapter is called. The complete CSV string is serialized before the picker
opens, and output streams close deterministically.

Milestone 12 adds a one-off XLSX document boundary:

- Compose launches `ActivityResultContracts.CreateDocument` with the official XLSX MIME type for
  every export; no storage permission or retained URI grant is requested.
- A focused internal OOXML writer packages the already-canonical snapshot into one new workbook
  containing one `WorqOrder_YYYY-MM-DD` worksheet and the exact shared canonical table (14 columns
  beginning with schema version 3 in `0.2.0`).
- The writer never opens or modifies an existing workbook, writes formulas, or stages plaintext
  on app-private disk. Cancellation occurs before output; a failed write uses the same best-effort
  partial-document cleanup boundary as CSV.
- The complete package is built in memory before the picker opens, so changes after snapshot
  preparation cannot change the pending payload.

CSV and XLSX files are unencrypted external artifacts once handed to the user-selected provider.

### Optional encrypted local storage

The current production plan does not implement WorqOrder-managed at-rest encryption for Room or
DataStore and must not claim that it does. Android's application sandbox is the present local
access boundary. Optional Milestone E retains this separately authorized design:

- non-exportable Android Keystore material anchors versioned, purpose-bound encryption keys;
- authenticated encryption protects sensitive app-private Room/DataStore data at rest, including
  database auxiliary files and backup behavior;
- a populated plaintext-to-encrypted migration is crash-safe, non-destructive, and never falls
  back to clearing/reseeding Room;
- key loss, invalidation, ciphertext corruption, low storage, and interrupted migration fail
  closed with explicit recovery guidance;
- plaintext task/client/export content is excluded from logs, crash text, caches, clipboard, and
  app-private temporary files; and
- app code continues to receive mapped domain models, so timer/date/export rules do not become
  coupled to encryption details.

If authorized, the implementation milestone must compare whole-database encryption with bounded
field/envelope encryption against Room query/index/migration needs before selecting a stable,
GPLv3-compatible API-26 solution. Cryptographic details are recorded in `DECISIONS.md` only after
that proof. No login, biometric, app PIN, or account gate is implied by optional encryption.

User-directed CSV/XLSX files and readable Google Sheets cells are plaintext external copies.
Google traffic uses TLS and Google-managed authorization, but readable Sheets export is not
end-to-end encrypted by WorqOrder. Optional Milestone E would not extend local encryption to
those exports.

## 10. Google boundary

Google support is a replaceable gateway outside the offline core.

- Do not add Firebase.
- Do not use service accounts in an APK.
- Use stable AndroidX Credential Manager/Google ID for account choice and Google Identity Services
  `AuthorizationClient` for Google-data authorization. Do not persist raw tokens in DataStore.
- Treat authentication/account choice separately from authorization to call Drive/Sheets. Because
  there is no backend, a Credential Manager ID token is not a verified application identity and is
  discarded after the sign-in result.
- Request only `drive.file`, then use the official Android Google Picker authorization flow
  filtered to the user-pasted spreadsheet ID and the Google Sheets MIME type. Do not request a
  Drive-wide, profile, email, or all-spreadsheets scope.
- Validate the selected file through a narrow Drive v3 edit-capability read and Sheets v4 metadata
  read, then use small, fakeable HTTPS/JSON REST gateways for later export.
- Gateway operations are suspendable and return typed outcomes: offline, authorization required/expired, permission denied, not found, marker conflict, rate limited, server failure, validation failure, canceled, and success.
- `GoogleSheetsExportCoordinator` captures the shared `ExportSnapshot`, obtains a normal fresh
  authorization token for the already Picker-granted file, and maps gateway results without
  exposing Google types to Main UI state. `GoogleSheetsExportPlanner` purely decides create,
  ownership/schema conflict, or complete replacement. `GoogleSheetsBatchJsonEncoder` converts that
  plan to one atomic batch, while `RestGoogleSheetsGateway` performs the narrow structure read and
  confirmed write. No class outside the shared snapshot builder selects or formats exported
  fields.
- Released `0.1.0` Google calls are explicit user actions. Version `0.2.0` adds only the opt-in,
  captured-date automatic operation described in sections 14–15; it is bounded and uses no
  unapproved retry loop. All calls stay within the no-cost standard tier. Do not attach billing,
  request a paid quota increase, or implement a path that can generate charges. Quota exhaustion
  is a safe failure and CSV remains available.
- The supported distribution is an owner-signed APK delivered from GitHub. Debug and
  direct-release OAuth identities bind to the exact package/signing SHA-1 that produces each
  artifact.

The Milestone 9 official-document review on 2026-07-26 selected stable Credential Manager `1.6.0`,
Google ID `1.2.0`, `play-services-auth:21.6.0`, and
`kotlinx-coroutines-play-services:1.10.2`. Detailed rationale and setup are in
`GOOGLE_INTEGRATION_ADR.md` and `GOOGLE_SHEETS_SETUP.md`.

The owner requires WorqOrder to remain GPLv3, free, and open source without Google Workspace,
organization membership, a custom domain, or paid Google services. Development uses an External
Testing audience; Milestone 17 must move the release project to External/In Production so any
eligible Google Account can authorize without an owner-managed test list. The sole `drive.file`
authorization scope is non-sensitive, so brand verification remains optional unless verified
name/logo presentation is desired. If Google's policy later removes that no-cost path, do not
broaden scope or enable billing—stop and revisit the Google feature.

## 11. Security and privacy

- No local account is required for core use.
- The required production build relies on Android's app sandbox and makes no claim of
  WorqOrder-managed Room/DataStore encryption.
- Store only necessary Google spreadsheet metadata; let Google-supported components manage credentials.
- Never log task descriptions, hardware/software-purchases text, spreadsheet contents,
  authorization headers, IDs unnecessarily, or credential payloads.
- Validate spreadsheet IDs/URLs strictly and display only sanitized errors.
- Use `ValueInputOption.RAW` for Sheets so user strings are not interpreted as formulas.
- CSV preserves field text with RFC quoting. Document that downstream spreadsheet programs can interpret formula-like CSV cells; do not silently alter authoritative text without a product decision.
- Network security uses platform TLS; no cleartext traffic.
- No storage permission is expected under the selected SAF/scoped approach.
- CSV and XLSX are explicitly unencrypted user-selected external files. Readable Google Sheets
  cells rely on Google account/access controls and are not app-level end-to-end encrypted.
- Optional user-presence/app-access gating is isolated to optional Milestone E and requires a
  new explicit owner authorization.
- Optional app-private at-rest encryption is isolated to optional Milestone E and also requires
  new explicit owner authorization.

## 12. Dependency policy

The implementation dependency set should remain limited to Android/Jetpack Compose,
lifecycle/navigation, coroutines, Room, DataStore, test libraries, the smallest stable Google
identity/Sheets stack that satisfies the gateway, and the focused XLSX implementation proven in
its owning milestone gate. Encryption components are not production dependencies unless optional
Milestone E is authorized. Avoid Apache POI, broad Excel stacks, redundant cryptography
frameworks, date libraries, DI frameworks, Firebase BOM, reflection-heavy mapping layers, and
general-purpose networking stacks unless a separately approved decision demonstrates the need.

All versions live in the version catalog. Renovation is a separate reviewed change. A dependency update must pass formatting, lint, unit, Room/migration instrumentation, Compose tests, and builds.

## 13. Test architecture

- Pure JVM tests own clocks, zones, DST dates, formatting, canonical names, task-metadata
  validation/copying, interval validation, rollover, splitting, export rows, CSV serialization,
  and deterministic XLSX package/cell generation.
- `kotlinx-coroutines-test` controls dispatchers/tickers.
- Room instrumentation tests use real SQLite, primarily in-memory databases plus one named reopen fixture, for schema creation, observations, normalization conflicts, foreign keys, unique indexes, cascades/restrictions, ordering, atomic active mutations, reopen persistence, and packaged schema availability. Version 1 has no predecessor migration; every later schema version must add populated migration-path coverage.
- ViewModel tests combine fake repositories/gateways and deterministic time.
- Compose UI tests cover the main workflows, disabled states, confirmation, settings, picker/authorization launch effects, and accessibility semantics.
- Fake `Clock`, monotonic source, zone provider, document destination, and Google gateway are first-class test fixtures.
- If optional Milestone E is authorized, its instrumentation adds populated migration, key
  lifecycle/failure, DB/WAL/SHM/DataStore plaintext-canary scans, backup configuration, and
  performance/regression fixtures.

## 14. Operational behavior

Core failures are represented in UI state and remain retryable. Last export outcome stores
destination, displayed date, time, and a safe error category/detail. `MainActivity.onResume`
invokes the application-scoped timer recovery coordinator even when Main is not visible; Main
initialization/resume, date-change detection, and rule-sensitive operations provide idempotent
retries. No boot receiver, wake lock, stopwatch WorkManager job, or foreground timer service
exists.

Version `0.2.0` may add one narrowly scoped, opt-in Android background schedule for Google Sheets
only. It captures a target epoch day and ZoneId near the end of that date, so an inexact execution
after midnight still exports the preceding intended date. The scheduling adapter never owns task
rows or tokens. It invokes the same `ExportSnapshotCoordinator` and Google replacement pipeline as
manual export. CSV/XLSX remain manual. If Room reports an active timer, the coordinator stores a
typed pending target instead of exporting and asks the post-Stop notification adapter to expose a
content-free action. Exact scheduler/auth APIs require official research and owner approval in
their dedicated milestone.

## 15. v0.2.0 architecture boundaries

No item in this section describes released `0.1.0` behavior until its owning v0.2 milestone lands.

- Add `EmployeeRepository` and Room-backed active/archive operations, exposed to users as
  **Consultant** management. `DailyTask` stores both the
  optional employee relationship and assignment-time name snapshot. Directory changes never
  cascade text changes into historical tasks.
- Add a bounded `ClientCsvImportParser` and `ClientImportCoordinator`. Android document access is
  isolated behind a read-only input adapter; parsing is pure, and one repository transaction
  appends/restores normalized names without replacing existing clients. Final active observation
  remains A–Z.
- Extend task metadata validation with Work Type and canonical decimal Mileage. Add
  `BillingMinutesCalculator` as pure derived logic; do not persist or tick it.
- `ExportRowBuilder` is the only schema-3 field-selection/formatting boundary. All destinations
  receive the same 14 strings and keep exact internal instants outside the projection.
- Extend the typed settings model with `LandscapeOrientation.RIGHT_HANDED` and
  `LandscapeOrientation.LEFT_HANDED`; the repository owns serialization, default/fallback, and
  Flow observation. Composables never read preference keys directly.
- Keep portrait Main on its production layout. A window-aware landscape root chooses one
  approximately equal two-column composition and mirrors column placement from the typed
  preference. In Right-handed mode the independently scrolling task `LazyColumn` fills the left
  column down to the shared action bottom margin and retains its attached right-edge scrollbar.
  A spanning top bar keeps WorqOrder at the far left and Settings at the far right. Below it, the
  control column owns timer, date, and Export/Add regions. Left-handed mirrors the two content
  columns without moving the global bar, reversing task order, or changing accessibility meaning.
- About reads generated version/stability metadata and renders only
  `WorqOrder v{versionName} - stable`; it creates no external intent or repository link.
- Reuse one presentation formatter for task-zone `HH:mm` Start/Stop values in interval cards and
  canonical export. Persistence/editing models retain exact instants and DST occurrence details.
- Put any running-timer lock-screen integration behind an interface owned by application/timer
  coordination. It observes authoritative active-timer identity and never becomes timer
  authority. Dismissal state is scoped to the active interval ID.
- Research current official Android Clock-like timer/alarm presentation before choosing a stable
  notification/AppWidget mechanism. Prefer system-rendered elapsed time so the app does not
  schedule ticks. Do not add a foreground service, wake lock, alarm, or WorkManager loop solely to
  maintain the surface. Treat permission, channel, lock-screen privacy, OEM suppression, process
  death, reboot, and swipe dismissal as explicit states rather than promising visibility.

## 16. Optional Milestone E boundary

Milestone E is an unscheduled, release-agnostic backburner item outside every release scope until
the owner explicitly assigns it. It alone owns optional local at-rest encryption, application locking through an approved
biometric/device-credential/PIN design, and screenshot/Recents privacy options. These concerns
remain adapters around current repository/navigation boundaries, require separate explicit owner
authorization, and may not be inferred from ordinary security or release work.
