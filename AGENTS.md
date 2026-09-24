# WorqOrder Repository Rules

- This repository is for a native Android application. Use Kotlin application code and Jetpack Compose UI only; do not add Java application code or XML layouts.
- Use Material 3, Compose Navigation, ViewModel, coroutines/Flow, Room, Preferences DataStore, `java.time`, Gradle Kotlin DSL, a version catalog, and KSP where supported.
- Use stable dependency releases only. Document and obtain approval before using any preview, alpha, beta, or release-candidate dependency.
- Keep Room as the authoritative source of task, client, and interval data. CSV, XLSX, and Google Sheets are one-way exports, not databases or synchronization systems.
- CSV, XLSX, and Google Sheets are the only export destinations. XLSX must use a focused, reviewed implementation; do not add Apache POI or another broad Excel stack without explicit owner approval.
- All three destinations must consume the same immutable canonical export dataset. Do not select,
  order, or format exported task fields independently inside a destination adapter.
- Production XLSX creates one new user-selected workbook per export through
  `ACTION_CREATE_DOCUMENT`; it never opens or updates an existing workbook. Persistent-workbook
  mode is obsolete and must not be reintroduced without a new explicit owner decision.
- The only automatic export destination is the single connected Google spreadsheet. Its opt-in
  near-end-of-day schedule captures the intended local work date, remains idempotent, and may run
  shortly after midnight while still exporting that captured date. CSV and XLSX remain manual.
- At the captured date's local midnight, automatic Google export must first normalize any running
  timer through the authoritative exact-boundary Room transaction. The interval closes at that
  boundary, no continuation task/interval is created, and the preserved date then exports
  automatically. Never export an open interval. If closure cannot yet be confirmed, retain the
  captured date as `TIMER_RUNNING`; a later successful boundary close or Stop automatically resumes
  it. Notifications remain for failures that actually require user attention and never expose
  task/client content.
- Do not add Firebase, a custom backend, a web wrapper, embedded credentials, service-account keys, passwords, OAuth client secrets, or unrestricted API credentials.
- WorqOrder must remain free and open source under GPLv3. Do not add billing, paid API tiers, paid
  quota increases, subscriptions, or a Google Workspace/organization requirement.
- The supported release path is direct distribution signed by the owner's permanent release key.
  Do not introduce another marketplace/signing workflow without explicit owner approval.
- Google export must stay within no-cost standard quotas and fail closed without automatic retries
  or charges. If Google's free `drive.file`/Picker/API policy changes, stop Google integration work
  for a new owner decision; CSV must remain available.
- Do not request broad storage permissions. Use Android user-mediated or scoped storage APIs.
- Do not use destructive Room migrations in release builds. Export Room schemas and add versioned migration tests from database version 1.
- Do not persist the changing stopwatch display or write the database on UI refresh ticks. Persist UTC interval boundaries and calculate display values.
- Apply `ZoneId`-aware date rules. Store UTC instants, the assigned work date, and its geographical zone ID; never infer historical work dates again from the current setting.
- Permit only one globally active timer. Enforce the invariant through the singleton active-timer model and transactions, not UI state alone.
- Do not add a foreground service merely to keep a counter alive. Process recovery comes from the persisted open interval.
- Use manual dependency injection through the application container unless a documented, approved need justifies a DI framework.
- Add or update tests whenever behavior changes, especially timer, date-boundary, migration, client-archive, and export behavior.
- Before declaring an implementation milestone complete, run formatting, lint, unit tests, relevant instrumentation tests, and the applicable debug/release builds.
- Do not silently change product behavior. Update the relevant specification and `docs/DECISIONS.md`, and call out the change for review.
- Treat credentials, signing material, `local.properties`, generated files, and account tokens as local secrets; never commit them.
- App-private at-rest encryption is deferred to optional Milestone E and must not be implemented
  without explicit owner permission. Milestone E is an unscheduled, release-agnostic backburner
  item outside v0.2.0 and every other release scope until the owner assigns it explicitly. The
  current production plan relies
  on Android's app sandbox and must not claim that Room or DataStore files are encrypted by
  WorqOrder. Never solve any storage or migration failure by silently deleting local data.
- User-directed CSV/XLSX files and readable Google Sheets exports are plaintext external copies and
  are not end-to-end encrypted by WorqOrder.
- Biometric, device-credential, PIN, or account-gated app access and optional screenshot/Recents
  privacy controls belong only to optional Milestone E and must not be implemented without
  explicit owner permission.
- Do not start a later milestone unless the user explicitly requests it. In particular, do not scaffold or implement the app during the planning milestone.
- Released `0.3.0` removes automatic daily-task rollover and permits at most one work interval per
  task. Preserve every `0.2.0` record through the explicit Room 4-to-5 migration; never discard or
  merge historical intervals.
- Under released `0.3.0`, starting a task that already has a completed interval creates and
  selects a new same-day task carrying the source task's user metadata and lineage, then starts
  that new task. Stable task/interval identities and timestamps are not copied as user metadata.
- Under released `0.3.0`, a timer crossing local midnight closes at the exact pinned-ZoneId day
  boundary and never creates a continuation task or interval. If Android has not scheduled the
  process at that instant, apply the exact persisted boundary at the next legitimate execution
  opportunity; do not add an exact alarm, wake lock, or foreground stopwatch service.
- Canonical export schema 5 has exactly 13 visible columns and one row per task. All three export
  adapters must continue consuming the same immutable projection; do not retain an interval
  number or redundant interval-duration column.
- Released `0.4.0` was planned and delivered through Milestones 36–41. Its historical prompts
  remain in `docs/V0_4_MILESTONE_PROMPTS.md`; do not reuse them for later release work.
