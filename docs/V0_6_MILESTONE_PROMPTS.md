# WorqOrder `0.6.0` Milestone Prompts

## 1. Purpose and fixed decisions

This roadmap governs the planned portable **Backup & Restore** release. Released `0.5.0` remains
the implementation baseline until the owner explicitly starts an implementation milestone. The
current Milestone 49 is planning-only: it may change Markdown documentation but no application,
Gradle, manifest, schema, dependency, or test source.

The approved product contract is:

- A user can create one portable, losslessly compressed WorqOrder backup, select a valid backup
  through Android's document picker, replace all portable local state, and swap back to the state
  that existed immediately before the latest successful replacement.
- The Settings section is named **Backup & Restore**. It appears after **Export Destination** and
  before the version text. **Import Backup** and **Create Backup** are side by side. A **Restore
  Previous State** subsection contains **Restore**, enabled only when a verified restore point
  exists.
- Import confirmation is titled **Replace All WorqOrder Data?** and says: “WorqOrder will save
  your current data as a restore point, then replace it with the selected backup.” Its actions are
  **Cancel** and **Continue**. Restore confirmation is titled **Restore Previous State?** and must
  explain that the state being replaced becomes the next restore point.
- Create, import, and restore are disabled while any timer is running. A backup never contains an
  active timer or open interval.
- Portable state includes every domain identity and relationship: active and archived Clients and
  Consultants, Tag catalogs and categories, tasks, lineage, ordered task Tag snapshots, Notes and
  all other task metadata, completed intervals and precise timestamps, export history, portable
  application preferences, and selected date/task/Consultant when still valid.
- OAuth/access/refresh tokens, credentials, account hints, connected-spreadsheet identity,
  notification permission/state, WorkManager jobs, pending automatic-export attempts, transient
  UI/file-picker state, caches, internal journals, the rolling restore point, and installation-
  local transport identity are excluded.
- Import preserves the selected export destination. Google authorization and spreadsheet
  connection are cleared; automatic Google export is disabled until the user reauthorizes,
  reconnects a sheet, and explicitly enables it again.
- A successful import generates a new installation/transport origin while preserving all domain
  IDs. This prevents a restored copy on another device from claiming another installation's
  Google rows.
- The portable format is plaintext. The UI and documentation must warn that a backup can contain
  sensitive work information and should be stored/shared accordingly. No encryption is implied.
- The maximum accepted compressed file is 100 MiB and maximum expanded content is 500 MiB.
  Parsing is streaming/bounded, only documented entries are accepted, sufficient storage is
  checked, and malformed/unsupported input fails before authoritative data changes.
- Import supports every older backup-format version for which an explicit logical upgrader exists.
  Format version 1 is the `0.6.0` baseline. A newer unsupported version fails closed with an
  actionable “update WorqOrder” result; it is never guessed or partially applied.
- The existing 14-column canonical task export remains unchanged. Backup is a separate portability
  feature, not a fourth report-export destination and not Google synchronization.

## 2. Portable format contract

The user-created file name is `WorqOrder_Backup_YYYY-MM-DD_HHmmss.zip`. Android
`ACTION_CREATE_DOCUMENT` writes it and `ACTION_OPEN_DOCUMENT` selects it with `application/zip`;
no broad storage permission is permitted. A document provider may still return misleading names or
MIME metadata, so only full content validation identifies a WorqOrder backup. The archive uses
standard ZIP Deflate through the JDK/Android runtime and contains exactly:

1. `manifest.json`
2. `data.json`

`manifest.json` contains a fixed WorqOrder marker (`worq.order`), `backupFormatVersion`, producer
version name/code, creation UTC instant, logical data-model version, UTF-8/Deflate declarations,
uncompressed byte count, and SHA-256 of the exact `data.json` bytes. `data.json` contains an
explicit logical DTO graph rather than SQLite, WAL/SHM, Room-schema JSON, DataStore protobuf, file
paths, or Android objects. JSON object order is irrelevant; array order is defined where it carries
meaning, including Tag snapshot order.

