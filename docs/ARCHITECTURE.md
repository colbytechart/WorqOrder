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
Room | DataStore | clocks/zones | CSV output | Google Sheets gateway
```

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
export.google          authorization and Sheets gateway
model                   UI/domain models that are not persistence entities
util                    narrow formatting/parsing helpers
```

Do not add one “use case” class per repository getter. Add named domain services where multiple entities, clocks, transactions, or invariants are involved.

## 4. Application container

`WorqOrderApplication` owns one lazily constructed application-scoped container. As of Milestone
6, the container constructs one retained `WorqOrderDatabase`, Room-backed client/task/active-timer
repositories, the selection-only Preferences DataStore repository, UTC/device-zone/elapsed
realtime adapters, one shared timer-operation mutex, one process-local live timer session,
`SelectionCoordinator`, `TimerCoordinator`, `ActiveTimerNormalizer`, and
`TaskMutationCoordinator`.

Later milestones extend the same boundary with:

- repositories/providers for remaining typed settings and manual effective ZoneId mode;
- export-row builder and CSV serializer;
- document-output adapter;
- Google authorization coordinator and `GoogleSheetsGateway`; and
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

The visible ticker runs only while collected and an interval is active. It emits display refresh signals (for example, every 16–100 ms depending on performance), never database mutations. Format milliseconds from the computed duration; do not imply 1 ms refresh precision.

## 6. Repository and service responsibilities

### Repositories

- `ClientRepository`: implemented in Milestone 2 with active/all-client `Flow` observations and add, rename, archive, and restore operations using one canonical-name validator. It returns typed invalid-name, duplicate-active-name, and not-found outcomes.
- `TaskRepository`: observes dates/tasks, fetches joined/detail models, atomically finds or creates
  an exact `(series, date, zone)` copy from current source metadata (client, short description, and
  hardware/software-purchases text), creates/updates/deletes daily tasks, inserts completed
  intervals, and exposes ordered validation history and completed totals.
- `ActiveTimerRepository`: observes/reads the singleton and delegates atomic open, multi-boundary
  continuation, retarget, and final close operations to `ActiveTimerDao`. Eligibility and boundary
  calculation remain outside the DAO.
- `SettingsRepository`: typed Flow access to theme, zone mode/manual ID, default export, spreadsheet metadata, and last export status.
- `SelectedTaskRepository`: implemented in Preferences DataStore with task/series hints plus the
  effective selection date/zone needed to distinguish real daily carryover from intentional
  historical browsing. It stores no task or interval truth.

### Domain services

- `TimerCoordinator`: typed Start/Stop entry point; validates exact-today eligibility, coordinates
  the shared mutex and Room transaction APIs, and owns clock-anomaly results/live-anchor changes.
- `ActiveTimerNormalizer`: calculates all crossed boundaries from the pinned session zone, applies
  one atomic continuation chain, follows the active task selection, and re-anchors display state.
- `SelectionCoordinator`: select/clear/observe, active-timer switching lock, dangling repair, and
  exact three-part daily rollover using current source metadata.
- `ManualIntervalValidator`: pure boundary, ordering, overlap, open/running-state, and explicit
  DST gap/overlap validation.
- `TaskMutationCoordinator`: shared task metadata validation, active-client-at-commit enforcement,
  today-only automatic selection after creation, validated manual interval add/edit/delete, and
  selected-task cleanup after daily-task deletion.
- `DurationMath`, `MidnightBoundaryCalculator`, and `LiveTimerSession`: pure accumulated duration,
  real-zone boundary, and process-local monotonic/recovery models.
- `ExportRowBuilder`: takes a consistent Room snapshot and emits stable logical rows.
- `ExportCoordinator`: normalize, snapshot, route by destination, record outcome, and never modify task data as part of delivery.

## 7. Concurrency and transaction model

- Milestone 3 adds one application-scoped `Mutex`, selection/date validation, normalization, and
  clock policy. The mutex reduces same-process races; database constraints and transactions remain
  the cross-coordinator protection.
- Open storage transaction: verify no active record/open candidate, allocate the next ordinal, insert one interval with `stop = null` and unique `active_slot = 1`, insert singleton active state with matching task/interval IDs, touch the task, and return one snapshot.
- Close storage transaction: load and validate the singleton and referenced open interval, write a valid later stop, release `active_slot`, delete active state, touch the task, and return one snapshot. Missing active state is an idempotent no-op.
- Midnight normalization receives a Kotlin-calculated ordered boundary plan. One Room transaction
  closes each segment, finds or creates the exact three-part daily copy, inserts the continuation,
  retargets the singleton, and either leaves the final interval open or closes/clears it for Stop.
- Task/client edits use optimistic current-state validation inside their write transaction, not only form validation.
- Export reads use one Room transaction and one captured `exportInstant` so task totals and interval durations agree.
- Google/SAF I/O occurs after the Room read transaction ends. Network/file failures never roll back or mutate task records.
- DAO write methods capable of creating/opening/closing intervals remain internal to the data layer; ViewModels cannot bypass the coordinator.

