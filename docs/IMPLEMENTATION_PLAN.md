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
5. CSV and XLSX delivery use the standard `ACTION_CREATE_DOCUMENT` save flow.
6. Daily-task identity is unique by `(task series ID, work date, ZoneId)`.
7. Room is authoritative; CSV, XLSX, and Google Sheets remain one-way export
   destinations.
8. WorqOrder remains free and open source under GPLv3. No supported milestone may introduce
   billing, a paid API tier, paid quota, Google Workspace/organization membership, or a custom
   domain requirement.
9. Distribution is the owner-signed APK from GitHub. Debug and direct-release Google OAuth
   identities bind to their exact package/signing SHA-1.
10. Google export uses no-cost standard quota and fails closed to CSV if Google's policy or
    available quota no longer permits that path.
11. The required production sequence relies on Android's application sandbox and does not claim
    WorqOrder-managed at-rest encryption for Room or DataStore. The complete encryption hardening
    plan is deferred to separately authorized optional Milestone 19.
12. Biometric, device-credential, PIN, or account-gated access is not part of the production
    sequence. It is a separately authorized optional milestone after the current project is
    complete.

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
- Pure completed/active/total duration calculation and `HH:MM:SS` presentation formatting,
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

Status: implemented; awaiting owner acceptance.

### Scope

- Task creation for the displayed date, client selector, inline Add Client through the
  same client repository operation, Create/Cancel semantics, and selection behavior.
- Add the required short-description field and optional text field labeled
  **Hardware / Software Purchases** to task creation/editing. Trim both, enforce an
  independent 400-character maximum for each, and continue to reject blank short
  descriptions.
- Evolve Room from version 1 to version 2 by adding the non-null
  `daily_tasks.hardware_software_purchases` column with an empty default. Export the
  version-2 schema and add a populated non-destructive `1 -> 2` migration test.
- Extend task models, repositories, and the existing selection-rollover/midnight-copy
  coordination so both text fields follow the documented daily-copy metadata rules.
- Task selection and accumulated total presentation for current and historical dates.
- Daily task metadata editing and chronological interval detail.
- Manual interval add/edit/delete using Milestone 3 validation and explicit DST choices.
- Confirmed daily-task deletion with running guards, transactional interval cascade,
  and no deletion of clients or other daily series copies.

### Verification gate

- Unit, Room, ViewModel, and Compose tests cover creation/cancellation, both 400-character
  text-field contracts, populated database migration, metadata rollover/copying,
  selection, repeated intervals, editor validation, DST input, running locks, and
  deletion scope.

## 10. Milestone 7 — Time-zone, theme, export-default, and persistent application settings

Entry: local task workflows accepted.

### Scope

- Preferences DataStore models and repositories for System/Light/Dark appearance,
  device/manual ZoneId mode, manual geographical ZoneId, default export destination,
  selection state consolidation, and non-secret export status.
- System appearance is the first-launch default and follows the device; Light and Dark
  remain enabled explicit overrides. All theme changes apply immediately.
- Place Client Management first in the Settings list, followed by Appearance, Time zone,
  export default, and Google connection status.
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

- Immutable destination-neutral schema-version-2 logical export rows built from one authoritative
  snapshot for the displayed date.
- Include **Hardware / Software Purchases** immediately after Description in the shared
  schema, preserving blank values and the same RFC-style handling as other user text.
- Exactly nine visible string columns shared by every destination: Work Date, Client Name,
  Description, Hardware / Software Purchases, Interval Number, Start Local, Stop Local, Interval
  Duration Formatted, and Task Total Duration Formatted.
- One row per interval, zero-interval rows, deterministic internal sorting, task-zone `HH:mm`
  clock output, accumulated `HH:MM:SS` duration output, and global running-timer export lockout.
- UTF-8 RFC-style CSV serialization with correct Unicode, comma, quote, and line-break
  handling.
