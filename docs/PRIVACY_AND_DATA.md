# Privacy and Data

This document describes the released `0.5.0` data contract, with earlier details retained for
upgrade transparency. Check GitHub Releases for authoritative artifacts. `0.4.0` Notes and `0.5.0`
Tags are ordinary local task data; their exported text is readable plaintext in CSV/XLSX/Google
Sheets. Section 11 describes the in-development `0.6.0` portable-backup implementation, which is not
part of released `0.5.0`.

## 1. Summary

WorqOrder is an offline-first, GPLv3 Android application. It does not operate a WorqOrder account,
analytics service, advertising system, Firebase project, custom backend, or developer-controlled
task database.

Core tracking works without network access. Network access is used only when the user chooses
Google identity, spreadsheet connection, or Google Sheets export.

## 2. Local Data

The app stores the following in its app-private storage:

- client IDs, names, active/archive state, and timestamps;
- Consultant directory IDs/names/archive state, plus the current Consultant selection;
- task IDs/series IDs, client and optional Consultant relationships, assignment-time Consultant
  snapshot, work date, stored geographical ZoneId, description, hardware/software-purchase text,
  Work Type, nullable Billing Status, canonical Mileage, optional Notes, and timestamps;
- interval IDs, UTC start/stop timestamps, manual-edit flag, and timestamps. Databases migrated
  from v0.2 may contain legacy ordinal information only in their migration source; schema 5 does
  not persist an ordinal column and each task owns zero or one interval;
- the singleton active-timer pointer;
- appearance, time-zone, landscape-handedness, export-destination, automatic-Google target/pending,
  notification-dismissal, and task-selection preferences;
- non-secret connected-spreadsheet metadata such as spreadsheet ID/title and connection time; and
- limited account display metadata when supplied by the Google identity flow.

Room is authoritative for client/task/interval/timer data. Preferences DataStore is not the timer
authority and does not manually store raw Google access or refresh tokens.

## 3. Network and Google Data

When the user chooses Google functionality:

- Google identity APIs handle account authentication and credential state.
- Google authorization requests only the non-sensitive `drive.file` scope.
- WorqOrder requests access to the exact spreadsheet selected/approved by the user.
- The app reads spreadsheet metadata needed to validate access and writes the canonical export
  table to WorqOrder-owned date worksheets.
- Google Sheets data is transmitted over HTTPS/TLS through Google APIs.

The app does not list the user's general Drive contents, import spreadsheet tasks, synchronize
remote edits, send task data to a WorqOrder server, or use an API key as private-file
authorization. Google data handling is limited to providing and maintaining the user-requested
connection and export feature.

Google's own terms and privacy policy apply to the user's Google account and spreadsheet. Account
or organization policies may independently block authorization.

Client Management may read one user-selected CSV through Android's document picker. WorqOrder
parses it only in process to append or restore validated client names; it does not upload or retain
the document, persist its URI permission, or import tasks.

## 4. External Exports

CSV and XLSX documents are written only after the user chooses a destination through Android's
document interface. Google Sheets exports go only to the connected spreadsheet.

The released `0.2.0` destinations received a historical 15-field schema. Version `0.3.0` uses
the immutable 13-field schema, and `0.4.0` appends `Notes` as column 14 across every destination:

1. Start date
2. End date
3. Consultant
4. Client
5. Description
6. Expense
7. Work type
8. Billing Status
9. Mileage
10. Start time
11. Stop time
12. Time spent
13. Billing minutes
14. Notes (`0.4.0` and later)

These external documents are plaintext/readable copies. They are not end-to-end encrypted by
WorqOrder. Anyone with access to the selected file location or spreadsheet may be able to read
them.

The v0.2 historical schema also included `Interval number` and `Interval duration`; those columns
are retained in the documented historical release record only and are not emitted by schema 5.

## 5. Retention and Deletion

Local records remain until the user edits/deletes them, clears application storage, or uninstalls
WorqOrder. Android application backup is disabled, so WorqOrder does not provide platform
backup/restore or device-transfer recovery.

- Deleting a task deletes that daily task and its intervals, not its client or same-series tasks
  on other dates.
- Removing a client archives it and preserves referenced history.
- Disconnecting a spreadsheet clears its local connection metadata but does not delete the file.
- Signing out also disconnects the spreadsheet but does not alter Room or the remote file.
- Exported CSV/XLSX files and Google worksheets must be deleted in the destination application.
- Removing WorqOrder from the Google Account's third-party connections revokes future Google
  access but does not delete local tasks or existing exports.

## 6. Security Boundaries

Android's application sandbox is the local access boundary. Neither released `0.2.0` nor released
`0.3.0` adds its own at-rest encryption layer to Room or DataStore. Android backup is
disabled, credentials and signing keys are not embedded, broad storage permissions are not
requested, and no cleartext network traffic is enabled.

