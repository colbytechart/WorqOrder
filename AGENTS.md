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
- If the scheduled Google export finds a running timer, persist the captured date as pending. Do
  not export an open interval. After Stop, show an actionable system notification; tapping it
  resumes or confirms export of the preserved date. Never expose task/client content in it.
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
- `0.5.0` development begins with documentation-only Milestone 42. Later milestones may start only
  on explicit owner instruction, with the owner's current weekly-token-budget percentage and the
  matching milestone branch. Follow `docs/V0_5_MILESTONE_PROMPTS.md`. Every milestone is divided
  into explicitly assigned Luna, Terra, and Sol subtasks. Stop after **every** subtask and wait for
  the owner to confirm the next assigned model. Prefer Luna for bounded inventory, fixtures, and
  mechanical tests; Terra for implementation; and Sol only for critical design, data-safety,
  difficult defect resolution, and final quality decisions.
- The owner alone runs all PowerShell/Gradle and Android emulator/device tests during `0.5.0`.
  Agents must provide complete PowerShell commands that redeclare JBR/SDK/project-local paths and
  `-Duser.home`, plus numbered manual test instructions. Do not run Gradle, ADB, emulator, or
  device tests on the owner's behalf and never claim an owner-run result before it is reported.
- Planned `0.5.0` reusable Tags are two category-scoped catalogs: Description Tags and Hardware /
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
- `0.5.0` Milestone 47 is the owner-approved Create/Edit task-form polish milestone: unified live
  character counters, compact picker-opening Tag summaries, consistent task-field labeling and
  spacing, no direct chip removal, selector-local assignment errors, live 400-code-point Tag-editor
  counters, filtered bulk Tag-picker selection controls, and Edit Task inline Add Client recovery
  when its assigned client is no longer active. Create/Edit footers contain only their two actions;
  non-field page messages remain in the scrollable content. The full audit/public-
  release handoff is Milestone 48.
- The former post-`0.4.0` environment teardown Milestones 42 and 43 are renumbered to Milestones
  49 and 50 and deferred until after the public `0.5.0` release. Milestone 49 is documentation-only;
  Milestone 50 remains optional and requires exact, separate owner authorization. Neither permits
  inferred deletion, uninstall, cloud changes, or other destructive action.
