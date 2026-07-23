# WorqOrder Implementation Plan

## 1. Planning baseline and repository state

At the start of this planning milestone, the repository was clean and contained only:

- `README.md`: a two-line title/Android-only description;
- `LICENSE`: existing repository license, unchanged;
- `.gitignore`: standard Gradle/Android/IDE/keystore/Google-service ignores; and
- `.gitattributes`: automatic text/LF normalization.

There was no Gradle project, Android source/resource, Room schema, or prior planning document. This milestone adds Markdown planning only and intentionally leaves the existing files untouched.

## 2. Delivery rules

- Work one explicitly requested milestone at a time; do not begin later milestones opportunistically.
- Start each milestone by reading `AGENTS.md`, relevant specifications, repository status, and any newer decisions.
- At each milestone entry, remind the owner of unresolved conflicts or external inputs that are critical to that milestone; leave non-critical later decisions deferred.
- Preserve unrelated/user changes in a dirty worktree.
- Select current stable dependencies at implementation time and record the version matrix. Do not copy preview versions from samples.
- Keep changes vertically reviewable; behavior and tests land together.
- Each milestone ends with formatting, lint, unit tests, relevant instrumentation/UI tests, and applicable debug/release builds. Record commands/results.
- A discovered requirement conflict updates `DECISIONS.md` and stops only the affected work; it is not permission to silently choose new product behavior.

## 3. Pre-implementation inputs

Fixed inputs and checks before Milestone 1 scaffolding is considered durable:

1. Use the final Android `applicationId` and Kotlin namespace `worq.order` exactly.
2. Confirm that the existing license is intended for this application (no change is proposed here).
3. Inventory Android Studio/SDK/JDK and set a local SDK path without committing it.
4. Verify current stable AGP, Gradle, Kotlin, Compose compiler/plugin, and KSP compatibility with installed API 36.1; install stable components if required.

D-030 fixes CSV delivery to `ACTION_CREATE_DOCUMENT`. Google Cloud ownership/configuration can wait until Milestone 6, and `worq.order` remains stable from Milestone 1.

## 4. Milestone 1 — Project and persistence foundation (recommended next)

This is the exact recommended next milestone. It deliberately excludes user-facing task/timer/export implementation.

### Scope

- Scaffold one native Android application module with Kotlin DSL, version catalog, Compose/Material 3, Compose Navigation foundation, min API 26, target API 36, and the verified latest stable installed compile SDK.
- Establish namespace/application ID as `worq.order`, debug/release build types without secrets, stable lint/format/test configuration, and Room schema export directory.
- Add a minimal accessible app/activity/theme/navigation shell that builds, with no pretend task functionality.
- Add the manual `ApplicationContainer` and abstractions for UTC clock, monotonic time, effective/device zone, dispatcher provider, document destination, and Google gateway interfaces only.
- Implement database version 1 entities/DAOs/transactions for clients, daily tasks, work intervals, and singleton active timer exactly per `DATA_MODEL.md`.
- Decide/test the optional partial unique open-interval index/trigger versus the approved singleton/internal-DAO invariant; record the final database choice.
- Add Preferences DataStore typed models/defaults (Dark, device zone, CSV default, null selection/connection), without credentials.
- Add repository interfaces and persistence implementations needed to prove create/read/constraints; do not add screens/workflows beyond the shell.
- Add UUID/time/date converters and pure duration formatting/canonical-name validation where required by the schema.
- Commit Room version 1 schema JSON and database documentation alignment.

### Explicit exclusions

- No main task list/create/edit/client/settings screens beyond a non-functional navigation/theme shell.
- No functional Start/Stop, ticker, rollover, midnight splitting, CSV picker/serialization, Google dependency/authorization/API call, Google Cloud configuration file, Firebase, or XLSX.
- No seed/sample production data.

### Verification/done gate

- Fresh database creation and reopen instrumentation tests.
- Tests for foreign keys, client active-name uniqueness, series/date/zone uniqueness, interval ordinal uniqueness, cascade/restrict behavior, and active singleton/open invariant.
- DataStore default/persistence tests where practical.
- Pure tests for canonical names, epoch-day/ZoneId conversion, and accumulated duration formatting.
- Dependency report confirms stable releases, KSP rather than kapt where supported, no prohibited libraries/permissions/secrets.
- Formatting, lint, unit tests, instrumentation tests, and debug plus applicable release build pass under the documented JDK/SDK matrix.

## 5. Milestone 2 — Local clients and daily-task workflow

Entry: Milestone 1 accepted.

### Scope

