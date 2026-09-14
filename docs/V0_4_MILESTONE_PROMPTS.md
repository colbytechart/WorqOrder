# WorqOrder `0.4.0` Task-Delegated Milestone Prompts

## Status and operating protocol

The owner has publicly released `0.3.0`. Milestone 36 prepares `0.4.0` documentation only;
Milestones 37–41 implement, verify, and release it. Milestones 42–43 are **after** the public
`0.4.0` release; 42 writes a non-destructive teardown guide, while 43 requires new, exact owner
authorization for any real closeout action. Optional security Milestone E remains unscheduled and
outside this release.

Before starting **each later milestone**, the owner gives an explicit start instruction, active
model, and current remaining weekly-token-budget percentage. The first phase estimates the entire
milestone's percentage cost, reads `AGENTS.md`, this file, relevant specifications and code,
confirms `main` → `v0.4.0-development` → the exact milestone branch, and reports conflicts before
changing behavior. Do not create, merge, commit, reset, or delete branches on the owner's behalf.
At each model transition, stop and wait for the owner to confirm that the assigned model is active.
The model active for a phase may do only that phase's work. The owner commits/merges only after the
final Sol gate. No external filesystem or internet access is implied by these prompts.

Use Luna for bounded inventories, fixtures, documentation reconciliation, and evidence; Terra for
well-specified Kotlin/Compose/repository/adapter changes; Sol for migration/invariant design,
Google-tab safety, high-risk corrections, and final review. Later phases reuse the preceding
handoff and current diff rather than rereading unrelated parts of the repository. Formatting,
lint, JVM tests, relevant connected tests, and debug/release builds remain mandatory for each
implementation milestone. The owner runs emulator tests with a project-local `-Duser.home` path
when the agent lacks the authorized SDK or external-directory access.

The approved `0.4.0` contract is narrow: editable optional Notes (999 characters); historical
Notes blank; a new repeated-Start task's Notes blank; cleaner Create Task header; fixed Create
Task bottom actions; and shared export schema 6 with Notes appended as visible column 14. Room
remains authoritative. Neither exact timestamp storage, no-rollover behavior, single-interval
cardinality, one-off CSV/XLSX, Google `drive.file`, nor the permanent signing identity changes.

## Milestone 36 — Documentation-only planning

### Task 36A — Luna: bounded inventory

```text
Begin v0.4.0 Milestone 36, Task 36A, with Luna. Confirm the clean milestone36 branch descends
from v0.4.0-development, which descends from released main. Read AGENTS.md, the prior roadmap,
current task/edit/export specifications, and the relevant implementation. Identify exactly which
planning documents need updates. Do not change app code, Room schemas, build versions, or release
settings. Hand the document and conflict inventory to Sol.
```

### Task 36B — Sol: approve coherent roadmap and stop

```text
Complete Milestone 36 planning with Sol. Record the owner's Notes clarification: Notes editable
after creation, but new tasks and repeated-Start copies start with blank Notes. Create the
versioned, non-destructive migration/export/UI/release roadmap with Luna/Terra/Sol phase handoffs;
move environment teardown after v0.4.0 publication. Verify document consistency and git diff;
do not implement features, run unnecessary builds, commit, merge, or start Milestone 37. Report the
branch state and pause for the owner's next start instruction.
```

## Milestone 37 — Notes persistence and repeat-Start semantics

### Task 37A — Sol: Room migration and transaction core

```text
Begin Milestone 37 on branch milestone37 with Sol. My remaining weekly token budget is [PERCENT]%.
Estimate the full milestone. Read AGENTS.md, V0_4_MILESTONE_PROMPTS.md, DATA_MODEL.md,
PRODUCT_SPEC.md, DECISIONS.md, and current Room/timer code. Add a non-destructive Room 5-to-6
migration with nullable-or-empty-safe Notes defaulting blank on all existing tasks. Preserve all
task/interval/client/Consultant/active-timer identities and data. Implement the atomic repeat-Start
exception: copy existing approved metadata but set the new task's Notes blank. No UI/export
behavior. Add the migration's core safety tests, then stop for Terra.
```

### Task 37B — Terra: typed data plumbing

```text
Continue Milestone 37 with Terra. Propagate Notes through entity/DAO/repository/form-state and
task-edit APIs, default new tasks to blank, allow blank Notes when saving, and validate at most
999 Unicode characters with field-level errors. Keep exact current client/Consultant, timer, and
selection behavior. Make only
compile-safe UI/export adapters where necessary; do not implement their final presentation. Stop
for Luna with files, tests, and risks.
```

### Task 37C — Luna: focused evidence

```text
Continue Milestone 37 with Luna. Add fresh-schema, populated 1/2/3/4/5-to-6, blank-history,
999/1000-character, edit-persistence, repeated-Start blank-Notes, concurrent-Start, active-timer,
and reopen tests. Check exported Room schema 6 and document actual data behavior. Run available
bounded offline gates; report exact results and stop for Sol.
```