- Released `0.4.0` Notes are optional task text with a 999-character limit, editable after creation.
  Existing tasks gain blank Notes through a non-destructive Room migration. Starting a completed
  task creates a new task with blank Notes even if its source has Notes; other approved metadata
  copying is unchanged.
- Released canonical export schema 6 has 14 visible columns, retaining schema-5 order and appending
  `Notes` as column 14. CSV, one-off XLSX, manual Google, and automatic Google must share the same
  immutable projection. Preserve existing Google date-tab rows and transport identities during a
  reviewed schema-5-to-6 compatibility transition; never clear another device's rows.
- Released `0.5.0` was delivered through Milestones 42–48. Its prompts and model assignments remain
  historical evidence in `docs/V0_5_MILESTONE_PROMPTS.md`; do not reopen those milestones or infer
  later work without explicit owner instruction.
- The owner alone runs all PowerShell/Gradle and Android emulator/device tests unless explicitly
  changing that rule.
  Agents must provide complete PowerShell commands that redeclare JBR/SDK/project-local paths and
  `-Duser.home`, plus numbered manual test instructions. Do not run Gradle, ADB, emulator, or
  device tests on the owner's behalf and never claim an owner-run result before it is reported.
- Released `0.5.0` reusable Tags are two category-scoped catalogs: Description Tags and Hardware /
  Software Purchase Tags. Catalog edits/deletions never rewrite task snapshots. A task stores
  ordered Tag text snapshots; repeated Start copies them because it copies Description and
  purchase metadata. Inline-created Tags remain in the catalog if task creation is cancelled.
- Description and Hardware / Software Purchases manual input plus their export-only composed Tag
  text are limited to 999 Unicode code points. Individual Tags are limited to 400. Description
  may be satisfied entirely by selected Description Tags. The canonical composer trims components,
  adds a period when a component lacks `.`, `?`, or `!`, and joins manual text followed by ordered
  snapshots with one space. It is the only source for Description/Expense export values.
- `0.5.0` uses a non-destructive Room 6-to-7 migration for Tag catalogs and task snapshots.
  Existing tasks retain their exact text and receive no snapshots. Canonical export schema 6 stays
  at the same 14 visible columns and Google layout; Tags do not create export columns or a Google
  schema transition.
- `0.5.0` Milestone 47 delivered the owner-approved Create/Edit task-form polish: unified live
  character counters, compact picker-opening Tag summaries, consistent task-field labeling and
  spacing, no direct chip removal, selector-local assignment errors, live 400-code-point Tag-editor
  counters, filtered bulk Tag-picker selection controls, and Edit Task inline Add Client recovery
  when its assigned client is no longer active. Create/Edit footers contain only their two actions;
  non-field page messages remain in the scrollable content. The full audit/public-
  release handoff was completed in Milestone 48.
- Planned `0.6.0` Milestones 49–55 add portable Backup & Restore. Milestone 49 is documentation-
  only; do not add application code until the owner explicitly starts Milestone 50. The governing
  format, atomicity, exclusion, compatibility, model-handoff, and owner-run test contract is
  `docs/V0_6_MILESTONE_PROMPTS.md`.
- Portable backups are bounded plaintext ZIP files containing versioned logical JSON, never raw
  Room/DataStore files. They include portable domain data, settings, selections, and export history,
  but exclude OAuth tokens/credentials, account and connected-sheet metadata, installation/Google
  transport identity, automatic-export pending state/jobs, notifications/permissions, transient UI,
  caches, journals, and the rolling restore point. Import preserves the chosen export destination,
  clears Google connection state, disables automatic Google export, and creates a new transport
  origin. No backup/import/restore may run while a timer is active.
- Import and Restore must fully validate before mutation and use a verified app-private no-backup
  restore point plus a durable, idempotent cross-store recovery journal. Room replacement is one
  transaction; never claim Room/DataStore atomicity without the recovery protocol. Restore uses
  swap semantics so the displaced current state becomes the next restore point. Uninstall naturally
  removes that app-private point.
- Current-format portable import must remain record-streamed: do not restore a whole-data byte
  array/String/DOM path or retain a second staged DTO. Preserve the absolute 100 MiB compressed and
  500 MiB expanded limits, the D-121 device-heap materialization ceiling, bounded logical values,
  exact ZIP allowlist/checksum validation, strict portable IDs, and typed fail-closed results.
- Confirmed Import/Restore runs off the main thread under the application operation lock. While
  authoritative Room/Preferences state is being replaced, keep the Settings route behind the
  non-dismissible progress barrier; startup/background entry points must continue serializing
  through recovery. The barrier supplements the durable journal and is never an atomicity claim.
- `0.6.0` Milestone 53 exposes the existing backup/replacement engine through a compact **Backup &
  Restore** Settings card and Android scoped-document pickers. Inline warnings, progress, success,
  and error status appear directly below the card title and above all actions; the normal card has
  no instructional copy or separate Restore subsection label. Destructive Import and Restore still
  require explicit explanatory confirmation dialogs. All actions are disabled while timing or busy,
  and document-picker return paths must recheck timer state before staging or writing.
- During Milestone 54C the owner explicitly deferred the remaining manual API 26 Backup & Restore
  lifecycle/performance matrix and all remaining manual physical-device checks. They are recorded
  as unexecuted, non-blocking evidence gaps in `docs/DEFERRED_TESTS.md`; never describe them as
  passing. Automated API 26 coverage and the owner-completed current-target manual/stability matrix
  remain the applicable `0.6.0` hardening evidence.
- The former post-release environment teardown Milestones 49 and 50 are renumbered to Milestones
  56 and 57 and deferred until after public `0.6.0`. Milestone 56 is documentation-only; Milestone
  57 remains optional and requires exact, separate owner authorization. Neither permits inferred
  deletion, uninstall, cloud changes, or other destructive action.