The implementation may add one focused stable JSON codec after dependency audit. Preview/RC
libraries require separate owner approval. ZIP handling uses platform APIs. Unknown ZIP entries,
duplicate entry names, path traversal names, encrypted ZIP entries, archive bombs, checksum or
declared-size mismatch, invalid UTF-8/JSON, unsupported versions, invalid enums/instants/ZoneIds,
duplicate IDs, broken references, open intervals, an active timer, or violated one-interval/task
and singleton invariants must fail closed before mutation.

## 3. Atomic replacement and rolling restore contract

Import and restore run under one application-wide replacement mutex coordinated with timer Start,
Stop, midnight normalization, exports, and task/client/catalog writes. The entire archive is
bounded, parsed, upgraded into the current logical model, and validated before confirmation or
mutation.

Before import changes state, WorqOrder serializes its current portable state to an app-private
temporary archive, flushes it, verifies it by rereading and validating its checksum/model, then
atomically promotes it as the single rolling restore point. Only then may replacement start.
The restore point lives under app-private no-backup storage, survives process death/reboot/update,
is excluded from Android backup and portable archives, and disappears naturally on uninstall.

Room replacement occurs in one transaction in foreign-key-safe order. Portable DataStore values
are then replaced while excluded runtime/Google state is reset and a new transport origin is
created. Because Room and DataStore cannot share one native transaction, a durable app-private
operation journal records phases and expected artifact hashes. Startup/resume reconciliation must
idempotently finish or roll back an interrupted operation from the verified restore point. No path
may silently delete data, expose a partly replaced app, or claim cross-store atomicity without this
journal.

Restore uses swap semantics. It first verifies the current rolling restore point, captures and
verifies the state currently in the app, applies the previous state, and only after success promotes
the displaced current state as the next restore point. A second Restore therefore undoes the first.
Failure leaves the original restore point and authoritative state recoverable.

## 4. Google row-identity compatibility

Portable task IDs remain stable, but an import into another installation must not overwrite rows
created by the source installation. Add an installation-local `exportOriginId`, excluded from all
portable snapshots, and use a versioned hidden Google identity equivalent to
`worqorder.task.v2:<originId>:<taskId>`. The visible schema remains 6 with 14 columns.

An upgraded installation may adopt its own legacy `worqorder.task.v1:<taskId>` rows through a
reviewed, idempotent transition. A successful portable import rotates the origin and prohibits that
restored installation from claiming legacy v1 rows. Google connection data is excluded and must be
re-established before export. Never clear or overwrite rows owned by another origin.

## 5. Universal execution rules

Every implementation milestone begins with a read-only branch/ancestry/cleanliness audit and a
preliminary token-budget estimate based only on the owner's current percentage. Before a subtask,
the owner activates its assigned model. After every subtask, stop and ask the owner to switch to the
next documented model. Do not silently continue.

The owner alone runs PowerShell, Gradle, ADB, emulator, physical-device, Google, and release tests.
For each gate, the agent supplies complete PowerShell commands that redeclare repository location,
JBR/SDK variables, project-local Gradle home and project-local `-Duser.home`, followed by numbered
manual checks. Never report a test as passed until the owner reports its result.

Luna owns inventories, fixtures, mechanical tests, traceability, and evidence collection. Terra
owns bounded production implementation and integration. Sol is reserved for format/security/
atomicity decisions, hard bug resolution, adversarial review, and final release judgment. The
sequence is deliberately token-efficient while retaining a Sol-level final quality gate.

## Milestone 49 — Portable backup planning and format contract (documentation only)

No application code, dependency, database, Gradle, manifest, or test source may change.

### Task 49A — Sol: product, security, and atomicity decisions

```text
Begin Milestone 49 with Sol. Read AGENTS.md and all relevant documents; audit the clean branch
chain main -> v0.6.0-development -> milestone49. Reconcile owner answers into a complete portable
format, validation, exclusion, compatibility, rolling-restore, Google identity, and crash-recovery
contract. Ask about material ambiguity before deciding. Do not implement application code. Stop
for Luna after the approved contract is written.
```

### Task 49B — Luna: persistence inventory and traceability matrix

```text
Continue Milestone 49 with Luna. Inventory every Room entity, DataStore field, installation-local
value, scheduler/notification state, credential-adjacent value, export-history value, and selection
state. Map each to include, exclude, regenerate, clear, or validate. Reconcile specifications and
acceptance IDs mechanically; do not change application code. Stop for Terra.
```