- Standard `ACTION_CREATE_DOCUMENT` delivery with suggested
  `worqorder_YYYY-MM-DD.csv` name, cancellation/failure/repeat outcomes, and no broad
  storage permission.
- Date-specific Main action label and persistent non-sensitive result state.

### Verification gate

- Golden nine-column schema/escaping tests, clock-only/DST/duration-truncation/running lockout,
  picker cancellation and provider failure tests, and static checks for no premature XLSX or
  storage permission.

## 12. Milestone 9 — Current Google authentication and Sheets integration plan

Entry: CSV logical row model stable. The owner supplies Google Cloud ownership and
signing inputs when this milestone begins.

Status: research and design completed; awaiting owner acceptance.

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

- Credential Manager `1.6.0`/Google ID `1.2.0` account-choice design,
  `AuthorizationClient` from `play-services-auth:21.6.0`, and
  `kotlinx-coroutines-play-services:1.10.2`.
- Per-file `drive.file` grant through the official Android Google Picker flow filtered
  to the pasted spreadsheet ID, followed by Drive edit-capability and Sheets metadata
  validation.
- Small fakeable HTTPS/JSON Drive v3 and Sheets v4 gateway design, token/sign-out
  model, error taxonomy, test strategy, ADR, and Google Cloud setup guide.
- No account UI, authorization code, or network call is required in this planning
  milestone.

## 13. Milestone 10 — Google account and spreadsheet connection settings

Entry: Milestone 9 integration plan approved; personal Google Cloud project, three APIs, External
Testing audience/test accounts, debug Android OAuth client, Web OAuth client ID, and test
spreadsheets are available. Release signing and any release OAuth client remain deferred.

### Scope

- Stable Credential Manager account choice, per-file Picker authorization, sign-out,
  grant-revocation reporting, expiry recovery, and cancellation.
- Spreadsheet URL/ID parsing, exact-ID Picker filtering, Drive edit-capability plus
  Sheets metadata validation, title/ID display, exactly-one connection, disconnect,
  and Google Sheets default-destination selection.
- No Firebase, service account, embedded secret, raw token in DataStore, Drive-wide
  authorization, or spreadsheet creation.
- No marketplace configuration, billing account, paid quota, Workspace organization, custom
  domain, or verified-brand dependency.

### Verification gate

- Fake-gateway unit/ViewModel/Compose tests plus controlled account/connection
  integration checks; credential, manifest, and scope static audits.

## 14. Milestone 11 — Idempotent Google Sheets export

Entry: account and spreadsheet connection accepted.

Status: implemented; awaiting owner acceptance.

### Scope

- Consume the one immutable destination-neutral schema-version-2 snapshot; do not independently
  select, order, or format fields.
- Include hardware/software-purchases text through that shared schema and write it as a
  raw value.
- Export only to the single connected spreadsheet and one application-owned
  `WorqOrder_YYYY-MM-DD` tab per displayed date.
- On the first export, reuse/rename the original first tab only after confirming the entire
  spreadsheet has no user content. Otherwise preserve all tabs and add the date tab.
- Store marker/schema/date in sheet-scoped developer metadata so row 1 can be the same nine-column
  header as CSV/XLSX. Reject unmarked same-name conflicts and atomically replace application-owned
  date-tab contents with the current authoritative snapshot.
- Create each date sheet with exactly nine columns and the required row count, and resize on
  replacement to avoid unnecessary use of Google Sheets' 10-million-cell spreadsheet limit.
- Preserve all other tabs, never import to Room, never create a spreadsheet, and expose
  useful retryable offline/auth/network/ambiguous-response states.
- Use bounded explicit-operation traffic within standard no-cost quotas; quota exhaustion never
  triggers billing, paid capacity, background work, or an unbounded retry.
- Implementation uses a fresh in-memory `drive.file` token, one narrow spreadsheet structure read,
  a blank-content inspection only when no prior WorqOrder marker exists and the requested tab is
  absent, and one atomic `spreadsheets.batchUpdate`. Sheet-nested marker/schema/date parsing,
  literal `stringValue` cells, confirmed spreadsheet ID, typed ambiguous-result handling, and
  generic non-sensitive last-attempt metadata are implemented without an export-history Room
  table.

