# WorqOrder `0.5.0` Task-Delegated Milestone Prompts

## Status and operating protocol

The owner has publicly released `0.4.0`. Milestone 42 prepares the approved `0.5.0` Tag feature
documentation only. Milestones 43–48 implement, integrate, verify, and release it. The former
post-`0.4.0` teardown Milestones 42 and 43 are renumbered to 49 and 50 and moved after public
`0.5.0`; they are not part of the Tag implementation. Optional security Milestone E remains
unscheduled and outside every release until separately assigned.

Before starting each milestone, the owner supplies an explicit start instruction, the active
model, and the current remaining weekly-token-budget percentage. The first subtask estimates the
whole milestone's percentage cost, reads `AGENTS.md`, this roadmap, relevant specifications and
code, verifies `main` → `v0.5.0-development` → the exact milestone branch, and reports conflicts
before changing behavior. Codex does not create, commit, merge, reset, tag, publish, or delete Git
branches for the owner.

Every subtask ends with a concise handoff and a **full stop**. Do not begin the next subtask until
the owner explicitly confirms its assigned model is active. A model performs only its assigned
subtask. Use Luna for bounded inventories, fixtures, straightforward Compose/test work, and
documentation evidence; Terra for the bulk of Kotlin, Room, repository, parser, ViewModel, and UI
integration; Sol only for critical design review, migration/export safety, difficult defects, and
the final quality decision. Reuse the preceding handoff rather than rereading unrelated files.

The owner manually runs every PowerShell/Gradle and Android emulator/device test. At each test
gate, Codex provides complete PowerShell commands that redeclare `JAVA_HOME`, `ANDROID_HOME`,
`ANDROID_SDK_ROOT`, project-local `GRADLE_USER_HOME`, project-local `$projectUserHome`, and passes
`"-Duser.home=$projectUserHome"`. Codex also provides numbered manual test instructions. Codex
does not invoke Gradle, ADB, an emulator, or a physical device and does not claim a test passed
until the owner reports it. Formatting, lint, JVM tests, relevant connected tests, migration
tests, and applicable debug/release builds remain mandatory gates.

No prompt grants internet or external-filesystem access. Preserve application ID `worq.order`,
the permanent signer, Room authority, disabled Android backup, GPLv3, no-cost direct GitHub
distribution, Google `drive.file`, one-off CSV/XLSX, the single connected Google spreadsheet,
and every current timer/date/one-interval rule. Do not change release versionName/versionCode
without explicit owner approval during Milestone 48.

## Approved `0.5.0` product contract

- Maintain separate reusable catalogs named **Description Tags** and **Hardware / Software
  Purchase Tags**. Tags are local conveniences, not export columns or synchronization records.
- Each catalog Tag is nonblank after normalization, at most 400 Unicode code points, category
  scoped, and unique after case-insensitive, surrounding/repeated-whitespace, and one-terminal-
  period normalization. Identical normalized text may exist once in each category.
- Add Settings → **Tag Management** immediately after Client Management and Consultant
  Management. Its overview opens a separate management page for each category. Each page supports
  real-time case-insensitive substring search with a clear action, alphabetical browsing, Add,
  Edit, Delete with confirmation, and category-specific CSV import.
- CSV import treats every nonblank cell—including a header-looking cell—as a Tag. It accepts
  correctly quoted commas, quotes, Unicode, and embedded line breaks, canonicalizes Tag whitespace,
  skips category duplicates without overwriting existing entries, sorts A–Z, and reports counts.
  A malformed cell/file, a Tag over 400 code points, a file over 1 MiB, or more than 10,000
  nonblank cells fails the entire import atomically. Use the Storage Access Framework and request
  no broad storage permission.
