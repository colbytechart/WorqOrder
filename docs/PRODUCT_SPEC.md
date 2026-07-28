# WorqOrder Product Specification

Status: planning baseline  
Product: WorqOrder for Android  
Minimum Android version: API 26  
Authoritative data store: local Room database

## 1. Product intent

WorqOrder is a fast, offline-first Android application for recording daily client tasks and their work intervals in real time. A user selects a daily task, starts and stops one global timer, reviews or corrects historical intervals, and exports the displayed date as CSV, as XLSX, or into one connected Google spreadsheet.

Task tracking, editing, client management, timer recovery, and local export-row generation must
work without a network, Google account, Firebase, a server, or a custom backend. Room is
authoritative. Exports are copies and never feed data back into Room.

## 2. Scope

### Included

- Native Kotlin Android application using Compose and Material 3.
- Daily task creation, selection, metadata editing, confirmation-based deletion, and interval editing.
- One globally active timer with activity/process recovery and local-midnight splitting.
- Active and archived client management.
- Device or manually chosen geographical time zone.
- Explicit Light and Dark themes, applied immediately.
- UTF-8 CSV and focused XLSX export through Android scoped/user-mediated storage flows.
- One connected Google spreadsheet, with one application-owned worksheet tab per date.
- Local settings and meaningful export status/error presentation.
- Keystore-backed encryption of sensitive app-private Room/DataStore content, without requiring
  the user to log in or complete a biometric prompt.
- Free and open-source distribution under GPLv3, with no paid service required for any supported
  workflow.

### Excluded

- Import from CSV, XLSX, or Google Sheets.
- Google Sheets synchronization, conflict merging, or cross-device task synchronization.
- Firebase, a custom backend, web application, web wrapper, Flutter, or React Native.
- Concurrent timers, background location, billing, required accounts for local use, or team
  collaboration.
- Biometric, device-credential, PIN, or account-gated app access in the required production
  sequence. That capability is reserved for an optional post-project milestone requiring separate
  owner authorization.
- Google Play distribution, Google Play App Signing, paid Google API quota, a billing account,
  Google Workspace/Cloud organization membership, or a custom-domain requirement.
- A foreground service in the initial MVP.
- A promise that app-private data survives uninstall or clearing application storage.

## 3. Core vocabulary

- **Displayed date:** the date selected on the main screen. It filters the task list and is always the export date.
- **Today:** `Clock.now()` interpreted in the currently effective application `ZoneId`.
- **Daily task:** one task record for one work date. Corresponding records on other dates share a task-series ID.
- **Hardware / Software Purchases:** optional free-form task metadata for listing hardware and software purchases associated with a daily task.
- **Interval:** a start instant and optional stop instant belonging to one daily task.
- **Open interval:** the sole interval with no stop instant, referenced by the singleton active-timer record.
- **Connected spreadsheet:** the one validated Google spreadsheet ID stored in settings. A connection is not a sync relationship.

## 4. Main screen

The initial route is the main screen. It has a top app bar, timer, date selector, task list, add-task action, and date-specific export action.

### Top app bar

- Show the title **WorqOrder**.
- Put a Settings icon button at the upper right with a content description and adequate touch target.
- A hamburger icon is not used unless a navigation drawer is later specified.

### Timer area

- Show `HH:MM:SS.mmm`. Hours are accumulated hours and can exceed 23; milliseconds always have three digits.
- If no task for the displayed date is selected, show zero and disable Start.
- For a selected task, show completed interval duration plus the current open interval contribution, if that task is running.
- Refresh may be coarser than 1 ms. Every rendered value is recalculated from timestamp/monotonic anchors; display ticks never cause database writes.
- Use one control labeled **Start** or **Stop** according to state.
- Start is enabled only when the displayed date is today, the selected task belongs to today, validation passes, and no interval is globally open.
- Stop targets the globally active interval, even if UI state has been recreated.

### Date selector

- Show a readable date label, previous-day button, next-day button, and calendar button.
- Default to today in the effective application zone.
- Only show tasks whose stored work date equals the displayed date.
- Historical and future dates support task creation and manual editing; live Start remains disabled.
- Date navigation remains available while timing so another date can be viewed. Export remains
  disabled until Stop. The active task remains the global timing selection even when its row is
  not on the displayed date; show a persistent running-task identity/date near the timer. Disable
  selection of every other row, and allow Stop from any displayed date. Creating or editing a
  non-running task must not change the timing selection or active interval.

