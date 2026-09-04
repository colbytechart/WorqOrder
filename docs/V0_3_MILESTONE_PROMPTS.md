# WorqOrder `0.3.0` Task-Delegated Milestone Prompts

## Operating protocol

These prompts govern only WorqOrder. Never import assumptions, files, terminology, or work from
Matter or another project. All assigned models use Extra High reasoning.

Before the first task phase of each milestone:

1. The owner must explicitly say to begin and supply the current remaining weekly-token-budget
   percentage. If either is missing, ask and stop.
2. The first assigned model estimates the percentage cost of the whole milestone. Use prior budget
   changes only when they represent WorqOrder work.
3. Read `AGENTS.md`, this document, every specification named by the milestone, and the relevant
   implementation.
4. Verify the exact milestone branch descends from the accepted integration tip and that
   `v0.3.0-development` remains separate from `main`. Do not create, merge, commit, reset, or
   delete branches for the owner.
5. Summarize all task phases and their model assignments. Stop for any conflict, destructive
   migration possibility, new dependency, external-filesystem need, or unapproved internet need.

## Model and handoff rules

- Use the narrowest capable model to minimize token cost without lowering the required quality.
- **Luna** owns exhaustively specified inventory, searches, fixtures, repetitive tests,
  documentation reconciliation, and evidence assembly. Luna must follow the written checklist
  literally, avoid architectural judgment, avoid broad rewrites, and escalate uncertainty.
- **Terra** owns bounded Kotlin/Compose/repository/adapter integration, mechanical propagation,
  compile fixes, and user-facing documentation. Terra must preserve established invariants and
  escalate any change to migration, concurrency, security, or product policy.
- **Sol** owns Room migration and invariant design, timer/export concurrency, destructive-range
  safety, security/release judgment, and the final quality review for every milestone.
- A model may perform only its active phase. At a different-model boundary, report the completed
  scope, files changed, tests run, failures or risks, and the exact next phase; then stop and ask
  the owner to switch. Do not continue until the owner confirms the requested model is active.
- The owner supplies permission and budget once per milestone, not again for every phase. Later
  phases read `AGENTS.md`, their exact prompt, the current diff/status, the preceding handoff, and
  only the relevant implementation; they do not repeat broad repository discovery.
- Do not commit during intermediate phases. The owner stages, commits, pushes, and merges only
  after the final Sol phase accepts the milestone.
- A milestone is incomplete until its final Sol review passes. Sol may make narrowly necessary
  corrections, but must return work to Terra or Luna if the correction belongs to their bounded
  specialty and is not trivial.

At the final Sol phase of every implementation milestone:

- update specifications and decisions to match actual behavior;
- run every repository-local check allowed by current permissions;
- give the owner complete PowerShell commands for offline JVM, lint, debug/release, connected, and
  focused checks, including the established project-local Gradle/user-home setup;
- give precise emulator/manual checks and never claim they passed before owner confirmation;
- report migrations, behavior, files, test evidence, unverified items, deviations, and risks;
- provide a short commit summary and a longer commit/PR description; and
- stop without beginning the next milestone.

No phase may add a foreground stopwatch service, exact alarm, app-owned wake lock, destructive Room
fallback, broad storage permission, backend, Firebase, paid service, new Google scope, native
credential, backup, or timestamp-precision loss. Room remains authoritative and GPLv3/direct GitHub
distribution remains permanent.

## Milestone 30 — Schema 5, lossless migration, and repeated-Start transaction

### Task 30A — Sol: migration and invariant core

```text
Begin WorqOrder Milestone 30, Task 30A, using Sol at Extra High reasoning. My current remaining
weekly token budget is [INSERT PERCENTAGE]%. Apply the milestone-start protocol and estimate the
whole milestone. Verify milestone30 and its ancestry before editing.

Implement the high-risk Room schema-5 core from DATA_MODEL D-082/D-083. Preserve every installed
v0.2.0 record. Retain daily_tasks, work_intervals, and active_timer; make series_id non-unique
lineage; remove ordinal from the production interval model; enforce one work_intervals row per
task_id; retain active-slot and singleton protections.

For a prior multi-interval task, order by start, old ordinal, and ID. Keep the original task with
the first interval. Create deterministic collision-resistant task IDs for later intervals, copy
all user metadata/date/ZoneId/lineage, and move each interval without changing its identity,
endpoints, manual flag, or timestamps. Repoint active_timer transactionally if its interval moves.
Define deterministic copied-task timestamps. Fail closed on any unprovable invariant; never
delete, reseed, or use destructive fallback.

Implement the atomic persistence/domain Start invariant: an untimed task receives its sole
interval; a completed task produces one same-day metadata copy with new task/interval identities,
selects it, and atomically creates its open interval and singleton active-timer row. Preserve
single-winner concurrent Start and source immutability. Add focused high-risk tests. Do not perform
Terra propagation, Luna's exhaustive fixtures/docs, midnight changes, final UI, or export schema.
At completion, provide a handoff and stop for Terra Task 30B.
```