### Verification gate

- Unchanged re-export creates no duplicates; changed/deleted local data replaces
  correctly; conflict tabs and unrelated tabs remain untouched; failed exports do not
  mutate Room or claim success.

## 15. Milestone 12 — Focused XLSX export

Entry: the Google Sheets export and shared logical export model are accepted, and the owner has
approved the final unified export columns.

### Scope

- Add XLSX as the third and final export destination alongside CSV and Google Sheets.
- Create one fresh XLSX workbook per export through `ACTION_CREATE_DOCUMENT`, parallel to CSV.
  Do not open existing workbooks, retain URI grants, or store workbook connection metadata.
- Reuse the unified logical snapshot, field semantics, displayed-date rule, deterministic row
  order, zero-interval behavior, and running-timer export lockout.
- Put exactly one `WorqOrder_YYYY-MM-DD` worksheet in each workbook, with the exact shared header
  at row 1 and complete canonical rows beginning at row 2.
- Use the official OOXML spreadsheet MIME type and suggested
  `worqorder_YYYY-MM-DD.xlsx` filename. Request no storage permission.
- Write all nine canonical values as literal text cells so formula-like input cannot execute and
  visible content remains equivalent to CSV/Google.
- Use the owner-approved focused internal writer with bounded memory and deterministic minimal
  ZIP/OOXML output. Add no XLSX dependency; Apache POI remains prohibited unless a separately
  documented owner decision supersedes that constraint.
- Expand the typed export-default setting and Main/Settings labels to exactly CSV, XLSX, and
  Google Sheets. Preserve CSV as the first-launch/corrupt-value fallback.
- Keep XLSX one-way and repeatable. Cancellation/failure must not mutate Room, claim success, or
  leave an app-managed partial file.
- Build the complete workbook package before opening the picker. After selection, write once,
  close deterministically, and use best-effort deletion if provider output fails.

### Security and correctness verification gate

- Golden workbook tests cover exact headers, shared row semantics, empty dates, zero/multiple
  intervals, running lockout, deterministic ordering, long durations, task-zone clock values,
  Unicode, commas, quotes, CR/LF, and formula-prefixed user strings as literal cells.
- Parse generated workbooks with an independent test reader and manually open representative
  files in current Microsoft Excel and LibreOffice.
- Test malformed/write-failure cleanup, picker cancellation, repeated independent exports,
  large-snapshot memory/time, no local mutation, no broad storage permission, and no app-private
  plaintext staging.
- Audit the ZIP package for required OOXML parts, path traversal hazards, external links, macros,
  formulas, hidden content, credentials, and unnecessary metadata.
- Run formatting, lint, unit, instrumentation/UI tests, and applicable debug/release builds.

## 16. Milestone 13 — Lifecycle, process-death, reboot, and timer hardening

Entry: core local and all three export workflows accepted.

Status: implemented; awaiting owner acceptance.

### Delivered scope

- An application-scoped recovery coordinator now serializes resume recovery, resolves the
  effective ZoneId, normalizes Room's authoritative active timer, and reconciles selection.
- Activity resume triggers recovery regardless of the visible navigation destination; Main also
  performs an idempotent retry and reconstructs presentation state from persisted data.
- Live display uses one collection-scoped 200-millisecond monotonic ticker without database tick
  writes or background execution.
- Active-timer reads fail closed for orphaned, missing, or multiple open intervals instead of
  treating structurally inconsistent persistence as a stopped timer.
- In-process wall-clock anomalies are compared with the monotonic anchor before date-boundary
  persistence. Owner-directed follow-up D-057 projects normalization and Stop from the valid
  monotonic anchor, preventing false midnight segments and final-total jumps after wall changes.
