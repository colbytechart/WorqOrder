# WorqOrder Requirements Traceability

## 1. Scope and status

This matrix retains released `0.2.0` and `0.3.0` evidence in historical Sections 1–10 and
describes implemented `0.4.0` work in Section 11. Section 12 records the released `0.5.0` audit
baseline. Rows explicitly marked historical are not live contracts. `Pass` means
the implementation and test evidence exist. `Partial` means
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
| Repeated ordered intervals and totals (v0.2 historical) | Interval DAO/entity, duration queries, edit UI | Room interval ordering, duration math, task/timer tests | Accepted timer/task manual runs | Historical pass; superseded in v0.3 by one interval per task |
| Manual add/edit/delete and overlap/date/DST validation | `ManualIntervalValidator`, task mutation coordinator, edit UI | Validator, mutation, edit ViewModel/Compose tests | Accepted Milestone 6 run | Pass |
| Room is authoritative; DataStore is preference/selection only | Room repositories, typed DataStore repositories | Room persistence/reopen and DataStore recreation tests | Process/reboot manual tests | Pass |

## 4. Timer, dates, and lifecycle

| Requirement / acceptance IDs | Implementation | Automated evidence | Manual evidence | Status |
|---|---|---|---|---|
| Start eligibility and exactly one active timer | `TimerCoordinator`, singleton `ActiveTimerEntity`, transactional DAO | Timer concurrency and Room structural/transaction tests | Start/Stop verified on physical device | Pass |
| Stop, repeated intervals, frozen accumulated total | Timer coordinator, live session, Main ViewModel | Timer and Main ViewModel tests | Accepted Milestones 4/13 runs | Pass |
| Timestamp-derived display; no persisted ticks | `LiveTimerSession`, collection-scoped Main ticker | Live session and “no tick writes” ViewModel tests | Background/screen-lock tests | Pass |
| Process/activity/reboot reconstruction | Recovery coordinator, Room active snapshot, activity/Main resume hooks | Recovery concurrency/process tests | Rotation, Don't Keep Activities, process kill, Recents, reboot passed | Pass |
| Daily selection rollover; no duplicate copy (v0.2 historical) | `SelectionCoordinator`, three-part task uniqueness | Released v0.2 selection/restart tests; Room unique index | Accepted v0.2 zone-change tests | Historical pass; superseded in v0.3 by no-rollover selection |
| One/multiple midnight splitting and idempotence (v0.2 historical) | Boundary calculator, active normalizer, transactional DAO | Real-zone/DST/multi-midnight unit and Room tests | Accepted automated evidence | Historical pass; superseded in v0.3 by exact one-boundary closure |
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
| One immutable 15-column schema-4 canonical snapshot (v0.2 historical) | `ExportSnapshotCoordinator`, `ExportModels` | Export row/CSV and destination coordinator tests | Cross-destination manual checks | Historical pass; superseded in v0.3 by schema 5 |
| Stable row ordering, zero-interval rows, escaping, Unicode, duration format | Export row builder and CSV serializer | CSV serializer/row tests | CSV export opened successfully | Pass |
| CSV `ACTION_CREATE_DOCUMENT`, neutral cancellation, no mutation | CSV coordinator/document destination/Main | JVM, contract, Main tests | Accepted Milestone 8 run | Pass |
| One-off XLSX from the same snapshot | Existing focused XLSX writer/coordinator | Existing writer/coordinator/contract/Main tests | Owner confirmed XLSX export works | Pass |
| Google sign-in, `drive.file`, one spreadsheet, disconnect/sign-out | Google authorizer/coordinator/repository/settings | Coordinator, DataStore, request-scope, ViewModel/UI tests | Owner-signed release passed with a fresh account never listed as a tester; connection/restart/sign-out passed | Pass |
| Google validation and typed failure mapping | Google coordinator and REST gateway | Parser/coordinator/fake gateway tests | Live validation passed | Pass |
| Per-date marked Google worksheet and cross-device merge | Planner, encoder, gateway, export coordinator | Planner/encoder/coordinator tests; second-device append and keyed re-export regressions | Owner reports supplied post-fix Step 4 manual checks passed, including same-date re-export; current connected XML records 108/108 passing | Pass by owner report; simultaneous cross-device writes remain a documented limitation |
| Running-timer export lockout for all destinations | Main ViewModel/UI and shared snapshot precondition | Main ViewModel/Compose tests | Manual lifecycle test 18 passed after fix | Pass |
| `0.2.0`: no task import/sync/local mutation or unbounded automatic retry | Export/import coordinators and gateway boundaries | Coordinator/fake tests and static review | Live export/import behavior accepted | Pass |