### Task 37D — Sol: final preservation gate

```text
Complete Milestone 37 with Sol. Audit migration/index/foreign-key integrity, every historical
field, repeat-Start transaction and race safety, test coverage, and no destructive fallback. Run
the complete implementation gate or provide exact owner commands for inaccessible connected
tests. Do not close while tests fail or are unverified. Report status and stop; do not start 38.
```

## Milestone 38 — Shared 14-column export and Google compatibility

### Task 38A — Sol: Google data-preservation design/core

```text
Begin Milestone 38 on branch milestone38 with Sol. My remaining weekly token budget is [PERCENT]%.
Estimate the full milestone, verify branch ancestry, and read AGENTS.md, EXPORT_SPEC.md,
GOOGLE_INTEGRATION_ADR.md, DECISIONS.md, and current shared snapshot/Google gateway. Design and
implement a bounded owned schema-5-to-6 date-tab transition: A:N visible (Notes at N), O
reserved, P hidden task ID. Preserve all existing rows, other-device task IDs, and their A:M
values; set legacy Notes blank without clearing external data. Fail closed on unowned, unknown,
newer, ambiguous, or unexpectedly occupied reserved cells. Maintain keyed append/update and
automatic-export captured-date safety. Stop for Terra.
```

### Task 38B — Terra: canonical projection and destinations

```text
Continue Milestone 38 with Terra. Append Notes as exact visible header 14 in the one immutable
schema-6 export projection. Propagate that same projection to CSV, one-off XLSX, manual Google,
and automatic Google. Keep the first 13 headers/values/order, one row per task, literal text,
stable task order, and existing Stop-before-export rule. Do not reselect/format Notes inside a
destination adapter or change one-off CSV/XLSX behavior. Stop for Luna.
```

### Task 38C — Luna: equivalence and legacy fixtures

```text
Continue Milestone 38 with Luna. Test exact 14 headers, blank/Unicode/comma/quote/newline/999
Notes, cross-destination value equivalence, immutable snapshot timing, zero-interval tasks,
manual/automatic Google, existing owned schema-5 rows, cross-device keyed rows, repeated export,
read/write failures, and conflict safety. Prove no Room mutation and no remote row deletion.
Update export/acceptance evidence and stop for Sol.
```

### Task 38D — Sol: final export-safety gate

```text
Complete Milestone 38 with Sol. Review all Google read-plan-write races and old-tab handling,
CSV/XLSX literal serialization, formula safety, exact schema parity, and idempotency. Run the
full relevant gate or supply exact owner emulator commands. Withhold completion on any data-loss
possibility or unverified required test, then stop; do not start 39.
```

## Milestone 39 — Create/Edit Notes and cleaner Create header

### Task 39A — Terra: bounded UI integration

```text
Begin Milestone 39 on branch milestone39 with Terra. My remaining weekly token budget is
[PERCENT]%. Estimate the milestone, verify ancestry, and read AGENTS.md, PRODUCT_SPEC.md,
ACCEPTANCE_TESTS.md, and the current Create/Edit screens and ViewModels. Add editable Notes below
Mileage, max 999 characters, blank by default and never required for Create, saved only on
confirmation. Remove only the
redundant Consultant and Client headings and selected-Consultant name text at the top of Create
Task, leaving the Task-for-date text followed by Choose a client. Preserve Consultant assignment,
client validation, dropdown, and all existing task behavior. Stop for Luna.
```

### Task 39B — Luna: UI and validation evidence

```text
Continue Milestone 39 with Luna. Add ViewModel/Compose/accessibility tests for successful creation
with blank Notes, new and historical blank Notes, edit persistence, 999/1000-character validation,
client/Consultant assignment still
required, correct header order, cancellation, and long text. Reconcile user-facing docs with
actual behavior; run bounded offline tests and stop for Sol.
```

### Task 39C — Sol: final behavior gate

```text
Complete Milestone 39 with Sol. Check no hidden Consultant rule was removed, no regression in
task identity or repeat-Start blank Notes, and proper keyboard/semantics behavior. Run required
build/lint/JVM/connected gates or provide owner commands. Report exactly what passed, then stop.
```

## Milestone 40 — Fixed Create Task action footer

### Task 40A — Terra: pinned footer

```text
Begin Milestone 40 on branch milestone40 with Terra. My remaining weekly token budget is
[PERCENT]%. Estimate the milestone and verify ancestry. Move Cancel and Create into a pinned
Scaffold bottom bar, separate from the Create Task scrollable form and visually layered like the
fixed header. Respect system navigation bars, IME, short landscape, large text/display scale,
touch targets, and button enablement. Do not modify save/cancel semantics or task data. Stop for
Luna.
```

### Task 40B — Luna: form-layout regression