- Create/Edit retain their manual Description and Hardware / Software Purchases fields. Each gains
  an unlabeled, single-row Tag control directly below its field. With no selection the button reads
  **Add tags**. With selections it reads **Edit tags** and is followed by either one truncated,
  accessible Tag chip or one `+N tags selected` count chip. Both use the lighter selected-chip
  color, outline, and pill shape. Each chip is only as wide as its label unless a long single Tag
  consumes the remaining row width and ellipsizes. The row never wraps, the chips open the
  picker, and removal occurs only inside the picker. The full-screen picker supports real-time
  filtering, a clear-search action, browsing,
  multi-select in selection order, selected count, Cancel/Apply, and inline Tag creation.
  Filtering never deselects hidden choices. A Tag created inline persists in the reusable catalog
  even if the task is later cancelled and is immediately selected unless validation prevents it.
- Catalog text is never a live task reference. Saving a task stores ordered, category-specific
  Tag text snapshots and optional source Tag IDs. Editing or truly deleting a catalog Tag affects
  only later selections. Existing task snapshots and exports remain unchanged. An old task exposes
  its saved chip; if its source was edited, the picker offers an explicit **Use Updated Version**
  replacement. A deleted source remains removable from the task but cannot be newly selected.
- Description and Hardware / Software Purchases expand from 400 to a maximum composed export value
  of 999 Unicode code points. Manual text, one joining space between components, Tag snapshot text,
  and generated periods all count. Description, Hardware / software purchases, and Notes normally
  show only **N / 999**. As soon as a value exceeds the limit, its live counter turns red and reads
  **Character limit: N / 999**; it does not wait for Create/Save. It blocks selecting a Tag that would exceed
  the limit, shows field-level validation if later manual edits exceed it, and disables Create/Save
  until valid. Description is satisfied by nonblank manual text
  or at least one Description Tag; purchases remain optional.
- Export composition is pure and centralized: trim blank/outer component whitespace, place manual
  text first, then Tag snapshots in selection order, append `.` to each nonblank component that
  does not end in `.`, `?`, or `!`, and join components with one ASCII space. Generated punctuation
  exists only in exported Description/Expense values; it never mutates the user's manual text or
  stored snapshots. CSV, XLSX, manual Google, and automatic Google consume the same composed
  immutable projection.
- Main task rows normally show only manual Description. When it is blank, show the first saved
  Description Tag with `+N tags` when more exist. The running-timer notification continues to show
  Client plus manual Description when available; otherwise Client only. It never exposes Tag text.
- Starting an already-timed task copies ordered Tag snapshots exactly because current behavior
  copies Description and Hardware / Software Purchases. Notes retain the released exception and
  start blank. Catalog edits/deletions do not influence this transactional copy.
- Room advances additively from schema 6 to 7 with Tag catalog and task-snapshot tables. Existing
  clients, Consultants, tasks, Notes, intervals, timers, settings, identities, and text remain
  byte-for-byte logically intact; historical tasks begin with no snapshots. Destructive fallback
  is prohibited. Canonical export schema **remains version 6 with the same 14 visible columns and
  Google layout** because Tags only compose existing Description and Expense values.

## Milestone 42 — `0.5.0` planning and branch preparation (documentation only)

### Task 42A — Luna: bounded specification inventory

```text
Begin v0.5.0 Milestone 42, Task 42A, with Luna. My remaining weekly token budget is [PERCENT]%.
Estimate the whole documentation milestone. Verify main → v0.5.0-development → milestone42 and a
clean starting tree. Read AGENTS.md, the approved Tag discussion, V0_4_MILESTONE_PROMPTS.md, and
the relevant product/data/export/architecture/decision/acceptance documents. Inventory exact
documents and current Description/Expense/repeated-Start/export constraints. Do not change Kotlin,
Room schemas, Gradle, resources, release identity, or tests. Record conflicts and stop for Terra.
```

### Task 42B — Terra: technical roadmap documentation

```text
Continue Milestone 42, Task 42B, with Terra. Update only planning/specification documents to record
the approved Tag catalogs, snapshot semantics, 999/400 limits, search/picker/management/import UX,
Room 6-to-7 migration, unchanged export schema 6, centralized composition, tests, and Milestones
43–50. Renumber the former teardown 42/43 to 49/50. Create V0_5_MILESTONE_PROMPTS.md with exact
model handoffs and owner-run test policy. Do not implement or run tests. Stop for Sol.
```