### Task 49C — Terra: roadmap and documentation integration

```text
Complete Milestone 49 with Terra. Integrate the approved contract and inventory across all relevant
Markdown files. Preserve released history, move teardown milestones to 56-57, add model-assigned
Milestones 50-55, and state that tests are owner-run. Audit documentation consistency and branch
topology, provide commit summary/body, then stop. Do not start Milestone 50.
```

## Milestone 50 — Portable snapshot model, validation, and compatibility foundation

### Task 50A — Luna: logical-model fixtures and validator tests

```text
Begin Milestone 50 with Luna after explicit owner instruction. Audit ancestry and current budget.
Add version-1 logical DTO fixtures and tests covering every included/excluded value, ordering,
Unicode, relationships, bounds, invalid enums/ZoneIds/instants, duplicate IDs, broken references,
active/open timer rejection, newer-version rejection, and older-version upgrader dispatch. Do not
implement archive or database replacement. Give owner-run compile/JVM commands, then stop for Terra.
```

### Task 50B — Terra: codec, model, validator, and installation-origin foundation

```text
Continue Milestone 50 with Terra. Implement the smallest stable JSON codec integration, explicit
versioned portable DTOs, bounded validation/upgrader interfaces, exportOriginId storage, and typed
results. Keep domain IDs stable and installation identity excluded. Do not add UI or mutate Room
from an archive. Update specifications/tests, provide owner-run gates, then stop for Sol.
```

### Task 50C — Sol: adversarial format and compatibility gate

```text
Complete Milestone 50 with Sol. Review dependency stability, JSON ambiguity, resource bounds,
relationship invariants, sensitive-field exclusions, origin rotation/adoption policy, and future
version dispatch. Fix only milestone defects, provide complete owner-run regression commands and
manual fixture checks, document evidence, and stop before Milestone 51.
```

### Milestone 50 completion evidence

Completed on the `milestone50` branch after the owner reported the full supplied offline
compilation/JVM/lint/debug/release gate and complete connected instrumentation suite passing. The
strict portable-property audit returned no forbidden runtime, credential, Google-connection,
automatic-export, origin, journal, or restore-point field, and `git diff --check` was clean. This
milestone provides only the versioned logical model, strict JSON codec/validator/upgrader boundary,
and installation-local export-origin foundation. It does not create/read archives, mutate Room or
DataStore from a backup, or expose Backup & Restore UI. Milestone 51 remains unstarted.

## Milestone 51 — Compressed backup creation and Android document output

### Task 51A — Luna: archive fixture and boundary tests

```text
Begin Milestone 51 with Luna. Add deterministic manifest/data/checksum/name fixtures plus tests for
ZIP entry allowlisting, duplicate/path/encryption rejection, compressed/expanded limits, cancellation,
I/O failure, low storage, Unicode, empty/large datasets, and no active timer. Stop for Terra after
owner-run JVM verification.
```

### Task 51B — Terra: streaming backup writer and Settings action boundary

```text
Continue Milestone 51 with Terra. Implement a streaming logical snapshot reader and two-entry
Deflate writer through ACTION_CREATE_DOCUMENT, typed progress/result handling, safe cancellation,
and the no-running-timer gate. Do not add import/restore UI yet or expose credentials/runtime state.
Update docs and give owner-run build/manual file-inspection tests; stop for Sol.
```

### Task 51C — Sol: privacy, integrity, and resource gate

```text
Complete Milestone 51 with Sol. Adversarially review plaintext warnings, SHA-256 coverage, exact
entry contract, memory/disk behavior, cancellation cleanup, SAF behavior, and excluded data. Fix
release-blocking defects, provide owner-run regression gates, record evidence, and stop.
```

## Milestone 52 — Atomic import, crash recovery, and rolling restore

### Task 52A — Sol: durable replacement protocol

```text
Begin Milestone 52 with Sol because this is the highest-risk data-safety milestone. Specify exact
journal phases, fsync/atomic-rename boundaries, Room replacement order, DataStore reset/replacement,
rollback/startup recovery, cancellation points, mutex ordering, restore swap, and fault-injection
matrix. Resolve ambiguity before code; stop for Terra.
```

### Task 52B — Terra: import/restore engine