## 7. Persistence, migration, accessibility, and security

| Requirement / acceptance IDs | Implementation | Automated evidence | Manual evidence | Status |
|---|---|---|---|---|
| Room versioning/schema export/no destructive fallback (v0.2 historical) | Database v4, explicit migrations, committed schemas 1/2/3/4 | Migration and packaged-schema instrumentation tests | Accepted emulator runs through Milestone 28 | Historical pass; superseded in v0.3 by schema 5 |
| Existing populated v1/v2 data and open timer migrate to v4 (v0.2 historical) | `MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_3_4` | Populated `1→4` and released `2→4` migration tests | Owner-signed populated `0.1.0` to `0.2.0` in-place update passed | Historical pass; retained as an input to the current v1/v2-to-v5 chain |
| Data survives recreation/background/process/reboot | Room/DataStore/recovery architecture | Reopen/recovery tests | Owner's Milestone 13 checklist passed | Pass |
| Accessibility semantics/touch/error/confirmation (`A11Y-01`–`A11Y-05`) | Compose screens and semantics | Compose semantics, large-font, dialog/state tests exist | Human TalkBack and display-scaling checks passed | Pass |
| Narrow/standard/large/landscape/large-text usability | Adaptive Compose layouts, landscape top actions, bounded compact pinned header | Narrow, 200%-font, short-landscape/navigation tests and complete 79-test suite pass | Corrected large-scale landscape visual check passed | Pass |
| No credentials/tokens/private keys committed | `.gitignore`, local config injection, redacted token type | Release tracked/intended-file scan: no credential-pattern matches | Permanent key is external and ignored | Pass |
| Least-scope Google access and safe token handling | `drive.file` request factory; in-memory token wrapper | Scope/connection tests and static scan | Live External-test authorization passed | Pass |
| No broad permissions; backup disabled | Manifest plus `data_extraction_rules.xml` | Manifest/lint/static audit | N/A | Pass |
| WorqOrder-managed at-rest encryption | Release-agnostic, unscheduled optional Milestone E | N/A | N/A | N/A |
| Biometric/PIN/account-gated app access and screenshot/Recents controls | Release-agnostic, unscheduled optional Milestone E | N/A | N/A | N/A |

## 8. Current audit gate

The accepted evidence in Sections 2–7 records the published `0.2.0` release and completed
Milestones 30–34. No current `0.3.0` behavior is known to be failing from those gates, but that is
not a `0.3.0` release declaration. The owner-signed `0.3.0` artifact, populated upgrade, repeated
API/device matrix, fresh-account Google exercise, performance observations, and final security/
dependency review are governed by the Milestone 35 evidence inventory; several of those gates now
have owner-reported passes, while other current-release manual checks remain outstanding.

## 9. `0.2.0` traceability

