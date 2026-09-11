# WorqOrder Data Model

Version scope: schema and behavior descriptions explicitly labeled released `0.1.0`/`0.2.0` or
versions 1–4 preserve historical/migration compatibility. The current `0.3.0` schema-5 model and
its no-rollover, zero-or-one-interval rules are authoritative in Section 14.

## 1. Storage conventions

- Stable IDs are random UUID strings generated in the application before insert. They are never reused or derived from mutable text.
- UTC instants are stored as signed 64-bit epoch milliseconds. This preserves Android clock and
  interval precision even though user-visible/exported duration strings intentionally omit
  fractional seconds.
- Work dates are stored independently as `LocalDate.toEpochDay()` signed 64-bit values.
- Zone IDs are IANA/geographical IDs accepted by `ZoneId.of`, for example `America/New_York`; fixed-offset display labels are not stored in place of them.
- Booleans are SQLite integers through Room.
- Every mutable entity has `createdAtEpochMs` and `updatedAtEpochMs`; updates use the injected UTC clock.
- Entities are persistence details. Repositories map them to domain models and validate strings/time values before writes.
- Room schema versions 1 through 5 store these primitive values directly and require no type converters. Domain mappings reconstruct `Instant`, `LocalDate`, and `ZoneId` deterministically. Version 5 is the current production schema; versions 1–4 remain migration inputs for released installations.

## 2. Entity relationship overview

```text
Client 1 ──────── * DailyTask 1 ──────── * WorkInterval
                         │                         │
                         └── seriesId              └── 0..1 ActiveTimer (singleton pointer)

Employee 1 -------- * DailyTask (nullable for migrated history)

Preferences DataStore: theme, zone mode, export default, Consultant selection,
landscape handedness, automatic-export target, selection hints, connected spreadsheet metadata,
running-notification dismissal, last export outcome
```

The required production sequence stores this logical model in ordinary app-private Room and
Preferences DataStore files protected by Android's application sandbox. Release-agnostic,
unscheduled Milestone E may add a separately authorized at-rest encryption adapter only after the
owner explicitly assigns it, without changing these entities,
relationships, IDs, UTC/date/ZoneId semantics, or Room's authority.

Daily tasks in a series are intentionally not parented by a separate series table. The stable
`seriesId` is lineage metadata only in current schema 5; current date/ZoneId reconciliation and
midnight handling never create rollover or continuation copies. Rows on other dates may be
historical migrated copies or explicit user-created tasks.

## 3. `clients`

| Column | Type | Rules |
| --- | --- | --- |
| `id` | TEXT PK | UUID, immutable |
| `name` | TEXT | trimmed/collapsed display name, 1–100 characters |
| `canonical_name` | TEXT | locale-independent lowercased/collapsed comparison key |
| `active_name_key` | TEXT nullable UNIQUE | canonical name while active; null while archived |
| `is_active` | INTEGER | active selector visibility |
| `created_at_epoch_ms` | INTEGER | UTC epoch millis |
| `updated_at_epoch_ms` | INTEGER | UTC epoch millis |
| `archived_at_epoch_ms` | INTEGER nullable | set on archive, cleared on restore |

`active_name_key` makes duplicate active canonical names structurally rejectable while allowing archived duplicates. All add/rename/restore operations compute the key through the same validator. Active query ordering is case-insensitive display name, then ID for stability.

Client deletion is not exposed. The task foreign key uses `ON DELETE RESTRICT` as defense in depth.

## 4. `daily_tasks`

| Column | Type | Rules |
| --- | --- | --- |
| `id` | TEXT PK | stable daily-task UUID |
| `series_id` | TEXT | stable UUID shared by corresponding dates |
| `client_id` | TEXT FK | references `clients.id`, delete restricted |
| `description` | TEXT | trimmed short description, 1–400 characters |
| `hardware_software_purchases` | TEXT | optional trimmed free-form text, 0–400 characters; empty string means none |
| `work_date_epoch_day` | INTEGER | assigned `LocalDate` |
| `zone_id` | TEXT | valid ZoneId used for this assignment |
| `created_at_epoch_ms` | INTEGER | UTC epoch millis |
| `updated_at_epoch_ms` | INTEGER | UTC epoch millis |

