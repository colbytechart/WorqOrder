# WorqOrder Requirements Traceability

## 1. Scope and status

This matrix audits the `0.2.0` release candidate through Milestone 29 against the current approved
specifications. `Pass` means the implementation and test evidence exist. `Partial` means
the implementation exists but final release-device, human, or external configuration evidence is
still required. `N/A` identifies an explicitly optional or prohibited capability.

The old Milestone 16 prompt item “No XLSX support” is superseded by the approved production
specification. XLSX is an implemented one-off export destination and was regression-audited without
changing its implementation in this milestone.

## 2. Platform and architecture

| Requirement | Implementation | Automated evidence | Manual evidence | Status |
|---|---|---|---|---|
| Native Android, Kotlin, Compose, Material 3 | `app/build.gradle.kts`, `ui/**`, `ui/theme/**` | Debug/release compilation; Compose suites | Accepted emulator/physical-device runs through Milestone 15 | Pass |
| Min API 26; compile 36.1; target 36 | `app/build.gradle.kts` | AAR metadata, debug/release build; 79-test API 26/API 36.1/API 37.1 suites | API 26/36.1 smoke plus API 37.1 forward compatibility passed | Pass |
| Kotlin DSL, version catalog, KSP | Root/module Gradle files, `gradle/libs.versions.toml` | Gradle configuration and KSP tasks | N/A | Pass |
| Restrained MVVM/repository/domain architecture | `app`, `ui`, `data`, `domain`, `timer`, `export` packages | ViewModel/domain/repository tests | N/A | Pass |
| Manual application container; no DI framework | `app/ApplicationContainer.kt` | Debug/release compilation | N/A | Pass |
| Stable dependency releases | Version catalog; dependency report; pinned Gradle wrapper checksum | Resolution/lint compatibility plus 36-package OSV audit | Official stable indexes and published wrapper hashes checked 2026-07-29 | Pass |
| No Java app code, XML layouts, Firebase, backend, foreground timer service, exact alarm, app-owned wake lock, or WorkManager timer ticks | Source tree, manifest, dependency graph | Repository/static scans | N/A | Pass |

## 3. Local clients, tasks, and intervals

| Requirement / acceptance IDs | Implementation | Automated evidence | Manual evidence | Status |
|---|---|---|---|---|
| Client add/validation/sort (`CL-01`–`CL-03`) | `ClientNameNormalizer`, `RoomClientRepository`, client ViewModel/UI | Normalizer, client ViewModel, Room, Compose tests | Accepted Milestone 5 run | Pass |
| Client rename/archive/restore (`CL-04`–`CL-07`) | Client repository/DAO and management UI | Client ViewModel, Room FK/join, Compose confirmation tests | Accepted Milestone 5 run | Pass |
| Task creation and selection | `TaskMutationCoordinator`, `SelectionCoordinator`, create UI/ViewModel | Task mutation, create ViewModel/Compose, Main tests | Accepted Milestones 4/6 physical run | Pass |
| Description and purchases fields, each 400 code points | `TaskMetadataValidator`, Room task columns, create/edit UI | Validator, create/edit ViewModel and Compose tests; migration 1→2 | Accepted Milestone 6 run | Pass |
| Task edit/delete; same-series siblings retained | Task mutation coordinator, task repository/DAO, edit UI | Task mutation, edit ViewModel/Compose, Room cascade/restriction tests | Accepted Milestone 6 run | Pass |
| Repeated ordered intervals and totals | Interval DAO/entity, duration queries, edit UI | Room interval ordering, duration math, task/timer tests | Accepted timer/task manual runs | Pass |
| Manual add/edit/delete and overlap/date/DST validation | `ManualIntervalValidator`, task mutation coordinator, edit UI | Validator, mutation, edit ViewModel/Compose tests | Accepted Milestone 6 run | Pass |
| Room is authoritative; DataStore is preference/selection only | Room repositories, typed DataStore repositories | Room persistence/reopen and DataStore recreation tests | Process/reboot manual tests | Pass |

## 4. Timer, dates, and lifecycle