### Task 30B — Terra: schema propagation

```text
Continue Milestone 30, Task 30B, using Terra at Extra High reasoning. Read the 30A handoff, current
diff, schema-5 entities/migration, and relevant DAO/repository/model files. Do not redesign the
migration or Start transaction.

Propagate the approved zero/one interval structure through entities, mappers, DAO and repository
surfaces, application container wiring, fixtures/builders, and compile-safe compatibility paths.
Remove production ordinal dependencies where schema 5 requires it. Keep temporary adapters narrow,
unable to create a second interval, and clearly assigned for later removal. Do not change midnight,
selection-rollover policy, final UI, or final export presentation. Add focused integration tests
for only this propagation. Report exact changes and stop for Luna Task 30C.
```

### Task 30C — Luna: exhaustive fixtures and evidence

```text
Continue Milestone 30, Task 30C, using Luna at Extra High reasoning. Do not redesign production
architecture. Read the 30A/30B handoffs, current diff, migration code, schema JSON, and existing
migration/test conventions.

Add or complete the following explicit tests and fixtures: fresh schema 5; populated 4-to-5;
released 2-to-5 and 1-to-5 paths; zero, one, and many old intervals; equal start values resolved by
old ordinal then ID; every nullable/user metadata value; archived Client and Consultant links;
active non-first interval repointing; stable interval IDs and UTC boundaries; deterministic copied
task IDs/timestamps; foreign keys; unique task_id interval constraint; active-slot and singleton
rules; cascade/restrict behavior; close/reopen; first and repeated Start; concurrent Start with one
winner; source immutability; and selection of the new copy. Ensure schema-5 JSON is committed in
the intended location.

Update DATA_MODEL, ARCHITECTURE, DECISIONS, ACCEPTANCE_TESTS, and migration evidence only where the
implemented behavior requires it. Do not weaken assertions, delete historical schemas/fixtures,
or modify high-risk production logic. Report failures or ambiguity for Sol; stop for Task 30D.
```

### Task 30D — Sol: milestone quality gate

```text
Complete Milestone 30, Task 30D, using Sol at Extra High reasoning. Review the full diff and all
handoffs. Audit data preservation, SQL ordering, identity/timestamp determinism, foreign keys,
active-timer repointing, transaction boundaries, concurrent Start, and every migration route.
Correct only confirmed in-scope defects. Run or supply the complete required verification and
manual upgrade checks. Do not expand into Milestone 31 behavior. Close the milestone only if all
required evidence passes, then provide the completion report and commit/PR text and stop.
```

## Milestone 31 — No-rollover selection and singular interval coordination

### Task 31A — Luna: call-site inventory

```text
Begin WorqOrder Milestone 31, Task 31A, using Luna at Extra High reasoning. My current remaining
weekly token budget is [INSERT PERCENTAGE]%. Apply the milestone-start protocol and estimate the
whole milestone. Verify milestone31 contains accepted Milestone 30.

Perform a read-only inventory of every production/test/document reference to selection rollover,
daily series find-or-create, date/ZoneId reconciliation, startup/resume task creation, timer
normalization creation, multiple intervals, Add Interval, selection persistence, and process
recovery. Classify each as current production caller, schema/migration compatibility, historical
evidence, test fixture, or stale candidate. Do not change behavior. Deliver exact file/symbol
locations and stop for Sol Task 31B.
```

### Task 31B — Sol: domain and concurrency rules

```text
Continue Milestone 31, Task 31B, using Sol at Extra High reasoning. Use the 31A inventory and
schema-5 implementation. Implement D-084/D-086 at the domain and transactional boundary. Date or
effective-zone reconciliation clears an ineligible timing selection and never creates data.
Historical/future Start remains disabled. DataStore series hints cannot override Room.

Complete singular interval invariants: manual Add succeeds only for an untimed task; a second
interval returns a structured failure; deleting the sole completed interval makes the same task
eligible for first Start; running edits/deletes remain blocked. Validate concurrency and
transaction ownership. Do not propagate UI/lifecycle call sites assigned to Terra and do not
replace midnight splitting yet. Add focused invariant tests, hand off, and stop for Task 31C.
```