Constraints/indexes:

- Index `(series_id, work_date_epoch_day, zone_id)` supports lineage/date/zone lookup. In schema 5 it is
  non-unique because lineage no longer creates rollover copies; versions 1–4 used the unique form.
- Index `(work_date_epoch_day)` supports the main list.
- Index `(client_id)` supports joins and client history.
- Short-description and purchases-text validation is primarily a domain constraint; migrations may add compatible SQLite checks when Room schema support is proven.

Changing client, short description, or hardware/software-purchases text changes only this daily
task. Current schema-5 behavior never copies those values automatically to another date. Deleting a
daily task cascades to its intervals, does not affect clients, and does not affect another row with
the same series ID.

The `hardware_software_purchases` column was introduced by the implemented version-2 migration in
Milestone 6. It is non-null with `DEFAULT ''` so every version-1 daily task migrates without
inventing purchase data. The shared task-metadata validator enforces the two independent
400-character limits rather than rebuilding the table destructively.

## 5. `work_intervals`

| Column | Type | Rules |
| --- | --- | --- |
| `id` | TEXT PK | stable interval UUID |
| `task_id` | TEXT FK | references `daily_tasks.id`, `ON DELETE CASCADE` |
| `start_epoch_ms` | INTEGER | UTC start instant |
| `stop_epoch_ms` | INTEGER nullable | null only for the global open interval |
| `active_slot` | INTEGER nullable UNIQUE | null when completed; fixed value `1` for the sole open interval |
| `was_manually_edited` | INTEGER | true after a manual boundary edit; manual creation also true |
| `created_at_epoch_ms` | INTEGER | UTC epoch millis |
| `updated_at_epoch_ms` | INTEGER | UTC epoch millis |

Constraints/indexes:

- Unique `(task_id)` enforces the schema-5 zero-or-one interval relationship.
- Index `(task_id, start_epoch_ms, id)` supports deterministic chronological display and validation.
- Unique nullable `active_slot` structurally prevents a second open interval: SQLite permits many nulls for completed rows but only one row containing `1`.
- Unique `(id, task_id)` supports the composite active-timer foreign key that proves the pointed interval belongs to the recorded task.
- Completed rows require `start_epoch_ms < stop_epoch_ms`.
- An interval must lie within `[taskDate.atStartOfDay(zone), nextDate.atStartOfDay(zone)]`; this zone-aware rule is enforced in the transaction service because SQLite cannot interpret `ZoneId` rules.
- Intervals for the same task cannot overlap. Validate against current rows inside the write transaction.
- Cross-midnight intervals are prohibited after normalization.

Schema 5 does not persist an ordinal. The compatibility presentation model may expose `ordinal = 1`
for the sole interval while older UI contracts are retired; it is never read from or written to Room.

Version 1 deliberately uses the nullable unique `active_slot` instead of a custom partial SQLite index or trigger. This keeps the invariant in the exported Room schema and avoids out-of-band schema objects. Public repository insertion creates only completed intervals; the active-timer transaction is the only data-layer path that writes `stop_epoch_ms = NULL` and `active_slot = 1`.

## 6. `active_timer`

| Column | Type | Rules |
| --- | --- | --- |
| `singleton_id` | INTEGER PK | always `1`; at most one row |
| `interval_id` | TEXT FK | part of composite reference to the open interval, delete restricted |
| `task_id` | TEXT | paired with `interval_id` in the composite foreign key |
| `boundary_zone_id` | TEXT | valid ZoneId captured at Start |
| `created_at_epoch_ms` | INTEGER | UTC epoch millis |
| `updated_at_epoch_ms` | INTEGER | UTC epoch millis |

The table is empty when stopped and contains one fixed-key row when running. Its composite `(interval_id, task_id)` foreign key references unique `(work_intervals.id, work_intervals.task_id)`, so Room/SQLite proves that the active interval belongs to the recorded task. `ON DELETE RESTRICT` blocks deletion of the active interval and, through task-to-interval cascade, blocks deletion of its running task.

The active row is the process-recovery pointer while `work_intervals.active_slot` is the structural global-open cardinality guard. The fixed-key DAO and transaction layer maintain the pointer invariant:

