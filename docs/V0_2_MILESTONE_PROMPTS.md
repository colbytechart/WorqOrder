# WorqOrder `0.2.0` Copy/Paste Milestone Prompts

These prompts are the authoritative consecutive execution sequence after the planning-only branch
preparation. Use one at a time. Do not start the next until the owner accepts the current
milestone. All prompts inherit `AGENTS.md`, the project-directory-only filesystem rule, offline
Gradle/JBR/SDK guardrails, and the conflict-pause rule.

## Milestone 18 — v0.2 planning and roadmap preparation

Status: completed as a planning-only milestone. It prepares and commits the `0.2.0` planning
documents and consecutive prompts without modifying application code, Gradle files, Room schema,
Android resources, or release behavior.

## Milestone 19 — v0.2 persistence and canonical export foundation

Status: completed. Room schema 3, the non-destructive migration, persistence/preferences
foundations, Billing Minutes, and canonical schema-3 exports passed offline and connected checks.

```text
Begin Milestone 19 for WorqOrder 0.2.0. Read AGENTS.md, every relevant document under docs/, and
the complete current implementation before modifying anything. Verify that the implementation
branch descends from the accepted planning-only milestone18 commit and that main/milestone17 remain
preserved. If any conflict exists, pause for my decision.

Set versionName to 0.2.0 and versionCode to 2. Implement only the v0.2 persistence/domain/export
foundation: add the Employee entity and repository foundations; add nullable employee ID plus
employee-name snapshot, Work Type, and canonical decimal Mileage to daily tasks; add selected
employee, landscape handedness, automatic-Google-enabled, and captured/pending-date typed settings;
and add pure Billing Minutes calculation (0 for zero duration, otherwise round exact total upward
to the next 15-minute multiple). New-task defaults belong to later UI milestones.

Advance Room to version 3 with an explicit non-destructive MIGRATION_2_3 from the released
version-2 schema, export the new schema JSON, and preserve populated clients/tasks/intervals/active
timer. Existing tasks migrate with blank employee, Unspecified Work Type, and blank Mileage.

Advance the one canonical export snapshot to schema version 3 with exactly these columns: Start
date, End date, Consultant, Client, Description, Expense, Work type, Mileage, Interval number,
Start time, Stop time, Interval duration, Time spent, Billing minutes. Both dates repeat the one
stored task date as MM/DD/YYYY. Start/Stop are HH:mm; durations remain HH:MM:SS; internal
timestamps remain precise. Adapt CSV,
one-off XLSX, and Google to consume the same rows. Known WorqOrder-owned Google schema-2 tabs may
upgrade atomically to schema 3; unowned or unknown/newer tabs remain protected.

Do not implement client CSV import, employee UI, task forms, scheduling, or lock-screen behavior.
Add migration, repository, Billing Minutes, canonical-row, CSV/XLSX/Google planner, and regression
tests. Run offline formatting/lint/JVM/debug/release checks and connected tests when available.
Report files, schema/migration, exact tests/results, unverified behavior, and a commit message, then
stop.
```

## Milestone 20 — Client CSV import

Status: implemented. Client-name-only CSV import uses bounded strict UTF-8 parsing and one atomic
append/restore/skip Room transaction; exact verification is recorded in the completion report.

```text
Begin Milestone 20. Read AGENTS.md, the v0.2 specifications, and current Milestone-19 code first.
Implement client-name-only Import From CSV in Client Management. Use Android's read-document file
picker restricted to CSV and no storage permission. Treat every nonblank cell in every row/column
as a proposed client; there is no special header.

Reuse existing trim/whitespace/case/100-character validation. Import appends and never overwrites
or removes existing clients: skip active canonical matches, restore archived matches, collapse
in-file duplicates, add remaining valid names, and leave active clients sorted A-Z. Parse quoted
commas, quotes, CR/LF, Unicode, and multiline cells. Enforce documented byte/row/cell limits.
Reject wrong file type, malformed/corrupt CSV, invalid/overlong cells, I/O failure, and excessive
input without crashing or partially mutating Room. Apply the accepted plan transactionally and
report added/restored/skipped counts.

Do not import tasks/intervals or alter exports. Add parser, transaction/repository, ViewModel,
Compose, accessibility, picker-cancel, and instrumentation tests. Run the full relevant offline
verification, report exact results/files/limits, suggest a commit message, and stop.
```

## Milestone 21 — Consultant management and selection

Status: implemented. Consultant Settings, active selection persistence/recovery, archival clearing,
transaction-time task snapshot capture, and the Create Task missing-selection route passed the
Milestone 21 verification gates recorded in its completion report.

