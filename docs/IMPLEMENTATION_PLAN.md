# WorqOrder Implementation Plan

## 1. Planning baseline and repository state

The repository began with its README, GPLv3 license, Git configuration, and planning
documents. Milestones 1 and 2 subsequently established the Android scaffold and Room
persistence foundation. Implementation proceeds one explicitly authorized milestone at
a time.

## 2. Delivery rules

- Start each milestone by reading `AGENTS.md`, the relevant specifications, repository
  status, existing implementation, and newer recorded decisions.
- Do not begin a later milestone until the owner explicitly requests it.
- Remind the owner of deferred conflicts or external inputs when they become critical
  to the milestone being entered.
- If requirements are inconsistent, pause the affected work and request a product
  decision; never silently choose different behavior.
- Preserve unrelated owner changes in a dirty worktree.
- Use current stable dependencies only. Record and obtain approval before adopting a
  preview, alpha, beta, or release-candidate artifact.
- Keep changes vertically reviewable: behavior, tests, and required documentation land
  together.
- End each implementation milestone with formatting, lint, unit tests, relevant
  instrumentation/UI tests, and applicable debug/release builds. Record exact commands
  and results.
- Do not access the internet or files outside this project without the owner's explicit
  permission. Follow the repository-specific environment guardrails when toolchain
  investigation is required.

## 3. Fixed implementation inputs

1. Android `applicationId` and Kotlin namespace are `worq.order`.
2. GNU GPL version 3 is the intended repository license.
3. Development uses Android Studio's Embedded JDK/JBR 21 and a local, uncommitted
   Android SDK path.
4. Minimum SDK is API 26. The build-proven scaffold uses target API 36 and compile SDK
   API 36.1.
5. CSV delivery uses the standard `ACTION_CREATE_DOCUMENT` save flow.
6. Daily-task identity is unique by `(task series ID, work date, ZoneId)`.
7. Room is authoritative; CSV and Google Sheets remain one-way export destinations.

## 4. Milestone 1 — Android project scaffold

Status: completed and accepted.

### Delivered scope

- One native Android application module using Kotlin DSL, a version catalog, Kotlin,
  Compose, Material 3, Compose Navigation, KSP, and manual dependency construction.
- Namespace/application ID `worq.order`, minimum API 26, target API 36, compile SDK
  36.1, and debug/release build types without credentials.
- Compile-safe Main, Create Task, Edit Task, Settings, and Client Management routes.
- Static Main layout regions, Material 3 Light/Dark foundations, centralized strings
  and dimensions, and actionable icon descriptions.
- Room and Preferences DataStore dependencies prepared without a production schema in
  this milestone.
- JVM and Compose smoke-test foundations.

### Proven initial version matrix

| Component | Version |
| --- | --- |
| Gradle wrapper | 9.4.1 |
| Android Gradle Plugin | 9.2.1 |
| Kotlin / Compose compiler plugin | 2.3.10 |
| KSP | 2.3.8 |
| Compile SDK | 36.1 |
| Target / minimum SDK | 36 / 26 |
| AndroidX Core | 1.18.0 |
| Activity Compose | 1.13.0 |
| Lifecycle | 2.10.0 |
| Navigation Compose | 2.9.8 |
| Room | 2.8.4 |
| Preferences DataStore | 1.2.1 |
| Kotlin coroutines | 1.10.2 |
| Compose BOM | 2026.06.00 |
| JUnit 4 | 4.13.2 |
| AndroidX Test JUnit / Runner / Espresso | 1.3.0 / 1.7.0 / 3.7.0 |

## 5. Milestone 2 — Room database and local persistence layer

Status: completed and accepted.

### Delivered scope

- Room version 1 entities, explicit foreign keys and indexes, DAOs, repository
  foundations, stable database name, and committed schema export.
- Clients with normalized active-name uniqueness and archival that preserves historical
  task references.
- Daily tasks using independent epoch-day storage and three-part
  `(series ID, work date, ZoneId)` uniqueness.
- Repeated ordered work intervals, cascading task-to-interval deletion, and completed
  duration aggregation.
- A singleton active-timer pointer plus structural one-open-interval protection and
  atomic start/stop persistence transactions.
- Creation, constraint, ordering, cascade/restrict, persistence/reopen, active-timer,
  and schema-availability instrumentation coverage.

## 6. Milestone 3 — Timer, interval, date, and rollover domain logic

Entry: Milestone 2 persistence accepted.