- Client repository/domain validation and active/archive/restore/rename transactions.
- Main screen static state: app bar, labeled date navigation/picker, task list/empty state, selection, totals from completed intervals, Add and destination-aware export placeholder disabled until its later milestone.
- Task creation for displayed date, inline Add Client using the same repository operation, 200-character validation/count, Create/Cancel semantics.
- Settings client management with active sorted list and archived restore path.
- Task metadata edit and confirmation-based daily-task deletion, with running-state guards already modeled even though timer UI is not yet enabled.
- DataStore preferred-series/concrete-task persistence and dangling-selection repair without actual date rollover creation.

### Tests/gate

Acceptance sections UI/clients/task create-edit-delete that do not require a running timer; Room transaction and ViewModel/coroutine tests; central Compose flows/accessibility; full milestone quality gate.

## 6. Milestone 3 — Timer, rollover, and midnight normalization

Entry: local CRUD accepted and fake time fixtures available.

### Scope

- `TimerCoordinator`, `TimerNormalizer`, `DailyRolloverService`, and process-local monotonic anchor/ticker.
- Transactional Start/Stop and one-global-active enforcement.
- Main timer `HH:MM:SS.mmm`, Start/Stop state, running row lock, selection switching block.
- Activity/process/device-restart recovery on next launch.
- Actual-date selection rollover with unique find/create.
- Foreground/resume/operation normalization and one/multiple-midnight split with pinned session zone.
- Clock-anomaly state and recovery UX; no foreground service or tick writes.

### Tests/gate

All timer and date acceptance scenarios, including concurrency, rotation, process reconstruction, background/device sleep, exact/multiple midnights, 23/25-hour days, spring/fall DST, external zone changes, and anomaly fixtures. Instrument transaction invariants and run Compose central workflow tests.

## 7. Milestone 4 — Interval editor and local settings completion

Entry: timer rules accepted.

### Scope

- Chronological interval detail, start/stop edit, delete, totals, and running locks.
- Zone-aware local date/time editor with DST gap rejection and overlap-offset choice.
- Optional manual interval Add only if it fits the milestone without weakening tests.
- Device/manual ZoneId settings and searchable/navigable geographical selector; block writes while active.
- Explicit Light/Dark UI with immediate persistence; optional System only after required choices work.
- Complete task/client/settings layouts, adaptive/font-scale/accessibility polish based on concepts.

### Tests/gate

All interval/time-zone/theme/accessibility acceptance scenarios; fake zones/clocks; ViewModel and Compose tests; full quality gate.

## 8. Milestone 5 — CSV logical export and delivery

Entry: accepted D-030 behavior and stable local data/timer normalization.

### Scope

- Immutable schema version 1 row model and `ExportRowBuilder` with one Room/export-instant snapshot.
- Exact deterministic sort, zero-interval/running-interval handling, local/UTC timestamps, accumulated duration fields.
- UTF-8 RFC-style CSV serializer and destination adapter using the approved SAF flow.
- Date/destination-specific main action, cancellation/failure/repeat outcome, last-export safe state.
- Storage UX consistent with the approved Downloads/WorqOrder reconciliation; no broad permission.

### Tests/gate

Golden schema tests, Unicode/quote/comma/CRLF tests, large-hour/DST/running snapshots, cancellation/partial failure, provider instrumentation, manifest/dependency checks for no storage permission/XLSX, and full quality gate.

## 9. Milestone 6 — Google authorization, connection, and export

Entry: CSV row model stable; signing strategy and Google Cloud owner available. The application ID is fixed as `worq.order`.

### Mandatory discovery gate

- Re-read current official Google Identity Android authorization, Credential Manager/sign-in, Sheets API/scopes, OAuth verification, and client setup documents.
- List candidate stable dependencies and prove min API/toolchain compatibility.
- If the only supported route requires a preview library or contradicts the no-backend model, stop for a documented exception/design decision rather than silently proceeding.
- Confirm Sheets-only scope still supports typed arbitrary existing spreadsheet IDs. If not, propose a Google picker/per-file workflow and update the product decision before code.

### Scope

- Publisher setup runbook using `EXPORT_SPEC.md`: Cloud project, Sheets API, consent/test users/verification, Android OAuth clients, package/fingerprints, and non-secret configuration.
- Stable supported sign-in/account choice, authorization, sign-out, expired-auth recovery; no token persistence/Firebase/service account.
- URL/ID parse, validation, connected title/ID display, Disconnect, one-connection/default destination settings.
- Gateway and date-tab marker protocol; create/revalidate/conflict/atomic replacement/sort/raw values.
- Offline, permission, cancellation, rate limit, server, ambiguous response, retry, and useful persistent safe outcome.
- Main Google label/routing and row-model reuse.

### Tests/gate