Rooted/compromised devices, unlocked physical access, debugging/forensic privileges, operating
system vulnerabilities, or access to external exports may expose data. Optional application-lock
and app-private encryption work is outside the current release and requires separate owner
authorization.

## 7. Cost and Distribution

WorqOrder is free and open source under GPLv3. Google integration uses standard no-additional-cost
quota and does not require a billing account, paid quota, custom domain, Workspace subscription,
or owner-managed test-user list. If Google's policy changes, Google integration work must pause
for a new product decision; CSV remains available.

## 8. Questions and Changes

Use this repository's issue tracker for privacy questions or reports. Changes to data collection,
storage, permissions, OAuth scopes, or network destinations must be documented in this file, the
product decisions, and release notes before distribution.

## 9. Notification and automation details

Billing Minutes is derived from interval durations rather than stored independently. Automatic
Google export is opt-in and stores only a non-sensitive captured target date, ZoneId, connection
association, and typed pending reason. It may transmit the captured date near or shortly after its
end. Successful automatic export is silent. Pending/error notifications contain no Consultant,
client, task, date, spreadsheet, or exported-row content. CSV and XLSX remain manual.

Milestone 28 implements Milestone 27's owner-approved private running-timer notification design.
Its full content contains Client, active task Description, and elapsed total; it contains no
Consultant, Expense, Mileage, Billing Status, or other task metadata. A redacted public version
shows only WorqOrder identity and elapsed time, with a completely blank supporting line. Android
may show the full private version on the lock screen when the user permits sensitive content, so
Client and Description must be treated as information intentionally exposed outside the app in
that setting.
The notification also exists in the notification shade and may bridge to paired notification
surfaces unless platform/user settings prevent it. Permission denial, channel disablement,
lock-screen privacy, screen-sharing protection, force-stop, and OEM policy may hide it. Swipe
dismissal stores only the active interval ID and never changes Room task or interval data. Reboot
recovery waits until first unlock so WorqOrder does not copy the task Description into
device-protected storage. Full implementation details are in `LOCK_SCREEN_SURFACE_ADR.md`. Optional Milestone E encryption/app-lock/screenshot/Recents controls are release-agnostic
backburner items outside `0.2.0` and every other release scope until explicitly assigned; this
notice must not imply otherwise.

## 10. Released `0.5.0` reusable Tags

`0.5.0` implements local Description and Hardware / Software Purchase Tag catalogs plus task-owned
text snapshots. They are ordinary potentially sensitive Room data under the same Android sandbox and
disabled-backup policy as tasks; WorqOrder does not claim app-managed at-rest encryption. Catalog
edit/delete does not erase text already snapshotted into a task. Task deletion removes that task's
snapshots, while deleting a catalog Tag leaves historical task content intact.

When the user exports, ordered snapshots are composed into the existing Description or Expense
cell and become plaintext in the chosen CSV/XLSX file or connected Google Sheet. No Tag column,
new OAuth scope, backend, account, analytics, telemetry, or automatic network destination is
introduced. CSV import reads only the document the user selects through the Storage Access
Framework and stores accepted cells locally; it does not upload the source document.

Main task rows may show a first Description Tag only when manual Description is blank. The running-
timer notification never receives Tag text, including in its private lock-screen form. Generated
export punctuation is not written back to stored manual text or snapshots.

## 11. In-development `0.6.0` portable backups

Portable backups are explicit user-directed plaintext files. They can contain Client/Consultant
names, tasks, descriptions, purchases, Tags, Notes, mileage, work/billing choices, dates, precise
interval timestamps, export history, and preferences. Anyone who can read the file may read that
work information; users must store and share it accordingly. WorqOrder does not upload the file.

OAuth/access/refresh tokens, credentials, Google account hints, connected-sheet IDs/titles,
notification permission/state, scheduler jobs/pending attempts, installation transport identity,
and transient UI/cache data are excluded. Import preserves the export-destination choice but clears
Google authorization/connection and disables automatic export until the user reconnects and opts in.

The rolling restore point is app-private and deliberately placed in no-backup storage. It is not
included in portable or Android backup, survives ordinary restart/update, and is removed with app
data on uninstall/Clear storage. `0.6.0` does not add encryption; optional encryption remains outside
scope in Milestone E.

Import parses current-format data incrementally and retains only the validated logical candidate
needed for replacement; pre-confirmation staging retains the private ZIP rather than a second data
copy. Heap-aware rejection limits denial-of-service risk from oversized valid-looking input. These
implementation bounds do not encrypt the plaintext or change the user's responsibility for an
externally saved/shared backup.