| Requirement | Planned implementation | Required automated evidence | Manual/official gate | Status |
| --- | --- | --- | --- | --- |
| Non-destructive `0.1.0` update | Room migration, schema export, typed preference defaults | Populated v1/v2 migrations including open timer passed in connected suite | Signed install-update smoke passed | Pass |
| 15-column canonical schema 4 with repeated Start/End dates and Billing Status (v0.2 historical) | Shared export row builder/snapshot | exact headers, `MM/DD/YYYY` duplicated dates, CSV/XLSX/Google equivalence, schema-2/schema-3 owned-tab upgrade | Open representative files/sheet | Historical pass through M29; superseded in v0.3 by schema 5 |
| Client-name CSV import append/A–Z | `ClientCsvParser`, Android bounded document source, import coordinator, Client DAO transaction, Client Management UI | UTF-8/BOM/quoting/line breaks/limits, file policy, normalization/duplicates, restore/rollback, ViewModel and Compose states | Owner verified valid and invalid import behavior in Milestone 20 | Pass |
| Consultant directory/selection/history | Employee Room repository, `ConsultantSelectionCoordinator`, typed DataStore selection, dedicated Consultant Management route, Settings selector, Create/Edit UI, transaction-captured task snapshot | repository/coordinator/ViewModel plus Room snapshot, active-reference race, persistence, dedicated-route navigation, and connected UI tests | Consultant Management, Settings selection, and Create/Edit assignment workflow | Pass through M23; final 97-test connected gate and manual Settings inspection passed |
| Work Type, Billing Status, and Mileage | Task metadata/repository/Create/Edit UI | persistence/migration, nullable history, Billable default, canonical decimal/input validation, ViewModel, Compose, and rollover coverage | Create/edit workflow including migrated blank status | Pass through M28 |
| Derived Billing Minutes | Pure calculator, observed task-detail projection, and export projection | zero, sub-15, boundary, owner example, long totals, interval-mutation refresh, and connected presentation coverage passed | Task-detail check | Pass through M22; 93-test connected gate passed |
| About text and strict clock-time `hh:mm a` | Build metadata and one shared UI/export clock formatter | exact text/no link; AM/PM, DST, destination-equivalence, and precision-preservation tests passed in the 190-test JVM and final 97-test connected gates | Settings/interval/export check | Pass through M23; zero failures, errors, or skips |
| Handed landscape | typed `LandscapeHandedness` preference, Settings radio controls, immutable Main projection, and mirrored two-column Main | default/corrupt fallback, persistence, ViewModel projection, Settings semantics, right/left geometry, 200%-text reachability, and portrait regressions passed in the 191-test JVM and final 98-test connected gates | Owner verified both handed modes, persistence, portrait preservation, task scrolling, controls, and responsive behavior | Pass through M24; zero connected failures, errors, or skips |
| Automatic Google captured-date export | `AutomaticGoogleExportManager`, one-shot WorkManager scheduler/worker, DataStore target tuple, Settings switch, pending notification | 200-test JVM gate and 100-test connected gate cover manager blockers/success/active-timer/disable, target persistence, conditional UI, inline recovery, and shared Google idempotency | First checklist half and notification denial/scroll behavior passed; remaining device/OEM timing cases deferred | Pass implementation and automated gate; partial manual device coverage documented |
| Running lock-screen surface | Room-backed notification coordinator, standard private/public chronometer notification, typed per-interval dismissal, Start/Stop/app/resume/date/boot reconciliation | coordinator authority/duration/dismissal/permission/clock tests; 208 JVM and 102 connected tests passed with zero failures/errors/skips | Owner passed permission/channel/private-public content, tap, swipe, Stop/new Start, background/lock, process/Recents, reboot, force-stop, and short resource checks | Pass through M28 |
| `0.2.0` release readiness | existing direct-signing/release pipeline | clean 208-test JVM/build/lint gate, 102-test connected gate, and 180-coordinate OSV review pass | API-26/API-36.1, signed-update, production non-test-account Google, signer, checksum, install, and smoke gates pass | Pass; public-download checksum/install confirmation follows publication |

## 10. Released `0.3.0` traceability

Milestone 35 release evidence and owner-run checks are retained in
`docs/MILESTONE_35_RELEASE_EVIDENCE.md`. The owner subsequently confirmed the public `0.3.0`
artifact checksum, physical-device update over existing data, and new task timing. These rows are
release-history evidence, not a claim that planned `0.4.0` behavior exists.

