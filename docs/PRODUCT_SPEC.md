# WorqOrder Product Specification

Status: released through `0.4.0`; `0.5.0` Tag changes in Section 17 are implemented on the
development branch, with Milestone 48 release audit and owner-run gates pending.
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
- One globally active timer with activity/process recovery and exact pinned-zone midnight closure
  without continuation records.
- Active and archived client management.
- Device geographical time zone in the production UI, with historical stored ZoneIds preserved.
  The existing manual-zone data/domain capability remains internal and is not exposed in `0.2.0`
  Settings.
- Explicit Light and Dark themes, applied immediately.
- UTF-8 CSV and focused XLSX export through Android scoped/user-mediated storage flows.
- One connected Google spreadsheet, with one application-owned worksheet tab per date.
- Local settings and meaningful export status/error presentation.
- Free and open-source distribution under GPLv3, with no paid service required for any supported
  workflow.

### Excluded

- Importing tasks or intervals from CSV, XLSX, or Google Sheets. Version `0.2.0` adds a narrowly
  scoped client-name-only CSV import; it is not task import or synchronization.
- Google Sheets synchronization, conflict merging, or cross-device task synchronization.
- Firebase, a custom backend, web application, web wrapper, Flutter, or React Native.
- Concurrent timers, background location, billing, required accounts for local use, or team
  collaboration.
- Biometric, device-credential, PIN, or account-gated app access in the required production
  sequence. That capability is reserved for an optional post-project milestone requiring separate
  owner authorization.
- Paid Google API quota, a billing account, Google Workspace/Cloud organization membership,
  custom-domain requirement, or marketplace dependency.
- A foreground service in the initial MVP.
- A promise that app-private data survives uninstall or clearing application storage.
- WorqOrder-managed encryption of app-private Room or DataStore files in the required production
  sequence. At-rest encryption and optional app-access/privacy controls are deferred to optional
  Milestone E and require separate owner authorization.

## 3. Core vocabulary

- **Displayed date:** the date selected on the main screen. It filters the task list and is always the export date.
- **Today:** `Clock.now()` interpreted in the currently effective application `ZoneId`.
- **Daily task:** one task record for one work date. Corresponding records on other dates share a task-series ID.
- **Hardware / Software Purchases:** optional free-form task metadata for listing hardware and software purchases associated with a daily task.
- **Consultant:** the user-facing name for an active Employee-directory choice used for new tasks;
  each daily task retains the
  employee name captured when assigned so later employee rename/archive cannot rewrite history.
- **Work Type:** task metadata with `On-Site` as the new-task default and `In-Office` as the other
  selectable value. Migrated tasks may remain `Unspecified` until edited.
- **Billing Status:** task metadata with exact choices `Billable`, `Do not bill`, and
  `Do not charge`. New tasks default to `Billable`; migrated tasks remain unassigned/blank until
  explicitly edited.
- **Mileage:** optional non-negative decimal task metadata stored without floating-point or
  locale-dependent conversion.
- **Billing Minutes:** derived task information: zero for no recorded time, otherwise the exact
  total interval duration rounded upward to the nearest 15-minute multiple.
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

- Show `HH:MM:SS`. Hours are accumulated hours and can exceed 23. Sub-second precision remains
  available to timer calculations and persisted interval boundaries but is intentionally omitted
  from user-visible duration text.
- If no task for the displayed date is selected, show zero and disable Start.
- For a selected task, show completed interval duration plus the current open interval contribution, if that task is running.
- Refresh may be coarser than 1 ms. Every rendered value is recalculated from timestamp/monotonic anchors; display ticks never cause database writes.
- Use one control labeled **Start** or **Stop** according to state.
- Start is enabled only when the displayed date is today, the selected task belongs to today, validation passes, and no interval is globally open.
- Stop targets the globally active interval, even if UI state has been recreated.
- Keep the timer card and complete date-selector bar pinned below the top app bar. Messages and task
  rows use an independent lower scrolling region so a long task list never moves the timer or date
  controls off screen.

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

Tasks are presented newest-created first, with a stable ID tie-breaker, so a newly created task
appears at the top without changing Room or export ordering. Empty dates show an explanatory empty
state and the add-task action. Large lists are lazy, expose a quiet visual scroll-position
indicator, remain keyboard/accessibility navigable, and do not rely on color alone for selected or
running state.

### Add task

A plus/FAB opens a task-creation screen or accessible dialog containing:

- a client selector of alphabetically sorted active clients;
- an inline **Add client** action using the same validation/repository path as Settings;
- a required composed Description: manual text plus ordered Description Tag snapshots may total at
  most 999 Unicode code points, and Tag-only Description is valid;
