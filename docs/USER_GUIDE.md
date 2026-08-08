# WorqOrder User Guide

Version: `0.1.0`

## 1. What WorqOrder Stores

WorqOrder keeps clients, tasks, intervals, and the active timer locally on the device. The Room
database is authoritative. CSV, XLSX, and Google Sheets are export copies; changing an export does
not change the app.

Do not uninstall WorqOrder or clear its storage when you need to retain local records. Android
application backup is disabled.

## 2. Manage Clients

1. Open **Settings** from the Main-screen gear.
2. Open **Client Management**.
3. Choose **Add client**, enter a name, and confirm.

Client names are trimmed, repeated whitespace is collapsed, and active duplicates are rejected
without regard to capitalization. A name may contain at most 100 characters.

From an active client row:

- **Rename** changes the name displayed by existing tasks because tasks retain the client ID.
- **Remove from client list** archives the client after confirmation. Historical tasks remain
  intact, but the client is unavailable for new tasks.

Open **Archived Clients** and choose **Restore** to make a removed client selectable again. If an
active client has the same normalized name, resolve the name conflict first.

### Manage Consultants

1. Open **Settings**. **Client Management** is first and **Consultant Management** is second.
2. Open **Consultant Management**.
3. Use **Add Consultant**, or rename/archive an active Consultant.
4. Restore removed entries from **Archived Consultants** when needed.
5. Return to Settings and choose the Consultant assigned to new tasks from the bare dropdown below
   the two management rows.

If no active Consultant is selected, Settings displays **Add and select a Consultant before
creating a task.** Directory changes keep historical task Consultant snapshots intact.

## 3. Create and Select Tasks

1. Browse to the required work date on Main.
2. Choose **Add task**.
3. Select an active client.
4. Enter a required **Short description**.
5. Optionally enter **Hardware / Software Purchases**.
6. Choose **Work Type**.
7. Choose **Billing Status**: **Billable** (the default), **Do not bill**, or **Do not charge**.
8. Optionally enter **Mileage**.
9. Choose **Create**.

Both text fields allow up to 400 characters. The new task belongs to the date that was displayed
when creation opened. A new task for today becomes selected automatically. A historical or future
task can be created and edited, but cannot be timed live.

If no active client exists, use the inline **Add client** action. Canceling task creation saves
nothing. Tapping a task row selects it when no timer is running.

## 4. Start and Stop Timing

- Select a task assigned to today.
- Choose **Start**. WorqOrder creates one open interval in Room.
- Choose **Stop** to close the interval.

The timer shows all completed intervals for the selected daily task plus the current active
interval. Selecting another task is blocked until Stop. Starting the same task again creates
another interval and continues from its accumulated total.

Accumulated durations are displayed as `HH:MM:SS`. WorqOrder still records precise interval
boundaries internally; fractional seconds are omitted from the interface to reduce visual clutter.

The displayed duration is calculated from time sources; the app does not write database ticks.
A running timer remains logically active when the app is backgrounded, the screen is locked, the
activity is recreated, the process is killed, or the device reboots. Reopen WorqOrder to
reconstruct the visible value from the persisted interval.

## 5. Browse Dates

Use the previous/next arrows or calendar action in the pinned date controls. **Today** returns
directly to the current date in the effective application time zone.

Browsing another date does not move or rewrite tasks and does not stop a running timer. Live Start
is available only while displaying today. If a timer crosses one or more local midnights,
WorqOrder normalizes the data into corresponding daily task copies when it resumes, normalizes, or
stops.

## 6. Edit or Delete a Task

Open a task's overflow menu and choose **Edit**.

The Edit Task screen allows you to:

- change the active client;
- change the short description or purchase notes;
- change Work Type, Billing Status, or Mileage (migrated tasks may initially have blank Billing
  Status);
- view the read-only work date, task ZoneId, and total duration;
- view interval Start Time and Stop Time in 12-hour `hh:mm AM/PM` form without seconds;
- add a completed manual interval;
- edit the start or stop of a completed interval;
- delete a completed interval; and
- delete the daily task.