| Requirement | Owning milestone/location | Required automated evidence | Manual gate | Planning status |
| --- | --- | --- | --- | --- |
| Preserve all schema-4 data while converting one task/many intervals to one task/one interval | M30; Room 4-to-5 migration, entities, DAOs | populated zero/one/many/open migration, counts/fields/FKs, reopen, schema JSON | owner reports signed populated `0.2.0` update passed on API 36.1, including two completed plus one active interval becoming three one-interval tasks | Pass through M35 owner report |
| Enforce zero or one interval per task | M30; unique interval `task_id` plus repositories | constraint, cascade, active-slot/singleton, invalid second insert | DB Inspector spot check | Pass through M30 |
| Repeated Start creates exactly one copied task at zero | M30/M31; TimerCoordinator and atomic Room operation | metadata/null copy, selection, concurrent Start, source immutability | repeated Start workflow passed | Pass through M31 Sol gate |
| No date/ZoneId rollover task creation | M31; SelectionCoordinator/recovery | startup/resume/date/zone/browse no-creation and stale-selection clear | date/ZoneId change and date-browsing scenarios passed | Pass through M31 Sol gate |
| Midnight closes exactly once with no continuation | M32; normalizer/recovery/notification | boundary/DST/missed-day/idempotence/concurrency/process/reboot | owner exercised foreground boundary closure; final external-close visible-date race is deterministic | Pass through M32 Sol gate |
| Midnight close precedes captured-date automatic Google export | M32; automatic manager/worker | close-before-snapshot, delay/pending/idempotence/auth/failure | owner exercised captured-date automatic and pending-completion exports | Pass through M32 Sol gate |
| Singular Interval UI | M33; Edit Task/ViewModels | empty/add/edit/delete/running/semantics/large-text | portrait/landscape inspection | Pass through M33 |
| One 13-column schema-5 row per task in CSV/XLSX/Google | M33; shared ExportRowBuilder/adapters | exact headers/values/order/equivalence; owned legacy tabs now fail closed, not overwritten | owner reports post-fix Step 4 manual checks and public APK install passed | Pass for released `0.3.0`; superseded only after schema 6 ships |
| No stale current rollover/multi-interval contracts | M34; code/docs audit | reference/static checks plus full regression | documentation review | Pass through M34; live continuation API removed, migration/history evidence retained, and full owner-run gate passed |
| Release-safe `0.2.0` upgrade and `0.3.0` artifact | M35; QA/release documents | full clean/API matrix/security/dependency gates | signed update, Google/export, checksum/install | Owner reports public APK checksum and physical update passed; limited performance evidence was accepted |
| Safe optional environment retirement instructions | Post-`0.5.0` M48 guide; separately authorized M49 actions | documentation/path/safety review only | owner inventory review | Deferred until after public `0.5.0` release |

## 11. `0.4.0` traceability baseline and implementation evidence

| Requirement | Owning milestone | Required automated evidence | Manual gate | Planning status |
| --- | --- | --- | --- | --- |
| Blank/default/editable Notes, max 999 Unicode code points, optional for Create | M37 data layer; M39 forms | repository validation, ViewModel and Compose field tests | create blank and filled Notes; edit/reopen | Planned |
| Preserve all prior records and active timer through Room 5-to-6 | M37 | populated 1–5-to-6 migration, schema JSON, reopen, FK and active-pointer tests | signed `0.3.0` update with real data in M41 | Planned |
| New repeated-Start task has blank Notes; source unchanged | M37 | atomic/concurrent Start and metadata-copy tests | repeated Start on task with Notes | Planned |
| Four export paths share schema 6, exactly 14 columns | M38 | exact headers, snapshot, CSV/XLSX/Google parity, automatic date tests | inspect one representative row in each destination | Planned |
| Existing owned Google schema-5 tabs retain all rows and task IDs | M38 | legacy tab, multi-device rows, keyed re-export, conflict/failure tests | manual re-export with another device's rows | Planned |
| Create header simplified without removing Consultant assignment | M39 | Compose and ViewModel validation/semantics tests | Create Task visual check | Planned |
| Cancel/Create pinned while form scrolls and remains accessible | M40 | portrait/landscape/IME/large-scale Compose tests | short phone and large-text inspection | Planned |
| `0.4.0` release compatibility and public APK | M41 | full offline/connected/migration/security/build gates | signer/hash, API 26/current, populated and fresh installs | Planned |

### Milestone 41A evidence override (2026-09-14)

Milestones 37–40 are implemented on `milestone41` through the merged `v0.4.0-development`
history. The following status notes supersede the provisional `Planned` labels above for the
Luna audit. They record implementation and automated evidence only; manual release checks and the
final release decision remain with Milestone 41B/41C, so no publication is claimed.

