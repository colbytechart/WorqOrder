# Privacy and Data

Effective for WorqOrder `0.1.0`

## 1. Summary

WorqOrder is an offline-first, GPLv3 Android application. It does not operate a WorqOrder account,
analytics service, advertising system, Firebase project, custom backend, or developer-controlled
task database.

Core tracking works without network access. Network access is used only when the user chooses
Google identity, spreadsheet connection, or Google Sheets export.

## 2. Local Data

The app stores the following in its app-private storage:

- client IDs, names, active/archive state, and timestamps;
- task IDs/series IDs, client relationship, work date, stored geographical ZoneId, description,
  hardware/software-purchase text, and timestamps;
- interval IDs, ordinal, UTC start/stop timestamps, manual-edit flag, and timestamps;
- the singleton active-timer pointer;
- appearance, time-zone, export-destination, and task-selection preferences;
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

## 4. External Exports

CSV and XLSX documents are written only after the user chooses a destination through Android's
document interface. Google Sheets exports go only to the connected spreadsheet.

All three destinations receive the same nine visible fields:

1. Work Date
2. Client Name
3. Description
4. Hardware / Software Purchases
5. Interval Number
6. Start Local
7. Stop Local
8. Interval Duration Formatted
9. Task Total Duration Formatted

These external documents are plaintext/readable copies. They are not end-to-end encrypted by
WorqOrder. Anyone with access to the selected file location or spreadsheet may be able to read
them.

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

Android's application sandbox is the local access boundary. WorqOrder `0.1.0` does not add its own
at-rest encryption layer to Room or DataStore. Android backup is disabled, credentials and signing
keys are not embedded, broad storage permissions are not requested, and no cleartext network
traffic is enabled.

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
