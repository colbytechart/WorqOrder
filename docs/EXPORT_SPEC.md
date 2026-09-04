# Export Specification

## 1. Principles

- Export exactly the date displayed on the main screen.
- Build one immutable logical snapshot from Room, the authoritative source.
- Use the same logical columns, row ordering, and value semantics for CSV, XLSX, and Google Sheets.
- Export is one-way. It never imports, marks tasks exported, deletes local records, or resolves changes made in an external copy.
- A retry is safe. Google Sheets re-export replaces the existing date tab idempotently; CSV and
  XLSX intentionally create independent user-selected files.
- Export requires no globally active timer. Main disables its export action for every destination
  while timing and identifies **Stop Timer to Export** as the required next action.
- XLSX is an approved focused destination after Google Sheets export. Do not use Apache POI or
  another broad Excel stack without explicit owner approval. Firebase, service accounts, custom
  backends, broad storage permissions, and embedded secrets remain prohibited.

## 2. Snapshot process

`ExportSnapshotCoordinator.prepare(displayedDate)`:

1. Main verifies that active-timer state is loaded and no timer is running.
2. Capture one `exportInstant` and the effective zone context under the timer-operation lock.
3. In one Room read transaction, load tasks for `displayedDate`, retained clients, ordered intervals, and active state.
4. Build rows and task totals evaluated at the same `exportInstant`.
5. Sort rows deterministically.
6. End the Room transaction.
7. Return one immutable, destination-neutral `ExportSnapshot` containing the internal schema
   version, displayed work date, snapshot instant, exact headers, and canonical string rows.
8. Pass that unchanged snapshot to the selected CSV, XLSX, or Google Sheets adapter. Adapters may
   escape/package/transport values but must not choose fields, change formatting, or reorder rows.
9. Persist a safe last-attempt result in settings; do not modify tasks/intervals.

An empty date still exports the header with no data rows. A task without intervals emits one row
with blank interval number/start/stop/duration and a zero task total. The normal product workflow
does not prepare or dispatch an export while an interval is open. The shared builder retains
defensive support for immutable data construction, but a running row is not an approved
user-visible export state.

Milestone 8 implements this as follows: one shared timer-operation lock covers capture of
`exportInstant`, boundary normalization, and the Room snapshot read. The DAO loads every joined
task/client and its ordered intervals for the displayed date inside one Room transaction. The row
builder produces one immutable destination-neutral dataset. The CSV adapter serializes that
dataset into one in-memory string before the create-document picker opens. Changes made after the
picker opens cannot change that pending payload. XLSX and Google adapters consume the same
dataset object.

## 3. Canonical export schema version 4 (`0.2.0`)

Schema version 4 is internal compatibility metadata. It is used by WorqOrder markers and export
adapters but is not a visible data column. Version `0.2.0` advances every destination together to
these exact 15 columns in this exact order:

| # | Column | Encoding |
| ---: | --- | --- |
| 1 | Start date | task work date formatted `MM/DD/YYYY` |
| 2 | End date | the same task work date formatted `MM/DD/YYYY` |
| 3 | Consultant | task's employee-name snapshot; blank for unassigned migrated history |
| 4 | Client | retained current client name |
| 5 | Description | short task description |
| 6 | Expense | optional hardware/software-purchases task text; blank when none |
| 7 | Work type | `On-Site`, `In-Office`, or blank for migrated `Unspecified` |
| 8 | Billing Status | `Billable`, `Do not bill`, `Do not charge`, or blank for migrated history |
| 9 | Mileage | normalized plain non-negative decimal text, or blank |
| 10 | Interval number | stable positive ordinal or blank |
| 11 | Start time | task-zone local clock time `hh:mm a`, or blank |
| 12 | Stop time | task-zone local clock time `hh:mm a`, or blank |
| 13 | Interval duration | accumulated `HH:MM:SS`, or blank |
| 14 | Time spent | exact same value previously named Task Total Duration; accumulated `HH:MM:SS` |
| 15 | Billing minutes | non-negative base-10 integer |

Both date columns intentionally repeat the same independently stored task work date. They are an
export projection only: WorqOrder does not store a date range and does not change date behavior in
the app. Start/Stop instants are converted using the task's stored geographical ZoneId and only then reduced
to strict 12-hour `hh:mm a` with uppercase AM/PM. Seconds, fractional seconds, date, offset, and ZoneId are intentionally omitted
from the export; the complete instants and task ZoneId remain stored in Room. Two fall-back
occurrences can therefore display the same clock time even though the app retains distinct
instants.