- an active row must point to an interval whose stop is null;
- every null-stop interval must be the referenced interval;
- no API outside the timer/data transaction layer may create a null-stop interval; and
- startup consistency checking reports/repairs only well-defined incomplete commits, never silently discards recorded time.

`ActiveTimerDao.createActiveIntervalAndTimer` inserts the sole open interval, inserts singleton ID `1`, and
touches the task in one Room transaction. `closeActiveIntervalAndClearTimer` validates the pointer, closes
the interval, releases `active_slot`, clears the singleton, and touches the task in one transaction. A
failed statement rolls back the whole change. Today/selection checks, exact pinned-zone boundary closure,
overlap validation, and clock-anomaly policy remain domain-service responsibilities; current schema 5
boundary closure does not split or create continuation rows.

Monotonic anchors are process-local and are not stored here. Persisting elapsed-realtime values across boots would be invalid.

## 7. Computed queries and models

Room projection `TaskListItemEntity` joins task/client and computes:

```text
completed total = SUM(max(stop_epoch_ms - start_epoch_ms, 0)) for completed intervals
```

The running contribution is added in the domain/UI layer from the active interval and the display time source. No total-duration column is stored, preventing cache drift. `TaskWithOrderedIntervalsEntity` retrieves task/client metadata, including hardware/software-purchases text, and the optional interval ordered by `start_epoch_ms`, then ID.

## 8. Preferences DataStore schema

Preferences are version-tolerant typed values with safe defaults:

| Preference | Value/default |
| --- | --- |
| `theme_mode` | `SYSTEM` by default; explicit `LIGHT` and `DARK` overrides |
| `time_zone_mode` | `DEVICE` by default |
| `manual_zone_id` | valid ZoneId string; ignored in device mode |
| `default_export_destination` | `CSV` by default; valid values are `CSV`, `XLSX`, and `GOOGLE_SHEETS` |
| `selected_series_id` | nullable UUID hint |
| `selected_task_id` | nullable UUID hint |
| `connected_spreadsheet_id` | nullable validated ID |
| `connected_spreadsheet_title` | nullable last validated title |
| `connected_google_account_hint` | nullable non-secret display identifier if supported/necessary |
| `last_export_*` | destination, work date, attempt instant, outcome, and optional safe error category |

Spreadsheet metadata is cleared on Disconnect. XLSX stores no document connection metadata or
persistable URI permission because every export creates a new user-selected file. Account tokens,
refresh tokens, passwords, service-account data, and OAuth client secrets are prohibited.

Milestone 8 persists no CSV document URI, payload, provider detail, task row, or exception text.
Every CSV attempt uses a fresh create-document flow; its safe last-attempt metadata is presentation
history only and never becomes task or timer truth.

Optional Milestone E retains the reviewed plan for Keystore-backed encryption of sensitive
DataStore-held identifiers/metadata. It is not part of the current production sequence and must
preserve the existing typed, version-tolerant preference contract if separately authorized.

## 9. Selection and rollover data rules

- `selected_series_id` expresses the preferred logical task across days.
- `selected_task_id` is a fast hint for the last concrete daily copy. Room validity always wins.
- Deleting the currently selected daily task clears both its task hint and preferred-series hint so rollover cannot recreate a task the user just deleted. Deleting a non-selected daily task leaves selection unchanged.
- Schema 5 no longer performs automatic local-date rollover. Date or ZoneId reconciliation clears a
  stale timing selection and never creates a task. Repeated Start creates a same-day task only when
  the selected task already owns a completed interval; its new ID retains lineage and metadata.
- Date-selector navigation alone never creates missing series copies. Historical tasks and their
  stored zones remain unchanged.

## 10. Referential and deletion behavior

| Action | Result |
| --- | --- |
| Archive client | client retained; hidden from new assignment; tasks untouched |
| Rename client | retained client name changes; referencing views/exports show current retained name |
| Delete daily task | confirmation; block if active; cascade its intervals; retain client and sibling dates |
| Delete interval | block active interval; delete only interval; totals derive automatically |
| Disconnect Google | clear local connection metadata; external sheet untouched |
| Export XLSX | use a transient create-document URI for one write; retain no URI or workbook state |
| Delete app/clear storage | outside durability guarantee; local database/preferences can be lost |