### Task 42C — Sol: minimal consistency decision

```text
Complete Milestone 42 with Sol. Review only for contradictions, migration/data-loss risk, export-
schema drift, unclear normalization/length rules, missing historical-task behavior, and unsafe
cleanup authority. Correct documentation defects only. Verify git diff and branch state without
running Gradle or device tests. Report changed documents and pause; do not start Milestone 43.
```

## Milestone 43 — Room schema 7 and Tag domain foundation

### Task 43A — Luna: implementation map and deterministic fixtures

```text
Begin Milestone 43, Task 43A, with Luna. My remaining weekly token budget is [PERCENT]%. Estimate
the entire milestone, verify ancestry, and map existing Room 6, task mutation, repeated-Start,
Unicode validation, and repository test utilities. Specify fixture cases for fresh schema 7,
populated 1-through-6 upgrades, catalogs, snapshots, ordering, deletion preservation, and atomic
task writes. Make only low-risk test-fixture scaffolding if useful. Do not implement production
migration/domain behavior. Stop for Terra.
```

### Task 43B — Terra: persistence, repositories, and pure rules

```text
Continue Milestone 43 with Terra. Implement explicit non-destructive MIGRATION_6_7, committed
schema 7 JSON, category-scoped Tag catalog persistence, ordered task Tag snapshots, indexes and
constraints, repositories/Flows, CRUD, true catalog deletion without snapshot deletion, search
normalization, duplicate keys, and pure export-text length/composition functions. Create/Edit task
writes and snapshot replacement must be transactional. Repeated Start copies snapshots with new
snapshot identities while keeping Notes blank. Existing tasks receive no snapshots. Do not build
Settings/picker UI or connect destination adapters. Stop for Luna.
```

### Task 43C — Luna: persistence and rule evidence

```text
Continue Milestone 43 with Luna. Add focused unit/Room tests for 400 Tag code points, normalized
duplicates including a terminal period, category independence, substring search, ordering,
catalog edit/delete preservation, 999 composed boundaries including spaces/periods, tag-only
required Description, atomic Create/Edit rollback, task cascade, repeated-Start copy, active timer,
and populated migrations from every supported schema. Update data evidence only. Supply the owner
with complete project-local PowerShell and connected-test commands, then stop for Sol without
running them.
```

### Task 43D — Sol: migration and invariant gate

```text
Complete Milestone 43 with Sol. Audit only migration preservation, foreign keys/indexes, dangling
source-ID intent, transaction boundaries, Unicode counting, normalization determinism, concurrent
Start, and destructive-fallback absence. Fix critical defects only. Interpret owner-run test
results and provide corrected commands when needed. Withhold completion until required owner-run
gates pass; then stop and do not start Milestone 44.
```

## Milestone 44 — Tag Management and atomic CSV import

### Task 44A — Luna: management UI scaffolding and fixtures

```text
Begin Milestone 44, Task 44A, with Luna. My remaining weekly token budget is [PERCENT]%. Estimate
the milestone and verify ancestry. Add bounded Material 3 Tag Management overview/category screen
scaffolding, navigation callbacks, empty/list/search/add/edit/delete/import semantics, accessibility
labels, and Compose fixtures/tests. Keep business rules in repositories/ViewModels and do not
duplicate client-import code blindly. Stop for Terra.
```

### Task 44B — Terra: complete CRUD/search/import integration

```text
Continue Milestone 44 with Terra. Integrate Settings → Tag Management after Client and Consultant
Management. Complete typed state/ViewModels and two category pages with A–Z lists, real-time
case-insensitive substring filtering, clear search, 400-character add/edit validation, duplicate
errors, confirmed true deletion, and actionable results. Implement SAF CSV reading with no broad
permission: every nonblank cell is a Tag; quoted data works; existing/file duplicates skip; 1 MiB,
10,000-cell, malformed, and overlength cases fail atomically without catalog mutation. Stop for
Luna.
```

### Task 44C — Luna: exhaustive management/import tests