Duration output truncates any sub-second remainder rather than rounding and never wraps accumulated
hours at 24. Empty optional values are empty fields, not the strings `null` or `N/A`.

Billing Minutes uses the exact task total before display truncation: zero total produces `0`; any
positive total produces `ceil(totalMilliseconds / 900000) * 15`. A zero-interval task therefore
has blank interval fields, `00:00:00` Time spent, and `0` Billing minutes. Export remains
blocked during timing, so canonical Billing Minutes never depends on an incomplete open interval.

Rows sort using retained internal metadata: task creation instant, task ID, interval start (null
last), interval ordinal, and interval ID. Those sort keys are not exported. This preserves stable,
deterministic output across all three destinations. Changing a column, encoding, or sort rule
requires one schema-version decision in this document and one change to the shared builder.

Room and domain models continue retaining every field omitted from the external
projection: snapshot instant, task ZoneId, client/task/series/interval IDs, interval state,
complete local/UTC instants, millisecond durations, task creation/update timestamps, and manual
edit flags.

## 4. CSV serialization

- MIME type: `text/csv`.
- Suggested name: `worqorder_YYYY-MM-DD.csv`.
- Encoding: UTF-8. Do not add a BOM unless a later compatibility test and recorded decision requires it.
- Line ending: CRLF for broad RFC-style interoperability.
- First record is the exact schema header; subsequent records are logical rows.
- Quote a field if it contains comma, quote, CR, or LF. Escape each quote as two quotes. Preserve Unicode and embedded line breaks.
- Do not use locale-specific number/date formatting in file values.
- Serialize/validate the complete one-day payload before opening the destination where practical.
- Picker cancellation before a URI is returned creates no file/partial file. It may be retained as
  a non-sensitive `Canceled` diagnostic attempt, but it produces no Main-screen banner, snackbar,
  success message, or error message.
- On output failure after creation, close the stream, attempt deletion only through the granted document API when supported, and report that a partial provider document may remain if deletion is unsupported.
- Repeating export is permitted. `ACTION_CREATE_DOCUMENT` may disambiguate an existing filename; do not overwrite unrelated files silently.

The Android adapter uses `ActivityResultContracts.CreateDocument("text/csv")`, which creates the
standard `ACTION_CREATE_DOCUMENT` intent with the MIME type and suggested filename. The AndroidX
contract does not promise that `CATEGORY_OPENABLE` is explicitly present when inspecting the raw
intent, so tests assert the action, MIME type, and title rather than an undocumented category.
After a URI is returned, `ContentResolver.openOutputStream(uri, "wt")` writes the already
serialized payload as UTF-8. Provider failure triggers a best-effort `ContentResolver.delete` of
that exact granted URI. The UI distinguishes success, retryable write failure, and the case where
a partial provider document could not be removed; cancellation simply returns to unchanged Main
content.

CSV faithfully preserves the consultant snapshot, client, description, expense, Work type,
Billing Status, and Mileage text. Some spreadsheet programs interpret cells beginning with `=`, `+`, `-`, or `@` as
formulas when opening CSV. RFC quoting does not prevent that behavior. Silently prefixing text
would change exported data, so formula-injection transformation is not part of schema version 4;
flag it in release security review and document safe import behavior.

### Schema-version 4 examples

The first record is always the following exact header:

```csv
Start date,End date,Consultant,Client,Description,Expense,Work type,Billing Status,Mileage,Interval number,Start time,Stop time,Interval duration,Time spent,Billing minutes
```

A completed interval may serialize as:

```csv
07/24/2026,07/24/2026,Alex Rivera,"Acme, Inc.","Repair ""north"" unit",Laptop,On-Site,Billable,18.5,1,09:00 AM,10:00 AM,01:00:00,01:00:00,60
```

A task without intervals has blank interval number/start/stop/duration and a zero task total:

```csv
07/24/2026,07/24/2026,Alex Rivera,Example Client,Planning,,On-Site,Billable,,,,,,00:00:00,0
```

The examples are shown with line breaks for readability; the file record terminator is CRLF.

## 5. CSV destination resolution

The original requirements asked for both:

- use the standard `ACTION_CREATE_DOCUMENT` save flow; and
- automatically create/use `Downloads/WorqOrder` as the default CSV destination.

The Storage Access Framework deliberately lets the system document provider/user control the save location. `ACTION_CREATE_DOCUMENT` can suggest a filename and initial URI but cannot force a folder or silently create `Downloads/WorqOrder`. Conversely, direct insertion into Downloads (for example through MediaStore on newer APIs) is not the standard create-document flow and is not uniform on API 26–28 without a different grant/permission strategy.

Accepted resolution:

1. Keep `ACTION_CREATE_DOCUMENT` for every CSV export, no storage permissions.
2. Suggest `worqorder_YYYY-MM-DD.csv` and, when a valid remembered provider URI exists, hint the `Downloads/WorqOrder` location.
3. On the first CSV export, explain that the user may create/select a `WorqOrder` folder in Downloads; the app itself does not claim it can force that folder.
4. Never request a directory-tree grant or create a folder automatically. If the user chooses/creates `Downloads/WorqOrder` in the picker, use that selected destination normally.

Official Android storage guidance reviewed for this plan: [Access documents and other files from shared storage](https://developer.android.com/training/data-storage/shared/documents-files).

## 6. XLSX workbook and delivery contract

Milestone 12 implements XLSX as a one-off file export parallel to CSV. Every export uses a fresh
system create-document flow; WorqOrder never opens, selects, connects, reads, or updates an
existing workbook.

- MIME type:
  `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.
- Suggested filename: `worqorder_YYYY-MM-DD.xlsx`.
- Android launches `ActivityResultContracts.CreateDocument` for every export. It stores no
  document URI, persistable grant, workbook name, or connection status.
- Each new workbook contains exactly one visible worksheet named
  `WorqOrder_YYYY-MM-DD`. Row 1 contains the exact 15 canonical headers and row 2 onward contains
  the same canonical rows used by CSV and Google Sheets.
- User text, dates, times, interval numbers, and durations are literal inline-string cells.
  Formula-like values beginning with `=`, `+`, `-`, or `@` never become formulas. Formatted
  durations remain text so accumulated hours do not wrap at 24.
- The focused internal writer emits a deterministic, minimal OOXML ZIP package with no macros,
  formulas, external links, hidden worksheets, credentials, account metadata, or
  application-private encryption keys. Apache POI and other Excel-generation dependencies are not
  used.
- The complete package is built in memory from the immutable snapshot before the picker opens.
  Cancellation therefore writes nothing and reports no success. After a URI is returned, output
  is written once and closed deterministically; failure triggers best-effort deletion of the
  partial provider document and never modifies Room.
- Repeated exports intentionally create independent workbooks. Each contains exactly one complete
  authoritative snapshot, so there is no within-workbook append, duplicate-row, ownership-marker,
  tab-conflict, or existing-content preservation behavior.
- XLSX output is an unencrypted user-controlled file. The required production build does not add
  WorqOrder-managed encryption to local Room/DataStore storage, and release-agnostic optional
  Milestone E, if ever separately assigned, would
  not extend local encryption to an exported document. Protecting, sharing, or deleting that
  external file is the user's responsibility.

The implemented package contains these fixed parts in deterministic order:

1. `[Content_Types].xml`
2. `_rels/.rels`
3. `xl/workbook.xml`
4. `xl/_rels/workbook.xml.rels`
5. `xl/styles.xml`
6. `xl/worksheets/sheet1.xml`

For schema version 4, `sheet1.xml` declares `A1:O<last-row>`, writes the header with the package's
bold text style, and
writes every canonical value as an `inlineStr` cell with `xml:space="preserve"`. SpreadsheetML
escape sequences preserve carriage returns and otherwise-illegal XML control characters; literal
user text that already resembles `_xHHHH_` is escaped so it is not misinterpreted. ZIP entry names
are fixed, entry timestamps are deterministic, and no task/client/account value appears in package
metadata.

Milestone 12 verification parses every generated workbook with an independent test reader and
manually opens representative outputs in Microsoft Excel and LibreOffice. Golden tests cover the
unified schema, exact single-sheet name, empty/zero/multiple intervals, running-export lockout, stable order,
clock-only local values, long durations, Unicode, commas, quotes, CR/LF, formula-prefixed text,
package integrity, large-snapshot memory/time, picker cancellation/output failure, repeated
independent exports, and no Room mutation.

## 7. Google connection model

Exactly one spreadsheet ID is connected at a time. Connection comprises:

- a Google account selected through Credential Manager;
- a user-confirmed Google Picker per-file grant managed by `AuthorizationClient`;
- a validated spreadsheet ID;
- its last validated title; and
- a connection timestamp/status (non-secret metadata).

Accept a canonical spreadsheet ID or a supported Sheets URL and parse the ID locally. Reject
malformed/oversized inputs. A pasted ID is not itself authorization. Connection launches the
current Android Google Picker authorization flow filtered to that exact ID and the Google Sheets
MIME type. A nonempty returned `picked_file_ids` set must contain exactly the parsed ID. After
Disconnect, the per-file grant intentionally remains; Google may therefore return an empty Picker
ID set when that exact grant is reused. An empty set is never sufficient by itself: the parsed ID
must still pass the `drive.file`-bounded Drive and Sheets checks below. A nonempty mismatch fails
without validation or persistence.

Validation then uses the returned `drive.file` token for:

1. Drive v3 `files.get` with
   `id,name,mimeType,trashed,capabilities(canEdit,canModifyContent)` to prove the selected file is
   an editable, modifiable, non-trashed Google spreadsheet; and
2. Sheets v4 `spreadsheets.get` with a narrow field mask sufficient for canonical spreadsheet
   ID/title and sheet properties.

Success proves that the authorized account and app grant can access/edit the selected spreadsheet
at that moment without performing a test write. It never creates a spreadsheet.

Disconnect clears local ID/title association only and leaves the Google identity/grant intact.
Sign-out makes export unavailable, attempts `AuthorizationClient.revokeAccess()`, clears
Credential Manager state, and clears the account hint plus all connected-spreadsheet metadata.
Neither action edits the spreadsheet or Room. If permissions later change, the next
validation/export reports permission/not-found status.

Settings renders Google Sheets Connection only when Google Sheets is the selected Export
Destination. A connected state hides the URL/ID and Validate and Connect controls; the user must
Disconnect before connecting a different spreadsheet. Selecting Google Sheets automatically
scrolls Settings to the newly revealed connection section. At the bottom of the same Export
Destination card, below the sign-in and spreadsheet-connection options, render a switch labeled
**Auto Export** with supporting text **Automatically export tasks at the end of each day.** Hide the
entire switch row for CSV and XLSX.

### Authentication and authorization choice

Identity and Google-data authorization are distinct. The official-document review completed on
2026-07-26 selects:

- AndroidX Credential Manager and Google ID for explicit Sign in with Google/account choice;
- Google Identity Services `AuthorizationClient` for data authorization and Picker;
- only `https://www.googleapis.com/auth/drive.file`;
- the Android Picker resource-parameter flow filtered to the pasted ID;
- fresh short-lived access tokens requested for explicit operations and held only in memory; and
- small Drive v3/Sheets v4 HTTPS/JSON REST gateways.

The current Android Picker flow resolves the earlier least-scope tradeoff. Google added official
Android guidance for `PICKER_OAUTH_TRIGGER`, `PICKER_FILE_IDS`, MIME filtering, and returned
`picked_file_ids`. The per-file grant preserves URL/ID input while requiring the user to confirm
sharing that file with WorqOrder. `drive.file` is non-sensitive and accepted by the Sheets
read/write methods used by WorqOrder.

`https://www.googleapis.com/auth/spreadsheets` is rejected. It supports paste-only access but is
sensitive, can access every spreadsheet available to the user, and conflicts with the fixed
no-cost/no-domain policy. If the selected `drive.file`/Picker workflow later becomes unavailable,
pause Google integration for a new owner decision and leave CSV operational. Drive-wide, profile,
email, and restricted scopes are not requested by the Sheets authorization flow.

Credential Manager returns an ID token, but WorqOrder has no backend to perform server-side token
verification. The raw ID token is discarded and cannot become an application security authority.
Only an optional non-secret account display hint may persist. Sheets authorization depends solely
on the `AuthorizationClient` access token.

Detailed rationale, dependency versions, token/sign-out rules, alternatives, and tests are in
`GOOGLE_INTEGRATION_ADR.md`.

Current official references reviewed on 2026-07-26:

- [Authorize access to Google user data](https://developer.android.com/identity/authorization)
- [Implement Sign in with Google](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation)
- [Choose Google Sheets API scopes](https://developers.google.com/workspace/sheets/api/scopes)
- [Google Picker for desktop and mobile apps](https://developers.google.com/workspace/drive/picker/guides/desktop-mobile-picker)
- [Choose Google Drive API scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)
- [Sheets `spreadsheets.batchUpdate`](https://developers.google.com/workspace/sheets/api/reference/rest/v4/spreadsheets/batchUpdate)
- [Sheets batch-update operations and `AddSheetRequest`](https://developers.google.com/workspace/sheets/api/guides/batchupdate)
- [Read, write, and search developer metadata](https://developers.google.com/workspace/sheets/api/guides/metadata)
- [Google Drive/Sheets file and cell limits](https://support.google.com/drive/answer/37603)

## 8. Google worksheet contract

For displayed date `YYYY-MM-DD`, the application-owned tab name is exactly:

```text
WorqOrder_YYYY-MM-DD
```

Visible layout:

| Cell/range | Content |
| --- | --- |
| row 1 | the 15 exact canonical column headers |
| row 2 onward | canonical export rows |

The ownership values are stored as sheet-scoped, `PROJECT`-visible developer metadata created by
WorqOrder's Google Cloud project, not as visible cells:

| Metadata key | Required value |
| --- | --- |
| `worqorder_export_marker` | `WORQORDER_EXPORT` |
| `worqorder_export_schema` | `4` |
| `worqorder_export_work_date` | the tab's `YYYY-MM-DD` date |

All three keys must occur exactly once at that sheet location. A missing/different marker makes a
same-named tab unowned. A valid marker with missing, duplicate, or incompatible schema/date
metadata is a schema conflict. This keeps visible content and row positions equivalent to
CSV/XLSX while retaining safe ownership checks. The export timestamp and task ZoneIds remain
internal snapshot/model data; they are intentionally not visible cells or ownership keys in
schema version 4. The table is application-owned. Users are warned that edits inside a marked tab
will be replaced on the next export. Other tabs are never touched.

Google Sheets has no published fixed sheet-count limit, but the connected spreadsheet has a
10-million-cell total limit. Add each date sheet with exactly 15 columns and only enough rows for
its header/data, and resize it on re-export so unused grid allocation does not consume the
spreadsheet unnecessarily.

### First export for a date

1. Obtain a fresh short-lived `drive.file` token with a normal
   `AuthorizationClient.authorize()` request. This request does not relaunch Picker while the
   existing per-file grant remains valid.
2. Call `GET /v4/spreadsheets/{spreadsheetId}` with `includeGridData=false` and the exact field
   mask for spreadsheet ID, sheet IDs/titles/grid sizes, and developer metadata nested on each
   owning sheet (plus spreadsheet-level metadata for defensive compatibility).
3. If no WorqOrder metadata exists yet and the date tab is absent, inspect user-entered cell and
   sheet content. A confirmed completely blank spreadsheet reuses its original first sheet; any
   populated or indeterminate spreadsheet preserves all existing tabs.
4. Send one `POST
   /v4/spreadsheets/{spreadsheetId}:batchUpdate` containing, in order:
   - either an `UpdateSheetPropertiesRequest` that renames/right-sizes the confirmed blank first
     sheet, or an `AddSheetRequest` with a collision-free non-negative sheet ID, exact title, 15
     columns, and `1 + dataRowCount` rows;
   - three `CreateDeveloperMetadataRequest` entries for the exact keys/values above; and
   - one `UpdateCellsRequest` covering row 1 through the final data row and columns 1 through 15.
5. Every cell is a `userEnteredValue.stringValue`. This is the `UpdateCellsRequest` equivalent of
   a raw literal write: formula-like client/task text is not parsed as a formula, and the gateway
   does not independently format any canonical value.
6. Return success only for a successful batch response whose confirmed `spreadsheetId` exactly
   matches the connected spreadsheet.

### Re-export

1. If the exact tab exists, read its marker/schema/date developer metadata from the sheet resource
   that owns it.
2. If marker is absent/different, return `TabNameConflict`; do not clear/write/rename the tab.
3. If the marker/date is valid and schema is `2` or `3`, treat it as a known WorqOrder-owned predecessor:
   atomically replace it with schema-4 metadata, headers, and rows. A newer/unknown schema or
   incompatible date remains a conflict and is not overwritten.
4. Send one atomic `spreadsheets.batchUpdate` containing an `UpdateSheetPropertiesRequest` that
   right-sizes the owned grid to exactly 15 columns and `1 + dataRowCount` rows, followed by an
   `UpdateCellsRequest` that replaces the complete header/data table. Shrinking removes obsolete
   trailing rows/cells.
5. Write rows in the shared stable sort order; do not independently sort or format at the gateway.
6. Return success only after confirmed update.

Replacement, not append-only merging, is authoritative. This resolves contradictory earlier append wording and ensures edits, deleted intervals, deleted tasks, and changed metadata are reflected without duplicates. Repeating unchanged export produces the same visible table.

Use raw string cell values for all 15 canonical fields so consultant, client, task text, Work type,
Billing Status, and Mileage are not evaluated as formulas and CSV/XLSX/Google content remains equivalent. Do not
create a new spreadsheet document at export.

Milestone 11 implements the sequence above through `GoogleSheetsExportCoordinator`, the pure
`GoogleSheetsExportPlanner`, `GoogleSheetsBatchJsonEncoder`, and `RestGoogleSheetsGateway`.
`ExportSnapshotCoordinator` remains the only source of field selection/order/formatting. The
gateway performs no automatic retry. An interrupted mutation response is classified as ambiguous
and never reported as success; retry is safe because the next complete batch replaces the same
marked date tab. The gateway parses sheet-scoped metadata from each `sheets[].developerMetadata`
collection; this is required for a subsequent export to recognize the marker it created.

## 9. v0.2.0 automatic Google daily export

Automation is opt-in and defaults off. Its switch exists only while Google Sheets is the selected
export destination and remains disabled until an authorized account and connected spreadsheet are
available. CSV and XLSX remain exclusively manual and never receive background destination access.

Scheduling uses stable WorkManager `2.11.2` and a uniquely named, non-expedited
`OneTimeWorkRequest` with `NetworkType.CONNECTED`; it does not promise an exact 11:59:00 alarm.
Recalculate and enqueue the next one-time request after each terminal target instead of using a
fixed 24-hour periodic request, so DST and geographical-zone rules remain explicit. When
scheduling near the end of a local date, capture all of these durable inputs:

- the target `workDate`/epoch day computed in the then-effective `ZoneId`;
- that canonical ZoneId string;
- a stable unique-work key plus the connected-spreadsheet association/fingerprint; and
- a non-sensitive pending state, never snapshot rows or a token.

Execution may occur shortly before or after midnight. It always prepares the captured target date,
not `today` at execution time. WorkManager persists/reschedules work across app restart and reboot
and honors Doze, but force-stop, OEM restrictions, constraints, and network availability can delay
it. AlarmManager/exact alarms, expedited work, and a foreground service are prohibited here.

At execution:

1. If the target was already confirmed exported by this automatic job, exit idempotently.
2. Revalidate that automation remains enabled, Google is still the selected destination, the same
   spreadsheet is connected, and authorization can be obtained through the approved flow. A
   worker may proceed only when `AuthorizationClient.authorize()` returns an already-granted token
   without interaction. A returned `PendingIntent` becomes authorization-required pending state;
   it is never launched from the worker.
3. If no timer is active, build a new authoritative immutable schema-4 snapshot for the captured
   target date and run the same marker-validated replacement protocol as manual Google export.
4. If any timer is active, do not snapshot or export. Persist the captured date as pending.
5. After a successful Stop transaction, post a content-free system notification that indicates a
   pending WorqOrder export without client/task names. Tapping it opens/resumes the app and
   performs or explicitly confirms Google export for that preserved date. Dismissal leaves the
   pending date recoverable in the app; it never marks success.
6. A successful automatic write advances to the next unresolved date and produces no Main-screen
   success state and no success notification. Failure stores only a safe typed reason and provides
   an actionable recovery path; it never mutates Room and returns a terminal WorkManager result
   rather than entering automatic retry/backoff.

Manual and automatic Google operations for the same date converge because both replace the same
owned schema-4 tab from current Room truth. Concurrent work must be uniquely serialized so a
single date cannot produce duplicate tabs or rows. Reboot, Doze, time-zone change, offline,
authorization expiry, disconnect/sign-out, permission loss, quota, and ambiguous response all
retain the captured-date rule and fail safely.

Only the oldest unresolved target is active. It is never overwritten by a newer scheduled date.
After success, advance one date at a time; if later dates became due during a multi-day timer or
offline period, enqueue the next due target immediately. Each worker handles at most one work date,
so catch-up is durable and bounded rather than an in-process loop. Selecting CSV/XLSX, disabling
automation, disconnecting, or signing out explicitly cancels unique work and clears automatic
pending state without changing Room or remote spreadsheet content.

Blocked-state notifications use one API-26+ **Pending Google Export** channel and contain no task,
client, consultant, account, spreadsheet, date, or exported-row content. On API 33+, enabling
automation requires `POST_NOTIFICATIONS`; the user can deny it, in which case the switch stays off.
If notification access is revoked later, pending state remains visible/recoverable in Google
Settings. Dismissing a notification never dismisses the pending export.

## 10. Google failure behavior

Typed failures and UI behavior:

| Failure | Behavior |
| --- | --- |
| Offline/timeout | show retryable offline error; local data unchanged |
| Authorization required/expired | preserve spreadsheet metadata, prompt reauthorization, allow retry |
| User cancels account/consent | neutral canceled outcome; do not claim connection/export |
| Permission denied/not found | mark validation stale, show account/access guidance |
| Same-name unmarked tab | show conflict with exact tab name; no overwrite |
| Newer/unknown marker schema | show compatibility conflict; no overwrite |
| Rate limit/server failure | preserve safe error and retry option; optional bounded backoff only during explicit operation |
| Partial/ambiguous remote response | do not claim success; re-read marker/table or allow idempotent retry |

Google can be collaboratively edited between read and write. Minimize the window, revalidate marker immediately before mutation where practical, and use an atomic batch. The API cannot make the external document a transactional peer of Room; local data is never rolled back or changed in response.

The current implementation performs one immediate structure/marker read followed by one atomic
batch. HTTP 401 clears the exact in-memory token and marks authorization stale; 404 maps to
missing/not-granted; non-quota 403 maps to permission denied; quota-flavored 403 and 429 map to
rate limited; connection/timeout/5xx/malformed responses remain distinct. No Room export-history
table exists. The already-approved typed `last_export_*` DataStore metadata records only
destination, work date, attempt time, success/cancel/failure, and a non-sensitive error category;
it is diagnostic presentation history and never stores spreadsheet IDs, rows, tokens, or API
responses.

## 11. Google Cloud setup guide

The complete developer procedure is in `GOOGLE_SHEETS_SETUP.md`. Its fixed inputs are:

- package/application ID `worq.order`;
- developer-controlled Google Cloud project under a personal Google account;
- Google Sheets API, Google Drive API, and Google Picker API enabled;
- only `https://www.googleapis.com/auth/drive.file` declared for Google data access;
- one debug Android OAuth client now and one direct-release client created in Milestone 17;
- one Web OAuth client ID for Credential Manager;
- an External audience in Testing during development and In Production for release, where any
  eligible Google Account can authorize without being added to a test-user list;
- no marketplace client, Workspace organization, custom domain, brand-verification dependency,
  Google Cloud billing account, or paid quota increase;
- an uncommitted `worqorder.google.webClientId` local/CI Gradle property; and
- no Firebase, `google-services.json`, API-key authorization, service account, client secret,
  token, signing key, or password in the repository/APK.

All calls must remain inside Google's no-cost standard quota. Quota exhaustion is reported and is
not bypassed through billing, paid capacity, background traffic, or unbounded retries.

## 12. Export tests

Tests must prove:

- exact schema/header/order and zero-task/zero-interval behavior across all three destinations;
- commas, quotes, CR/LF, Unicode, both task text fields, long hours, `hh:mm a` task-zone clock
  values, and sub-second duration truncation;
- Main and its event handler reject every export destination while a timer is running;
- picker cancellation writes nothing, changes no local data, and shows no transient status;
- serializer/output failures do not claim success;
- XLSX package/cell-type integrity, literal formula-like text, independent-reader compatibility,
  bounded-memory behavior, and no app-private plaintext staging;
- one connected spreadsheet only;
- URL/ID parsing and validation;
- create marked date tab with invisible metadata, exact 15-column visible table, re-export
  replacement, obsolete-row clearing/grid resizing, and stable shared ordering;
- unchanged re-export has no duplicate rows;
- local edit/delete is reflected by replacement;
- unmarked tab and incompatible marker are untouched;
- Google/XLSX date tabs have equivalent visible headers/rows and no destination-specific field
  selection or formatting;
- one-off XLSX launches a fresh create-document flow for each export, contains exactly one date
  tab, retains no URI metadata, and never reads an existing workbook;
- offline, auth expiration, permission, rate-limit, server, and ambiguous-response states are useful/retryable; and
- all failures leave Room task data unchanged.

Version `0.2.0` additionally tests consultant snapshot stability, Work type, nullable/exact Billing
Status, normalized/blank Mileage, Billing minutes at zero/positive/boundary/long totals, exact 15 renamed headers and
duplicated `MM/DD/YYYY` Start date/End date values,
schema-2/schema-3 owned-tab upgrade to schema 4, unowned-tab protection, and equivalent CSV/XLSX/Google
values. Automatic-export tests cover captured-date execution before/after midnight, inexact delay,
running-timer pending state, post-Stop notification action, notification dismissal, reboot/Doze,
zone changes, disconnect/sign-out, authorization/offline/quota failure, manual/automatic races,
idempotency, no Main success message, no sensitive notification content, and no CSV/XLSX schedule.

## 13. Planned canonical export schema version 5 (`0.3.0`)

Schema 5 becomes active only with the Room single-interval migration and replaces schema 4 across
CSV, XLSX, manual Google, and automatic Google together. Every task produces exactly one row.
There is no interval ordinal and no second duration value.

The exact visible column order and spelling is:

| # | Header | Value |
| ---: | --- | --- |
| 1 | Start date | task work date as `MM/dd/yyyy` |
| 2 | End date | the same task work date as `MM/dd/yyyy` |
| 3 | Consultant | assignment-time Consultant snapshot or blank |
| 4 | Client | current referenced Client display name |
| 5 | Description | complete task Description |
| 6 | Expense | complete Hardware / Software Purchases value |
| 7 | Work type | `On-Site`, `In-Office`, or blank migrated value |
| 8 | Billing Status | `Billable`, `Do not bill`, `Do not charge`, or blank migrated value |
| 9 | Mileage | canonical non-negative decimal text or blank |
| 10 | Start time | sole interval start in task ZoneId as `hh:mm AM/PM`, or blank |
| 11 | Stop time | sole completed stop in task ZoneId as `hh:mm AM/PM`, or blank |
| 12 | Time spent | sole completed duration as accumulated `HH:MM:SS`; `00:00:00` when untimed |
| 13 | Billing minutes | derived 15-minute-ceiling integer; `0` when untimed |

`Interval number` and `Interval duration` are intentionally absent. Exact UTC instants, IDs,
ZoneIds, manual-edit state, and internal schema metadata remain in Room or the immutable snapshot
context but are not visible export columns.

### Snapshot and ordering

The coordinator first performs any required midnight boundary closure, verifies that no unstable
target-date interval remains, and captures one immutable task-plus-optional-interval projection.
Sort tasks by creation timestamp ascending and task ID ascending, matching schema 4's stable task
order. There is no interval-level sort. Picker delay or UI changes cannot mutate the prepared
snapshot, and export never mutates Room.

### Destination effects

- CSV emits the exact 13 headers and one RFC-style UTF-8 row per task.
- XLSX writes one worksheet with an `A:M` table and the same literal values.
- Google writes headers/data below its existing ownership marker. An owned schema-2, schema-3, or
  schema-4 tab may be atomically replaced and upgraded to schema 5; clear the complete previous
  application-owned range so obsolete columns N/O cannot remain. Unknown/newer markers and
  unowned same-name tabs still fail closed.
- Re-export remains authoritative replacement and duplicate-free. A schema-5 task cannot create
  multiple rows.
- Automatic Google export uses the captured preceding date after midnight closure. CSV and XLSX
  remain user-initiated `ACTION_CREATE_DOCUMENT` flows.

Example header:

```csv
Start date,End date,Consultant,Client,Description,Expense,Work type,Billing Status,Mileage,Start time,Stop time,Time spent,Billing minutes
```

An untimed task has blank Start/Stop, `00:00:00` Time spent, and `0` Billing minutes. A running
interval is never projected with a blank Stop as a successful export row.