### Task list

Each row shows:

- client name;
- short task description;
- total duration formatted with accumulated hours;
- selected state;
- running state when applicable; and
- a vertical three-dot overflow menu.

If the same series/date has retained assignments from different ZoneIds, show a zone qualifier so the rows are distinguishable; exports include both and identify each stored zone.

Tap selects a row. There is one selected task/series at a time. While an interval is open, the running row stays selected; other rows, task edits, task deletion, and material edits to the running task are disabled. Overflow actions are **Edit task** and **Delete task**. Delete requires confirmation.

Empty dates show an explanatory empty state and the add-task action. Large lists are lazy, keyboard/accessibility navigable, and do not rely on color alone for selected or running state.

### Add task

A plus/FAB opens a task-creation screen or accessible dialog containing:

- a client selector of alphabetically sorted active clients;
- an inline **Add client** action using the same validation/repository path as Settings;
- a required short description, trimmed, maximum 400 characters;
- an optional text field labeled **Hardware / Software Purchases**, trimmed when nonblank, maximum 400 characters; and
- **Create** and **Cancel** actions.

Create validates and writes one daily task. If its work date is today, it becomes selected. Cancel writes nothing. Creating on a historical or future displayed date returns to that date but does not change the live-timing selection merely because it was created.

### Export

The bottom action always identifies both date and destination, for example **Export Jul 22 as CSV**, **Export Jul 22 as XLSX**, or **Submit Jul 22 to Google Sheets**. It exports the displayed date, not implicitly “today.” Google Sheets without authorization/a validated spreadsheet navigates to and focuses the relevant settings section. CSV and XLSX launch their respective create-document pickers directly.

Export is disabled whenever the global timer is running, regardless of displayed date or selected
destination. The disabled Main action reads **Stop Timer to Export**. After Stop commits the final
UTC boundary and clears active state, CSV, XLSX, or Google Sheets can export the authoritative
completed data. No destination exposes an incomplete interval with a blank Stop Local.

## 5. Clients

Settings shows active clients sorted alphabetically and supports add, rename, and archive. An optional Archived section supports restore.

Client names:

- are trimmed;
- collapse repeated internal whitespace for duplicate comparison;
- cannot be blank;
- have a 100-character maximum;
- are compared case-insensitively using a locale-independent canonical key; and
- cannot duplicate another active canonical name.

Archive, not delete, removes a client from new-task selectors. The retained client row continues to satisfy historical task relationships and exports. Restore fails with an actionable message if its canonical name conflicts with an active client. Renaming a retained client updates the name shown by all tasks that reference it; see the explicit assumption in `DECISIONS.md`.

## 6. Tasks and interval editing

A task-edit screen changes the daily task's client, short description, and **Hardware / Software Purchases** text and lists intervals chronologically. It supports manually adding an interval, editing a completed interval's start/stop, and deleting a completed interval through the same validation path.

Routine interval cards show the task-zone local start and stop clock times without an appended UTC
offset. The interval editor still exposes earlier/later occurrence choices when a fall-back overlap
makes the offset materially necessary. Successfully saving task metadata returns to the main
screen; validation or persistence failure keeps the editor open.

Validation rejects:

- blank or over-400-character short descriptions;
- over-400-character **Hardware / Software Purchases** text; this optional field may be blank;
- a start at or after stop;
- overlap with another interval for that task;
- an interval outside the start/end instants of the task's stored work date and zone;
- a second open interval;
- a cross-midnight manual interval;
- manual edits to the globally running interval; and
- deletion or material editing of the running task.

For DST overlaps, the editor must show enough offset/occurrence information to distinguish repeated local times. Nonexistent local times in a DST gap are rejected with guidance. Deleting a task removes its intervals in one Room transaction, retains its client and task-series siblings on other dates, and never changes prior external exports.

## 7. Settings

Settings contains:

- **Clients:** active list with add/rename/archive and optional archived list with restore. Client Management is the first normal Settings item so the most frequent local-data administration workflow is immediately reachable.
- **Appearance:** explicit System, Light, and Dark choices, with System as the first-launch default. System follows the device appearance while Light and Dark remain enabled as immediately selectable overrides. Changes are persisted in Preferences DataStore and apply immediately without recreating navigation or timer state.
- **Time zone:** device-zone mode or manual geographical `ZoneId`, searchable/navigable selector, and effective ID display. Device mode is the first-launch default. Mode/zone changes are blocked during timing. Historical stored dates and zone IDs never move.
- **Export Destination:** CSV, XLSX, or Google Sheets, with CSV as the first-launch and corrupt-value
  fallback after the XLSX milestone.
