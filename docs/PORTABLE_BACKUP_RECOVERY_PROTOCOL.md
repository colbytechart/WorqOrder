# Portable Backup Replacement and Recovery Protocol

## 1. Status and scope

This document began as the Milestone 52A design gate for `0.6.0` and remains the authoritative
implementation contract for importing a portable backup, recovering an interrupted replacement,
and performing the one-generation **Restore Previous State** swap. Milestone 52A changed
documentation only; Tasks 52B-52D implemented, fault-tested, and audited the protocol without
creating a second replacement path.

This protocol does not change the portable ZIP format, Room schema 7, canonical task-export
schema 6, Android backup policy, Google scope, or permanent signing identity. It never imports a
raw database or Preferences DataStore file and never uses destructive migration.

## 2. State and trust boundaries

The replacement engine treats local state as four distinct groups:

1. **Authoritative Room state:** Clients, Consultants, Tags, tasks, task Tag snapshots, completed
   intervals, and the singleton active-timer record. Import is prohibited unless the active-timer
   record and every open interval are absent.
2. **Portable preferences:** appearance, time-zone mode/manual ZoneId, last export attempt,
   Consultant selection, landscape handedness, and valid task selection. For Import only, the
   destination installation's current export destination overrides the value in the archive.
   Restore applies the export destination stored in the restore point as part of swap semantics.
3. **Installation-local and runtime state:** Google account/sheet hints, automatic-export enablement
   and pending target, running-notification dismissal, WorkManager work, notifications, and the
   Google transport origin. A successful Import or Restore clears the Google connection and
   automatic-export state, cancels related work/notifications, clears transient notification
   dismissal, and installs one pre-generated new transport origin with legacy-v1 adoption disabled.
4. **Recovery-only state:** the phase journal, operation artifacts, and rolling restore point.
   These live only below `Context.noBackupFilesDir`, never enter a portable archive, and are
   naturally removed by Clear Storage or uninstall.

The portable archive is untrusted input even when it was created by WorqOrder. The current archive
reader must enforce the 100 MiB compressed and 500 MiB expanded absolute limits plus the documented
device-heap materialization limit, the exact two-entry ZIP allowlist, safe entry names,
non-encrypted Deflate input, UTF-8 and strict JSON, manifest/data byte counts and SHA-256, explicit
format upgrading, graph validation, and every schema-7 invariant before a confirmation can
authorize mutation. Current-format JSON is record-streamed into one logical candidate DTO; the
whole byte array, String, and DOM must never coexist in production.
The device-heap ceiling is the smaller of 500 MiB and one eighth of the process maximum heap, with
an 8 MiB floor. A file below the absolute format ceiling can therefore be rejected on a constrained
device rather than causing process death.

## 3. Application-wide operation gate

Milestone 52B introduces one application-scoped `ApplicationDataOperationLock`. Its mutex is the
outermost lock for portable replacement/recovery, background operations that can overlap it, and
snapshots that require a coherent Room/preferences view. Those entry points acquire locks only in
this order:

1. `ApplicationDataOperationLock`;
2. the existing `TimerOperationLock`, when a timer snapshot or mutation is involved;
3. the Google export operation mutex, when remote export is involved;
4. a Room transaction or one Preferences DataStore `edit` call;
5. recovery-filesystem operations.

No code may acquire an earlier lock while holding a later lock. Replacement code holds the
application lock and calls dedicated low-level bulk Room/preferences adapters that do not reacquire
it. Background/runtime entry points that can overlap replacementâ€”startup, Activity resume, boot,
and automatic exportâ€”enter through the application lock. Ordinary repositories do not reacquire
that outer lock from inside a replacement transaction. Instead, after destructive confirmation,
the Settings route presents a non-dismissible progress barrier that blocks navigation and every
unrelated visible mutation until replacement terminates. Timer state is rechecked under the timer
lock after the application lock. A Google export must not hold its Google mutex while waiting for
the application lock; it obtains its immutable local snapshot first, then performs remote I/O under
the Google mutex.

The confirmation dialog and document picker never hold a mutex. Import may copy, parse, upgrade,
and validate the selected document before confirmation without the application lock because that
work is read-only. After **Continue**, the engine acquires the application lock, revalidates the
staged source, rechecks the active-timer/open-interval invariant, and captures the then-current
local state. This makes the restore point correspond to the state actually replaced.