- Recovery, concurrency, process reconstruction, multiple-midnight, time-zone, clock-anomaly,
  rollback, and UI lifecycle behavior have automated coverage. Device-only scenarios are
  documented in `LIFECYCLE_TEST_PLAN.md`.
- No foreground service, alarm, wake lock, boot receiver, WorkManager timer job, or per-tick
  persistence was added.

### Scope

- Wire foreground/resume/operation normalization to Android lifecycle boundaries.
- Harden activity recreation, background/device sleep, process death, reboot, stale
  selection, active-timer recovery, and clock-anomaly recovery.
- Exercise transaction interruption and invariant repair paths without a foreground
  service, alarm, or per-tick persistence.

### Verification gate

- Instrumented lifecycle/process simulations and manual reboot/background scenarios
  prove persisted open-interval recovery and correct normalization.

## 17. Milestone 15 — Accessibility, usability, and error-state refinement

Entry: all primary MVP workflows function through Milestone 13.

### Scope

- Accessibility semantics, touch targets, TalkBack order, large font, small-screen and
  adaptive layout, contrast, and Light/Dark review.
- Refine empty, loading, disabled, validation, conflict, offline, authorization, retry,
  storage failure, and destructive-confirmation states without changing approved behavior.
- Profile long task lists and timer recomposition; fix measured issues without
  architecture expansion.

### Verification gate

- Automated accessibility/UI coverage plus documented manual device, font-scale,
  TalkBack, theme, and error-recovery checks.

### Implemented owner-directed cancellation refinement

- CSV/XLSX picker cancellation and Google export authorization cancellation write nothing, alter
  no Room data, clear in-progress UI, and show no Main-screen cancellation status. The diagnostic
  last-attempt outcome may remain `Canceled`.

## 18. Milestone 16 — Full specification audit, regression testing, and security review

Entry: feature and refinement milestones accepted.

### Scope

- Map every product requirement and acceptance scenario to implementation and passing
  evidence.
- Keep the timer and date-selector bar pinned above an independently scrolling lower Main
  message/task region and present newly created daily tasks first; preserve Room and canonical
  export ordering.
- Run the full unit, coroutine, Room/migration, ViewModel, Compose, lifecycle, CSV, XLSX, fake
  Google, and controlled integration suites.
- Audit permissions, logs, credentials, OAuth scopes, backup behavior, storage, spreadsheet
  ownership, spreadsheet-formula risk, dependency licenses and
  vulnerabilities, migration policy, no-billing/no-Play policy, standard quota behavior, and
  prohibited technology.
- Resolve specification drift through explicit decisions and documentation updates.

### Verification gate

- No unmapped required behavior, unexplained test gap, prohibited dependency,
  permission, credential, destructive migration, or known data-loss path remains. Documentation
  explicitly states that WorqOrder-managed local at-rest encryption is not current production
  scope.

## 19. Milestone 17 — Release preparation and developer handoff

Entry: Milestone 16 audit accepted.

### Scope

- Final API/device and populated-database migration matrix, performance check, and
  debug/release build verification.
- Versioning, signing and release configuration through the owner's secure process.
- Create the permanent direct-release keystore/fingerprint and matching Android OAuth client through
  the owner's secure process.
- Move the External OAuth audience to In Production so the test-user list no longer gates any
  Google Account. Prove the release-signed APK with an account never listed as a tester; document
  external Workspace/Advanced-Protection restrictions and retain repository-hosted privacy/user
  guidance without making a custom domain or paid brand verification a release dependency.
- Document no-cost standard quota behavior, CSV fallback, external plaintext exports, the absence
  of WorqOrder-managed local at-rest encryption, known limitations, and maintenance guidance.
- Update README and produce a release candidate without committing signing material,
  credentials, tokens, `local.properties`, or generated local state.
- Initial release identity is fixed at `0.1.0` / code `1`. Publish the owner-signed APK and SHA-256
  through a GitHub Release. Keep R8/resource shrinking disabled unless a separately tested owner
  decision changes that policy.

### Verification gate