```text
Continue Milestone 44 with Luna. Add parser/repository/ViewModel/Compose tests for both categories,
empty states, Add/Edit/Delete, confirmation, duplicate normalization, real-time filtering and
clear, Unicode/quoted/comma/newline CSV, every-cell behavior, append-not-overwrite, duplicate
summary, exact limits, cancellation, corrupt/read failure, atomic rollback, alphabetical results,
navigation, large text, and semantics. Provide owner commands and manual file-picker checks; stop
for Sol without running tests.
```

### Task 44D — Sol: import and UX safety gate

```text
Complete Milestone 44 with Sol. Review only parser resource bounds, transaction atomicity, no
storage overreach, error specificity, duplicate correctness, historical-snapshot isolation, and
accessible management UX. Correct critical defects, evaluate owner-run gates, and stop. Do not
start task integration.
```

## Milestone 45 — Create/Edit Tag selection and task behavior

### Task 45A — Luna: reusable picker/chip presentation

```text
Begin Milestone 45, Task 45A, with Luna. My remaining weekly token budget is [PERCENT]%. Estimate
the milestone and verify ancestry. Implement bounded reusable Compose presentation for field-
associated, single-row Add/Edit controls and picker-opening summary chips, plus the full-screen
searchable multi-select picker: clear search, selected count, Cancel/Apply, stable keys/order,
one selected Tag or one total-count summary, full accessibility text,
and inline-create callbacks. Do not own persistence or duplicate domain rules in composables.
Add presentation tests and stop for Terra.
```

### Task 45B — Terra: task-state and repository coordination

```text
Continue Milestone 45 with Terra. Integrate Description and Hardware / Software Purchase Tags into
Create/Edit ViewModels and screens. Preserve unsaved form state across picker navigation. Inline
creation persists independently and selects the new/existing normalized Tag. Enforce projected
live N / 999 and over-limit Character limit: N / 999 counts, tag-only Description validity, selection order, duplicate prevention,
and overlimit errors. Preserve old snapshots; support explicit Use Updated Version; deleted sources
remain removable only. Save metadata/snapshots atomically. Apply repeated-Start copying, main-row
manual-description/fallback display, and notification manual-description-only rules. Stop for Luna.
```

### Task 45C — Luna: form/history/accessibility evidence

```text
Continue Milestone 45 with Luna. Add ViewModel/Compose/integration tests for tag-only Description,
manual-plus-tags limits, search filtering without deselection, clear, selection order, picker-based removal,
Cancel/Apply, inline create then task cancel, historical edited/deleted sources, explicit update,
atomic save failure, task reopen, repeated Start copy, main fallback, notification privacy, running-
task guards, rotation/recreation, landscape, keyboard, large text, and TalkBack semantics. Provide
owner commands and numbered manual checks; stop for Sol without executing them.
```

### Task 45D — Sol: task integrity and usability gate

```text
Complete Milestone 45 with Sol. Audit task/catalog authority, unsaved state, historical immutability,
repeat-Start concurrency, running-task restrictions, 999 counter accuracy, accessibility, and
compact-screen behavior. Fix critical defects only and evaluate all owner-run evidence. Stop; do
not start export integration.
```

## Milestone 46 — Shared export composition and compatibility

### Task 46A — Luna: cross-destination fixtures

```text
Begin Milestone 46, Task 46A, with Luna. My remaining weekly token budget is [PERCENT]%. Estimate
the milestone and verify ancestry. Prepare exact fixtures for blank/manual/tag-only/mixed values,
selection order, punctuation, whitespace, Unicode, 999 limit, Expense mapping, historical no-Tag
tasks, and repeated exports. Assert the visible 14 headers and schema marker remain version 6.
Do not implement adapter behavior. Stop for Terra.
```

### Task 46B — Terra: canonical projection integration