The progress barrier is concurrency control at the UI boundary, not crash authority. All archive
and replacement work runs on the I/O dispatcher, while the durable journal remains authoritative
if the Activity disappears or the process dies.

Startup recovery owns the same lock and completes before repositories, timer recovery,
automatic-export reconciliation, notifications, or normal navigation may expose state. If journal
recovery cannot converge, normal application use remains blocked behind a typed recovery failure;
the app must never expose a mixed Room/DataStore generation.

## 4. Recovery directory and durable files

The fixed directory is `noBackupFilesDir/portable_replacement`. App-private creation permissions
are retained. It contains only fixed, operation-ID-qualified names selected by the app; archive
entry names and user filenames never become local paths.

| File role | Lifetime and purpose |
|---|---|
| `restore-point.zip` | The one verified rolling portable restore point. |
| `source-<operationId>.zip` | A private exact copy of a fully preflighted Import archive. Restore reads `restore-point.zip` as its source instead. |
| `displaced-<operationId>.zip` | A verified portable capture of the state that the operation will displace. It is rollback authority and the next restore point after a successful Restore. |
| `local-preferences-<operationId>.json` | Recovery-only snapshot of every current known Preferences key/value, including excluded Google/runtime/origin values. It is never portable and exists solely to restore the exact pre-operation local state after failure. It contains no OAuth/access/refresh token because WorqOrder never persists those tokens. |
| `prior-restore-<operationId>.zip` | Import-only preservation of the restore point that existed before the new displaced state is promoted. It is absent when no prior point existed. |
| `replacement-journal.json` | Strict, versioned journal naming the operation, next action, hashes, byte counts, target origin, prior-point presence, and failure direction. |

Every artifact record stores its exact whole-file SHA-256 and byte count. A portable ZIP is also
reopened through the production bounded reader so its manifest/data hashes and logical model are
verified. The recovery-only preferences snapshot has a versioned schema and its own SHA-256. The
journal includes a monotonically increasing sequence and a SHA-256 over its canonical payload;
unknown versions, values, or fields fail closed.

### 4.1 Durable file primitive

Every durable write or replacement uses the same filesystem primitive:

1. write a uniquely named temporary file in `portable_replacement`;
2. flush userspace buffers, call `FileDescriptor.sync()`, and close;
3. reread and fully verify the temporary file;
4. rename within the same directory using the platform's atomic rename operation;
5. fsync the parent directory through the Android/Linux file-descriptor API;
6. reread the promoted name and verify its recorded byte count and SHA-256.

A phase is not advanced until all six steps succeed. A temporary file is never accepted as
authority. Replacement of an existing name uses a same-directory atomic rename; copying across
filesystems is prohibited. If atomic rename or parent-directory sync is unavailable/fails, the
operation fails before authoritative mutation or remains journaled for recovery. Directory entries
are also synced after deletion. These requirements are behind a small filesystem interface so JVM
fault tests can interrupt before and after every durability boundary.

The journal itself uses this primitive for every transition (`journal.next` to
`replacement-journal.json`). The old valid journal remains authoritative until the new journal is
synced and promoted. A process death therefore yields either the old complete phase or the new
complete phase, never a partially written phase.

## 5. Journal model and next-action phases

The journal records actions still to perform rather than optimistic claims about actions that may
have completed. Repeating the recorded action must be safe. Required fields are:

- journal schema version, operation ID, and `IMPORT` or `RESTORE` kind;
- `nextAction`, sequence, created/updated UTC instants, and `FORWARD` or `ROLLBACK` direction;
- source, displaced, local-preferences, current restore-point, and optional prior-restore hashes
  and byte counts;
- source backup format/model versions and logical `data.json` SHA-256;
- the exact pre-generated target `exportOriginId` (never generated during replay);
- the destination export choice that Import must preserve;
- whether a prior restore point existed;
- a non-sensitive typed failure category, never task/client content or raw API/JSON data.

The allowed `nextAction` values are:

| Next action | Idempotent responsibility |
|---|---|
| `PREPARE_ROLLING_POINT` | Import only: preserve any old point as `prior-restore`, then promote the verified displaced archive to `restore-point`. Restore verifies that its source point is still unchanged. |
| `APPLY_TARGET_ROOM` | Replace Room with the validated target in one transaction. |
| `APPLY_TARGET_PREFERENCES` | Perform one absolute DataStore replacement containing target portable values plus required local resets and the journal's exact new origin. |
| `RESET_TARGET_RUNTIME` | Idempotently cancel automatic-export work/attention notification and running-timer notification/session state. No remote Google mutation occurs. |
| `FINALIZE_SUCCESS` | Import deletes the preserved prior point; Restore atomically promotes displaced state as the next restore point. Delete operation staging and then the journal. |
| `APPLY_ROLLBACK_ROOM` | Replace Room with the validated displaced archive in one transaction. |
| `APPLY_ROLLBACK_PREFERENCES` | Restore the exact recovery-only pre-operation preference snapshot in one DataStore edit. |
| `RESET_ROLLBACK_RUNTIME` | Reconcile schedulers/notifications from restored local preferences, without performing a report export. |
| `RESTORE_PRIOR_ROLLING_POINT` | Import restores the old rolling point (or removes the newly created point if none existed). Restore leaves its original source point in place. |
| `FINALIZE_ROLLBACK` | Verify original logical/local state, delete staging, and finally delete the journal. |

Advancing a phase means durably writing the next action after the current action has succeeded and
been verified. If a process dies after the action but before advancement, startup repeats it and
accepts only either the expected before-state or expected after-state hash. Any third state blocks
recovery rather than guessing.

`PREPARE_ROLLING_POINT` is written before an Import moves an existing restore point. After it
finishes, the journal advances to `APPLY_TARGET_ROOM`; this durable transition is the commit intent.
For Restore, the verified source point is left untouched and the same transition is the commit
intent. Once commit intent exists, cancellation cannot abandon the operation.

Advancing from `RESET_TARGET_RUNTIME` to `FINALIZE_SUCCESS` is a second, irreversible success
decision made only after complete target equivalence has passed. Before that transition, a caught
target-application error may durably choose rollback. At or after `FINALIZE_SUCCESS`, recovery only
rolls forward: Import may already have deleted its prior point, and Restore may already have
replaced its source point. A finalization failure therefore retains the journal and retries
finalization rather than attempting rollback from an ambiguous point state.

## 6. Import protocol

1. Stream the selected document into `source-<id>.zip` with compressed-byte accounting and whole-
   file hashing. Sync, reopen, fully preflight, upgrade to the current logical DTO, validate, and
   retain only the private staged copy. Invalid input removes staging and changes nothing.
2. Present **Replace All WorqOrder Data?**. Cancel removes staging and changes nothing.
3. On **Continue**, acquire the application lock and then timer lock. Reverify the source; reject if
   a timer/open interval now exists.
4. Read the currently selected export destination. Generate one target transport origin now.
   Capture current portable state as `displaced-<id>.zip`, sync it, and verify it through the same
   reader. Capture and verify the complete recovery-only local preference snapshot. Check available
   storage before each artifact write; an estimate is advisory, while a short write is a hard
   pre-mutation failure.
5. If `restore-point.zip` exists, fully verify it. Durably create the journal with
   `PREPARE_ROLLING_POINT`, including whether that point exists and every artifact hash.
6. Idempotently move the old point to `prior-restore-<id>.zip` when present, promote the displaced
   archive as `restore-point.zip`, sync/verify, then advance to `APPLY_TARGET_ROOM`. No authoritative
   data has changed before this commit-intent transition.
7. Apply Sections 8 and 9, advancing through target Room, preferences, and runtime actions.
8. At `FINALIZE_SUCCESS`, verify authoritative target equivalence and the rolling point's displaced
   hash. Delete `prior-restore`, source, recovery-only preferences, and other operation staging with
   synced directory updates. Delete the journal last. Return success only after the journal is gone.

## 7. Restore swap protocol

1. Acquire the application lock and timer lock. Reject when a timer/open interval exists.
2. Fully verify `restore-point.zip`; absence or corruption disables/fails Restore without mutation.
3. Capture and verify current portable state as `displaced-<id>.zip`, capture the complete local
   preference recovery snapshot, and generate one exact target origin.
4. Durably create the journal at `PREPARE_ROLLING_POINT`. Verify the restore point still has the
   recorded source hash, leave it untouched, and advance to `APPLY_TARGET_ROOM` (commit intent).
