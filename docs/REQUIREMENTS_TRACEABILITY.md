# WorqOrder Requirements Traceability

## 1. Scope and status

This matrix audits the required production application through Milestone 17 against the current
approved specifications. `Pass` means the implementation and test evidence exist. `Partial` means
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
| No Java app code, XML layouts, Firebase, backend, foreground timer service, alarm, wake lock, WorkManager ticks | Source tree, manifest, dependency graph | Repository/static scans | N/A | Pass |

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
| One immutable nine-column canonical snapshot | `ExportSnapshotCoordinator`, `ExportModels` | Export row/CSV and destination coordinator tests | Cross-destination manual checks | Pass |
| Stable row ordering, zero-interval rows, escaping, Unicode, duration format | Export row builder and CSV serializer | CSV serializer/row tests | CSV export opened successfully | Pass |
| CSV `ACTION_CREATE_DOCUMENT`, neutral cancellation, no mutation | CSV coordinator/document destination/Main | JVM, contract, Main tests | Accepted Milestone 8 run | Pass |
| One-off XLSX from the same snapshot | Existing focused XLSX writer/coordinator | Existing writer/coordinator/contract/Main tests | Owner confirmed XLSX export works | Pass |
| Google sign-in, `drive.file`, one spreadsheet, disconnect/sign-out | Google authorizer/coordinator/repository/settings | Coordinator, DataStore, request-scope, ViewModel/UI tests | Owner-signed release passed with a fresh account never listed as a tester; connection/restart/sign-out passed | Pass |
| Google validation and typed failure mapping | Google coordinator and REST gateway | Parser/coordinator/fake gateway tests | Live validation passed | Pass |
| Per-date marked Google worksheet and idempotent replacement | Planner, encoder, gateway, export coordinator | Planner/encoder/coordinator tests | Re-export and blank-first-sheet flows passed after fixes | Pass |
| Running-timer export lockout for all destinations | Main ViewModel/UI and shared snapshot precondition | Main ViewModel/Compose tests | Manual lifecycle test 18 passed after fix | Pass |
| `0.1.0`: no import, sync, local mutation, or automatic retry | Export coordinators/gateway boundaries | Coordinator/fake tests and static review | Live retry behavior accepted | Pass |

## 7. Persistence, migration, accessibility, and security

| Requirement / acceptance IDs | Implementation | Automated evidence | Manual evidence | Status |
|---|---|---|---|---|
| Room versioning/schema export/no destructive fallback | Database v2, migrations, committed schemas 1/2 | Migration and packaged-schema instrumentation tests passed in the 78-test connected suite | Accepted emulator run | Pass |
| Existing populated v1 data and open timer migrate to v2 | `MIGRATION_1_2` | Populated migration instrumentation test passed in the 78-test connected suite | Accepted emulator run | Pass |
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

## 9. Planned `0.2.0` traceability (not yet implemented)

| Requirement | Planned implementation | Required automated evidence | Manual/official gate | Status |
| --- | --- | --- | --- | --- |
| Non-destructive `0.1.0` update | Room migration, schema export, typed preference defaults | Populated v2 migration including open timer | Install-update smoke | Planned M19 |
| 13-column canonical schema 3 | Shared export row builder/snapshot | CSV/XLSX/Google equivalence and schema-2 owned-tab upgrade | Open representative files/sheet | Planned M19/M22 |
| Client-name CSV import append/A–Z | Parser, document input, transaction coordinator | Quoting, corruption, bounds, duplicates, restore, rollback | Picker/import sample | Planned M20 |
| Employee directory/selection/history | Employee Room repository, DataStore selection, task snapshot | CRUD/conflict/archive/snapshot/migration/rollover tests | Settings/task workflow | Planned M21/M22 |
| Work Type and Mileage | Task metadata/repository/Create/Edit UI | validation, keyboard semantics, persistence/migration | Create/edit workflow | Planned M22 |
| Derived Billing Minutes | Pure calculator and task/export projections | zero, sub-15, boundary, examples, long totals | Task/export check | Planned M19/M22 |
| About text and interval `HH:mm` | Build metadata and shared display formatter | exact text/no link; precision-preservation tests | Settings/interval check | Planned M23 |
| Handed landscape | typed preference and mirrored two-column Main | persistence, semantics, short/large-scale Compose tests | both handed modes/API range | Planned M24 |
| Automatic Google captured-date export | research-approved scheduler/coordinator | target date, pending Stop, failures, races, idempotence | official research approval + delayed/device test | Planned M25/M26 |
| Running lock-screen surface | research-approved platform adapter | authority/dismissal/permission/process/performance tests | official alternatives approval + device test | Planned M27/M28 |
| `0.2.0` release readiness | existing direct-signing/release pipeline | full relevant regression and migration gates | signed update/fresh install/Google account | Planned M29 |