- Formatting, lint, all unit/instrumentation/UI/integration/security tests, migration
  verification, and applicable release build pass; handoff documentation is complete and
  reproducible.

## 20. Optional Milestone 18 — Post-project product options and application-access security

Entry: the current production project through Milestone 17 is fully completed and accepted, and
the owner separately gives explicit permission to begin this optional milestone.

This milestone is not required for current production completion. Do not remind the owner about it
until Milestone 17 is completely finished, and never begin it from a general request to continue
the current project.

### Optional scope

- Revisit the threat model specifically for unauthorized use of an unlocked or shared device.
- Offer an opt-in app lock backed by Android BiometricPrompt with device credential where supported;
  decide whether a local PIN fallback is acceptable without weakening Keystore protection.
- Define lock-on-launch, background timeout, screen-off, process-death, task-switcher, and
  sensitive-screen reauthentication behavior without interrupting or corrupting a running timer.
- Keep core records local and offline. Do not add a WorqOrder cloud account, custom backend,
  password database, remote recovery service, or Google-account requirement merely to unlock the
  app.
- Add privacy-screen controls such as sensitive recent-app previews and screenshots only after
  explicit usability review.
- Design recovery and key-binding behavior so enrollment changes, credential removal, or biometric
  lockout never cause silent database deletion. Clearly document any security/recovery tradeoff.
- Add an explicit XLSX mode choice: keep the production separate one-off workbook for each manual
  export or use a connected persistent workbook. Both modes consume the same canonical
  nine-column snapshot and never change Room.
- Optionally schedule automatic export of the just-completed local date at its ZoneId-aware
  midnight only when the selected destination is Google Sheets or a valid persistent XLSX
  workbook. CSV and one-off XLSX are excluded because they require a user-selected destination.
- Design automatic export around current Android background-execution guidance without a
  foreground service merely waiting for midnight. Define reboot/catch-up, Doze, zone changes,
  offline/auth expiration, missing/revoked XLSX URI, bounded retry, duplicate/idempotent
  replacement, notification/privacy, enable/disable controls, and battery behavior before
  implementation.
- Automatic XLSX recovery may prompt for a replacement document only while the user is present; a
  background run with an invalid URI records a safe pending failure and cannot silently select a
  filesystem destination.
- Simplify routine Task interval cards so completed Start and Stop values display only task-zone
  `HH:mm`. Retain the full persisted UTC instants, stored task ZoneId, and DST occurrence/offset
  information; the interval editor must still expose occurrence details when a fall-back overlap
  makes them necessary. Keep the already-implemented CSV/XLSX/Google Start/Stop output at the same
  `HH:mm` precision through the one canonical export formatter.
- Add an **About** section as the final Settings content. Display `BuildConfig.VERSION_NAME` as
  plain text and provide a small accessible external link to the exact GitHub Release tag for that
  installed version, using the canonical public repository base URL and
  `releases/tag/v{versionName}`. Do not hardcode a version independently of Gradle.
- Replace the current phone-landscape Main arrangement with a two-column layout. In the default
  **Right-handed** mode, the independently scrolling task list occupies approximately the left
  half and the right half contains four vertically stacked regions: title/Settings, timer, date
  controls, then Export/Add task. **Left-handed** mode mirrors the columns. Portrait behavior
  remains unchanged.
- Add a typed Preferences DataStore setting named **Landscape Orientation**, shown after
  Appearance, with explicit **Left-handed** and **Right-handed** radio buttons. Right-handed is the
  first-install and corrupt-value fallback. Apply changes immediately without changing Room,
  selection, timing, or export state.
- Add an optional running-timer lock-screen surface that shows the WorqOrder icon/name, current
  task name, and system-rendered elapsed timer only while an interval is active. Swiping it away
  hides it for that active interval without stopping or changing the timer; a later Start may show
  a new surface.