## 11. Migration policy

Database version 1 is never “throwaway.” The implementation milestone must:

1. configure Room schema JSON export and commit the version 1 schema;
2. test creation, foreign keys, unique rollover, client active-name uniqueness, cascade/restrict behavior, and singleton timer behavior;
3. preserve custom indexes/triggers in schema creation and migration tests if used;
4. add explicit migrations for every future version; and
5. omit `fallbackToDestructiveMigration` from release construction.

Migration tests populate clients, archived clients, multiple task series/dates, completed/open intervals, and preferences-relevant identifiers before migrating, then verify data and invariants afterward.

Implemented schema details:

- production database name: `worqorder.db`;
- Room annotation: `version = 5`, `exportSchema = true`;
- committed schemas:
  `app/schemas/worq.order.data.local.WorqOrderDatabase/1.json`,
  `app/schemas/worq.order.data.local.WorqOrderDatabase/2.json`,
  `app/schemas/worq.order.data.local.WorqOrderDatabase/3.json`,
  `app/schemas/worq.order.data.local.WorqOrderDatabase/4.json`, and
  `app/schemas/worq.order.data.local.WorqOrderDatabase/5.json`;
- production construction uses `Room.databaseBuilder` without startup deletion, seeding, or destructive fallback; and
- there is no `0 -> 1` migration because version 1 is the first schema. Production construction
  registers the explicit `MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_3_4`, and `MIGRATION_4_5`; every later change must add another
  explicit forward migration and instrumentation test.

Implemented first schema evolution:

- Milestone 6 incremented Room to version 2 and added
  `daily_tasks.hardware_software_purchases TEXT NOT NULL DEFAULT ''`;
- existing task IDs, series IDs, client relationships, descriptions, dates, zones, timestamps,
  intervals, and active-timer state remain unchanged;
- KSP exports
  `app/schemas/worq.order.data.local.WorqOrderDatabase/2.json`; and
- a populated `1 -> 2` migration instrumentation test verifies the empty default for existing
  tasks and preservation of every pre-existing relationship and timer invariant.

If optional Milestone E is separately authorized, its plaintext-to-encrypted-storage transition
is a separate non-destructive storage migration even when no Room entity version changes. Its
tests must populate the previous production database/preferences, interrupt every durable
transition phase, reopen after process death/reboot, and prove all rows, relationships,
active-timer state, settings, and selection hints survive. Missing/invalidated keys or corrupt
ciphertext must never trigger destructive Room creation.

## 12. Deliberate non-models

- No `start1/stop1/...` columns.
- No stored ticking stopwatch or denormalized task-total column.
- No remote-ID columns for synchronization.
- No Google row IDs or export flags are required because Google export replaces a marked date tab from an authoritative snapshot.
- No XLSX entities are needed; XLSX remains a transient one-way document projection. There are
  also no attachments, user table, Firebase IDs, or server queues.

## 13. v0.2.0 schema evolution

Milestone 19 advances Room from version 2 to version 3 through an explicit, populated,
non-destructive migration and commits the resulting schema JSON. Existing clients, tasks,
intervals, active-timer state, IDs, dates, zones, and timestamps must remain intact.

### `employees`

| Column | Type | Rules |
| --- | --- | --- |
| `id` | TEXT PK | stable UUID, immutable |
| `name` | TEXT | trimmed/collapsed display name, 1–100 characters |
| `canonical_name` | TEXT | locale-independent duplicate key |
| `active_name_key` | TEXT nullable UNIQUE | canonical name while active; null while archived |
| `is_active` | INTEGER | selectable-state flag |
| `created_at_epoch_ms` | INTEGER | UTC epoch millis |
| `updated_at_epoch_ms` | INTEGER | UTC epoch millis |
| `archived_at_epoch_ms` | INTEGER nullable | set on removal, cleared on restore |

Employee is the internal persistence name; every user-facing label calls this directory
**Consultant**. Normalization, active-name uniqueness, A–Z ordering, archive, and restore follow the
same durable principles as clients. An employee directory row is not the historical display
authority for an already-created task.

### v0.2 additions to `daily_tasks`

