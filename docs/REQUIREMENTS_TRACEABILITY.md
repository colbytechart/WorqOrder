# WorqOrder Requirements Traceability

## 1. Scope and status

This matrix audits the required production application through Milestone 16 against the current
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
| Min API 26; compile 36.1; target 36 | `app/build.gradle.kts` | AAR metadata, debug/release build | Final min/target API device matrix is Milestone 17 | Partial |
| Kotlin DSL, version catalog, KSP | Root/module Gradle files, `gradle/libs.versions.toml` | Gradle configuration and KSP tasks | N/A | Pass |
| Restrained MVVM/repository/domain architecture | `app`, `ui`, `data`, `domain`, `timer`, `export` packages | ViewModel/domain/repository tests | N/A | Pass |
| Manual application container; no DI framework | `app/ApplicationContainer.kt` | Debug/release compilation | N/A | Pass |
| Stable dependency releases | Version catalog; dependency report | Resolution and lint compatibility checks | Live advisory/CVE lookup not performed offline | Partial |
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
| One/multiple midnight splitting and idempotence | Boundary calculator, active normalizer, transactional DAO | Real-zone/DST/multi-midnight unit and Room tests | Natural midnight runs deferred to optional Milestone 18 by owner | Pass |
| Device/manual ZoneId and historical stability | Device zone source, settings provider/repository | Zone provider/settings/selection tests | Device and manual zone changes passed | Pass |
| Spring-forward/fall-back correctness | `java.time` boundaries and validator | Boundary, validator, timer tests | Both transitions passed manually | Pass |
| Forward/backward wall-clock correction while process lives | Monotonic projected Stop/normalization | Live session, coordinator, recovery, Main tests | Both correction tests passed after D-057 | Pass |
| No foreground/background stopwatch execution | No service/worker/alarm; persisted open interval | Static manifest/dependency scan | Background, lock, process, reboot checks | Pass |

## 5. Main UI and settings

| Requirement / acceptance IDs | Implementation | Automated evidence | Manual evidence | Status |
|---|---|---|---|---|
| Main regions, empty/loading/error states (`UI-01`–`UI-03`) | `MainScreen`, immutable `MainUiState` | Main Compose/navigation and ViewModel tests | Accepted device runs | Pass |
| Pinned timer/date-selector and independent lower scrolling region (`UI-03a`) | `MainContent`, `MainContentScrollIndicator` | Pinned-bounds long-list Compose test covers both fixed regions | Corrected 16-test Main connected suite passed | Pass |
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
| Google sign-in, `drive.file`, one spreadsheet, disconnect/sign-out | Google authorizer/coordinator/repository/settings | Coordinator, DataStore, request-scope, ViewModel/UI tests | Live test spreadsheet connection/reconnection/sign-out passed | Pass |
| Google validation and typed failure mapping | Google coordinator and REST gateway | Parser/coordinator/fake gateway tests | Live validation passed | Pass |
| Per-date marked Google worksheet and idempotent replacement | Planner, encoder, gateway, export coordinator | Planner/encoder/coordinator tests | Re-export and blank-first-sheet flows passed after fixes | Pass |
| Running-timer export lockout for all destinations | Main ViewModel/UI and shared snapshot precondition | Main ViewModel/Compose tests | Manual lifecycle test 18 passed after fix | Pass |
| No import, sync, local mutation, or automatic retry | Export coordinators/gateway boundaries | Coordinator/fake tests and static review | Live retry behavior accepted | Pass |

## 7. Persistence, migration, accessibility, and security

| Requirement / acceptance IDs | Implementation | Automated evidence | Manual evidence | Status |
|---|---|---|---|---|
| Room versioning/schema export/no destructive fallback | Database v2, migrations, committed schemas 1/2 | Migration and packaged-schema instrumentation tests passed in the 78-test connected suite | Accepted emulator run | Pass |
| Existing populated v1 data and open timer migrate to v2 | `MIGRATION_1_2` | Populated migration instrumentation test passed in the 78-test connected suite | Accepted emulator run | Pass |
| Data survives recreation/background/process/reboot | Room/DataStore/recovery architecture | Reopen/recovery tests | Owner's Milestone 13 checklist passed | Pass |
| Accessibility semantics/touch/error/confirmation (`A11Y-01`–`A11Y-05`) | Compose screens and semantics | Compose semantics, large-font, dialog/state tests exist | Human TalkBack/OEM display-scaling pass remains Milestone 17 | Partial |
| Narrow/standard/large/landscape/large-text usability | Adaptive Compose layouts | Narrow, 200%-font, landscape/navigation tests exist | Standard physical Pixel accepted; broader release matrix pending | Partial |
| No credentials/tokens/private keys committed | `.gitignore`, local config injection, redacted token type | Tracked-file secret scan: no credential-pattern matches | Release key/OAuth production setup remains Milestone 17 | Pass |
| Least-scope Google access and safe token handling | `drive.file` request factory; in-memory token wrapper | Scope/connection tests and static scan | Live External-test authorization passed | Pass |
| No broad permissions; backup disabled | Source/merged manifest | Manifest/lint/static audit | N/A | Pass |
| WorqOrder-managed at-rest encryption | Explicitly optional Milestone 19 | N/A | N/A | N/A |
| Biometric/PIN/account-gated app access | Explicitly optional Milestone 18 | N/A | N/A | N/A |

## 8. Current audit gate

No required behavior is known to be failing. The current branch is not declared release-ready
because the final API/device/TalkBack/performance matrix belongs to Milestone 17, and release
signing/OAuth production configuration is intentionally not yet present. The complete connected
instrumentation suite passed with 78 tests, zero failures, and zero skipped tests.