### Task 31C — Terra: selection and recovery integration

```text
Continue Milestone 31, Task 31C, using Terra at Extra High reasoning. Preserve Sol's transaction
boundaries. Propagate no-rollover behavior through selection persistence, process reconstruction,
deletion, repository coordination, Main/Edit ViewModels, and structured UI-facing results. Remove
only callers proved obsolete by 31A and fully replaced here. Keep midnight compatibility only as
needed to compile, and preserve notification/export behavior. Add focused integration/ViewModel
tests. Report changes and stop for Luna Task 31D.
```

### Task 31D — Luna: regression and documentation

```text
Continue Milestone 31, Task 31D, using Luna at Extra High reasoning. Add explicit regression cases
for stale date and stale ZoneId selection clearing, browsing with no writes, startup/resume with no
task creation, selection persistence, active-timer authority after process reconstruction,
repeated-Start selection, metadata/null copying, sole-interval Add/Edit/Delete, selected-task
deletion, and no idle duplication. Preserve migration fixtures and midnight tests for Milestone 32.
Update relevant specifications and acceptance evidence to actual behavior only. Do not change core
logic; report uncertainty and stop for Sol Task 31E.
```

### Task 31E — Sol: milestone quality gate

```text
Complete Milestone 31, Task 31E, using Sol at Extra High reasoning. Audit the combined diff for
hidden rollover writes, stale DataStore authority, duplicate repeated-Start logic, transaction
races, process-recovery errors, and accidental midnight-policy changes. Correct in-scope defects,
run or supply the complete gate/manual checks, provide the completion report and commit/PR text,
and stop.
```

## Milestone 32 — Midnight auto-stop and automatic-export ordering

### Task 32A — Sol: exact-boundary transaction

```text
Begin WorqOrder Milestone 32, Task 32A, using Sol at Extra High reasoning. My current remaining
weekly token budget is [INSERT PERCENTAGE]%. Apply the milestone-start protocol and estimate the
whole milestone. Verify Milestones 30 and 31 are present.

Implement D-085 as one idempotent, concurrency-safe domain/persistence operation. Close the sole
open interval at the first next-day atStartOfDay boundary in the active timer's pinned ZoneId,
clear active_timer and stale selection, end the live monotonic session, and create no continuation
task/interval. Several missed days still close once at the first boundary. Preserve ordinary
pre-boundary Stop and the approved wall-clock anomaly policy. Prove normalize/Stop concurrency and
idempotency with focused tests. Do not add exact alarms, a foreground stopwatch service, app-owned
wake locks, continuous loops, or database ticks. Stop for Terra Task 32B.
```

### Task 32B — Terra: lifecycle and export integration

```text
Continue Milestone 32, Task 32B, using Terra at Extra High reasoning. Integrate Sol's single
boundary operation into foreground date observation, Activity/process recovery, post-unlock reboot
recovery, running-notification reconciliation, Stop races, and automatic Google work. Late
execution must persist the exact boundary rather than wake time.

Make automatic Google work eligible for a captured date only after that date's local boundary.
Before snapshot/export, close a stale interval belonging to that target date. Preserve the single
captured target, one bounded WorkManager job, silent success, marked-tab idempotency, typed pending
authorization/network/quota handling, and manual-only CSV/XLSX. Do not redesign the core
transaction. Add focused Android/integration tests and stop for Luna Task 32C.
```

### Task 32C — Luna: clock, failure, and manual-test matrix

```text
Continue Milestone 32, Task 32C, using Luna at Extra High reasoning. Add the enumerated tests for
exact boundary, just before/after, repeated normalization, several missed dates, process death,
reboot recovery entry, notification cancellation, non-DST zone, spring-forward, fall-back,
unusual atStartOfDay behavior, device/manual zone, wall-clock anomalies, late worker execution,
offline/auth/permission/quota pending states, no continuation records, and no duplicate export.
Add deterministic fake clocks/ZoneIds and race fixtures without weakening production tests.
Update LIFECYCLE_TEST_PLAN, TIMER_AND_DATE_RULES, ACCEPTANCE_TESTS, and exact emulator/device
simulation steps. Do not modify concurrency logic; stop for Sol Task 32D.
```