```text
Begin Milestone 21. Read AGENTS.md, all consultant/employee-data/product decisions, and the existing app.
Implement the user-facing Consultant Settings section directly below Client Management, backed by
the internal Employee entity. Support active A-Z
view, add, rename, archive/remove from selection, archived view, restore, and one persisted current
consultant selection. Use the approved client-like normalization and conflict behavior.

Historical tasks use their consultant-name snapshot: rename/archive must never rewrite earlier
tasks or exports. New/future assignment uses the current directory name. An explicit task edit may
later correct a daily task. When there is no active selected consultant, task creation must expose a
usable blocking/redirect state, but complete task form changes remain Milestone 22.

Add Room/repository/DataStore/ViewModel/Compose/accessibility tests for defaults, CRUD, conflicts,
sorting, archive/restore, selection recovery, selected-employee archival, snapshot preservation,
and migrated blank tasks. Run relevant offline and connected checks, report exact results and a
commit message, then stop.
```

## Milestone 22 — Task metadata, Billing Minutes, and export integration

Status: implemented and verified. Offline compile/JVM/lint checks passed, and the owner's
project-local emulator run passed all 93 connected tests with zero failures, errors, or skips.

```text
Begin Milestone 22. Read AGENTS.md and all v0.2 task/export specifications. Complete Create/Edit
Task integration for required selected Consultant, Work Type radio buttons (On-Site default,
In-Office alternative), and optional Mileage. Mileage must request a decimal numeric keyboard,
accept only digits and one decimal point under the approved bounds, reject malformed/negative
values, and persist/export canonical locale-independent decimal text. Migrated Unspecified/blank
values remain honest until edited.

Show derived Billing Minutes as task information: 0 with no recorded duration; otherwise use the
exact combined interval total rounded up to a multiple of 15. It must update after timer/manual
interval changes without a stored counter. Rollover/midnight copies Employee ID/name snapshot,
Work Type, and Mileage from the source daily task.

Complete the exact 14-column schema-3 values across CSV, one-off XLSX, and Google Sheets through the existing single
canonical builder. Do not add destination-specific selection/formatting. Prove owned schema-2
Google tabs upgrade safely and repeated export stays duplicate-free. Keep Start/Stop backend
precision and the Stop-before-export rule.

Add domain, migration/repository, ViewModel, Compose, accessibility, and all-destination tests,
including the owner examples for Billing Minutes. Run relevant offline and connected checks,
report behavior/results/files and a commit message, then stop.
```

## Milestone 23 — About, simplified intervals, and text-entry capitalization

```text
Begin Milestone 23. Read AGENTS.md and current UI/version specifications. Add the final Settings
About content with only the Gradle-derived text "WorqOrder v0.2.0 - stable". Do not add a GitHub,
repository, browser, or release link.

Remove the routine interval Duration field; label interval values Start Time/Stop Time and present
them in task-zone 12-hour hh:mm a. Keep Task Total above the interval list and place Billing
Minutes directly below it. Make all text boxes request sentence capitalization from the Android
keyboard without rewriting saved user text. Keep the same 24-hour HH:mm export values. Preserve
full UTC instants, milliseconds, stored ZoneId,
duration math, interval editor precision, and explicit DST-overlap occurrence information. This is
presentation/export formatting only; do not truncate stored data.

Add exact About/no-link tests, interval formatting/semantics tests, DST and precision regression
tests, and run all relevant checks. Report results/files and a commit message, then stop.
```

## Milestone 24 — Handed two-column landscape

```text
Begin Milestone 24. Read AGENTS.md, the approved landscape specification/concept notes, and current
Main implementation. Add Landscape Orientation Settings with Right-handed default/corrupt fallback
and Left-handed alternative, persisted through typed DataStore and applied immediately.

Portrait remains unchanged. In landscape, keep a spanning top bar with WorqOrder at far left and
Settings at far right. Below it, Right-handed places the independently scrolling task list in the
left approximate half and timer/date/Export+Add controls in the right; Left-handed mirrors only
those content columns. The task list starts at its approved heading, reaches the same bottom margin
as the action buttons, and keeps its scrollbar attached to the list's right edge. Do not reverse
task order or lose selection/running semantics.

Test API 26/current API, short/standard/large phones, both handed modes, portrait, 200% text, large
display scale, narrow multi-window bounds, TalkBack traversal, 48dp actions, state restoration,
and timer/date/export workflows. Run relevant checks, report results/configurations/files and a
commit message, then stop.
```

## Milestone 25 — Automatic Google export official research and design

```text
Begin Milestone 25 as research/design only. Read AGENTS.md, GOOGLE_INTEGRATION_ADR.md,
EXPORT_SPEC.md, and current Google implementation. Before internet access, explain exactly what
official documentation must be checked and ask for the narrowest permission required. Use only
current official Android Developers and Google Identity/Workspace sources.

Determine the stable API-26-compatible approach for approximate near-11:59 PM work, Doze/reboot,
unique captured-date scheduling, notification permission/channels, and operation constraints.
Verify whether the existing drive.file AuthorizationClient grant can support unattended work or
must become a pending user action. Preserve free/GPLv3/no billing/no backend/no broader scope.

The fixed behavior is: Google-only opt-in switch; CSV/XLSX manual; captured target date/ZoneId may
run after midnight; active timer becomes pending; after Stop a content-free notification tap
resumes/performs or confirms that date; successful automatic export is silent; idempotent owned-tab
replacement; no exact alarm/foreground stopwatch service/unbounded retry.

Update ADR/setup/export/decision/acceptance docs with the selected mechanism, alternatives,
permissions, dependencies, error/retry/privacy/battery policy, and manual setup. Implement no
functional scheduler. Present findings and pause for explicit approval.
```

