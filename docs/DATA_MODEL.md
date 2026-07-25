# WorqOrder Data Model

## 1. Storage conventions

- Stable IDs are random UUID strings generated in the application before insert. They are never reused or derived from mutable text.
- UTC instants are stored as signed 64-bit epoch milliseconds. Millisecond precision matches the UI/export contract and Android clocks.
- Work dates are stored independently as `LocalDate.toEpochDay()` signed 64-bit values.
- Zone IDs are IANA/geographical IDs accepted by `ZoneId.of`, for example `America/New_York`; fixed-offset display labels are not stored in place of them.
- Booleans are SQLite integers through Room.
- Every mutable entity has `createdAtEpochMs` and `updatedAtEpochMs`; updates use the injected UTC clock.
- Entities are persistence details. Repositories map them to domain models and validate strings/time values before writes.
- Room version 1 stores these primitive values directly and requires no type converters. Domain mappings reconstruct `Instant`, `LocalDate`, and `ZoneId` deterministically.

## 2. Entity relationship overview

```text
Client 1 ──────── * DailyTask 1 ──────── * WorkInterval
                         │                         │
                         └── seriesId              └── 0..1 ActiveTimer (singleton pointer)

Preferences DataStore: theme, zone mode, export default, selection hints,
connected spreadsheet metadata, last export outcome
```

Daily tasks in a series are intentionally not parented by a separate series table in version 1. The stable `seriesId` plus work date and assignment zone identifies a rollover copy, and each daily copy carries the metadata used for the next rollover.

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

- Unique `(series_id, work_date_epoch_day, zone_id)` prevents duplicate rollover copies in one date-rule context. A same-series/same-date task assigned under a different geographical zone remains distinct rather than having its historical zone silently changed.
- Index `(work_date_epoch_day)` supports the main list.
- Index `(client_id)` supports joins and client history.
- Short-description and purchases-text validation is primarily a domain constraint; migrations may add compatible SQLite checks when Room schema support is proven.

Changing client, short description, or hardware/software-purchases text changes only this daily task. A later rollover copies the changed values. Deleting a daily task cascades to its intervals, does not affect clients, and does not affect another row with the same series ID.

The `hardware_software_purchases` column is introduced by the planned version-2 migration in
Milestone 6. It is non-null with `DEFAULT ''` so every version-1 daily task migrates without
inventing purchase data. The 400-character limits are enforced through the shared task-metadata
validator rather than a destructive table replacement.

## 5. `work_intervals`

| Column | Type | Rules |
| --- | --- | --- |
| `id` | TEXT PK | stable interval UUID |
| `task_id` | TEXT FK | references `daily_tasks.id`, `ON DELETE CASCADE` |
| `ordinal` | INTEGER | positive sequence within the task |
| `start_epoch_ms` | INTEGER | UTC start instant |
| `stop_epoch_ms` | INTEGER nullable | null only for the global open interval |
| `active_slot` | INTEGER nullable UNIQUE | null when completed; fixed value `1` for the sole open interval |
| `was_manually_edited` | INTEGER | true after a manual boundary edit; manual creation also true |
| `created_at_epoch_ms` | INTEGER | UTC epoch millis |
| `updated_at_epoch_ms` | INTEGER | UTC epoch millis |

Constraints/indexes:

- Unique `(task_id, ordinal)`.
- Index `(task_id, start_epoch_ms)` for chronological display/overlap checks.
- Unique nullable `active_slot` structurally prevents a second open interval: SQLite permits many nulls for completed rows but only one row containing `1`.
- Unique `(id, task_id)` supports the composite active-timer foreign key that proves the pointed interval belongs to the recorded task.
- Completed rows require `start_epoch_ms < stop_epoch_ms`.
- An interval must lie within `[taskDate.atStartOfDay(zone), nextDate.atStartOfDay(zone)]`; this zone-aware rule is enforced in the transaction service because SQLite cannot interpret `ZoneId` rules.
- Intervals for the same task cannot overlap. Validate against current rows inside the write transaction.
- Cross-midnight intervals are prohibited after normalization.

Ordinals are stable presentation/export numbers, not array indexes. Deleting an interval does not renumber surviving rows; a new ordinal is `max + 1`. Chronological UI ordering uses start instant, then ordinal/ID. This avoids export identities changing after edits.

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

`ActiveTimerDao.createActiveIntervalAndTimer` allocates the next ordinal, inserts the open interval, inserts singleton ID `1`, and touches the task in one Room transaction. `closeActiveIntervalAndClearTimer` validates the pointer, closes the interval, releases `active_slot`, clears the singleton, and touches the task in one transaction. A failed statement rolls back the whole change. Today/selection checks, midnight splitting, overlap validation, and clock-anomaly policy remain later domain-service responsibilities.

Monotonic anchors are process-local and are not stored here. Persisting elapsed-realtime values across boots would be invalid.