- an optional **Hardware / software purchases** manual field plus ordered purchase Tag snapshots,
  whose composed value may total at most 999 Unicode code points;
- an active **Consultant** selected in Settings and required for creation;
- a **Work Type:** radio group containing **On-Site** and **In-Office**, defaulting to On-Site;
- a **Billing Status** radio group containing **Billable**, **Do not bill**, and **Do not charge**,
  defaulting new tasks to Billable;
- an optional **Mileage** decimal field that uses the numeric-decimal keyboard and accepts only a
  validated non-negative decimal representation;
- optional **Notes**, limited to 999 Unicode code points; and
- **Create** and **Cancel** actions.

Create validates and writes one daily task with an immutable employee-name snapshot. If its work
date is today, it becomes selected. Cancel writes nothing. Creating on a historical or future
displayed date returns to that date but does not change the live-timing selection merely because
it was created.

After any positive recorded duration exists, task detail shows **Billing Minutes** as the exact
combined interval duration rounded upward to the nearest 15-minute multiple. With no recorded time
it is `0`; any positive total below 15 minutes is `15`. This value is derived, not a ticking or
independently persisted counter.

All free-text entry controls, including task Description, Hardware / Software Purchases, client,
and consultant names, request sentence capitalization from the Android keyboard. This is an input
method hint only; WorqOrder does not silently alter text the user has entered.

### Export

In portrait, the bottom action identifies both date and destination, for example **Export Jul 22
as CSV**, **Export Jul 22 as XLSX**, or **Submit Jul 22 to Google Sheets**. In landscape, Export
and Add task move into the top application bar so the independent task-list viewport is not
starved. The title remains left-aligned, the two generously sized actions are centered as a pair,
and Settings remains at the far right. The visible export label is shortened to **Export CSV**,
**Export XLSX**, or **Export Sheets**, while accessibility semantics retain the full displayed date
and destination. Every layout exports the displayed date, not implicitly “today.” Google Sheets
without authorization/a validated spreadsheet navigates to and focuses the relevant settings
section. CSV and XLSX launch their respective create-document pickers directly.

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

Version `0.2.0` adds **Import From CSV** to Client Management. The Android picker accepts CSV
only. Every nonblank cell is treated as a proposed client name. Import appends to the current list
and never overwrites it: active duplicates are skipped, archived canonical matches are restored,
in-file duplicates collapse, and the resulting active list is A–Z. Invalid, malformed, corrupt,
over-limit, or unreasonably large input fails atomically without a partial import or crash.
The selected document must have a `.csv` filename and a recognized CSV MIME type. Import accepts
strict UTF-8 (including an optional leading BOM), quoted commas and quotes, CR/LF record endings,
Unicode, and quoted multiline cells. It is bounded to 1 MiB, 10,000 records, 20,000 cells, and
1,024 UTF-16 code units in one raw cell; the existing normalized 100-code-point client-name limit
still applies. Cancellation changes nothing and shows no failure. A completed import reports added,
restored, and skipped counts.
Import results appear directly below **Import From CSV** and before **Active Clients**. Showing or
dismissing an import error returns the Client Management list to the top so the actionable message
cannot remain hidden above a long active or archived list.

### Consultants

Settings labels the internal Employee directory as **Consultant** and maintains active/archived
consultants with the same normalization, validation, ordering, rename, archive, and restore
principles as clients. Settings places standalone **Client Management** and **Consultant
Management** navigation rows first and second. The Consultant row opens a dedicated screen
containing Add Consultant plus the active and archived management lists. The current-selection
dropdown and missing-selection warning appear directly over the Settings background below those
rows, without a Consultant card or explanatory paragraph. A current
active consultant is selected for new tasks. Each task stores an
internal employee ID plus the consultant-name snapshot captured on assignment. Renaming or
archiving a directory entry never changes an earlier task's consultant name or export. New task
creation is blocked until an active consultant is selected; migrated `0.1.0` tasks remain blank until
explicitly edited.

## 6. Tasks and interval editing

A task-edit screen changes the daily task's consultant assignment, client, short description,
**Hardware / Software Purchases**, Work Type, Billing Status, and Mileage and shows its optional
Interval. It supports manually adding one interval when untimed, editing a completed interval's
start/stop, and deleting that completed interval through the same validation path.