- Before implementing the lock-screen request, verify the current official Android mechanism and
  OS/version support. The requested swipe behavior most closely resembles a lock-screen-visible
  notification, while true lock-screen widgets are not uniformly available. Choose the narrowest
  stable API that meets the behavior, document any unavoidable notification-shade/device-setting
  limitations, request notification permission only where Android requires it, and never add a
  foreground service or app-driven tick loop merely to keep the elapsed display current.

### Optional security and bug verification gate

- Test successful/failed/canceled authentication, lockout, device-credential fallback, enrollment
  changes, background timeout boundaries, screen off/on, rotation, process death, reboot, and
  concurrent navigation.
- Test Start/Stop and midnight normalization while UI access is locked; locking must not stop,
  duplicate, or lose an active interval.
- Test accessibility of prompts, no sensitive content before unlock, no bypass through deep links,
  notifications, task-switcher snapshots, exported activities, or restored navigation state.
- Re-run Room, timer, export, lifecycle, Compose, and release regression suites and perform a
  focused bypass/abuse-case security review. Encryption suites apply only if optional Milestone 19
  has also been separately authorized and completed.
- Test persistent/one-off XLSX mode migration and switching, identical rows in both modes,
  create-document cancellation, unrelated-tab preservation, and stale URI recovery.
- Test midnight scheduling across ordinary days, spring-forward/fall-back, manual/device ZoneId
  changes, missed execution, Doze, reboot, offline/auth expiry, concurrent manual export, and
  repeated delivery. Each successful date must converge to one duplicate-free tab.
- Test that disabling automation cancels future work, that CSV/one-off XLSX never auto-run, and
  that no background failure mutates Room, creates unbounded retries, or exposes sensitive data.
- Test that interval cards omit seconds without changing persisted instants, duration calculations,
  edit precision, date-boundary validation, or explicit DST-overlap disambiguation.
- Test that About displays the exact Gradle-generated version and opens the corresponding public
  release URL for debug/release variants without exposing credentials or accepting an arbitrary
  URL.
- Test default/right-handed and persisted/left-handed landscape modes at API 26/current target,
  short and standard landscape, 200% font, large display scale, narrow multi-window bounds, and
  TalkBack. The task list must retain usable width/height and independent scrolling, while every
  control-region action remains reachable and at least 48 dp.
- Test lock-screen surface creation/removal, dismissal for one running interval, next-Start
  reappearance, Stop cleanup, process death, reboot/resume, screen lock/unlock, permission denial,
  private lock-screen settings, task rename/deletion constraints, and no Room/DataStore tick
  writes. It must not claim visibility when the OS/user suppresses private lock-screen content.
- Profile the optional lock-screen surface and mirrored landscape layout for CPU, memory, battery,
  recomposition, and background work. No implementation may regress the accepted five-Hz Main
  profile or introduce continuous background execution.
- Require explicit owner acceptance of every usability/security tradeoff before release.

## 21. Optional Milestone 19 — Data Protection and Encryption Hardening

Entry: optional Milestone 18 is complete or has been explicitly declined, and the owner separately
gives explicit permission to begin optional Milestone 19.

This milestone is not required for current production completion. Do not begin it from a general
request to continue or harden the current project. The required production build relies on
Android's application sandbox and must not claim WorqOrder-managed Room/DataStore encryption.

### Threat model and design gate

- Document assets, trust boundaries, attacker capabilities, and explicit limits before selecting
  a cryptographic implementation. The protected local assets include Room main/WAL/SHM content,
  sensitive DataStore values, export snapshots while held by the app, and diagnostic output.
- Select only stable, maintained, Android/API-26-compatible, GPLv3-compatible components. Prefer
  Android Keystore non-exportable key material, authenticated encryption such as AES-GCM, and a
  design that preserves Room transactions, indexes, migrations, and acceptable startup/query
  performance.
- Compare whole-database encryption with carefully bounded field/envelope encryption. Choose and
  record one design after proving it encrypts auxiliary database files and does not weaken
  normalized-name uniqueness, date/task queries, or migration support.