| Column | Type | Rules / migration default |
| --- | --- | --- |
| `employee_id` | TEXT nullable FK | selected active consultant; null for migrated `0.1.0` tasks |
| `employee_name_snapshot` | TEXT | immutable-at-assignment display/export value; empty for migrated tasks |
| `work_type` | TEXT | `ON_SITE`, `IN_OFFICE`, or migrated `UNSPECIFIED` |
| `billing_status` | TEXT nullable | `BILLABLE`, `DO_NOT_BILL`, `DO_NOT_CHARGE`, or null for every migrated task |
| `mileage` | TEXT nullable | validated normalized non-negative decimal text; null means not entered |

New task creation requires an active selected employee, defaults Work Type to `ON_SITE`, and
defaults Billing Status to `BILLABLE`. Editing a daily task may correct its employee, Work Type,
Billing Status, or Mileage. Employee rename/archive does
not rewrite `employee_name_snapshot` on prior tasks; future tasks use the employee's then-current
name. This preserves historical exports while retaining the directory relationship where it is
still valid. Removing an employee is archive/deactivation, not destructive deletion.

Mileage is stored as canonical plain decimal text rather than floating point. Accept digits and
at most one decimal separator in the UI, normalize the stored/exported value without locale-based
grouping, reject negative, exponent, NaN, infinity, and malformed values, and define a reasonable
at most 9 integer digits and 3 meaningful fractional digits; input is bounded to 32 characters
before parsing. Strip redundant leading/trailing zeroes and store zero as `0`. Existing tasks migrate with blank
Mileage, `UNSPECIFIED` Work Type, and null Billing Status rather than inventing historical facts.

### Derived Billing Minutes

Billing Minutes is exposed as task information but is not a Room column. It is derived from the
exact non-negative sum of the task's intervals so it cannot become stale:

```text
total == 0 ms  -> 0
total > 0 ms   -> ceil(total / 900_000 ms) * 15
```

Thus any positive duration below or equal to 15 minutes bills 15, and every larger total rounds
up to the next integer multiple of 15. The value may include the active contribution in live UI;
exports continue to require Stop and therefore derive it only from completed authoritative
intervals.

Approved examples: `12:32` total yields `15`; `13:11 + 3:03:45 + 1:16:00` totals `4:32:56`
(272 minutes 56 seconds) and yields `285` Billing Minutes.

### v0.2 Preferences DataStore additions

| Preference | Value/default |
| --- | --- |
| `selected_employee_id` | nullable; no selected employee on a migrated install until chosen |
| `landscape_handedness` | `RIGHT_HANDED`; alternate `LEFT_HANDED` |
| `automatic_google_export_enabled` | `false` |
| `automatic_google_target_epoch_day` | nullable captured local work date |
| `automatic_google_target_zone_id` | nullable canonical geographical ZoneId captured with target |
| `automatic_google_connection_fingerprint` | nullable non-secret association used to reject stale work |
| `automatic_google_pending_reason` | nullable typed non-sensitive state |

Unknown/corrupt values fall back safely. DataStore remains non-authoritative for task/timer data.
Archiving the selected employee clears `selected_employee_id` before the coordinated operation is
reported complete from the application's perspective. Room and DataStore cannot share one physical
transaction, so UI state also treats a missing/archived selected ID as unselected and startup/
observation reconciliation removes any stale preference. None of these operations rewrites an
existing daily task.
The automatic-export target must survive process death/reboot. It represents the oldest unresolved
date and is never replaced by a newer target. Confirmed success advances it by one date when later
dates are already due; explicit disable/destination-change/disconnect/sign-out cancels it. Corrupt
or incomplete target tuples fail closed and are reconstructed only through schedule reconciliation,
never by changing Room data.

### v0.2 relationships and copy rules

```text
Employee 1 ---- * DailyTask (nullable relationship for migrated history)
                    + immutable employee_name_snapshot
Client   1 ---- * DailyTask 1 ---- * WorkInterval
```

Released `0.2.0` daily rollover and midnight continuation copied the source daily task's employee
ID/name snapshot, Work Type, Billing Status (including null), Mileage, client, description, and
purchases into a new daily copy. Current `0.3.0` behavior no longer performs either copy: date or
midnight handling closes/clears the existing task's sole interval and leaves historical tasks
unchanged.