### Task 32D — Sol: milestone quality gate

```text
Complete Milestone 32, Task 32D, using Sol at Extra High reasoning. Audit every boundary caller,
transaction/race, pinned-ZoneId calculation, missed-day case, WorkManager ordering, pending state,
and prohibition on continuation records/background mechanisms. Correct in-scope defects. Run or
supply the complete automated and device gate, provide the completion report and commit/PR text,
and stop.
```

## Milestone 33 — Singular Interval UI and canonical export schema 5

### Task 33A — Terra: UI, projection, CSV, and XLSX

```text
Begin WorqOrder Milestone 33, Task 33A, using Terra at Extra High reasoning. My current remaining
weekly token budget is [INSERT PERCENTAGE]%. Apply the milestone-start protocol and estimate the
whole milestone. Verify accepted Milestones 30-32. Do not alter Room schema or timer architecture.

Implement the user-facing optional singular Interval. Edit Task uses the label Interval. Untimed
tasks offer Add Interval; timed tasks offer Edit/Delete for the sole completed interval; running
edits/deletes remain blocked. Preserve date/ZoneId/DST precision, validation, totals, Billing
minutes, metadata, confirmations, unsaved edits, accessibility, portrait/handed landscape, and
large-text reachability. Repeated Start must visibly select the new zero-based duplicate.

Implement shared immutable export schema 5 with exactly: Start date, End date, Consultant, Client,
Description, Expense, Work type, Billing Status, Mileage, Start time, Stop time, Time spent,
Billing minutes. Emit one row per task. Untimed rows have blank Start/Stop, 00:00:00 Time spent,
and 0 Billing minutes. Preserve MM/dd/yyyy, hh:mm AM/PM, duration HH:MM:SS, literal text,
deterministic ordering, UTF-8/RFC CSV, and no Room mutation. Make CSV and one-off XLSX consume these
same 13 strings and set XLSX bounds to A:M. Do not modify Google range ownership; stop for Sol 33B.
```

### Task 33B — Sol: Google owned-range safety

```text
Continue Milestone 33, Task 33B, using Sol at Extra High reasoning. Make manual and automatic
Google export consume the exact shared schema-5 projection. Safely replace and upgrade only
WorqOrder-owned tabs marked with known schema 2, 3, or 4. Clear the complete prior owned range so
obsolete N/O columns and stale rows disappear. Never overwrite an unowned or unknown/newer tab.
Preserve blank-first-sheet behavior, same-date idempotency, authorization/error semantics, and the
exact connected spreadsheet. Add focused request-planner/range-safety tests. Stop for Luna 33C.
```

### Task 33C — Luna: cross-destination and UI evidence

```text
Continue Milestone 33, Task 33C, using Luna at Extra High reasoning. Add exact tests for the 13
headers/spelling/order, one timed row, one untimed row, deterministic task order, escaping, commas,
quotes, line breaks, Unicode, long duration, 12-hour clock output, and identical logical values in
CSV/XLSX/manual Google/automatic Google. Test known owned schema 2/3/4 upgrades, stale N/O clearing,
unowned/unknown conflicts, cancellation, output failure, and Room immutability. Add Compose/
ViewModel/accessibility cases for absent/completed/running Interval, Add/Edit/Delete availability,
repeated-Start duplicate display, large text, portrait, and both handed landscape arrangements.
Update EXPORT_SPEC, PRODUCT_SPEC, USER_GUIDE, ACCEPTANCE_TESTS, and decisions to actual behavior.
Do not alter production safety logic; stop for Sol Task 33D.
```

### Task 33D — Sol: milestone quality gate

```text
Complete Milestone 33, Task 33D, using Sol at Extra High reasoning. Audit cross-destination
equivalence, Google destructive-range safety, old-marker upgrades, one-row-per-task behavior,
singular interval mutations, accessibility, and absence of local export mutation. Correct in-scope
defects, run or supply the full automated/manual CSV/XLSX/Google gate, provide the completion report
and commit/PR text, and stop.
```

## Milestone 34 — Obsolete-path removal and specification reconciliation

### Task 34A — Luna: classified inventory and documentation candidates