## Milestone 26 — Automatic Google daily export implementation

```text
Begin Milestone 26 only after I approve Milestone 25's official design. Read all approved docs and
current code. Implement the Google-only conditional Settings switch, durable captured target
date/ZoneId, approved inexact unique scheduling/rescheduling, idempotent schema-3 Google export,
and bounded safe failures. CSV/XLSX must remain manual.

If any timer is running, export nothing and persist the target as pending. After Stop commits,
show the approved content-free notification. Tapping resumes/performs or confirms that captured
date; dismissal never claims success or loses recoverable pending state. Successful automatic
export shows no Main status or success notification. Sign-out/disconnect/disable/zone change,
offline/auth/permission/quota/ambiguous responses, reboot/Doze/delay, and manual-export races must
be safe and duplicate-free. Room remains authoritative and tokens remain in memory only.

Add unit, Work/scheduler, repository, ViewModel, Compose, accessibility, notification, process/
reboot simulation, integration, and battery/background tests from the approved design. Run all
relevant checks, report exact results and remaining device tests, suggest a commit message, then
stop.
```

## Milestone 27 — Running-timer lock-screen official research and design

```text
Begin Milestone 27 as research/design only. Read AGENTS.md, timer/lifecycle rules, and current app.
Before internet access, explain the required official Android research and request only the
narrowest permission. Base the review on current official Android Clock timer/alarm behavior and
stable notification/chronometer/lock-screen/AppWidget APIs across API 26 through the target API.

Compare feasible mechanisms, notification/runtime permission implications, swipe dismissal,
lock-screen privacy/OEM suppression, process death/reboot, exact elapsed presentation, and whether
the requested behavior is possible without a foreground service or app-owned background tick.
Room must remain authority; the surface appears only while running and dismissal affects only its
active interval.

Update architecture/product/decision/privacy/acceptance docs with alternatives and recommendation.
Implement no lock-screen code. Explain user-visible tradeoffs and pause for my explicit approval.
```

## Milestone 28 — Approved running-timer lock-screen surface

```text
Begin Milestone 28 only after I approve Milestone 27's official mechanism. Implement exactly that
running-only surface with WorqOrder icon/name, active task name, and elapsed timer. Use Room's
active interval and persisted timestamps; never make notification/widget state authoritative or
write ticks. Swiping dismisses only the current interval's surface and does not Stop; a later Start
may show a new surface. Stop removes it.

Handle permission denial, private lock-screen settings, OEM suppression, navigation, rename/edit
constraints, screen lock/unlock, Recents, process death, reboot, and recovery honestly. Do not add
a foreground service, exact alarm, wake lock, or continuous app-owned background ticker solely for
display.

Add unit/ViewModel/accessibility/instrumentation/device tests and CPU/memory/battery profiling.
Run all relevant checks, report exact results/limitations/files and a commit message, then stop.
```

## Milestone 29 — v0.2 integration and release readiness

```text
Begin Milestone 29. Read AGENTS.md, all docs, v0.2 decisions, and the complete repository. Audit
only implemented v0.2 requirements and preserve working XLSX/Google behavior. Verify a real update
from signed 0.1.0 retains every client/task/interval/active timer/setting and applies safe new
defaults. Run formatting, lint, JVM, migration, Compose, connected API-26/current, debug/release,
Google/export, accessibility, orientation, scheduling/notification, lifecycle, security/secret,
and focused CPU/memory/battery gates. Fix release-blocking defects only; do not add optional scope.

Finalize README, USER_GUIDE, PRIVACY_AND_DATA, QA, traceability, security, known limitations,
release checklist, handoff, and changelog for 0.2.0. Keep Android backup disabled, GPLv3, direct
GitHub APK distribution, permanent signing identity, drive.file-only/no billing, and no Play
release. Generate/verify the signed APK and SHA-256 without committing secrets/artifacts unless
policy explicitly says so.

Report complete migration/test/release readiness, blockers, artifact path/checksum/version, files,
commit/tag suggestion, and step-by-step GitHub release instructions, then stop.
```

## Optional Milestone E — release-agnostic backburner

Milestone E is outside `0.2.0` and every other release scope and has no scheduled execution. It may
begin only after the owner explicitly assigns and names Milestone E. Its scope is WorqOrder-managed
at-rest encryption, opt-in biometric/device-credential/
approved-PIN app locking, and optional screenshot/Recents privacy controls with their own threat
model, migration, failure, accessibility, performance, security, and regression gates. It must not
be inferred from a request to continue, harden, or release `0.2.0`.