5. Apply the restore-point logical state through target Room/preferences/runtime phases. Restore
   applies the portable export destination stored in that point; like Import, it clears Google
   connection/automatic state and uses the journal's new origin.
6. Only after target equivalence succeeds does `FINALIZE_SUCCESS` atomically replace
   `restore-point.zip` with the verified displaced archive. If process death occurs around this
   rename, recovery accepts the recorded source hash (swap still due) or displaced hash (swap
   complete), and rejects anything else.
7. Delete staging and the journal last. A second Restore applies the displaced archive and performs
   the same swap, thereby undoing the first Restore.

## 8. One-transaction Room replacement

The target DTO is converted to entities only after preflight. One `RoomDatabase.withTransaction`
per application attempt performs this exact foreign-key-safe order:

1. recheck that `active_timer` is empty and no interval is open;
2. delete `active_timer`, `work_intervals`, `task_tag_snapshots`, `daily_tasks`, `tags`, `clients`,
   then `employees`;
3. insert Clients, Employees/Consultants, Tags, daily tasks, ordered task Tag snapshots, then
   completed work intervals;
4. leave `active_timer` empty;
5. verify row counts, unique identities, parent references, at-most-one interval per task, snapshot
   category/order uniqueness, no open interval, and `PRAGMA foreign_key_check` before commit.

Insert uses `ABORT`, never replace/upsert. Any exception rolls back the whole Room transaction.
Replay repeats the same clear-and-insert transaction with the same identities and is therefore
idempotent. After commit, reread through the logical snapshot mapper and require equality with the
expected target Room graph before advancing the journal.

## 9. One-edit Preferences replacement and runtime reset

The target preferences are constructed before mutation and applied in one DataStore `edit`:

- clear existing keys, then write only validated target portable settings/selections;
- on Import, replace the archive's default export destination with the destination recorded in the
  journal; on Restore, use the restore point's destination;
- retain selected Consultant only when it names an imported active Consultant;
- retain selected task only when task ID, series ID, work date, and ZoneId match the imported task;
- omit all Google account/sheet metadata;
- set automatic Google export false and omit its target/pending fields;
- omit running-notification dismissal and other transient state;
- write the journal's exact new export origin and set legacy-v1 adoption false.

Completion of `DataStore.edit` is followed by a typed reread/equality check before phase advance.
Runtime reset then idempotently cancels the unique automatic-export work and attention notification,
cancels running-timer notification/session state, and invalidates in-memory selection/settings/
connection projections as needed. It never revokes a Google account remotely, deletes a sheet,
or performs an export.

The recovery-only local preference snapshot is an exact current-version allowlist of every known
Preferences key needed to reconstruct the pre-operation installation-local state. Rollback applies
it with one clear-and-repopulate `edit` and verifies equality. Adding a new Preferences key later
requires classifying it as portable, reset-on-replacement, or recovery-only and updating this
snapshot contract before release.

## 10. Failure, rollback, and startup reconciliation

Failures before a durable journal propagate normally after verified temporary cleanup. They cannot
change Room, DataStore, runtime scheduling, origin, or the rolling point.

After commit intent but before the durable `FINALIZE_SUCCESS` transition, a caught error in target
Room, Preferences, runtime reset, or target-equivalence verification is handled in a
non-cancellable recovery context. The engine durably changes direction to `ROLLBACK` with next
action `APPLY_ROLLBACK_ROOM`, then:

1. reapplies the displaced portable Room graph transactionally;
2. reapplies the recovery-only local preferences exactly;
3. reconciles runtime schedulers/notifications from those restored preferences;
4. for Import, restores the verified prior rolling point, or removes the newly promoted point when
   none existed; for Restore, verifies the original point remains unchanged;
5. verifies pre-operation Room and preference equivalence, cleans staging, and deletes the journal
   last.

If rollback itself fails, the journal and verified artifacts remain. The app reports/retains a
typed recovery-required state and blocks normal use; it never clears the database, discards the
journal, or silently chooses defaults.

At `FINALIZE_SUCCESS` or later, errors never change direction. Reconciliation repeats only the
hash-aware finalization and cleanup actions until the successful target and next restore point are
verified.