```text
Begin WorqOrder Milestone 34, Task 34A, using Luna at Extra High reasoning. My current remaining
weekly token budget is [INSERT PERCENTAGE]%. Apply the milestone-start protocol and estimate the
whole milestone. Verify accepted Milestones 30-33.

Search production, tests, and every document for multiple intervals per task, ordinal/interval
number, accumulated repeated intervals, daily rollover/find-or-create, midnight continuation,
schema-4/15-column current claims, and pre-boundary automatic export. Classify every occurrence as:
(A) v0.1/v0.2 history that must remain; (B) migration compatibility/fixture that must remain;
(C) optional Milestone E unrelated content; or (D) stale current/future v0.3 material.

Rewrite only clearly documentary category-D items and produce an exact candidate list of
production/tests for Terra. Never erase changelog/history, prior schemas, migration tests,
historical examples, or compatibility code. Do not broad-replace text, alter schema, dependencies,
permissions, Gradle, OAuth, signing, backup, UI design, export spelling, or product scope. If
classification is uncertain, retain it and flag Sol. Stop for Terra Task 34B.
```

### Task 34B — Terra: proven dead-code cleanup

```text
Continue Milestone 34, Task 34B, using Terra at Extra High reasoning. Use Luna's classified list.
Remove only category-D production methods/imports and stale current-behavior fixtures whose
callers are proven absent and whose behavior is fully replaced. Resolve bounded compile/test
fallout without changing schema, transaction semantics, dependencies, UI, or product policy.
Never remove migration compatibility or historical evidence. Report every retained uncertain item
and stop for Luna Task 34C.
```

### Task 34C — Luna: traceability and proof searches

```text
Continue Milestone 34, Task 34C, using Luna at Extra High reasoning. Reconcile PRODUCT_SPEC,
ARCHITECTURE, DATA_MODEL, TIMER_AND_DATE_RULES, EXPORT_SPEC, ACCEPTANCE_TESTS, DECISIONS,
IMPLEMENTATION_PLAN, USER_GUIDE, KNOWN_LIMITATIONS, privacy/security/traceability documents, and
README so v0.3 is unambiguous while v0.2 remains labeled history. Add only missing regression
assertions identified by traceability.

Run focused searches proving no current contract says one task has many intervals, rollover creates
tasks, midnight continues, or current exports have 15 columns. Do not chase a zero search count by
deleting valid history. Assemble the full permitted test commands and evidence; do not ignore,
baseline, delete, or weaken failures. Stop for Sol Task 34D.
```

### Task 34D — Sol: milestone quality gate

```text
Complete Milestone 34, Task 34D, using Sol at Extra High reasoning. Audit all removals and document
changes against migrations, historical evidence, schema-5 behavior, and release traceability.
Confirm no needed compatibility path was removed and no scope expanded. Correct in-scope defects,
run or supply the full gate, list retained historical/uncertain material, provide the completion
report and commit/PR text, and stop.
```

## Milestone 35 — Final tests, upgrade audit, and GitHub release

### Task 35A — Luna: release-evidence assembly

```text
Begin WorqOrder Milestone 35, Task 35A, using Luna at Extra High reasoning. My current remaining
weekly token budget is [INSERT PERCENTAGE]%. Apply the milestone-start protocol and estimate the
whole milestone. Verify accepted Milestones 30-34 and separation from main.

Using existing repository evidence, assemble a release checklist and traceability inventory for
every v0.3 requirement, migration route, lifecycle scenario, accessibility configuration, export
destination, automatic export state, permission, dependency, secret scan, signer check, and
upgrade/fresh-install test. Mark each pass, fail, or unverified; do not invent results. Draft exact
owner-run commands for clean unit/lint/debug/release, API-26/current connected suites, artifact
inspection, and populated v0.2-to-v0.3 upgrades. Identify documentation gaps without changing
release identity or high-risk code. Stop for Terra Task 35B.
```

### Task 35B — Terra: bounded release polish

```text
Continue Milestone 35, Task 35B, using Terra at Extra High reasoning. Use Luna's evidence matrix.
Fix only bounded, non-architectural release defects explicitly within v0.3 scope. Finalize
user-facing README, CHANGELOG, USER_GUIDE, privacy/data, known-limitations, and release-instruction
wording. Update versionName to 0.3.0 and versionCode only after the owner explicitly supplies the
approved code. Do not modify signing credentials, OAuth identity/scope, migration/concurrency
architecture, dependencies, or release claims. Stop for Sol Task 35C.
```

### Task 35C — Sol: final release gate