### Scope

- Testable UTC clock, monotonic time, effective ZoneId, and current-date abstractions.
- Persistent selected-task/series state with select, clear, observe, dangling-state
  repair, and exact series/date/zone rollover reconciliation.
- Typed Start and Stop results coordinated with Room transactions.
- One-global-active enforcement under concurrent starts.
- Idempotent active-timer normalization across one or multiple local midnights using the
  active session's pinned geographical ZoneId.
- Daily continuation task find/create, metadata copying, continuation intervals, and
  transactional active-pointer retargeting.
- Pure completed/active/total duration calculation and `HH:MM:SS.mmm` formatting,
  including totals over 23 hours and clock-anomaly clamping.
- Reusable manual-interval validation for ordering, task-day bounds, overlap, open and
  running-state conflicts, and explicit DST gap/overlap resolution.
- Process-live monotonic display modeling and process-recovery UTC reconstruction. No
  lifecycle wiring, final UI ticker, or foreground service.

### Verification gate

- Unit tests cover start/stop outcomes, repeated intervals and accumulated totals,
  selection rollover/reuse, duration formatting, validation, exact boundaries,
  anomalies, recovery, non-DST and DST behavior, and one/multiple midnight splits.
- Coroutine and Room integration tests prove concurrency and transactional invariants.
- Formatting, lint, JVM tests, debug/release compilation, and emulator instrumentation
  tests pass before completion is declared.

## 7. Milestone 4 — Main screen and live timer presentation

Entry: Milestone 3 domain contracts and persistence coordination accepted.

### Scope

- Replace static Main state with a ViewModel backed by task, selection, and active-timer
  flows.
- Present the selected task's completed total plus active contribution, with a
  collection-scoped monotonic refresh loop and no per-tick database writes.
- Wire Start/Stop, running/selected row states, disabled task switching, historical-date
  live-timing restriction, date navigation/picker, empty state, and lifecycle-aware
  collection.
- Keep Add Task and export routes appropriately limited until their owning milestones.

### Verification gate

- ViewModel/coroutine tests and central Compose workflows cover timer display, controls,
  row locking, activity recreation, accumulated repeated intervals, and historical-date
  behavior.

## 8. Milestone 5 — Client management in Settings

Entry: Main presentation accepted.

### Scope

- Active client list sorted alphabetically, add, rename, archive, archived-client view,
  and restore.
- Shared normalization and repository validation for all client-entry surfaces.
- Historical client resolution remains intact after archive.

### Verification gate

- Repository/ViewModel/Compose tests cover validation, normalized duplicates, sorting,
  rename, archive/restore, and preserved historical task display.

## 9. Milestone 6 — Task creation, selection, editing, intervals, and deletion

Entry: client management accepted.

### Scope

- Task creation for the displayed date, client selector, inline Add Client through the
  same client repository operation, Create/Cancel semantics, and selection behavior.
- Task selection and accumulated total presentation for current and historical dates.
- Daily task metadata editing and chronological interval detail.
- Manual interval add/edit/delete using Milestone 3 validation and explicit DST choices.
- Confirmed daily-task deletion with running guards, transactional interval cascade,
  and no deletion of clients or other daily series copies.

### Verification gate

- Unit, Room, ViewModel, and Compose tests cover creation/cancellation, selection,
  repeated intervals, editor validation, DST input, running locks, and deletion scope.

## 10. Milestone 7 — Time-zone, theme, export-default, and persistent application settings

Entry: local task workflows accepted.

### Scope

- Preferences DataStore models and repositories for explicit Light/Dark appearance,
  device/manual ZoneId mode, manual geographical ZoneId, default export destination,
  selection state consolidation, and non-secret export status.
- Immediate theme application.
- Searchable/navigable ZoneId selection and display of the effective ZoneId.
- Block time-zone setting mutations while a timer is active; historical task dates and
  stored zones never move.
- Default destination supports CSV and a not-yet-connected Google Sheets state without
  adding Google authorization.

### Verification gate

- DataStore persistence/default tests, effective-zone tests, ViewModel/Compose setting
  tests, active-timer guards, and historical-date stability tests.

## 11. Milestone 8 — CSV export

Entry: local data and export-default settings accepted.

### Scope

- Immutable schema-versioned logical export rows built from one authoritative snapshot
  for the displayed date.
- One row per interval, zero-interval rows, deterministic sorting, UTC/local fields,
  totals, and running-interval snapshot rules.