## 7. Computed queries and models

Room projection `TaskListItemEntity` joins task/client and computes:

```text
completed total = SUM(max(stop_epoch_ms - start_epoch_ms, 0)) for completed intervals
```

The running contribution is added in the domain/UI layer from the active interval and the display time source. No total-duration column is stored, preventing cache drift. `TaskWithOrderedIntervalsEntity` retrieves task/client metadata, including hardware/software-purchases text, and all intervals ordered by `start_epoch_ms`, then ordinal and ID.

## 8. Preferences DataStore schema

Preferences are version-tolerant typed values with safe defaults:

| Preference | Value/default |
| --- | --- |
| `theme_mode` | `DARK` by default; explicit `LIGHT` supported; optional `SYSTEM` |
| `time_zone_mode` | `DEVICE` by default |
| `manual_zone_id` | valid ZoneId string; ignored in device mode |
| `default_export_destination` | `CSV` by default |
| `selected_series_id` | nullable UUID hint |
| `selected_task_id` | nullable UUID hint |
| `connected_spreadsheet_id` | nullable validated ID |
| `connected_spreadsheet_title` | nullable last validated title |
| `connected_google_account_hint` | nullable non-secret display identifier if supported/necessary |
| `last_export_*` | destination, work date, attempt instant, outcome/error category and safe message |
| `csv_destination_uri` | nullable persistable tree/document hint only if the final CSV flow uses it |

Spreadsheet metadata is cleared on Disconnect. Account tokens, refresh tokens, passwords, service-account data, and OAuth client secrets are prohibited.

## 9. Selection and rollover data rules

- `selected_series_id` expresses the preferred logical task across days.
- `selected_task_id` is a fast hint for the last concrete daily copy. Room validity always wins.
- Deleting the currently selected daily task clears both its task hint and preferred-series hint so rollover cannot recreate a task the user just deleted. Deleting a non-selected daily task leaves selection unchanged.
- Actual local-date rollover uses `(series_id, todayEpochDay, effectiveZoneId)` find-or-create in one transaction. On unique-insert race, fetch the winner. If another same-series/same-date task exists under a different stored zone, retain it as a distinct assignment; do not rewrite it.
- Date-selector navigation alone does not create missing series copies. Creation occurs on an actual configured-date rollover or a Start operation that requires today's corresponding task.

## 10. Referential and deletion behavior

| Action | Result |
| --- | --- |
| Archive client | client retained; hidden from new assignment; tasks untouched |
| Rename client | retained client name changes; referencing views/exports show current retained name |
| Delete daily task | confirmation; block if active; cascade its intervals; retain client and sibling dates |
| Delete interval | block active interval; delete only interval; totals derive automatically |
| Disconnect Google | clear local connection metadata; external sheet untouched |
| Delete app/clear storage | outside durability guarantee; local database/preferences can be lost |

## 11. Migration policy

Database version 1 is never “throwaway.” The implementation milestone must:

1. configure Room schema JSON export and commit the version 1 schema;
2. test creation, foreign keys, unique rollover, client active-name uniqueness, cascade/restrict behavior, and singleton timer behavior;
3. preserve custom indexes/triggers in schema creation and migration tests if used;
4. add explicit migrations for every future version; and
5. omit `fallbackToDestructiveMigration` from release construction.

Migration tests populate clients, archived clients, multiple task series/dates, completed/open intervals, and preferences-relevant identifiers before migrating, then verify data and invariants afterward.

Implemented version-1 details:

- production database name: `worqorder.db`;
- Room annotation: `version = 1`, `exportSchema = true`;
- committed schema path: `app/schemas/worq.order.data.local.WorqOrderDatabase/1.json`;
- production construction uses `Room.databaseBuilder` without startup deletion, seeding, or destructive fallback; and
- there is no `0 -> 1` migration because version 1 is the first schema. The first schema change must add an explicit forward migration and migration instrumentation test.

Planned first schema evolution:

- Milestone 6 increments Room to version 2 and adds
  `daily_tasks.hardware_software_purchases TEXT NOT NULL DEFAULT ''`;
- existing task IDs, series IDs, client relationships, descriptions, dates, zones, timestamps,
  intervals, and active-timer state remain unchanged;
- the migration exports
  `app/schemas/worq.order.data.local.WorqOrderDatabase/2.json`; and
- a populated `1 -> 2` migration instrumentation test verifies the empty default for existing
  tasks and preservation of every pre-existing relationship and timer invariant.

## 12. Deliberate non-models

- No `start1/stop1/...` columns.
- No stored ticking stopwatch or denormalized task-total column.
- No remote-ID columns for synchronization.
- No Google row IDs or export flags are required because Google export replaces a marked date tab from an authoritative snapshot.
- No XLSX entities, attachments, user table, Firebase IDs, or server queues.