| Requirement | Implementation and automated evidence | Manual/release status |
|---|---|---|
| Notes persistence, optional Create, editable 999-code-point validation | `TaskMetadataValidator`, Create/Edit ViewModels and screens; `TaskMetadataValidatorTest`, `CreateTaskViewModelTest`, `EditTaskViewModelTest`, `CreateTaskScreenTest`, and `EditTaskScreenTest` | Automated pass; M41 manual create/edit/reopen check pending |
| Room 5-to-6 preservation | Additive migration and committed `app/schemas/6.json`; `Schema6MigrationCoreTest` populated upgrade/reopen/FK/active-pointer coverage | Automated pass; signed 0.3.0 populated update remains an M41C owner gate |
| Repeated Start creates a blank-Notes copy without changing the source | `TimerCoordinator` atomic operation and concurrent-start coverage, including `repeatedStartCreatesSelectedSameDayCopyAndPreservesSourceTask` | Automated pass; manual Notes-copy check pending |
| Shared schema 6 projection across CSV, one-off XLSX, manual Google, and automatic Google | Shared `ExportRowBuilder`; `ExportRowBuilderAndCsvSerializerTest`, `Schema6ExportEquivalenceTest`, Google planner/encoder, and automatic-export tests | Automated pass; representative destination inspection remains an M41C gate |
| Schema-5 owned Google tab preservation | Legacy-upgrade planner and keyed merge; `GoogleSheetsLegacyUpgradePlannerTest` and Google planner/encoder regressions | Automated pass; cross-device manual re-export remains an M41C gate |
| Simplified Create header and pinned Cancel/Create footer | `CreateTaskScreen` and `CreateTaskActionFooter`; full 117-test connected suite plus focused 11-test short-viewport/large-scale suite passed | Automated pass; short-phone and large-text visual checks pending |
| 0.4.0 release compatibility and public APK | M41C still owns the clean offline lint/JVM/debug/release, connected, migration, security, dependency, and signing gates | Partial; no final artifact or release decision produced in Luna phase |

### Milestone 41C evidence update (2026-09-15)

The later evidence supersedes the pending-manual labels in the Milestone 41A snapshot above; the
provisional planning table remains only as a requirement baseline. The owner reports a populated
signed `0.3.0` to `0.4.0` install-over with preserved clients, tasks, settings, open timer, and
blank migrated Notes. Owner manual Steps 6–9 passed for optional/editable Notes, blank Notes on
repeated Start, Create/Edit pinned-footer presentation, owned schema-5 Google tab upgrade and
re-export without duplicate rows, and the exact 14-column CSV/XLSX schema. The current complete
connected suite passed 118/118 on API 26 and 118/118 on API 36 before the final bounded footer/
icon polish. The final clean offline gate passed 250 JVM tests, both lint variants, and debug,
test, and owner-signed release assembly. `apksigner` and `aapt2` verify the candidate's permanent
signer, package, `0.4.0`/code 4, and minSdk 26. No public APK was claimed at this snapshot;
subsequent live Google evidence follows. Release status remains **partial** until publication.

The owner later reported a live `0.4.0` automatic Google export succeeded with all 14 visible
columns and no duplicate row. The final bounded Edit footer/icon changes were followed by a
complete API-26 connected rerun: generated XML records 119/119 passing tests, including the new
pinned-footer placement/event test. The owner also reports API-37 Create/Notes/timer/orientation
smoke and a 14-column Google export to the shared API-36 spreadsheet that appended rather than
overwrote the first device's row. The owner explicitly confirmed the API-37 install was fresh
from the exact signed candidate and its Google account was never on the OAuth tester list.
Implementation and pre-publication verification passed. The owner subsequently published
`0.4.0`, downloaded the GitHub asset, verified its identity, installed it on a physical device,
and reported expected behavior; the `0.4.0` release gate is complete.

## 12. `0.5.0` Milestones 42–47 implementation traceability baseline

The implementation and focused automated evidence below are present on the `milestone48` branch.
Owner-run release, signed-artifact, upgrade, device, accessibility, lifecycle, and public-download
checks remain **Partial** until the owner reports them. No row below claims a test that was not
reported by the owner.

The original planning table below is retained as historical provenance; the current audit table
follows it.

### Historical planning baseline (superseded)