- Define key creation, versioning, rotation, device-unlock availability, backup/restore behavior,
  and fail-closed handling for missing, invalidated, or corrupt keys. Never silently delete or
  recreate authoritative Room data after a cryptographic failure.

### Optional implementation scope

- Migrate existing plaintext Room data to encrypted-at-rest storage through a crash-safe,
  non-destructive, tested upgrade path with rollback/recovery guidance.
- Encrypt sensitive DataStore-held metadata using the same reviewed key hierarchy or a separate
  purpose-bound key. Keep raw Google access/refresh/ID tokens prohibited.
- Disable Android backup for protected app data or define explicit encrypted
  backup/data-extraction exclusions so ciphertext is never restored without its usable key.
- Keep sensitive task/client/export content out of logs, crash messages, analytics, notifications,
  clipboard, previews, and app-private temporary files. Continue using in-memory immutable export
  snapshots and clear references promptly after delivery.
- Preserve TLS-only Google transport and Google-managed authorization. Readable Google Sheets
  cells are not end-to-end encrypted by WorqOrder. User-directed CSV and XLSX documents cross the
  app boundary as unencrypted files; the save UI and documentation must state that responsibility.
- Document that plaintext necessarily exists transiently in process memory while the unlocked app
  displays, edits, or exports it, and that this milestone does not defend against an attacker who
  controls an already-unlocked device/process.

### Optional security, migration, and bug verification gate

- Populate a real prior-version plaintext database and preferences, upgrade them, verify every
  client/task/interval/active-timer/selection/setting, then reopen after process death and reboot.
- Search the encrypted database, WAL, SHM, DataStore, cache, backup artifacts, logs, and crash
  output for seeded canary plaintext; no protected canary may be recoverable at rest.
- Test fresh install, upgrade interruption at each durable phase, low-storage/write failure,
  corrupted ciphertext, wrong/missing/invalidated key, key-version rotation, concurrent reads and
  writes, active timer during upgrade gating, and downgrade behavior. Every failure must be
  explicit and non-destructive.
- Re-run Room constraints/migrations, timer lifecycle/recovery, all export snapshot/delivery,
  Google authorization, optional app-access behavior if present, and UI regression suites to catch
  encryption integration bugs.
- Benchmark startup, date-list queries, client normalization/conflict checks, timer Start/Stop,
  migration time, database growth, memory, and battery against recorded pre-encryption baselines.
- Audit release manifests, backup/data-extraction rules, dependency licenses/CVEs, random number
  use, nonce uniqueness, key aliases/purposes, logging, temporary files, and release-build
  obfuscation behavior.
- Run formatting, lint, unit, instrumentation/UI/security tests, and debug/release builds on at
  least API 26 and the current target API.

## 22. Dependency selection checklist

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
coroutines, Room, Preferences DataStore, and AndroidX testing. Google dependencies were
selected through the mandatory Milestone 9 discovery gate. XLSX dependencies passed this
checklist in Milestone 12. Local-encryption dependencies may be considered only if optional
Milestone 19 is separately authorized and must pass this checklist before being added.

## 23. Risk register