```text
Continue Milestone 46 with Terra. Make the immutable export snapshot builder call the single pure
composer for Description and Expense. Feed identical values to CSV, one-off XLSX, manual Google,
and automatic Google without destination-specific Tag logic. Preserve exact 14 headers, schema-6
marker, hidden Google task identity, keyed append/update, other-device rows, Stop-before-export,
captured-date behavior, literal/formula safety, one-off CSV/XLSX lifecycle, and zero Room mutation.
Catalog edits/deletes alone must never change a task export. Stop for Luna.
```

### Task 46C — Luna: equivalence and regression evidence

```text
Continue Milestone 46 with Luna. Add exact cross-destination equivalence, punctuation/counting,
snapshot immutability, catalog mutation isolation, CSV quoting, XLSX literal cell, manual/automatic
Google, existing owned schema-6 date tab, cross-device rows, repeated keyed export, failures, and
no-local-mutation tests. Include a regression proving no schema-7 Google marker or fifteenth column
is introduced. Supply owner offline/connected/manual export commands and stop for Sol.
```

### Task 46D — Sol: export data-safety gate

```text
Complete Milestone 46 with Sol. Audit centralized composition, exact destination parity, Google
ownership/idempotency and cross-version compatibility, formula/CSV/XLSX safety, automatic export,
and unchanged schema 6. Fix only high-risk defects and evaluate owner-run results. Withhold closure
on data loss, overwrite, duplicate, or schema drift; then stop.
```

## Milestone 47 — Create/Edit task-form polish and consistency

This is an owner-approved, focused Sol milestone created from the final Create/Edit usability
adjustments discovered after Milestone 46. It changes presentation and validation feedback only;
Room records, Tag snapshots, canonical export composition, and export schema 6 remain unchanged.

### Task 47A — Sol: focused implementation and closeout

```text
Complete Milestone 47 with Sol. Verify the milestone branch and preserve all existing task/Tag
behavior. Remove redundant field Tag headings. Give Description, Hardware / software purchases,
and Notes one shared live counter rule: N / 999 while valid and red Character limit: N / 999
immediately while over limit, with Create/Save disabled until corrected. Use one non-wrapping Tag
control row: Add tags when empty, Edit tags when populated, one selected lighter outlined pill for
one Tag, or one intrinsic-width +N tags selected pill for multiple Tags. Both pills open the picker;
neither removes directly. Bound a long single-Tag pill to the remaining row width and ellipsize it.
Use consistent spacing before Work Type and the exact GUI label Hardware / software purchases.
Keep the Create/Edit fixed footers limited to their respective two actions. Show missing or
unavailable Client/Consultant errors as small field-style text directly below the relevant selector
or recovery button, coloring only that button and the error; leave other page messages in the
scrollable body. Give Settings and inline Tag add/edit dialogs live `N / 400` counters, red
`Character limit: N / 400` over-limit feedback, and disabled confirmation while over limit. In each task Tag
picker, keep Select All and Deselect All with the non-scrolling controls. If Edit Task's assigned
client is archived/unavailable, offer the same inline Add client and archived-match restore flow as
Create Task; make the resulting assignment an unsaved edit without coupling client-directory
creation/restoration to task Save or Cancel. Apply bulk Tag actions only to the
visible filtered results, preserve hidden selections, and reject an over-limit Select All without
partially changing the draft.
Update focused tests and specifications, provide complete owner-run PowerShell and manual visual
checks, audit the final diff, and provide short and long commit messages. Do not start Milestone 48.
```

**Status:** completed on `milestone47` with owner-reported passing compilation, JVM, lint,
debug/release build, connected-instrumentation, and manual verification. Milestone 48 has not
started.

## Milestone 48 — Full `0.5.0` audit and public-release handoff

### Task 48A — Luna: traceability and manual checklist

```text
Begin Milestone 48, Task 48A, with Luna. My remaining weekly token budget is [PERCENT]%. Estimate
the milestone, verify ancestry, and map every approved Tag/migration/import/UI/export requirement
to implementation, automated tests, and owner manual checks. Reconcile QA, security, privacy,
known limitations, user guide, README, changelog, and release checklist without claiming unrun
tests or publication. Stop for Terra.
```

### Task 48B — Terra: bounded release documentation and polish