## 8. Time architecture

Use three distinct concepts:

1. `UtcClock.now(): Instant` for persisted boundaries, creation/update timestamps, recovery, and export snapshots.
2. `MonotonicTimeSource.elapsedRealtimeNanos()` for the live contribution while a process session remains alive. Android's elapsed realtime source includes device sleep.
3. `EffectiveZoneIdProvider.zoneId(): ZoneId` from device mode now and validated manual settings later.

At Start/recovery, the UI establishes a base duration and a monotonic anchor. It derives future frames from the monotonic delta. After process death, it reconstructs once from the persisted start instant and current UTC instant, then re-anchors monotonically.

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

Milestone 3 implements only atomic timing-selection preferences: task ID, series ID, effective
selection date, and selection ZoneId. Theme, time-zone mode/manual ID, export default, spreadsheet
metadata, and last export outcome remain Milestone 7 or later. DataStore does not contain task
rows, interval state, passwords, service-account material, raw access tokens, or refresh tokens.

### File output

Use Android user-mediated/scoped storage. A `DocumentOutputDestination` wraps `ContentResolver` operations so cancellation, output failure, encoding, and unit/instrumentation tests can be isolated. The open CSV bytes are fully serialized before requesting/committing output where practical; close streams deterministically.

## 10. Google boundary

Google support is a replaceable gateway outside the offline core.

- Do not add Firebase.
- Do not use service accounts in an APK.
- Use Google-supported Android account authorization, with Google Play services managing authorization credentials. Do not persist raw tokens in DataStore.
- Treat authentication/account identity separately from authorization to call Sheets.
- Request only the Sheets scope required for user-entered arbitrary spreadsheet IDs and document why; no Drive-wide scope.
- Use the Sheets API v4 through a stable supported client or a small REST adapter, selected after an implementation-time official-doc review.
- Gateway operations are suspendable and return typed outcomes: offline, authorization required/expired, permission denied, not found, marker conflict, rate limited, server failure, validation failure, canceled, and success.

The planning review (2026-07-22) found current official guidance directing Android apps to Credential Manager for Sign in with Google and to the Google Identity authorization API for access to Google data. Official samples can mention alpha dependencies even when stable-only policy forbids them; therefore Milestone 9 must select a stable supported path or document an explicit exception for approval. See `EXPORT_SPEC.md` for the setup and verification gate.

## 11. Security and privacy

- No local account is required for core use.
- Store only necessary Google spreadsheet metadata; let Google-supported components manage credentials.
- Never log task descriptions, hardware/software-purchases text, spreadsheet contents,
  authorization headers, IDs unnecessarily, or credential payloads.
- Validate spreadsheet IDs/URLs strictly and display only sanitized errors.
- Use `ValueInputOption.RAW` for Sheets so user strings are not interpreted as formulas.
- CSV preserves field text with RFC quoting. Document that downstream spreadsheet programs can interpret formula-like CSV cells; do not silently alter authoritative text without a product decision.
- Network security uses platform TLS; no cleartext traffic.
- No storage permission is expected under the selected SAF/scoped approach.

## 12. Dependency policy

The implementation dependency set should remain limited to Android/Jetpack Compose, lifecycle/navigation, coroutines, Room, DataStore, test libraries, and the smallest stable Google identity/Sheets stack that satisfies the gateway. Avoid date libraries, Excel libraries, DI frameworks, Firebase BOM, reflection-heavy mapping layers, and general-purpose networking stacks unless the Google client choice demonstrably needs one.

All versions live in the version catalog. Renovation is a separate reviewed change. A dependency update must pass formatting, lint, unit, Room/migration instrumentation, Compose tests, and builds.

## 13. Test architecture

- Pure JVM tests own clocks, zones, DST dates, formatting, canonical names, task-metadata
  validation/copying, interval validation, rollover, splitting, export rows, and CSV serialization.
- `kotlinx-coroutines-test` controls dispatchers/tickers.
- Room instrumentation tests use real SQLite, primarily in-memory databases plus one named reopen fixture, for schema creation, observations, normalization conflicts, foreign keys, unique indexes, cascades/restrictions, ordering, atomic active mutations, reopen persistence, and packaged schema availability. Version 1 has no predecessor migration; every later schema version must add populated migration-path coverage.
- ViewModel tests combine fake repositories/gateways and deterministic time.
- Compose UI tests cover the main workflows, disabled states, confirmation, settings, picker/authorization launch effects, and accessibility semantics.
- Fake `Clock`, monotonic source, zone provider, document destination, and Google gateway are first-class test fixtures.

## 14. Operational behavior

Core failures are represented in UI state and remain retryable. Last export outcome stores destination, displayed date, time, and a safe error category/detail. There is no background auto-sync or scheduled export. Process recovery is triggered on app startup/resume and before Start, Stop, edit, delete, and export operations that depend on normalized timer state.