| Requirement | Planned implementation | Automated evidence | Owner manual evidence | Status |
|---|---|---|---|---|
| Non-destructive Room 6-to-7 Tag migration | M43 catalog/snapshot entities and migration | fresh/populated 1–7 migration, FK/index/reopen tests | signed populated `0.4.0` install-over | Planned |
| Tag CRUD/history/search | M43 repositories; M44 management UI | repository/ViewModel/Compose normalization, history, search tests | both category pages and delete confirmation | Planned |
| Atomic bounded Tag CSV import | M44 SAF parser/import transaction | quotes/Unicode/duplicates/limits/failure rollback tests | valid, duplicate, malformed, oversize picker flows | Planned |
| Create/Edit picker and chips | M45 reusable UI plus task coordination | picker/state/validation/history/accessibility tests | portrait/landscape/large-text/keyboard task flows | Planned |
| 999 composed field validation | M43 pure composer; M45 form integration | exact Unicode boundary, punctuation/space, tag-only Description tests | counters, errors, blocked save, correction | Planned |
| Historical and repeated-Start snapshots | M43 transactions; M45 presentation | source edit/delete, explicit update, copy/concurrency tests | reopen/edit/delete/restart timing flows | Planned |
| Main fallback and notification privacy | M45 presentation/notification mapping | manual/Tag fallback and redaction tests | visible/running/locked inspection | Planned |
| Unchanged schema-6 cross-destination export | M46 canonical projection integration | CSV/XLSX/manual+automatic Google equivalence and regression tests | same-task exports and cross-device Google append | Planned |
| `0.5.0` release compatibility and public APK | M48 audit/handoff | owner-run lint/JVM/build/API26/current connected gates | populated upgrade, physical install, signer/hash/public download | Planned |
| Safe environment retirement guidance/actions | M49 guide; optional M50 exact actions | documentation/path/safety review only | separate owner inventory/authorization | Deferred until after public `0.5.0` |

### Current implementation audit

| Requirement | Implementation location | Automated evidence | Owner manual evidence | Status |
|---|---|---|---|---|
| Non-destructive Room 6-to-7 Tag migration | `WorqOrderMigrations.MIGRATION_6_7`, Tag entities/DAOs | `Schema6MigrationCoreTest`, `WorqOrderDatabaseTest`, committed schema 7 | owner reports exact signed-candidate populated `0.4.0` install-over/reopen passed | Pass |
| Tag CRUD/history/search | `RoomTagRepository`, Tag management ViewModels/screens | `TagTextRulesTest`, `TagCategoryManagementViewModelTest`, `TagManagementScreenTest` | owner reports both category pages, search, edit/delete confirmation passed | Pass |
| Atomic bounded Tag CSV import | `TagCsvImportCoordinator`, `RoomTagImportRepository` | `TagCsvImportCoordinatorTest`, `WorqOrderDatabaseTest`, `TagManagementScreenTest` | owner reports valid/duplicate/malformed/oversize/cancel picker flows passed | Pass |
| Create/Edit picker, chips, and bulk controls | `TaskTagFormComponents`, `TagPickerComponents`, Create/Edit ViewModels | `TaskTagViewModelTest`, `EditTaskViewModelTest`, `TaskTagFormScreenTest`, `TagPickerComponentsTest` | owner reports portrait/landscape/large-text/keyboard flows passed | Pass |
| 999 composed field validation | `TaskTextComposer`, `TaskMetadataValidator`, Create/Edit form components | `TagTextRulesTest`, `TaskMetadataValidatorTest`, Create/Edit ViewModel/UI tests | owner reports counters, punctuation, tag-only Description, blocked save/correction passed | Pass |
| Historical and repeated-Start snapshots | `TaskMutationCoordinator`, task snapshot DAOs | `TaskTagViewModelTest`, `EditTaskViewModelTest`, Room migration/repeated-Start tests | owner reports reopen/edit/delete/restart and catalog-history flows passed | Pass |
| Main fallback and notification privacy | main row mapping and `RunningTimerNotificationMapper` | Main/notification unit and Compose tests | owner reports visible/running/locked inspection and redaction passed | Pass |
| Unchanged schema-6 cross-destination export | `ExportSnapshotCoordinator`, `TaskTextComposer`, CSV/XLSX/Google adapters | `Schema6ExportEquivalenceTest`, `ExportRowBuilderAndCsvSerializerTest`, Google/XLSX tests | owner reports CSV/XLSX/manual+automatic Google and cross-device append passed | Pass |
| Midnight timer auto-close then automatic Google export | `ActiveTimerNormalizer`, exact-boundary Room close, `AutomaticGoogleExportManager` automatic timer-fallback resumption | timer boundary/concurrency tests plus manager scheduled/Stop/reconcile regressions | owner reports exact Stop, silent captured-date export, and no continuation/duplicate passed | Pass |
| `0.5.0` release compatibility and public APK | migrations, release config, handoff docs | clean build/lint/JVM/current and API-26 connected suites pass by owner report | exact signed populated upgrade, fresh install, permanent signer, package/version/hash, GitHub download, and physical install pass | Pass |