Fake-gateway unit/ViewModel/Compose scenarios plus controlled integration tests against a non-production spreadsheet/account. Prove no new spreadsheet is created, unchanged re-export has no duplicates, changes/deletes replace correctly, conflicts remain untouched, other tabs remain untouched, local Room never changes, and credential/scope static checks pass.

## 10. Milestone 7 — MVP hardening and release candidate

Entry: all feature milestones individually accepted.

### Scope

- Full API 26–target device/emulator matrix, upgrade/migration matrix, process-death/background/DST exploratory tests.
- Performance profiling for long daily lists/ticker/Room queries/export payloads; fix measured issues without architecture expansion.
- Accessibility audit, large-font/small-screen/Light/Dark review, final copy/error recovery.
- Privacy/security review of logs, scopes, storage, CSV formula risk, manifest, dependency/license inventory, signing/build configuration.
- Google consent/verification production readiness and release runbook.
- Update README/user documentation and produce signed release candidate through the owner's release process.

### Gate

Every acceptance scenario mapped to passing evidence; clean database upgrade; no prohibited dependency/permission/credential; formatting/lint/unit/instrumentation/UI tests and debug/release builds pass; known limitations documented.

## 11. Dependency selection checklist

At the first milestone that needs a dependency:

1. Prefer Android/Jetpack/Kotlin/Google official artifacts.
2. Check official stable release notes and Maven metadata; reject alpha/beta/RC/dynamic versions.
3. Verify min SDK 26 and stable AGP/Kotlin/KSP compatibility.
4. Add one version-catalog alias and rationale; avoid redundant/transitive stacks.
5. Check license, size, manifest additions, permissions, and credential behavior.
6. Run the whole milestone gate and commit dependency lock/report artifacts only if the project adopts them.

Likely categories, not frozen versions: Android Gradle/Kotlin/Compose plugins, Compose BOM stable, activity-compose, Material 3, navigation-compose, lifecycle ViewModel/runtime Compose, coroutines, Room runtime/ktx/compiler KSP/testing, DataStore preferences, AndroidX test/JUnit/Compose test, coroutine-test, and the later minimal stable Google identity/Sheets client set.

## 12. Risk register

| Risk | Impact | Mitigation/gate |
| --- | --- | --- |
| `ACTION_CREATE_DOCUMENT` cannot force Downloads folder | Files may be saved outside `Downloads/WorqOrder` | Accepted D-030 gives location control to the standard system picker; no storage-permission workaround |
| OAuth configuration differs from app identity | Google authorization fails | Register the exact `worq.order` application ID with each required signing fingerprint |
| API 36.1 minor compile syntax/tool compatibility | Build failure or preview-tool temptation | Prove stable matrix in Milestone 1; install latest mutually stable SDK if needed |
| Google Android auth APIs evolve/stable sample gap | Integration churn or policy conflict | Mandatory official-doc/stable-library gate in Milestone 6 |
| Sheets scope is sensitive | Consent/verification burden | Sheets-only scope with written arbitrary-ID justification; no Drive scope |
| Collaborative sheet changes between marker check/write | Possible remote conflict | Exact marker, narrow read, atomic batch, raw values, idempotent retry; never change Room |
| Wall-clock correction while timer runs | Live and persisted elapsed can disagree | Monotonic live view, UTC persistence, explicit anomaly state/manual correction |
| DST/zone changes and midnight transitions | Misassigned dates/durations | Stored ZoneIds, `atStartOfDay`, pinned active zone, fake-clock/real-zone tests |
| Process death during multi-step timer mutation | orphaned/open state | One Room transaction, singleton pointer, invariant tests/recovery check |
| Client rename changes historical displayed name | User expectation mismatch | Accepted D-022 and visible documentation; add snapshot only through schema decision |
| CSV spreadsheet-formula interpretation | Downstream security concern | raw Sheets writes; faithful CSV plus release review/user guidance, no silent mutation |
| Room migration loss | Irrecoverable local truth | schema exports from v1, explicit migrations, populated migration tests, no destructive fallback |
| Very frequent timer recomposition | battery/performance issues | collection-scoped coarse ticker, derived state, profile and adjust without losing timestamp accuracy |

## 13. Traceability

- Product behavior and concept reconciliation: `PRODUCT_SPEC.md`.
- Layering/toolchain/security/test strategy: `ARCHITECTURE.md`.
- Tables, columns, constraints, preferences, migrations: `DATA_MODEL.md`.
- Start/Stop/display/rollover/midnight/DST/anomalies: `TIMER_AND_DATE_RULES.md`.
- Logical rows, CSV, Google protocol/setup/failures: `EXPORT_SPEC.md`.
- Observable verification: `ACCEPTANCE_TESTS.md`.
- Fixed choices and unresolved inputs: `DECISIONS.md`.

Changing implementation behavior requires updating the corresponding source document and acceptance tests in the same review.