```text
Continue Milestone 40 with Luna. Add Compose/accessibility and device checklist cases proving
the footer stays visible while all form fields remain reachable in portrait, short landscape,
large fonts, display scaling, and with the keyboard open; verify validation, Back/Cancel,
double-submit prevention, TalkBack order, and button semantics. Stop for Sol.
```

### Task 40C — Sol: final usability gate

```text
Complete Milestone 40 with Sol. Review insets/scroll layering and no-clipped-controls evidence;
run required tests/builds and collect owner manual results where device access is restricted.
Withhold completion if the fixed footer obscures a field. Report exact status and stop.
```

## Milestone 41 — `0.4.0` final audit and release

### Task 41A — Luna: traceability and evidence

```text
Begin Milestone 41 on branch milestone41 with Luna. My remaining weekly token budget is
[PERCENT]%. Estimate the full milestone, verify ancestry, and assemble exact requirements-to-
implementation/test/manual evidence for Notes, migration, export schema 6, Google tab preservation,
header/footer UI, regression, privacy, and signing. Do not claim publication or unrun tests.
Stop for Terra.
```

### Task 41B — Terra: release documentation and bounded polish

```text
Continue Milestone 41 with Terra. Update README, CHANGELOG, user/privacy/release/handoff docs
to describe implemented `0.4.0` behavior, while clearly marking unpublished state until the
owner publishes. Fix only bounded presentation/doc defects. Do not change signing identity,
application ID, dependencies, or Room migration policy. Stop for Sol.
```

### Task 41C — Sol: comprehensive release decision

```text
Complete Milestone 41 with Sol. Obtain explicit owner approval of versionName 0.4.0 and its
versionCode before changing build identity. Audit the complete migration chain and populated
0.3.0 update, all 14-column destinations including automatic Google and owned legacy tabs,
app lifecycle, security, accessibility, SDK 26/current devices, CPU/battery, permissions,
dependencies, release signer SHA-1, package name, and reproducible APK checksum. Run clean offline
lint/JVM/debug/release and relevant connected suites; request owner-only external/device actions
where required. Withhold release readiness until every required gate passes. Give exact GitHub
merge/tag/upload/public-download instructions; do not commit, merge, tag, or publish for the owner.
Stop after the owner confirms public install and retained data.
```

## Milestone 42 — Post-release environment teardown guide only

Start only after the owner verifies the public `0.4.0` release and separately says to begin.

### Task 42A — Luna: non-destructive inventory

```text
Begin post-release Milestone 42 with Luna. Ask the owner for verified project-specific paths and
inventory Android Studio, SDK/JBR, AVDs, Gradle, ADB, drivers, virtualization, repository clones,
OAuth, releases, and signing backups using read-only commands within approved paths. Classify
each item as preserve, shared/retain, project-only candidate, or unknown. Never delete or change
machine/cloud state. Stop for Terra.
```

### Task 42B — Terra: staged reversible guide

```text
Continue Milestone 42 with Terra. Draft exact owner-executed Windows/Android cleanup instructions
only for verified project-exclusive targets. Include backups, rollback and checks; do not use
broad recursive paths or change BIOS/system, OAuth, signing, Git history, or release assets.
Do not execute the guide. Stop for Sol.
```

### Task 42C — Sol: destructive-safety review

```text
Complete Milestone 42 with Sol. Audit every instruction for target precision, shared-tool risk,
recoverability, signer/password backups, OAuth, public artifacts, and firmware safety. Require
separate explicit owner decisions for actual removal. Publish only a safe guide, not actions.
```

## Milestone 43 — Optional owner-directed closeout

Milestone 43 is **not automatic** and may never begin from a generic request to finish the
project. After reviewing Milestone 42, the owner must name exact targets and separately authorize
each material action. Unknown or shared targets remain untouched.

### Task 43A — Luna: target and backup verification

```text
Begin optional Milestone 43 with Luna only after the owner names exact targets. Verify each
target's resolved path, project exclusivity, backup/recovery status, and explicit owner approval.
Do not remove anything. Stop if any target is unknown/shared or outside approved access, and hand
the verified target list to Terra.
```

### Task 43B — Terra: approved reversible steps only

```text
Continue optional Milestone 43 with Terra. Perform only separately authorized, exact, recoverable
project-only steps from the reviewed guide; verify each result. Never touch signing backups,
remote releases/history, desired app/export data, shared toolchains, cloud configuration, or
firmware without a new specific approval. Stop before any irreversible step for Sol review.
```

### Task 43C — Sol: irreversible-action and continuity gate

```text
Complete optional Milestone 43 with Sol. Review any proposed irreversible action's exact target,
backup, rollback limits, shared-tool impact, and owner authorization before it occurs. Withhold
unsafe steps. Check afterward that source, public releases, signer/key backups, and desired OAuth
configuration remain available. Report every material removal and recovery status; do not infer
permission for another target.
```