```text
Complete Milestone 35, Task 35C, using Sol at Extra High reasoning. Audit the full v0.3
specification, code, dependency, security, migration, lifecycle, accessibility, performance, and
regression state. Own any high-risk correction. Prove a signed populated v0.2.0 installation
upgrades without uninstall/clear, including zero/one/many intervals, all metadata/directories/
preferences, Google connection and auto-export state, selection, and a non-first active interval.
Confirm schema 5 creates one task per old interval without loss or repeated-migration duplication.

Require clean JVM/lint/debug/release, full API-26/current connected suites, relevant physical/next
API smoke, first/repeated Start, no rollover, exact logical midnight closure, notification cleanup,
automatic Google close-before-export, and CSV/XLSX/Google equivalence/idempotency. Inspect merged
manifests, permissions, backup-disabled state, secrets/logs, drive.file scope, stable dependencies,
advisories only with approved internet, CPU/memory, permanent signer, package, and upgrade install.

Do not invent signing material or readiness. Build/verify the owner-signed APK only through approved
local inputs; report path, size, SHA-256, and certificate. Finalize QA/security/traceability/handoff/
release checklist evidence. Provide exact owner commands for milestone35 to v0.3.0-development to
main, annotated v0.3.0 tag, GitHub Release/APK upload, checksum/signature, upgrade/fresh install,
public download, and post-release verification. Withhold release if any required gate fails or is
unperformed. Provide final report and commit/PR/release text, then stop.
```

## Milestone 36 — Safe project-environment teardown guide

### Task 36A — Luna: non-destructive inventory

```text
Begin WorqOrder Milestone 36, Task 36A, using Luna at Extra High reasoning. My current remaining
weekly token budget is [INSERT PERCENTAGE]%. Apply the milestone-start protocol and estimate the
whole milestone. This milestone writes instructions only. Never uninstall, delete, move, edit,
disable, reset, or change machine/cloud/firmware state.

First verify from owner-provided evidence that v0.3.0 is released and publicly checked. Then create
a questionnaire and read-only command list to inventory: Android Studio installations/plugins/
settings; every AVD/image/snapshot; Android SDK path/packages/licenses; JBR/JDK/Kotlin; all Gradle
homes/wrappers/caches/daemons/environment variables; ADB/USB drivers; Windows Hypervisor Platform,
Virtual Machine Platform, Hyper-V; BIOS virtualization and pre-project state if known; repository
clones/worktrees/stashes/build outputs; VS Code project extensions; Git/GitHub settings; Google
Cloud OAuth project/client; permanent release keystore/password backups; and anything installed
only for WorqOrder.

Do not inspect outside approved paths yourself. Do not assume an item is project-exclusive.
Classify each item as preserve, shared/retain, project-only/removal candidate, or unknown/manual
decision. Protect remote history/tags/releases/source, public APK/checksum, multiple offline
keystore/password/recovery backups, desired app/export data, and OAuth records. Draft the inventory
portion of PROJECT_TEARDOWN_GUIDE and stop for Terra Task 36B.
```

### Task 36B — Terra: staged Windows/Android cleanup guide

```text
Continue Milestone 36, Task 36B, using Terra at Extra High reasoning. Use only paths and inventory
facts verified by the owner. Extend PROJECT_TEARDOWN_GUIDE with staged, reversible-first
instructions: stop daemons/emulators; remove project AVDs/images using supported tools; remove
verified project-only caches; optionally uninstall Android Studio/JDK/SDK only when not shared;
clean verified environment/PATH entries; discuss Windows virtualization and BIOS restoration only
as device-specific owner-confirmed choices; preserve/archive the repository until last; and verify
each stage.

Provide exact Windows GUI/PowerShell commands only for explicit verified targets. Never use broad
recursive targets, unresolved variables, cross-shell deletion, or automated BIOS/system changes.
Include warnings, backups, rollback/reinstallation, and post-step checks. Do not execute anything
or change application/release/cloud state. Stop for Sol Task 36C.
```

### Task 36C — Sol: destructive-safety gate

```text
Complete Milestone 36, Task 36C, using Sol at Extra High reasoning. Audit every proposed teardown
step for target precision, shared-tool dependencies, recoverability, signing-key continuity,
credential/OAuth preservation, remote release durability, virtualization/firmware risk, and
Windows command safety. Remove or rewrite ambiguous/destructive instructions. Require explicit
owner confirmation before each irreversible action and make clear that the guide, not Codex,
performs nothing. No application code, release tags, historical docs, cloud configuration, or
machine state may change. Deliver the reviewed documentation-only report and optional commit/PR
text, then stop.
```