- **Google Sheets:** show the complete **Google Sheets Connection** section only while Google Sheets
  is the selected export destination. It contains authorization/sign-in state, sign-out,
  spreadsheet URL/ID input, Validate and Connect, connected title/ID, and Disconnect. Once a
  spreadsheet is connected, hide the URL/ID input and Validate and Connect action until it is
  disconnected. When the user changes Export Destination to Google Sheets, automatically scroll
  the Settings list to reveal this section.

Disconnecting a spreadsheet clears its ID/title association but does not delete the spreadsheet or
revoke unrelated account access. Sign-out clears the app's Google identity/authorization session
and connected-spreadsheet metadata through supported Google APIs, marks Google export unavailable,
and does not alter Room.

## 8. Persistence and recovery

- Room is created on first need and reopened thereafter; it is never cleared or reseeded on normal startup.
- Clients, daily tasks, intervals, and the singleton active-timer pointer survive supported lifecycle/process/device restart events.
- Preferences DataStore holds preferences and selection hints, not task records or raw OAuth/access/refresh tokens.
- XLSX exports retain no document URI or workbook connection metadata. Every XLSX export uses a
  fresh user-mediated create-document result.
- Versioned, non-destructive migrations and Room schema exports begin at database version 1.
- Selection persists as a preferred task-series ID plus the last concrete daily-task ID and the date/zone context in which that task was selected. Invalid references are repaired safely. The displayed date is not persisted; normal startup displays today.
- When the effective local date or geographical zone changes, an eligible timing selection lazily finds or creates its new daily task using `(series ID, work date, assignment ZoneId)` uniqueness, copies the prior daily task's current client, short description, and hardware/software-purchases text, and becomes selected. A task intentionally selected outside its own stored date/zone context remains view-only instead of being rolled. The zone context prevents a task assigned under a different zone from being silently repurposed.
- The production encryption milestone protects sensitive Room and DataStore content at rest with
  Android Keystore-backed key material and a non-destructive migration. Database auxiliary files,
  backup rules, caches, and diagnostics are part of the protected-data audit.
- Encryption failure is explicit and fail-closed. The app must never silently clear/reseed Room
  because a key is missing, invalidated, or incompatible.
- No user login, biometric prompt, device-credential prompt, or app PIN is required by the
  production sequence. Plaintext is necessarily present transiently in process memory while the
  unlocked app displays, edits, or exports it.

## 9. Export behavior summary

- CSV, XLSX, and Google Sheets use the same row model and stable column order defined in
  `EXPORT_SPEC.md`.
- The shared visible schema has exactly nine columns: Work Date, Client Name, Description,
  Hardware / Software Purchases, Interval Number, Start Local, Stop Local, Interval Duration
  Formatted, and Task Total Duration Formatted. Destination adapters do not independently select
  or format fields.
- CSV is UTF-8, RFC-style quoted, repeatable, and one row per interval; zero-interval tasks still emit one row.
- Every XLSX export creates one new standards-compliant, unencrypted OOXML workbook through
  `ACTION_CREATE_DOCUMENT`. It contains one `WorqOrder_YYYY-MM-DD` worksheet for the displayed
  date and never opens, reads, or updates an existing workbook.
- Exactly one Google spreadsheet can be connected. Its per-date tab is
  `WorqOrder_YYYY-MM-DD`.
- In Google Sheets, a marked WorqOrder tab is replaced from the current authoritative date
  snapshot on re-export. An unmarked same-name tab is a conflict and is not overwritten. Repeated
  XLSX exports intentionally create independent files, each containing one complete snapshot.
- No export modifies or deletes local data. Failures and cancellation do not claim success.
- App-private data is encrypted at rest after the production hardening milestone. User-selected
  CSV/XLSX files and readable Google Sheets cells are intentionally outside that boundary and are
  not end-to-end encrypted by WorqOrder.
- Persistent versus one-off XLSX choice and optional automatic local-midnight export to
  Google/future persistent XLSX are deferred to optional Milestone 18 and require separate owner
  authorization.

## 10. Concept-image review

The three supplied images are visual concepts, not pixel-perfect requirements. Their dark, high-contrast, vertically arranged direction is compatible with Compose/Material 3, but the following differences must be resolved in favor of the written product rules:

| Concept detail | Conflict or gap | Resolution |
| --- | --- | --- |
| Title reads “task time” | Product name is WorqOrder. | Use **WorqOrder**. |
| Hamburger menu | Requirement calls for a Settings icon, and no drawer content is specified. | Use a settings/gear icon that navigates to Settings. |
| Timer shows `HH:MM:SS` | Milliseconds are required. | Show `HH:MM:SS.mmm`, responsively scaled. |
| Date controls show arrows/calendar plus `+` and `-`, with no visible date | The date must be labeled; add task is separate; minus is not a deletion affordance for the whole list. | Add a prominent date label, keep previous/next/calendar, use one labeled/accessible FAB for Add Task, and remove the ambiguous minus. |
| Rows emphasize client and clock ranges | Rows must also show description, total duration, selected/running state. | Use client + description as primary metadata and total duration as trailing content; optionally show a time range as secondary detail. |
| Bottom button says “submit” | Destination and displayed date are ambiguous. | Use the date/destination-specific export labels. |
| New-task dialog says “ok,” lacks Add Client, and has no purchases field | Required actions and validation are less clear. | Label **Create**/**Cancel**, add inline **Add client**, and add **Hardware / Software Purchases** as a second text field. Show a character count for each text field with a 400-character limit. |
| Settings uses a fixed-offset “Eastern Time” label | Fixed offsets fail DST and the product requires geographical IDs. | Show `America/New_York` (with a friendly label optionally), never store only `UTC-05:00`. |
| “Dark mode” toggle only | System, Light, and Dark choices are required. | Use a three-choice radio group. System is selected by default and follows the device; Light and Dark remain enabled as explicit overrides. |
| Export default appears as “Connected Google Sheet” | Connection state and default destination are distinct. | Separate the CSV/XLSX/Google Sheets destination choice from account/spreadsheet connection status and controls. |
| Client plus/minus and selected row | Minus is ambiguous and rename/archive/restore are absent. | Give each client an overflow/action menu with Rename and Archive; use a distinct Add button and an Archived section. |

Additional visual requirements for implementation are Material 3 semantics, 48 dp touch targets, scalable text, contrast in both themes, screen-reader labels, and layouts that remain usable under font scaling and small screens. Exact colors and typography remain a design choice for a later UI milestone.

## 11. Requirement reconciliations and risks

1. **Google re-export:** the early append-and-sort wording conflicts with the later marker-and-replace rule. Marker-validated replacement is authoritative because it is idempotent and reflects local edits/deletions. Rows are generated in stable sorted order.
2. **CSV folder versus create-document:** use `ACTION_CREATE_DOCUMENT` for every export. The system picker/user owns the final location, so the app may suggest but cannot force or silently create `Downloads/WorqOrder`. No broad storage permissions or directory-tree grant are used.
3. **Google authorization scope:** the user still pastes a spreadsheet URL/ID, then confirms that
   exact file through the official Android Google Picker resource-authorization flow. Request only
   the non-sensitive per-file `drive.file` scope and verify the returned picked ID before validating
   edit capability. Do not fall back to the sensitive all-spreadsheets scope. If Google's
   no-cost `drive.file`/Picker workflow stops supporting the product, pause Google export changes
   for a new owner decision and retain CSV.
4. **Device zone changes during timing:** the zone captured at Start is pinned for that active session's splitting. The new effective device zone applies after Stop. This prevents a device-setting change from rewriting an interval's date semantics mid-run.
5. **Manual wall-clock changes:** while the process retains a valid elapsed-realtime anchor, live
   display, midnight normalization, and the persisted UTC Stop endpoint use the same monotonic
   projection so Stop does not jump. After process death/reboot, wall-clock reconstruction is the
   only available source; impossible negative recovery remains a surfaced error.

## 12. Product completion criteria

The production project is complete only when all required acceptance tests in
`ACCEPTANCE_TESTS.md` pass on the supported
API range, release migrations are non-destructive, offline core behavior is proven, exported
schemas are stable across all three destinations, app-private at-rest encryption is proven, no
prohibited permissions/credentials/dependencies are present, and the Google setup guide has been
exercised with the debug signing fingerprint. The permanent direct-release fingerprint and release
OAuth client are deliberately deferred to Milestone 17. Google Play signing is not part of
completion. Optional Milestone 18 app-access gating is not required for this completion definition.