- UTF-8 RFC-style CSV serialization with correct Unicode, comma, quote, and line-break
  handling.
- Standard `ACTION_CREATE_DOCUMENT` delivery with suggested
  `worqorder_YYYY-MM-DD.csv` name, cancellation/failure/repeat outcomes, and no broad
  storage permission.
- Date-specific Main action label and persistent non-sensitive result state.

### Verification gate

- Golden schema/escaping tests, duration/DST/running snapshots, picker cancellation and
  provider failure tests, and static checks for no XLSX or storage permission.

## 12. Milestone 9 — Current Google authentication and Sheets integration plan

Entry: CSV logical row model stable. The owner supplies Google Cloud ownership and
signing inputs when this milestone begins.

### Mandatory discovery gate

- With explicit internet permission, re-read current official Google Identity Android
  authorization, Credential Manager/sign-in, Sheets API/scopes, OAuth verification,
  and client setup documentation.
- Identify stable Google-supported dependencies compatible with the installed toolchain
  and minimum API 26.
- Confirm the narrowest supported authorization scope for writing an arbitrary
  user-specified existing private spreadsheet.
- If only a preview dependency, broader scope, backend, or incompatible flow is
  supported, stop for a documented decision rather than implementing.

### Deliverables

- Approved authentication and Sheets gateway design, dependency/version proposal,
  scope rationale, token-handling model, error taxonomy, test strategy, and updated
  Google Cloud setup guide.
- No account UI, authorization code, or network call is required in this planning
  milestone.

## 13. Milestone 10 — Google account and spreadsheet connection settings

Entry: Milestone 9 integration plan approved and Google Cloud configuration available.

### Scope

- Supported account sign-in/authorization, sign-out, expiry recovery, and cancellation.
- Spreadsheet URL/ID parsing, validation, title/ID display, exactly-one connection,
  disconnect, and Google Sheets default-destination selection.
- No Firebase, service account, embedded secret, raw token in DataStore, Drive-wide
  authorization, or spreadsheet creation.

### Verification gate

- Fake-gateway unit/ViewModel/Compose tests plus controlled account/connection
  integration checks; credential, manifest, and scope static audits.

## 14. Milestone 11 — Idempotent Google Sheets export

Entry: account and spreadsheet connection accepted.

### Scope

- Reuse the CSV logical row schema and exact column order.
- Export only to the single connected spreadsheet and one application-owned
  `WorqOrder_YYYY-MM-DD` tab per displayed date.
- Create/verify marker and schema version, reject unmarked same-name conflicts, and
  atomically replace application-owned date-tab contents with the current authoritative
  snapshot.
- Preserve all other tabs, never import to Room, never create a spreadsheet, and expose
  useful retryable offline/auth/network/ambiguous-response states.

### Verification gate

- Unchanged re-export creates no duplicates; changed/deleted local data replaces
  correctly; conflict tabs and unrelated tabs remain untouched; failed exports do not
  mutate Room or claim success.

## 15. Milestone 12 — Lifecycle, process-death, reboot, and timer hardening

Entry: core local and export workflows accepted.

### Scope

- Wire foreground/resume/operation normalization to Android lifecycle boundaries.
- Harden activity recreation, background/device sleep, process death, reboot, stale
  selection, active-timer recovery, and clock-anomaly recovery.
- Exercise transaction interruption and invariant repair paths without a foreground
  service, alarm, or per-tick persistence.

### Verification gate

- Instrumented lifecycle/process simulations and manual reboot/background scenarios
  prove persisted open-interval recovery and correct normalization.

## 16. Milestone 13 — Accessibility, usability, and error-state refinement

Entry: all primary workflows function.

### Scope

- Accessibility semantics, touch targets, TalkBack order, large font, small-screen and
  adaptive layout, contrast, and Light/Dark review.
- Refine empty, loading, disabled, validation, conflict, offline, authorization, retry,
  and destructive-confirmation states without changing approved behavior.
- Profile long task lists and timer recomposition; fix measured issues without
  architecture expansion.

### Verification gate

- Automated accessibility/UI coverage plus documented manual device, font-scale,
  TalkBack, theme, and error-recovery checks.

## 17. Milestone 14 — Full specification audit, regression testing, and security review

Entry: feature and refinement milestones accepted.

### Scope

- Map every product requirement and acceptance scenario to implementation and passing
  evidence.
- Run the full unit, coroutine, Room/migration, ViewModel, Compose, lifecycle, CSV, fake
  Google, and controlled integration suites.