Routine interval cards omit the individual Duration field and show **Start Time** and **Stop
Time** as task-zone local 12-hour `hh:mm a`, without seconds or an appended UTC offset. Task Total
remains above the interval list and the derived Billing Minutes counter is directly below it,
never repeated per interval. The interval editor still exposes earlier/later occurrence choices when a fall-back overlap
makes the offset materially necessary. Successfully saving task metadata returns to the main
screen; validation or persistence failure keeps the editor open.

Validation rejects:

- a blank composed Description or one exceeding 999 Unicode code points;
- a composed **Hardware / software purchases** value exceeding 999 Unicode code points; this
  optional field may be blank;
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

- **Client Management:** the first standalone Settings row opens active add/rename/archive,
  archived restore, and CSV import.
- **Consultant Management:** the second standalone Settings row opens add, rename, archive,
  archived-view, and restore behavior backed internally by Employee persistence.
- **Consultant selection:** `Choose a Consultant` and the missing-selection warning appear directly
  below the two management rows over the Settings background, without a surrounding card.
- **Appearance:** explicit System, Light, and Dark choices, with System as the first-launch default. System follows the device appearance while Light and Dark remain enabled as immediately selectable overrides. Changes are persisted in Preferences DataStore and apply immediately without recreating navigation or timer state.
- **Time zone:** no time-zone controls are exposed in Settings. Device-zone mode remains the
  first-launch and corrupt-value default. Existing time-zone implementation and persisted values
  remain intact for compatibility; historical stored dates and zone IDs never move.
- **Export Destination:** CSV, XLSX, or Google Sheets, with CSV as the first-launch and corrupt-value
  fallback after the XLSX milestone. When Google Sheets is selected, this card also contains the
  conditional Google sign-in and spreadsheet-connection controls followed by the **Auto Export**
  switch row.
- **Google Sheets:** show the complete **Google Sheets Connection** section only while Google Sheets
  is the selected export destination. It contains authorization/sign-in state, sign-out,
  spreadsheet URL/ID input, Validate and Connect, connected title/ID, and Disconnect. Once a
  spreadsheet is connected, hide the URL/ID input and Validate and Connect action until it is
  disconnected. When the user changes Export Destination to Google Sheets, automatically scroll
  the Settings list to reveal this section.
- **Landscape Orientation:** Right-handed and Left-handed radio choices. Right-handed is the
  first-install/corrupt-value fallback; portrait is unaffected.
- **About:** final content showing only `WorqOrder v0.2.0 - stable`, derived from build metadata,
  with no GitHub repository or release link.

When Google Sheets is selected, show an opt-in switch labeled exactly **Auto Export** at the bottom
of the **Export Destination** card, below the Google sign-in and Sheets connection options. Its
supporting description is exactly **Automatically export tasks at the end of each day.** It
defaults off, is completely hidden for CSV/XLSX, and cannot be enabled until
authorization, a valid spreadsheet connection, and the notification capability required for
blocked-work recovery exist. An enabled schedule
captures the intended effective-zone work date near 11:59 PM. Android may run it approximately or
shortly after midnight, but it must still export that captured date. Successful automatic export
shows no Main-screen success status and no success notification.

The approved design uses an inexact, unique WorkManager one-time request recalculated for each
local date. The oldest unresolved target is never overwritten and later due dates advance one at a
time after recovery. Google access can complete unattended only when the existing `drive.file`
grant yields a token without interactive resolution; otherwise a content-free notification returns
the user to WorqOrder. There is no automatic retry loop, exact alarm, foreground export service,
backend, stored token, broader scope, or Google Play Store release dependency.

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
- In current `0.3.0` behavior, an effective local date or geographical zone change never finds or creates a daily task. An earlier-day timing selection is cleared, today begins without an automatically selected task, and the user selects or creates today's task explicitly. Historical `0.2.0` rollover records remain preserved by migration.
- The current required production sequence does not add WorqOrder-managed at-rest encryption to
  Room or DataStore. Android's app sandbox remains the local access boundary; optional Milestone
  E retains the separately authorized encryption and non-destructive migration plan.
- Storage failure must remain explicit and must never silently clear or reseed Room.
- No user login, biometric prompt, device-credential prompt, or app PIN is required by the
  production sequence. Plaintext is necessarily present transiently in process memory while the
  unlocked app displays, edits, or exports it.

## 9. Export behavior summary

- CSV, XLSX, and Google Sheets use the same row model and stable column order defined in
  `EXPORT_SPEC.md`.