```text
Continue Milestone 48 with Terra. Fix only confirmed bounded defects, finalize user/developer/release
documentation, and ensure public instructions accurately describe Tag management/import/pickers,
limits, historical snapshots, exports, and migration. Do not change identity, signer, dependencies,
OAuth scope, backup, or architecture. Stop for Luna.
```

### Task 48C — Luna: owner-run gate orchestration

```text
Continue Milestone 48 with Luna. Provide complete project-local PowerShell commands for clean
format/lint/JVM/debug/release and API-26/current connected suites, plus migration, populated
0.4.0-to-0.5.0 install-over, CSV/XLSX/Google/manual/automatic export, accessibility, lifecycle,
and device smoke checklists. Collect and record only results the owner reports. Stop for Sol.
```

### Task 48D — Sol: minimal final release decision

```text
Complete Milestone 48 with Sol. Obtain explicit owner approval before changing versionName or
versionCode. Audit Room 1-through-7 upgrades, Tag/task invariants, all exports, security/dependencies,
signer/package, permissions, accessibility, lifecycle/performance, owner-run test evidence, and
the final signed artifact hash. Fix only release blockers. Withhold readiness while any required
gate is failing or unverified. Provide exact GitHub merge/tag/upload/download-verification steps;
never commit, merge, tag, publish, or run tests for the owner. Stop after public install is verified.
```

## Milestone 49 — Post-`0.5.0` environment teardown guide only

This is the former Milestone 42. Start only after the owner verifies the public `0.5.0` release
and separately instructs it. It performs documentation and inventory only—never removal.

### Task 49A — Luna: non-destructive inventory

```text
Begin post-release Milestone 49 with Luna only after explicit owner instruction. Ask for verified
project-specific paths and inventory Android Studio, SDK/JBR, AVDs, Gradle, ADB, drivers,
virtualization, repository clones, OAuth, releases, and signing backups using read-only commands
within approved paths. Classify preserve, shared/retain, project-only candidate, or unknown. Never
delete or change machine/cloud state. Stop for Terra.
```

### Task 49B — Terra: staged reversible guide

```text
Continue Milestone 49 with Terra. Draft exact owner-executed cleanup instructions only for verified
project-exclusive targets, including backups, rollback, and checks. Do not use broad recursive
paths or change firmware/system, OAuth, signing, Git history, public releases, or user data. Do not
execute the guide. Stop for Sol.
```

### Task 49C — Sol: destructive-safety review

```text
Complete Milestone 49 with Sol. Audit every instruction for exact targets, shared-tool risk,
recoverability, signer/password backups, OAuth, public artifacts, and firmware safety. Require a
new explicit owner decision for every actual removal. Publish only a safe guide and stop.
```

## Milestone 50 — Optional owner-directed closeout

This is the former Milestone 43. It is not automatic and may never begin from a generic request to
finish the release or project. The owner must first review Milestone 49, name exact targets, and
separately authorize each material action. Unknown/shared targets remain untouched.

### Task 50A — Luna: target and backup verification

```text
Begin optional Milestone 50 with Luna only after exact owner authorization. Verify every target's
resolved path, project exclusivity, backup/recovery status, and explicit approval. Do not remove
anything. Stop on unknown/shared/out-of-scope targets and hand the verified list to Terra.
```

### Task 50B — Terra: approved reversible actions only

```text
Continue Milestone 50 with Terra. Perform only separately authorized, exact, recoverable project-
only steps from the reviewed guide and verify each result. Never touch signing backups, remote
history/releases, desired app/export data, shared toolchains, cloud configuration, or firmware
without new specific approval. Stop before any irreversible step for Sol.
```

### Task 50C — Sol: irreversible-action and continuity gate

```text
Complete optional Milestone 50 with Sol. Review each proposed irreversible action's exact target,
backup, rollback limits, shared impact, and owner authorization before it occurs. Withhold unsafe
steps. After authorized work, verify source, public releases, signer/key backups, and desired OAuth
configuration remain available. Report every material removal and recovery status; never infer
permission for another target.
```