- Audit permissions, logs, credentials, OAuth scopes, storage, spreadsheet ownership,
  CSV formula risk, dependency licenses, migration policy, and prohibited technology.
- Resolve specification drift through explicit decisions and documentation updates.

### Verification gate

- No unmapped required behavior, unexplained test gap, prohibited dependency,
  permission, credential, destructive migration, or known data-loss path remains.

## 18. Milestone 15 — Release preparation and developer handoff

Entry: Milestone 14 audit accepted.

### Scope

- Final API/device and populated-database upgrade matrix, performance check, and
  debug/release build verification.
- Versioning, signing and release configuration through the owner's secure process.
- Google consent/verification readiness, privacy/user documentation, setup/runbook,
  backup limitations, known limitations, and maintenance guidance.
- Update README and produce a release candidate without committing signing material,
  credentials, tokens, `local.properties`, or generated local state.

### Verification gate

- Formatting, lint, all unit/instrumentation/UI/integration tests, migration verification,
  and applicable release build pass; handoff documentation is complete and reproducible.

## 19. Dependency selection checklist

At the first milestone that needs a dependency:

1. Prefer Android, Jetpack, Kotlin, or Google official artifacts.
2. Check official stable release notes and Maven metadata; reject alpha, beta, RC,
   dynamic, or undocumented versions.
3. Verify minimum API 26 and the proven AGP/Kotlin/KSP compatibility.
4. Add one version-catalog alias and rationale; avoid redundant or transitive stacks.
5. Check license, size, manifest additions, permissions, and credential behavior.
6. Run the complete owning-milestone gate and commit only required reproducibility
   artifacts.

The initial scaffold already includes Compose, Activity, Lifecycle, Navigation,
coroutines, Room, Preferences DataStore, and AndroidX testing. Google dependencies are
deferred until the mandatory Milestone 9 discovery gate.

## 20. Risk register

| Risk | Impact | Mitigation/gate |
| --- | --- | --- |
| `ACTION_CREATE_DOCUMENT` cannot force a Downloads subfolder | The system picker may save outside `Downloads/WorqOrder` | Accepted D-030 gives final location control to the user; do not add a storage-permission workaround |
| OAuth configuration differs from app identity | Google authorization fails | Register exact `worq.order` package with each required signing fingerprint |
| Google Android auth APIs evolve | Integration churn or conflict with stable-only rule | Mandatory official-doc and stable-library gate in Milestone 9 |
| Sheets scope is sensitive | Consent/verification burden | Request the narrowest supported scope and document arbitrary-ID need; no Drive-wide scope without approval |
| Collaborative sheet changes race an export | Possible remote conflict | Marker, narrow reads, atomic batch, raw values, idempotent retry; never change Room |
| Wall-clock correction while timer runs | Live and persisted elapsed can disagree | Monotonic live view, UTC persistence, non-negative clamp, explicit anomaly result |
| DST/zone changes and midnight transitions | Misassigned dates or intervals | Stored/pinned ZoneIds, `atStartOfDay`, three-part uniqueness, real-zone tests |
| Process death during timer mutation | Orphaned/open state | One Room transaction, singleton pointer, structural open-slot constraint, recovery tests |
| Client rename changes historical displayed name | User expectation mismatch | Retain approved live client reference behavior and document it visibly |
| CSV spreadsheet-formula interpretation | Downstream security concern | Faithful CSV plus security review/user guidance; do not silently mutate values |
| Room migration loss | Irrecoverable local truth | Schema exports from version 1, explicit migrations, populated tests, no destructive release fallback |
| Frequent timer recomposition | Battery/performance issues | Collection-scoped coarse ticker, derived state, profiling, and no tick writes |

## 21. Traceability

- Product behavior and concept reconciliation: `PRODUCT_SPEC.md`.
- Layering, toolchain, security, and test strategy: `ARCHITECTURE.md`.
- Tables, columns, constraints, preferences, and migrations: `DATA_MODEL.md`.
- Start/Stop, display, rollover, midnight, DST, and anomalies:
  `TIMER_AND_DATE_RULES.md`.
- Logical rows, CSV, Google protocol, setup, and failures: `EXPORT_SPEC.md`.
- Observable verification: `ACCEPTANCE_TESTS.md`.
- Fixed choices and unresolved inputs: `DECISIONS.md`.

Changing implementation behavior requires updating the corresponding source document
and acceptance tests in the same review.