## 13. Planned `0.6.0` portability traceability

These rows track planned `0.6.0` work and completed foundations; they are not claims about released
`0.5.0` or about unimplemented archive/import/UI behavior.

| Requirement | Planned implementation | Required automated evidence | Required manual evidence | Status |
|---|---|---|---|---|
| Logical versioned two-entry ZIP | versioned DTO/strict JSON codec/manifest foundation in M50; bounded writer in M51; bounded strict reader in M52; record-streamed current decoder and heap-aware materialization ceiling in M54C | DTO/strict-shape plus production exact-entry/Deflate/byte-count/SHA-256/bounds/cancellation/read-rejection, streaming-equivalence, escaped-duplicate, and runtime-limit tests | owner-created valid and malformed fixtures pass; API-26/current connected and current hardening pass; API-26 manual/physical matrices are deferred under D-122 | Pass through the non-deferred Milestone 55 pre-publication gate |
| Complete portable state and exclusions | logical DTO/exclusion allowlist in M50; Room snapshot and portable preference adapters in M51; exact replacement/equivalence in M52 | complete/empty/Unicode fixtures, forbidden-field audit, Room-backed snapshot/replacement, and exact logical round-trip tests pass | populated signed `0.5.0` install-over, Backup/Import comparison, and fresh-install checks pass | Pass through the non-deferred Milestone 55 pre-publication gate |
| Atomic import and crash recovery | M52A frozen protocol plus M52B-D application lock, verified restore point, self-checking next-action journal, transactional Room and exact Preferences replacement, startup convergence, and M54C destructive-operation UI barrier | Room postconditions, preference equivalence, every-phase fault/restart/idempotency/cancellation/rollback tests plus barrier-state/Compose coverage | owner fixture swap and current-target force-stop/reopen matrix pass; API-26 manual/physical repetitions are deferred under D-122 | Pass through the non-deferred Milestone 55 pre-publication gate |
| Swap-style one-generation Restore | M52 verified no-backup point, displaced-state capture, journaled swap/finalization, and exact rollback authority | swap directions, repeated reconciliation, corruption/failure preservation, and exact DTO equivalence pass | owner reports valid import, Restore, reverse swap, and fresh-install behavior pass | Pass through the non-deferred Milestone 55 pre-publication gate |
| Version compatibility | explicit current decoder and older-format upgrader boundary in M50; concrete future adapters as formats evolve | current round-trip, malformed/invalid/newer rejection, and explicit legacy-dispatch fixtures pass | current format imports; unsupported-newer format shows the approved update-required failure | Pass for portable format 1; no historical portable format exists yet, and future formats require explicit upgraders |
| Google ownership safety | installation-local origin repository in M50; v2 hidden keys, gated legacy adoption, origin-aware export coordination, disconnect/sign-out cleanup in M54B; exact 32-hex origin and delimiter-safe portable IDs in M54C | origin persistence, malformed-state fail-closed, original-install v1 adoption, restored-origin foreign/v1/unkeyed non-claim, cleanup-failure, and strict-origin/ID tests | signed original-install adoption, two-device same-sheet append, disconnect/sign-out cleanup, and final Google smoke pass by owner report | Pass through the non-deferred Milestone 55 pre-publication gate |
| Settings UX/accessibility | `BackupRestoreViewModel`, compact Settings card, SAF launchers, typed persistent status, and M54C modal replacement barrier | state, semantics, confirmation, duplicate-submit, picker-cancel, timer-race, barrier-state/Compose coverage, and archive matrix | UI/fixture/current-target replacement checks pass; remaining API-26 manual/physical repetitions are deferred under D-122 | Pass through the non-deferred Milestone 55 pre-publication gate |
| Unchanged task exports | existing immutable schema-6 projection | Task 54A/54B cross-destination and ownership regressions | CSV/XLSX/manual+automatic Google final smoke passes | Pass through the non-deferred Milestone 55 pre-publication gate |
| Release safety | Milestone 55 checklist/evidence | full lint/JVM/build/connected/migration suites pass | signed emulator upgrade/fresh install pass; physical smoke remains deferred under D-122 | Pre-publication pass; final-main artifact and independently downloaded GitHub asset remain pending |