At every process start and foreground resume, reconciliation checks the journal before all other
recovery. With no journal it may remove only unreferenced stale `.tmp` files after verifying they
are not `restore-point.zip`. With a journal it validates the journal and all required artifacts,
acquires the application lock, and repeats its recorded next action until success or a typed hard
failure. `FORWARD` phases resume forward. `ROLLBACK` phases resume rollback. Reconciliation is safe
to call repeatedly and concurrently; the application lock admits one runner.

A corrupt/missing journal with evidence of an in-progress operation, or a journal whose required
artifact hash does not match, is not auto-deleted. It fails closed for owner-visible recovery
guidance. The engine never guesses whether Room or DataStore is the old or new generation.

## 11. Cancellation contract

Cancellation is honored only during external copy/preflight, before confirmation, and during
pre-journal current-state capture. It closes streams, removes unpromoted temporary files, and
leaves all state unchanged.

From the first durable journal through journal deletion, convergence runs in `NonCancellable`
application scope. Activity destruction or caller cancellation may detach the UI but cannot cancel
the engine. No coroutine cancellation may occur inside a Room replacement transaction, between a
successful DataStore edit and its journal advance, or during an atomic artifact promotion. Process
death remains supported through the journal; it is not modeled as coroutine cancellation.

## 12. Required fault-injection matrix

Milestone 52C must inject a throw and a simulated process death immediately before and after every
row below, reconstruct all collaborators from durable state, invoke reconciliation twice, and
prove the expected final state. Each case runs for Import with and without a prior restore point
and for Restore.

| Boundary | Required proof |
|---|---|
| source copy, sync, verification, and promotion | invalid/partial input never creates a journal or mutates local state |
| displaced archive write, sync, verification, and promotion | current state remains exact and prior point unchanged |
| local-preference recovery snapshot write/verify | current Room/preferences/runtime remain exact |
| initial journal temp write, sync, rename, directory sync, reread | either no journal/no mutation or one valid recoverable journal |
| prior restore rename and directory sync | recovery recognizes old/new filename state by expected hashes |
| displaced-to-restore promotion and verification | Import has a verified rollback point before commit intent |
| commit-intent journal transition | pre-intent cancellation abandons safely; post-intent cancellation converges |
| before, inside, after Room transaction and before journal advance | Room is wholly old or wholly target; replay/rollback is exact |
| before/after DataStore edit and before journal advance | replay recognizes and converges from either whole preference generation |
| every runtime cancel/reconcile action | repetition is harmless and no export or remote destructive call occurs |
| target equivalence verification | mismatch switches durably to rollback; no success is reported |
| Restore-point swap rename/sync/verification | restore point is exactly source-before or displaced-after, never missing/partial |
| prior-point cleanup, each staging deletion, and journal deletion | cleanup replay cannot delete the sole authoritative restore artifact |
| each rollback Room/preferences/runtime/point phase | original state and original point are recovered exactly or app stays blocked |
| corrupt journal, missing artifact, wrong hash, unsupported journal version | fail closed without guessing or deleting authoritative data |
| concurrent Start/Stop/midnight, task/client/Consultant/Tag writes, export, Import, and Restore | mutex ordering prevents mutation races and deadlock |
| low storage and short writes at every artifact/action | failure occurs before mutation or journaled rollback remains available |

Equivalence assertions compare complete logical Room graphs, all known Preferences keys, selected
state, restore-point hash, transport-origin behavior, WorkManager unique-work presence, and
notification state. Tests must also prove no task/client content, raw archive JSON, account ID,
spreadsheet ID, or credential appears in journal errors or logs.

## 13. Milestone 52 completion evidence

Tasks 52B-52D implemented this frozen contract. The owner reported successful compilation, full
debug JVM and connected instrumentation suites, `lintDebug`, and debug/release APK assemblies.
Fault/equivalence coverage exercises every journal action in both directions, replay, cancellation,
rollback, corrupt restore points, exact Room and Preferences target generations, and logical DTO
round trips. The final Sol gate added journal self-checking, bounded typed recovery preferences,
Room identity/count/foreign-key postconditions, displaced-generation verification, one shared
application-data lock, and startup-before-use convergence. Any future change to phase/lock order,
rollback authority, export-destination behavior, successful-replacement Google reset, or swap
semantics still requires an owner-approved documented decision. Settings integration remains
Milestone 53.