| Requirement / acceptance IDs | Implementation | Automated evidence | Manual evidence | Status |
|---|---|---|---|---|
| Start eligibility and exactly one active timer | `TimerCoordinator`, singleton `ActiveTimerEntity`, transactional DAO | Timer concurrency and Room structural/transaction tests | Start/Stop verified on physical device | Pass |
| Stop, repeated intervals, frozen accumulated total | Timer coordinator, live session, Main ViewModel | Timer and Main ViewModel tests | Accepted Milestones 4/13 runs | Pass |
| Timestamp-derived display; no persisted ticks | `LiveTimerSession`, collection-scoped Main ticker | Live session and “no tick writes” ViewModel tests | Background/screen-lock tests | Pass |
| Process/activity/reboot reconstruction | Recovery coordinator, Room active snapshot, activity/Main resume hooks | Recovery concurrency/process tests | Rotation, Don't Keep Activities, process kill, Recents, reboot passed | Pass |
| Daily selection rollover; no duplicate copy | `SelectionCoordinator`, three-part task uniqueness | Selection/restart tests; Room unique index | Zone-change manual tests | Pass |
| One/multiple midnight splitting and idempotence | Boundary calculator, active normalizer, transactional DAO | Real-zone/DST/multi-midnight unit and Room tests | Accepted automated evidence | Pass |
| Device/manual ZoneId and historical stability | Device zone source, settings provider/repository | Zone provider/settings/selection tests | Device and manual zone changes passed | Pass |
| Spring-forward/fall-back correctness | `java.time` boundaries and validator | Boundary, validator, timer tests | Both transitions passed manually | Pass |
| Forward/backward wall-clock correction while process lives | Monotonic projected Stop/normalization | Live session, coordinator, recovery, Main tests | Both correction tests passed after D-057 | Pass |
| No foreground/background stopwatch execution | No service/worker/alarm; persisted open interval | Static manifest/dependency scan | Background, lock, process, reboot checks | Pass |

## 5. Main UI and settings

| Requirement / acceptance IDs | Implementation | Automated evidence | Manual evidence | Status |
|---|---|---|---|---|
| Main regions, empty/loading/error states (`UI-01`–`UI-03`) | `MainScreen`, immutable `MainUiState` | Main Compose/navigation and ViewModel tests | Accepted device runs | Pass |
| Pinned timer/date-selector and independent lower scrolling region (`UI-03a`) | `MainContent`, `MainContentScrollIndicator` | Pinned-bounds long-list plus 200%-font 640 × 360 dp landscape tests cover both fixed regions and list reachability | Focused large-scale landscape connected regression passed | Pass |
| Newest-created task first (`UI-03a`) | Main ViewModel observation projection | Main ViewModel ordering test | Accepted emulator run | Pass |
| Selection/running lock/date navigation (`UI-04`–`UI-08`) | Main ViewModel/domain/UI | Main ViewModel and Compose tests | Accepted Milestones 4/13 runs | Pass |
| Explicit export date/destination (`UI-09`) | Main state/screen strings | Main ViewModel/Compose tests | Accepted CSV/Google/XLSX runs | Pass |
| Theme System/Light/Dark and immediate persistence | Settings/DataStore/theme root | Settings and application-theme tests | Accepted Milestone 7 run | Pass |
| Device/manual time-zone settings and running lock | Settings repository/ViewModel/UI | Settings repository/ViewModel/Compose tests | Accepted Milestones 7/13 runs | Pass |
| Export destination and conditional Google settings | Settings state/UI | Settings ViewModel/Compose tests | Accepted Milestone 11 run | Pass |

## 6. Export and Google connection

| Requirement / acceptance IDs | Implementation | Automated evidence | Manual evidence | Status |
|---|---|---|---|---|
| One immutable 15-column schema-4 canonical snapshot | `ExportSnapshotCoordinator`, `ExportModels` | Export row/CSV and destination coordinator tests | Cross-destination manual checks | Pass |
| Stable row ordering, zero-interval rows, escaping, Unicode, duration format | Export row builder and CSV serializer | CSV serializer/row tests | CSV export opened successfully | Pass |
| CSV `ACTION_CREATE_DOCUMENT`, neutral cancellation, no mutation | CSV coordinator/document destination/Main | JVM, contract, Main tests | Accepted Milestone 8 run | Pass |
| One-off XLSX from the same snapshot | Existing focused XLSX writer/coordinator | Existing writer/coordinator/contract/Main tests | Owner confirmed XLSX export works | Pass |
| Google sign-in, `drive.file`, one spreadsheet, disconnect/sign-out | Google authorizer/coordinator/repository/settings | Coordinator, DataStore, request-scope, ViewModel/UI tests | Owner-signed release passed with a fresh account never listed as a tester; connection/restart/sign-out passed | Pass |
| Google validation and typed failure mapping | Google coordinator and REST gateway | Parser/coordinator/fake gateway tests | Live validation passed | Pass |
| Per-date marked Google worksheet and idempotent replacement | Planner, encoder, gateway, export coordinator | Planner/encoder/coordinator tests | Re-export and blank-first-sheet flows passed after fixes | Pass |
| Running-timer export lockout for all destinations | Main ViewModel/UI and shared snapshot precondition | Main ViewModel/Compose tests | Manual lifecycle test 18 passed after fix | Pass |
| `0.2.0`: no task import/sync/local mutation or unbounded automatic retry | Export/import coordinators and gateway boundaries | Coordinator/fake tests and static review | Live export/import behavior accepted | Pass |