- Current `0.3.0` schema 5 advances the shared visible schema to exactly 13 columns: Start date, End
  date, Consultant, Client, Description, Expense, Work type, Billing Status, Mileage, Start time,
  Stop time, Time spent, and Billing minutes. Both date values repeat the task's one stored work date
  as `MM/DD/YYYY`; this export projection does not change in-app date behavior. The released `0.2.0`
  schema-4 columns remain documented in its historical acceptance section.
  Destination adapters do not independently select or format fields.
- CSV is UTF-8, RFC-style quoted, repeatable, and one row per task; untimed tasks still emit one row.
- Every XLSX export creates one new standards-compliant, unencrypted OOXML workbook through
  `ACTION_CREATE_DOCUMENT`. It contains one `WorqOrder_YYYY-MM-DD` worksheet for the displayed
  date and never opens, reads, or updates an existing workbook.
- Exactly one Google spreadsheet can be connected. Its per-date tab is
  `WorqOrder_YYYY-MM-DD`.
- In Google Sheets, a marked schema-5 date tab merges rows by hidden stable task identity:
  previously exported tasks are updated and unseen tasks are appended, while rows from other
  devices are preserved. An unmarked same-name tab is a conflict and is not overwritten. Repeated
  XLSX exports intentionally create independent files, each containing one complete snapshot.
- No export modifies or deletes local data. Failures and cancellation do not claim success.
  Canceling a document picker or Google authorization returns to unchanged Main content without a
  cancellation banner or snackbar; a non-sensitive canceled diagnostic attempt may be retained.
- The required production build does not claim WorqOrder-managed at-rest encryption for Room or
  DataStore. User-selected CSV/XLSX files and readable Google Sheets cells are plaintext external
  copies and are not end-to-end encrypted by WorqOrder.
- XLSX remains a fresh, user-selected one-off workbook for every manual export. Persistent-XLSX
  mode is obsolete. Automatic export is opt-in and Google-Sheets-only in `0.2.0`; CSV and XLSX
  always remain manual.

## 10. Concept-image review

The three supplied images are visual concepts, not pixel-perfect requirements. Their dark, high-contrast, vertically arranged direction is compatible with Compose/Material 3, but the following differences must be resolved in favor of the written product rules:

| Concept detail | Conflict or gap | Resolution |
| --- | --- | --- |
| Title reads “task time” | Product name is WorqOrder. | Use **WorqOrder**. |
| Hamburger menu | Requirement calls for a Settings icon, and no drawer content is specified. | Use a settings/gear icon that navigates to Settings. |
| Timer shows `HH:MM:SS` | The original written requirements added milliseconds. | Use the
  concept's cleaner `HH:MM:SS` presentation. Persist and calculate exact sub-second boundaries,
  but omit fractional seconds from visible and exported duration strings. |