| Risk | Impact | Mitigation/gate |
| --- | --- | --- |
| `ACTION_CREATE_DOCUMENT` cannot force a Downloads subfolder | The system picker may save outside `Downloads/WorqOrder` | Accepted D-030 gives final location control to the user; do not add a storage-permission workaround |
| OAuth configuration differs from app identity | Google authorization fails | Register exact `worq.order` plus debug SHA-1 now and direct-release SHA-1 only in Milestone 17; no Play identity |
| Google Android auth APIs evolve | Integration churn or conflict with stable-only rule | Milestone 9 selected current stable versions; recheck official releases at Milestone 10 implementation |
| Picker grant does not match pasted spreadsheet ID | Wrong file connected or per-file access unavailable | Filter Picker by exact ID/MIME type, reject every nonempty `picked_file_ids` mismatch, and require exact-ID Drive capability plus Sheets metadata validation even when reconnect reuses a retained grant with no repeated Picker IDs |
| OAuth project remains in Testing | Only listed testers can authorize and `drive.file` grants can expire after seven days | Before distribution, use External/In Production and prove an unlisted account can connect/export; `drive.file` is non-sensitive and verified branding/custom domain remains optional |
| Google standard quota or policy changes | Google export could fail or invite paid capacity | Never attach billing or buy quota; use bounded explicit calls, show a useful failure, keep CSV available, and require a new owner decision |
| Collaborative sheet changes race an export | Possible remote conflict | Marker, narrow reads, atomic batch, raw values, idempotent retry; never change Room |
| XLSX writer is incompatible or unsafe | Malformed workbooks or formula execution | Focused internal Milestone 12 writer, literal cells, independent-parser/golden tests, Excel/LibreOffice checks, and no Apache POI |
| One-off XLSX provider write fails after document creation | A partial external file may remain | Build and validate bytes before the picker, close output deterministically, attempt provider deletion on failure, report partial-output risk, and never change Room |
| Per-date sheets exhaust Google grid allocation or become unwieldy | Export failure or poor spreadsheet usability | Exactly nine columns, required row counts, resize on replacement, monitor the official 10-million-cell spreadsheet limit, and surface a capacity error before mutation |
| Plaintext app-private database/preferences are extracted from a compromised or sufficiently privileged device | Sensitive client/task data is disclosed | Document that current production relies on Android's application sandbox and does not provide WorqOrder-managed at-rest encryption; retain stronger protection only as optional Milestone 19 |
| Optional encryption key is lost or invalidated | Authoritative local data becomes unavailable if optional Milestone 19 is later implemented | Require versioned key hierarchy, documented recovery limits, non-destructive failure, interrupted-migration tests, and never silently reset Room |
| Optional encryption degrades core performance | Slow startup, task lists, or timer mutations if optional Milestone 19 is later implemented | Record pre-encryption baselines and enforce focused startup/query/migration/memory benchmarks within that optional milestone |
| User assumes local records or exported files/Sheets are end-to-end encrypted | Sensitive data is handled under an incorrect expectation | Explicitly document that current Room/DataStore rely on the Android sandbox, CSV/XLSX are unencrypted user-controlled files, and readable Sheets rely on Google/TLS controls |
| Wall-clock correction while timer runs | Live and persisted elapsed can disagree | Monotonic live view, UTC persistence, non-negative clamp, explicit anomaly result |
| DST/zone changes and midnight transitions | Misassigned dates or intervals | Stored/pinned ZoneIds, `atStartOfDay`, three-part uniqueness, real-zone tests |
| Process death during timer mutation | Orphaned/open state | One Room transaction, singleton pointer, structural open-slot constraint, recovery tests |
| Client rename changes historical displayed name | User expectation mismatch | Retain approved live client reference behavior and document it visibly |
| CSV spreadsheet-formula interpretation | Downstream security concern | Faithful CSV plus security review/user guidance; do not silently mutate values |
| Room migration loss | Irrecoverable local truth | Schema exports from version 1, explicit migrations, populated tests, no destructive release fallback |
| Frequent timer recomposition | Battery/performance issues | Collection-scoped coarse ticker, derived state, profiling, and no tick writes |

## 24. Traceability

- Product behavior and concept reconciliation: `PRODUCT_SPEC.md`.
- Layering, toolchain, security, and test strategy: `ARCHITECTURE.md`.
- Tables, columns, constraints, preferences, and migrations: `DATA_MODEL.md`.
- Start/Stop, display, rollover, midnight, DST, and anomalies:
  `TIMER_AND_DATE_RULES.md`.
- Logical rows, CSV, XLSX, Google protocol, setup, and failures: `EXPORT_SPEC.md`.
- Observable verification: `ACCEPTANCE_TESTS.md`.
- Fixed choices and unresolved inputs: `DECISIONS.md`.

Changing implementation behavior requires updating the corresponding source document
and acceptance tests in the same review.