```text
Continue Milestone 52 with Terra. Implement bounded archive reading, full preflight validation,
verified app-private no-backup restore-point creation, durable journal, transactional Room replace,
portable DataStore apply, Google/runtime reset, origin rotation, selection reconciliation, startup
recovery, and verified swap-style Restore. Never use destructive migration or partial best-effort
import. Provide owner-run gates; stop for Luna.
```

### Task 52C — Luna: fault-injection and equivalence evidence

```text
Continue Milestone 52 with Luna. Build comprehensive fake-filesystem/database/preferences fault
tests for every journal phase, process reconstruction, repeated reconciliation, rollback, swap undo,
invalid restore point, cancellation, and exact logical round-trip equivalence. Give owner-run tests
and stop for Sol.
```

### Task 52D — Sol: data-loss gate

```text
Complete Milestone 52 with Sol. Audit all replacement paths for data loss, half-applied Room/
DataStore state, stale credentials/schedulers, concurrency deadlocks, and unbounded input. Require
owner-reported fault and migration gates before closure. Fix only milestone blockers and stop.
```

## Milestone 53 — Backup & Restore Settings experience

### Task 53A — Luna: semantics, state, and presentation tests

```text
Begin Milestone 53 with Luna. Add Compose/ViewModel fixtures for exact labels, section placement,
side-by-side actions, timer-disabled state, restore availability, confirmations, shared persistent
status, error semantics, Activity recreation, large text, narrow/landscape layouts, and picker
cancellation. Stop for Terra after owner-run compile/tests.
```

### Task 53B — Terra: Settings and document-picker integration

```text
Continue Milestone 53 with Terra. Implement the approved Backup & Restore section, confirmations,
SAF launchers, progress/disabled states, restore swap explanation, plaintext warning, and one shared
success/error status line that survives Activity recreation but is excluded from backups. Keep
business rules in coordinators/ViewModels and preserve current Settings behavior. Stop for Sol.
```

### Task 53C — Sol: destructive-UX and accessibility review

```text
Complete Milestone 53 with Sol. Review replacement consent, status/actionability, TalkBack/live
regions, focus/scroll stability, touch targets, large text, accidental double-submit, process
recreation, and timer races. Fix only milestone defects, provide owner-run connected/manual gates,
record evidence, and stop.
```

## Milestone 54 — Compatibility and full backup hardening

### Task 54A — Luna: compatibility/corruption matrix

```text
Begin Milestone 54 with Luna. Expand fixtures across released Room 1-7 upgrade states, backup format
v1, empty/maximal Unicode data, archived directories, Tag snapshots, export history, selection,
malformed/adversarial archives, restart/reboot, and two-device restores. Add cross-destination
regressions proving canonical export schema 6 remains unchanged. Stop for Terra.
```

### Task 54B — Terra: Google identity transition and integration hardening

```text
Continue Milestone 54 with Terra. Finish the reviewed v1-to-v2 hidden Google row identity adoption,
foreign-restore origin behavior, Google disconnect/auto-disable reconciliation, scheduler cleanup,
and any bounded integration defects found by fixtures. Never change visible export columns or claim
another origin's rows. Provide owner-run Google/device/lifecycle tests; stop for Sol.
```

### Task 54C — Sol: security, performance, and compatibility audit

```text
Complete Milestone 54 with Sol. Audit archive parsing, ZIP-bomb/path traversal defenses, memory/disk
bounds, sensitive exclusions, crash recovery, migration/update/downgrade limits, Google ownership,
concurrency, and hours-long stability. Resolve blockers, publish exact owner-run API-26/current/
physical-device matrices, update evidence, and stop before release work.
```

## Milestone 55 — Full `0.6.0` audit and public-release handoff

### Task 55A — Luna: traceability and owner-run evidence orchestration

```text
Begin Milestone 55 with Luna. Audit branch state and map every v0.6 requirement to implementation,
automated tests, and manual evidence. Supply complete project-local PowerShell gates for formatting,
lint, JVM, debug/release builds, API-26/current connected suites, upgrade/fresh install, backup round-
trip, crash recovery, Google, lifecycle, accessibility, and performance. Record only owner-reported
results. Stop for Terra.
```

### Task 55B — Terra: bounded release documentation and fixes