## 7. Persistence, migration, accessibility, and security

| Requirement / acceptance IDs | Implementation | Automated evidence | Manual evidence | Status |
|---|---|---|---|---|
| Room versioning/schema export/no destructive fallback | Database v4, explicit migrations, committed schemas 1/2/3/4 | Migration and packaged-schema instrumentation tests | Accepted emulator runs through Milestone 28 | Pass |
| Existing populated v1/v2 data and open timer migrate to v4 | `MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_3_4` | Populated `1→4` and released `2→4` migration tests | Owner-signed populated `0.1.0` to `0.2.0` in-place update passed | Pass |
| Data survives recreation/background/process/reboot | Room/DataStore/recovery architecture | Reopen/recovery tests | Owner's Milestone 13 checklist passed | Pass |
| Accessibility semantics/touch/error/confirmation (`A11Y-01`–`A11Y-05`) | Compose screens and semantics | Compose semantics, large-font, dialog/state tests exist | Human TalkBack and display-scaling checks passed | Pass |
| Narrow/standard/large/landscape/large-text usability | Adaptive Compose layouts, landscape top actions, bounded compact pinned header | Narrow, 200%-font, short-landscape/navigation tests and complete 79-test suite pass | Corrected large-scale landscape visual check passed | Pass |
| No credentials/tokens/private keys committed | `.gitignore`, local config injection, redacted token type | Release tracked/intended-file scan: no credential-pattern matches | Permanent key is external and ignored | Pass |
| Least-scope Google access and safe token handling | `drive.file` request factory; in-memory token wrapper | Scope/connection tests and static scan | Live External-test authorization passed | Pass |
| No broad permissions; backup disabled | Manifest plus `data_extraction_rules.xml` | Manifest/lint/static audit | N/A | Pass |
| WorqOrder-managed at-rest encryption | Release-agnostic, unscheduled optional Milestone E | N/A | N/A | N/A |
| Biometric/PIN/account-gated app access and screenshot/Recents controls | Release-agnostic, unscheduled optional Milestone E | N/A | N/A | N/A |

## 8. Current audit gate

No required behavior is known to be failing. Permanent signing and production OAuth configuration
are complete, and the signed release APK verifies with one RSA-4096 signer. The owner-signed
fresh-account Google exercise passed without a tester-list or owner-intervention dependency. The
representative physical performance observation passes after the five-Hz timer refinement. The
accessibility, minimum, target, next-API, populated-update, complete connected, performance, and
production Google gates pass. Staging the verified APK as a GitHub Release asset is a publication
step, not an implementation blocker.

## 9. `0.2.0` traceability