The explicit `MIGRATION_2_3` remains tested from a populated version-2 database containing
active/archived clients, multiple tasks and intervals, and an open active timer. Destructive
migration remains prohibited. Milestone 27 adds `MIGRATION_3_4`, which adds only the nullable
`billing_status` column. Populated version-1, version-2, and version-3 upgrade paths preserve their
entire task/interval/active-timer graph and leave Billing Status null.

## 14. Current v0.3.0 Room schema 5

Schema 5 is a mandatory non-destructive upgrade from the released schema 4. Room remains the
authoritative store. It changes task/interval cardinality and lineage indexes without flattening
interval timestamps into `daily_tasks`.

### Task lineage and interval cardinality

- `daily_tasks.series_id` remains a stable internal lineage value but is no longer unique with
  work date and ZoneId. Drop `index_daily_tasks_series_date_zone` and replace it with a non-unique
  lookup index over the same columns. No automatic copy operation may depend on it.
- `work_intervals` retains its stable interval ID, task foreign key, UTC start/nullable stop,
  active slot, manual-edit flag, and creation/update timestamps.
- Remove `ordinal`; ordering is task-level rather than interval-within-task ordering.
- Add a unique index on `work_intervals.task_id`. A task therefore owns zero or one interval.
- Retain the globally unique nullable `active_slot`, composite interval/task identity, singleton
  `active_timer`, task cascade, and client/Consultant restrictions.

### Deterministic 4-to-5 conversion

The migration must run as one Room/SQLite migration transaction and must be safe for a populated
released database:

1. Order every task's intervals by start instant, prior ordinal, then interval ID.
2. Leave zero-interval and one-interval tasks unchanged except for the rebuilt table shape.
3. For a multi-interval task, retain the original task ID and earliest interval relationship.
4. For each later interval, insert a copied task with a deterministic collision-resistant UUID
   derived from the source task and interval IDs. Retain the source `series_id`, client,
   Consultant relationship/snapshot, Description, Expense, Work type, Billing Status, Mileage,
   work date, and ZoneId. Use deterministic timestamps derived from preserved source/interval
   timestamps so retry or test reconstruction cannot create different rows.
5. Move each later interval to its copied task without changing interval ID, UTC endpoints,
   manual-edit flag, or interval timestamps.
6. If `active_timer` points to a moved interval, update its `task_id` in the same transaction while
   preserving interval ID, boundary ZoneId, and timer timestamps.
7. Rebuild constraints/indexes, enable foreign-key checking, and fail the upgrade rather than
   deleting, merging, or reseeding data if any invariant cannot be proven.

Migration tests must compare pre/post counts and field values, cover zero/one/many intervals,
equal-start ordering, an open non-first interval, active-timer pointer integrity, selected-task
reconciliation after open-interval movement, idempotent reopen, cascade/restrict behavior, and the
committed version-5 schema JSON. Destructive fallback remains prohibited.

### New writes after migration

- New user-created tasks receive a new task and lineage ID.
- Starting an untimed task inserts its sole interval and singleton active timer transactionally.
- Starting a task whose completed interval remains present creates a copied task with a new task
  ID, preserves its lineage and user metadata, selects it, and inserts the new open interval plus
  active-timer row in one transaction.
- Manual Add Interval is allowed only when the task has none. Edit/Delete operates only on the
  sole completed interval; running edits remain blocked.
- Date or ZoneId changes never create task records. Selection hints may be retained for migration
  compatibility, but rollover lookup/creation is removed and stale timing selection is cleared.

### Milestone 30C migration evidence

`Schema5MigrationCoreTest` exercises a populated schema-4 file with archived Client/Consultant
directory rows, nullable task metadata, zero-/one-/many-interval tasks, and an active non-first
interval. It verifies deterministic copied task IDs, preserved IDs/endpoints/metadata and archived
foreign-key links, active-pointer repointing, foreign-key integrity, reopen persistence, and removal
of the legacy `ordinal` column. `WorqOrderDatabaseTest` additionally verifies packaged schema 5,
the structural one-interval guard, same-series lineage rows, and populated version 1/2-to-5 paths.