```text
Continue Milestone 55 with Terra. Reconcile README, user/privacy/security/handoff/checklist/change
documents and fix only confirmed release blockers. Do not choose versionCode, rebuild identity,
commit, merge, tag, sign, or publish for the owner. Supply artifact/signature/package/install-over
commands and stop for Sol.
```

### Task 55C — Sol: final release decision

```text
Complete Milestone 55 with Sol. Obtain explicit owner approval before changing versionName or
versionCode. Audit format compatibility, Room 1-through-current migration, data-loss recovery,
privacy/security/dependencies, Google identity, package/signer, owner-run results, signed artifact
hash, public download, and physical install-over. Withhold readiness while any required gate is
failing or unverified. Provide exact integration-branch merge, tag, GitHub release, independent
download verification, and rollback steps; never execute owner Git/release operations. Stop after
the owner confirms the public release.
```

## Milestone 56 — Post-`0.6.0` environment teardown guide only

This is the former v0.5 Milestone 49. It remains documentation-only and starts only after a
successful public `0.6.0` release plus a new explicit owner instruction.

### Task 56A — Luna: non-destructive inventory

```text
Begin Milestone 56 with Luna only after explicit owner instruction following public 0.6.0. Use
read-only commands within approved paths to classify Android Studio, SDK/JBR, AVDs, Gradle, ADB,
drivers, virtualization, clones, OAuth, releases, and signing backups as preserve, shared/retain,
project-only candidate, or unknown. Do not delete or change state. Stop for Terra.
```

### Task 56B — Terra: staged reversible guide

```text
Continue Milestone 56 with Terra. Draft exact owner-executed cleanup instructions only for verified
project-exclusive targets, including backup, rollback, and validation. Never use broad paths or
change firmware/system, OAuth, signing, Git history, public releases, or user data. Do not execute
the guide. Stop for Sol.
```

### Task 56C — Sol: destructive-safety review

```text
Complete Milestone 56 with Sol. Audit exact targets, shared-tool risk, recoverability, signing and
password backups, OAuth, public artifacts, and firmware safety. Require a new explicit owner
decision for every actual removal. Publish only a safe guide and stop.
```

Each task must stop for the next model. It may inspect only approved exact paths, must distinguish
project-only from shared tools, and may not delete, uninstall, modify cloud/firmware, or touch
signing/source/release/user data.

## Milestone 57 — Optional owner-directed closeout

This is the former v0.5 Milestone 50. It is not automatic and may never begin. The owner must first
review Milestone 56, name exact targets, and separately authorize every material action.

### Task 57A — Luna: target and backup verification

```text
Begin optional Milestone 57 with Luna only after exact owner authorization. Verify resolved paths,
project exclusivity, backup/recovery, and approval. Do not remove anything; stop for Sol on unknown,
shared, or out-of-scope targets.
```

### Task 57B — Sol: exact authorization and irreversible-action gate

```text
Continue Milestone 57 with Sol. Review each exact target, shared impact, backup, rollback limit, and
owner authorization. Withhold unsafe or ambiguous actions and hand only separately authorized,
reversible steps to Terra. Do not change state. Stop for Terra.
```

### Task 57C — Terra: separately approved reversible actions only

```text
Continue Milestone 57 with Terra. Perform only the exact, separately approved, recoverable steps
from Sol's reviewed list and verify each outcome. Never expand scope or touch retained signing,
source, releases, OAuth, user data, shared tools, cloud configuration, or firmware. Stop for Sol.
```

### Task 57D — Sol: continuity and recovery verification

```text
Complete Milestone 57 with Sol. Verify retained source, public releases, signer/key backups, OAuth,
and owner-designated data/tools. Report every material removal and recovery status; never infer
permission for another target.
```

Unknown/shared targets remain untouched. Never infer authority from finishing a release. Preserve
source, remote history/releases, permanent signer/key backups, OAuth setup, and desired data unless
the owner makes a new explicit decision naming the exact item.

## 6. Deferred optional Milestone E

Milestone E remains outside `0.6.0` and every release schedule. Portable backups are deliberately
plaintext and do not authorize the deferred at-rest encryption, app lock, biometric/PIN, or privacy-
screen work. Milestone E begins only when the owner explicitly assigns it.