| Date controls show arrows/calendar plus `+` and `-`, with no visible date | The date must be labeled; add task is separate; minus is not a deletion affordance for the whole list. | Add a prominent date label, keep previous/next/calendar, use one labeled/accessible FAB for Add Task, and remove the ambiguous minus. |
| Rows emphasize client and clock ranges | Rows must also show description, total duration, selected/running state. | Use client + description as primary metadata and total duration as trailing content; optionally show a time range as secondary detail. |
| Bottom button says “submit” | Destination and displayed date are ambiguous. | Use the date/destination-specific export labels. |
| New-task dialog says “ok,” lacks Add Client, and has no purchases field | Required actions and validation are less clear. | Label **Create**/**Cancel**, add inline **Add client**, and add **Hardware / Software Purchases** as a second text field. Show a character count for each text field with a 400-character limit. |
| Settings uses a fixed-offset “Eastern Time” label | Fixed offsets fail DST and the product requires geographical IDs. | Show `America/New_York` (with a friendly label optionally), never store only `UTC-05:00`. |
| “Dark mode” toggle only | System, Light, and Dark choices are required. | Use a three-choice radio group. System is selected by default and follows the device; Light and Dark remain enabled as explicit overrides. |
| Export default appears as “Connected Google Sheet” | Connection state and default destination are distinct. | Separate the CSV/XLSX/Google Sheets destination choice from account/spreadsheet connection status and controls. |
| Client plus/minus and selected row | Minus is ambiguous and rename/archive/restore are absent. | Give each client an overflow/action menu with Rename and Archive; use a distinct Add button and an Archived section. |

Additional visual requirements for implementation are Material 3 semantics, 48 dp touch targets, scalable text, contrast in both themes, screen-reader labels, and layouts that remain usable under font scaling and small screens. Exact colors and typography remain a design choice for a later UI milestone.

The reattached `0.2.0` landscape concept is authoritative for composition, not exact pixels. In
Right-handed mode, the Tasks heading and list occupy the left column from below the global title
line to the same bottom margin used by the action buttons; its quiet scrollbar stays attached to
that list's right edge. The right column contains the timer card, complete date controls, and the
Export/Add pair, while the WorqOrder title stays at the upper left and Settings remains at the
upper right. Left-handed mode mirrors the two functional columns while preserving logical
accessibility traversal. Responsive sizing may depart from the mockup to prevent clipping.

## 11. Requirement reconciliations and risks

1. **Google re-export:** the released marker-and-replace behavior erased same-date rows from a
   second device. Current schema-5 tabs use marker validation plus hidden task-ID merging.
   Deleting local work does not remove a previously exported Google row; this is one-way export,
   not cloud synchronization. CSV and XLSX remain independent one-off snapshots.
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
schemas are stable across all three destinations, no
prohibited permissions/credentials/dependencies are present, and the Google setup guide has been
exercised with debug and permanent direct-release fingerprints. Optional Milestone E
app-access/privacy controls and at-rest encryption are not required for this completion definition.

## 13. Approved v0.2.0 product additions

The following are required `0.2.0` development scope, implemented only through the explicitly
authorized consecutive milestones in `IMPLEMENTATION_PLAN.md`:

1. **Client CSV import.** Client Management launches an Android read-document picker restricted
   to CSV. Every nonblank CSV cell is treated as a proposed client name. Import appends to the
   existing list; it never replaces it. Existing active canonical matches are skipped, archived
   matches are restored, duplicates within the file collapse, and the result remains A–Z. A
   malformed, non-CSV, over-limit, or unreasonably large input fails transactionally without a
   partial import or crash. The UI reports added/restored/skipped counts.
2. **Consultant assignment.** Standalone Client Management and Consultant Management rows appear
   first and second in Settings. The current Consultant dropdown/warning follows directly over the
   screen background, and Consultant Management opens the dedicated active/archived directory. New tasks require an
   active consultant. Rename/archive/restore uses the
   approved snapshot rules so historical task consultant names and exports never change
   retroactively. Migrated `0.1.0` tasks remain unassigned/blank until explicitly edited.
3. **Work Type, Billing Status, and Mileage.** Create/Edit Task provides one-choice `On-Site`/`In-Office` controls,
   defaulting new tasks to `On-Site`, plus an optional validated decimal Mileage field that opens
   a numeric-decimal keyboard. Between those controls, Billing Status provides `Billable`,
   `Do not bill`, or `Do not charge`, defaulting new tasks to `Billable`. Existing tasks migrate
   to `Unspecified`, blank Billing Status, and blank Mileage.
4. **Billing Minutes.** Show zero until time exists, then round the task's exact combined interval
   duration upward to 15-minute increments. Do not persist a redundant counter. Include the
   derived integer in every export.
5. **Canonical export schema 4.** All three destinations use the same immutable 15-column snapshot
   specified in `EXPORT_SPEC.md`; Start date and End date repeat the stored date as `MM/DD/YYYY`,
   and Start time/Stop time use task-zone `hh:mm a`. Duration values remain accumulated `HH:MM:SS`.
   Google-owned schema-2 tabs are safely
   upgraded/replaced on re-export; unowned same-name tabs remain protected.
6. **Simplified interval presentation and text entry.** Routine interval cards omit Duration and
   display Start Time/Stop Time as `hh:mm a`. Task Total and Billing Minutes appear once above the
   list. Free-text controls request sentence capitalization. Persisted instants, editing precision,
   offsets needed for DST ambiguity, duration math, and user-entered text stay exact.
7. **About.** The last Settings content reads `WorqOrder v0.2.0 - stable`, without a repository or
   release link.
8. **Handed two-column landscape.** A global top bar spans the screen with WorqOrder at the far
   left and Settings at the far right, matching the approved concept. Below it, default
   Right-handed mode places the full-height task list in approximately the left half, from its
   heading to the common bottom margin, with its scrollbar attached to the list's right edge. The
   right half stacks timer, date controls, and Export/Add actions. Left-handed mirrors only those
   two functional columns. The task list remains independently scrollable under large
   text/display sizes; portrait remains unchanged.
9. **Running-timer system surface.** Milestone 27's official review selected, and Milestone 28
   implements, a silent,
   dismissible standard notification with Android's system chronometer; a portable
   lock-screen-only AppWidget does not exist across API 26-36. The
   notification also appears in the shade and is eligible for the lock screen, subject to runtime
   permission, channel, privacy, user, and OEM policy. Private content shows app identity, Client,
   active task Description, and accumulated elapsed total; the redacted public version leaves the
   supporting line blank and omits both Client and Description. Swiping hides only that active interval's surface and never stops the Room
   timer. Reboot recovery occurs after first unlock through a one-shot boot receiver; force-stop
   recovery waits for the next user launch. Do not add a foreground service or app-owned tick loop.
   The owner-approved design, implementation record, and rejected alternatives are in
   `LOCK_SCREEN_SURFACE_ADR.md`. Device/OEM visibility remains outside WorqOrder's control.
10. **Automatic Google daily export.** The opt-in Google-only scheduler captures the intended
    effective-zone date near the end of that date. Approximate execution after midnight still
    exports the captured prior date, never a newly blank day. Owned-tab replacement keeps the
    operation duplicate-free. At the captured date's local-midnight boundary, an active interval
    closes transactionally at the exact pinned-ZoneId boundary without a continuation task or
    interval; the completed captured date then exports automatically. If closure cannot yet be
    confirmed, retain `TIMER_RUNNING` and automatically resume after a later successful boundary
    close or Stop. CSV/XLSX never auto-run. Authorization, connectivity, and other actionable
    failures remain pending without mutating Room.

The former persistent-XLSX choice and the previously deferred natural-midnight device exercises
are not `0.2.0` backlog items. Appropriate automated and manual verification remains mandatory for
each actual behavior change.

## 14. Optional Milestone E

Milestone E is an unscheduled, release-agnostic backburner item outside `0.2.0` and every other
release scope until the owner explicitly assigns it. It requires a separate explicit owner
instruction and contains only optional WorqOrder-managed at-rest encryption, opt-in local
biometric/device-credential/approved-PIN app locking, and separately reviewed screenshot/Recents
privacy controls, together with their dedicated migration, security, accessibility, performance,
and regression testing. It must not add a mandatory account, backend, destructive recovery, or
false protection claims.

## 15. Approved v0.3.0 product changes

This section is the complete feature scope for `0.3.0`. It supersedes the earlier rollover,
midnight-continuation, multi-interval, and one-row-per-interval rules only after the corresponding
`0.3.0` implementation milestones land. Every other accepted `0.2.0` behavior remains unchanged.

Milestones 30 through 33 have implemented the schema-5 one-interval model, repeated-Start
transaction, no-rollover selection behavior, exact midnight closure, automatic-export ordering,
final presentation, and schema-5 export behavior on the `0.3.0` development branch. Milestone 34
reconciled obsolete references while retaining migration and historical evidence. The owner
completed the Milestone 35 release gate and published `0.3.0`; this section now describes released
behavior.

1. **No automatic task rollover.** A selected task is never copied merely because the effective
   date or ZoneId changes, the app resumes, recovery runs, or Start is evaluated. At a real date
   change, an earlier-day timing selection is cleared. Today begins with no automatically selected
   or generated task; the user selects or creates one explicitly.
2. **Midnight closes instead of continuing.** A timer that reaches the next local-day boundary in
   its pinned geographical ZoneId closes at that exact boundary. It does not create a next-day task
   or continuation interval. If Android has suspended the process, the next legitimate worker,
   resume, reboot recovery, or launch records the same exact boundary retrospectively. No exact
   alarm, wake lock, or foreground stopwatch service is introduced.
3. **At most one interval per task.** A task contains zero or one interval. Edit Task presents a
   singular **Interval** section. An untimed task may receive its interval through Start or manual
   entry; a completed interval may be edited or deleted, but a second interval may not be appended.
4. **Repeated timing creates a task.** Pressing Start on today's selected task with a completed
   interval atomically creates a new task with a new stable task ID, copies all user metadata and
   the source lineage ID, selects it, and opens its sole interval. The original task and interval
   remain unchanged. Client, Consultant snapshot, Description, Expense, Work type, Billing Status,
   Mileage, work date, and assignment ZoneId are copied. Creation/update timestamps and interval
   identity are new rather than copied.
5. **Safe historical conversion.** The Room 4-to-5 migration converts every existing task with
   several intervals into one task per interval without losing clients, Consultants, task
   metadata, IDs that can be preserved, UTC endpoints, manual-edit state, dates, zones, or a
   running timer. The original task retains its earliest interval; later intervals receive
   deterministic copied tasks in chronological order. A running interval and singleton active
   timer are repointed transactionally when necessary. Zero- and one-interval tasks are unchanged.
6. **One export row per task.** Canonical schema 5 contains exactly 13 columns: Start date, End
   date, Consultant, Client, Description, Expense, Work type, Billing Status, Mileage, Start time,
   Stop time, Time spent, and Billing minutes. `Interval number` and the redundant `Interval
   duration` are removed. CSV, XLSX, manual Google, and automatic Google export use the identical
   immutable dataset.
7. **Automatic-export ordering.** Automatic Google export retains the captured preceding work
   date and runs at Android's first best-effort opportunity after its local midnight. Before the
   snapshot, any still-open interval from that date is transactionally closed at the exact
   boundary. Google authorization, connectivity, and scheduling failures retain the existing safe
   pending behavior. CSV and XLSX remain manual.

`0.3.0` does not otherwise redesign clients, Consultants, task metadata, Settings, landscape
layout, notifications, authentication, destinations, backup policy, licensing, or distribution.

## 16. Approved `0.4.0` product changes (released)

Milestones 37–40 implemented the behavior below. After the Milestone 41 gates, the owner published
and physically verified `0.4.0`. GitHub Releases remains authoritative for downloadable artifacts.

1. **Task Notes.** New tasks have an optional blank `Notes` field below Mileage on Create Task.
   Notes accept up to 999 Unicode characters and can be edited later in Edit Task. A
   non-destructive Room migration initializes every pre-`0.4.0` task's Notes to blank. When Start
   on a completed task creates a new same-day task, that new task's Notes are **blank**, even if
   the source task contains Notes. Other approved copied metadata and lineage remain unchanged.
2. **Cleaner Create Task introduction.** After `Task for [date]`, present the client dropdown
   without separate `Consultant`/`Client` headings or selected-Consultant name text. This is
   presentation only: a selected Consultant is still required and assigned to the new task, and
   active-client selection/validation remains in force.
3. **Always-available Create actions.** Keep the existing fixed header. Put Cancel and Create in
   a fixed bottom footer, visually above and separate from the scrollable form. Insets, keyboard,
   short landscape, large text, and accessibility must not hide form fields or actions. The footer
   uses the page background color in every theme.
4. **One shared export change.** Canonical export schema 6 has the same first 13 visible columns
   and per-task row semantics as schema 5, followed by `Notes` as column 14. CSV, one-off XLSX,
   manual Google, and automatic Google use the same immutable dataset. Existing owned Google
   schema-5 date tabs, their rows, and hidden task IDs must survive a safe transition; ambiguous,
   unowned, or unknown tabs fail closed. No export synchronizes back into Room.
5. **Release continuity.** Preserve package `worq.order`, the permanent owner signer, prior data,
   disabled Android backup, direct no-cost GitHub distribution, GPLv3, the narrow Google scope,
   and existing timer/date rules. The owner explicitly approved `0.4.0`/versionCode `4` before
   the release-candidate build identity was changed.
6. **Compact Edit Task presentation.** Do not display the stored task ZoneId on Edit Task. Keep it
   intact for date, interval, DST, and export correctness. Put Delete Task and Save Task Changes in
   an equal-width fixed footer, Delete on the left and Save on the right, using the page background
   color without changing confirmation, validation, or persistence behavior.

Milestones 36–41 own planning, implementation, verification, and public-release handoff. The
owner subsequently published and physically verified `0.4.0`. The former teardown Milestones 42
and 43 are deferred and renumbered to post-`0.5.0` Milestones 49 and 50. Security Milestone E
stays unscheduled outside release scope.

## 17. Implemented `0.5.0` reusable-Tag product changes (release audit pending)

The assigned implementation milestones are complete through Milestone 47 on the development
branch. Milestone 48 still owns the release audit and owner-run gates. This feature adds reusable
local text catalogs without adding export columns or changing Room authority.

1. **Two independent catalogs.** Settings places **Tag Management** after Client Management and
   Consultant Management. Its overview opens separate **Description Tags** and **Hardware /
   Software Purchase Tags** pages. Each page supports alphabetical browsing, real-time case-
   insensitive substring search with a clear action, Add, Edit, and confirmed true Delete.
2. **Bounded Tag text.** A Tag contains at most 400 Unicode code points after surrounding and
   repeated whitespace normalization. Blank input is rejected. Duplicate detection is category-
   scoped and ignores case, surrounding/repeated whitespace, and one terminal period. The same
   normalized text may exist once in each different category.
3. **Safe CSV import.** Each category page imports a user-selected CSV through the Storage Access
   Framework. Every nonblank cell—including a header-looking cell—is a Tag. Correct quoting,
   Unicode, commas, and embedded line breaks are parsed. Existing and within-file duplicates are
   skipped without overwriting entries. The resulting list remains alphabetical and reports added/
   skipped counts. Malformed or overlength content, a file over 1 MiB, or more than 10,000 nonblank
   cells fails atomically and changes nothing. No broad storage permission is requested.
4. **Compact task selection.** Description and Hardware / Software Purchases retain normal manual
   entry and gain one unlabeled, non-wrapping row directly below each field. With no selections,
   its button reads **Add tags** and has no chip. With selections, the button reads **Edit tags**
   and is followed by either the one selected Tag in an ellipsized chip or a single `+N tags
   selected` chip for multiple selections. These informational chips use the lighter selected
   color, outline, pill shape, and intrinsic label width. Only a long single Tag expands into the
   remaining width before ellipsizing. Both button and chip open the picker; chips have no
   direct close action. A full-screen
   picker supports browse, real-time filter, clear, multi-select in selection order, selected
   count, Cancel/Apply, and inline creation. Filtered-out selections remain selected. An inline-
   created Tag is persisted and selected even if the task form is later cancelled. Non-scrolling
   **Select All** and **Deselect All** actions affect only rows visible under the current search;
   hidden selections remain unchanged. An over-limit Select All changes nothing and explains the
   limit through the existing picker error.
5. **Snapshot history.** Saving a task copies selected Tag text and order into task-owned
   snapshots. Catalog edits/deletions never rewrite old tasks or exports. An old task continues to
   show saved chips; an edited source offers an explicit **Use Updated Version** replacement, while
   a deleted source is removable but cannot be newly selected. Deletion removes the catalog entry
   after confirmation; no Archived Tags area is added.
6. **Expanded composed limits.** Manual Description and Hardware / Software Purchases expand from
   400 to an exported composed maximum of 999 Unicode code points. Manual text, Tag snapshots,
   generated punctuation, and joining spaces all count. Description, Hardware / Software
   purchases, and Notes show **N / 999** through the limit. Above the limit, show the live red
   **Character limit: N / 999** state. Never prefix the normal counter with `Exported text:`.
   Prevent an overlimit selection, surface later manual overage next
   to the field, and disable Create/Save
   until valid. Description is valid when either manual text or at least one Description Tag is
   nonblank. Purchases remains optional.
7. **Export-only composition.** For Description and Expense, trim components, place manual text
   first and snapshots in selection order, append a period to a component that lacks terminal
   `.`, `?`, or `!`, and join components with one ASCII space. Generated punctuation is never
   written back to manual text or snapshots. All destinations use this one canonical function.
8. **Compact display/privacy.** A main task row shows the manual short Description when available;
   otherwise it shows the first Description Tag and `+N tags` for additional snapshots. The
   running notification continues to show Client plus manual Description, or Client alone when
   manual text is blank; it never exposes Tag text.
9. **Repeat and edit behavior.** Repeated Start copies ordered Tag snapshots because it already
   copies Description and purchase metadata; Notes still starts blank. Editing task Tags changes
   only that task. Task deletion cascades its snapshots but never deletes catalog entries.
10. **Compatibility.** Add only an explicit non-destructive Room 6-to-7 migration. Existing tasks
    retain exact manual fields and begin with no Tag snapshots. Canonical export schema 6 remains
    exactly 14 visible columns; Tags compose existing Description and Expense values. Google tab
    ownership/layout and CSV/XLSX lifecycle remain unchanged.
11. **Local assignment feedback and action-only footers.** Create/Edit footers contain only their
    respective two actions. A missing/unavailable Client or Consultant error appears as small text
    directly below its selector or recovery button; that button's outline/content and the error use
    the error color without recoloring menu entries. Other page messages remain in the scrollable
    body. Every Settings and inline Tag add/edit dialog shows live **N / 400** feedback, changes to
    red **Character limit: N / 400** above the limit, and disables confirmation until corrected.
12. **Edit client recovery.** If an Edit Task form's assigned client is archived or otherwise no
    longer active, show **Add client** beside the existing client recovery feedback. It opens the
    same validated inline editor and archived-match restore confirmation as Create Task. The new or
    restored client is selected as an unsaved task edit; adding/restoring the directory entry is an
    immediate independent action and is not undone by cancelling the task edit.

Milestones 42–48 own planning through public-release verification. Milestone 47 owns the final
Create/Edit presentation consistency pass; Milestone 48 owns the release audit. Deferred
environment teardown and optional closeout are Milestones 49–50 and cannot start implicitly. See
`V0_5_MILESTONE_PROMPTS.md` for model assignments and mandatory owner-run test handoffs.