Manual intervals must stay within the task's local work date, start before stop, and not overlap
another interval. A spring-forward time that does not exist is rejected. A repeated fall-back
time requires an explicit earlier/later occurrence choice. Running intervals and running tasks
cannot be materially edited or deleted.

**Save task changes** saves metadata and returns to Main. Back navigation with unsaved changes
offers a discard choice. Deleting an interval or task requires confirmation. Deleting a daily task
does not delete its client or same-series task copies on other dates.

## 7. Appearance, Landscape Orientation, and Device Time Zone

Open **Settings**:

- **Use system setting** follows the device Light/Dark appearance and is the default.
- **Light** and **Dark** explicitly override it.
- Under **Landscape Orientation**, **Right-handed** (the default) places Tasks on the left and
  timer/date/actions on the right. **Left-handed** mirrors those two content areas. The choice
  applies immediately, persists across launches, and does not change portrait layout or task
  order.

Time-zone controls are not exposed in Settings. New and default configurations follow the phone's
current geographical time zone. Existing date/ZoneId persistence remains intact, and historical
task dates, stored task zones, and intervals are never rewritten.

The bottom of Settings displays the installed build version and stability as bare footer text, for
example **WorqOrder v0.2.0 - stable**. There is no About card, and the text does not open a website
or repository.

## 8. Choose an Export Destination

In **Settings > Export Destination**, select:

- **CSV**
- **XLSX**
- **Google Sheets**

All destinations export the displayed date using the same canonical dataset. Stop the active
timer before exporting.

## 9. Export CSV or XLSX

1. Select CSV or XLSX in Settings.
2. Return to Main and browse to the date.
3. Choose the bottom export action, whose label identifies the date and destination.
4. Choose the filename/location in Android's save interface.

CSV is UTF-8. XLSX is an unencrypted OOXML workbook created fresh for each export. Repeating either
export creates another user-chosen document and does not change Room. Canceling the save picker
returns to Main without a cancellation message.

Every exported Start time and Stop time uses the same task-zone 12-hour `hh:mm AM/PM` format shown
in task details. Accumulated duration columns retain `HH:MM:SS` and may exceed 24 hours.

## 10. Connect Google Sheets

1. In Settings, choose **Google Sheets** as the export destination. The screen scrolls to
   **Google Sheets Connection**.
2. Choose **Sign in with Google** and select an account.
3. Paste an editable spreadsheet's full Google Sheets URL or spreadsheet ID.
4. Choose **Validate and connect**.
5. In Google's file-consent flow, approve the exact requested spreadsheet.
6. Confirm that the spreadsheet title and ID appear as connected.

WorqOrder requests access only to files explicitly granted through the `drive.file` scope. The
spreadsheet must be editable by the signed-in account.

**Disconnect spreadsheet** forgets the spreadsheet but leaves the Google account signed in.
**Sign out** clears the local Google identity state and also disconnects the spreadsheet. Neither
action changes local tasks or the remote spreadsheet.

## 11. Export to Google Sheets

1. Stop any running timer.
2. Display the required work date.
3. Choose **Submit [date] to Google Sheets**.

WorqOrder writes one `WorqOrder_YYYY-MM-DD` worksheet per date. Re-exporting replaces the
application-owned table so rows are not duplicated.

If the workbook is entirely blank, WorqOrder may reuse its blank initial worksheet. If a
same-named worksheet contains data but lacks WorqOrder's ownership marker, WorqOrder does not
overwrite it. Rename that worksheet and retry.

If authorization expires, retry and approve the same spreadsheet. If edit permission is lost,
restore Editor access or connect another spreadsheet. Offline, timeout, quota, and server failures
do not change local data.

## 12. Data Removal and Recovery Boundaries

- Delete individual tasks or intervals through their confirmation dialogs.
- Archive clients instead of deleting referenced history.
- Disconnect/sign out to clear Google connection metadata.
- Delete exported CSV/XLSX files or Google worksheets using their owning applications.
- Android **Clear storage** or uninstall removes WorqOrder's local database and preferences.

WorqOrder has no account, backend, recycle bin, automatic cloud backup, or two-way import recovery.