| Requirement | Planned implementation | Required automated evidence | Manual/official gate | Status |
| --- | --- | --- | --- | --- |
| Non-destructive `0.1.0` update | Room migration, schema export, typed preference defaults | Populated v1/v2 migrations including open timer passed in connected suite | Signed install-update smoke passed | Pass |
| 15-column canonical schema 4 with repeated Start/End dates and Billing Status | Shared export row builder/snapshot | exact headers, `MM/DD/YYYY` duplicated dates, CSV/XLSX/Google equivalence, schema-2/schema-3 owned-tab upgrade | Open representative files/sheet | Pass through M28; final release regression repeats in M29 |
| Client-name CSV import append/A–Z | `ClientCsvParser`, Android bounded document source, import coordinator, Client DAO transaction, Client Management UI | UTF-8/BOM/quoting/line breaks/limits, file policy, normalization/duplicates, restore/rollback, ViewModel and Compose states | Owner verified valid and invalid import behavior in Milestone 20 | Pass |
| Consultant directory/selection/history | Employee Room repository, `ConsultantSelectionCoordinator`, typed DataStore selection, dedicated Consultant Management route, Settings selector, Create/Edit UI, transaction-captured task snapshot | repository/coordinator/ViewModel plus Room snapshot, active-reference race, persistence, dedicated-route navigation, and connected UI tests | Consultant Management, Settings selection, and Create/Edit assignment workflow | Pass through M23; final 97-test connected gate and manual Settings inspection passed |
| Work Type, Billing Status, and Mileage | Task metadata/repository/Create/Edit UI | persistence/migration, nullable history, Billable default, canonical decimal/input validation, ViewModel, Compose, and rollover coverage | Create/edit workflow including migrated blank status | Pass through M28 |
| Derived Billing Minutes | Pure calculator, observed task-detail projection, and export projection | zero, sub-15, boundary, owner example, long totals, interval-mutation refresh, and connected presentation coverage passed | Task-detail check | Pass through M22; 93-test connected gate passed |
| About text and strict clock-time `hh:mm a` | Build metadata and one shared UI/export clock formatter | exact text/no link; AM/PM, DST, destination-equivalence, and precision-preservation tests passed in the 190-test JVM and final 97-test connected gates | Settings/interval/export check | Pass through M23; zero failures, errors, or skips |
| Handed landscape | typed `LandscapeHandedness` preference, Settings radio controls, immutable Main projection, and mirrored two-column Main | default/corrupt fallback, persistence, ViewModel projection, Settings semantics, right/left geometry, 200%-text reachability, and portrait regressions passed in the 191-test JVM and final 98-test connected gates | Owner verified both handed modes, persistence, portrait preservation, task scrolling, controls, and responsive behavior | Pass through M24; zero connected failures, errors, or skips |
| Automatic Google captured-date export | `AutomaticGoogleExportManager`, one-shot WorkManager scheduler/worker, DataStore target tuple, Settings switch, pending notification | 200-test JVM gate and 100-test connected gate cover manager blockers/success/active-timer/disable, target persistence, conditional UI, inline recovery, and shared Google idempotency | First checklist half and notification denial/scroll behavior passed; remaining device/OEM timing cases deferred | Pass implementation and automated gate; partial manual device coverage documented |
| Running lock-screen surface | Room-backed notification coordinator, standard private/public chronometer notification, typed per-interval dismissal, Start/Stop/app/resume/date/boot reconciliation | coordinator authority/duration/dismissal/permission/clock tests; 208 JVM and 102 connected tests passed with zero failures/errors/skips | Owner passed permission/channel/private-public content, tap, swipe, Stop/new Start, background/lock, process/Recents, reboot, force-stop, and short resource checks | Pass through M28 |
| `0.2.0` release readiness | existing direct-signing/release pipeline | clean 208-test JVM/build/lint gate, 102-test connected gate, and 180-coordinate OSV review pass | API-26/API-36.1, signed-update, production non-test-account Google, signer, checksum, install, and smoke gates pass | Pass; public-download checksum/install confirmation follows publication |

## 10. Planned `0.3.0` traceability

| Requirement | Owning milestone/location | Required automated evidence | Manual gate | Planning status |
| --- | --- | --- | --- | --- |
| Preserve all schema-4 data while converting one task/many intervals to one task/one interval | M30; Room 4-to-5 migration, entities, DAOs | populated zero/one/many/open migration, counts/fields/FKs, reopen, schema JSON | signed populated `0.2.0` update in M35 | Approved, not implemented |
| Enforce zero or one interval per task | M30; unique interval `task_id` plus repositories | constraint, cascade, active-slot/singleton, invalid second insert | DB Inspector spot check | Approved, not implemented |
| Repeated Start creates exactly one copied task at zero | M31; TimerCoordinator and atomic Room operation | metadata/null copy, selection, concurrent Start, source immutability | repeated Start workflow | Approved, not implemented |
| No date/ZoneId rollover task creation | M31; SelectionCoordinator/recovery | startup/resume/date/zone/browse no-creation and stale-selection clear | next-date simulation | Approved, not implemented |
| Midnight closes exactly once with no continuation | M32; normalizer/recovery/notification | boundary/DST/missed-day/idempotence/concurrency/process/reboot | foreground and late-execution simulations | Approved, not implemented |
| Midnight close precedes captured-date automatic Google export | M32; automatic manager/worker | close-before-snapshot, delay/pending/idempotence/failure | controlled boundary Google test | Approved, not implemented |
| Singular Interval UI | M33; Edit Task/ViewModels | empty/add/edit/delete/running/semantics/large-text | portrait/landscape inspection | Approved, not implemented |
| One 13-column schema-5 row per task in CSV/XLSX/Google | M33; shared ExportRowBuilder/adapters | exact headers/values/order/equivalence, owned schema 2–4 upgrade, conflicts | open all three outputs | Approved, not implemented |
| No stale current rollover/multi-interval contracts | M34; code/docs audit | reference/static checks plus full regression | documentation review | Approved, not implemented |
| Release-safe `0.2.0` upgrade and `0.3.0` artifact | M35; QA/release documents | full clean/API matrix/security/dependency gates | signed update, Google/export, checksum/install | Approved, not implemented |
| Safe optional environment retirement instructions | M36; `PROJECT_TEARDOWN_GUIDE.md` | documentation/path/safety review only | owner inventory review | Approved, not started |
