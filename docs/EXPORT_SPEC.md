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

## 3. Canonical export schema version 2

Schema version 2 is internal compatibility metadata. It is used by WorqOrder markers and future
export adapters but is not a visible data column. Every destination presents these exact nine
columns in this exact order:

| # | Column | Encoding |
| ---: | --- | --- |
| 1 | Work Date | `YYYY-MM-DD` |
| 2 | Client Name | retained current client name |
| 3 | Description | short task description |
| 4 | Hardware / Software Purchases | optional task text; blank when none |
| 5 | Interval Number | stable positive ordinal or blank |
| 6 | Start Local | task-zone local clock time `HH:mm`, or blank |
| 7 | Stop Local | task-zone local clock time `HH:mm`, or blank |
| 8 | Interval Duration Formatted | accumulated `HH:MM:SS`, or blank |
| 9 | Task Total Duration Formatted | accumulated `HH:MM:SS` |

Start/Stop instants are converted using the task's stored geographical ZoneId and only then reduced
to 24-hour `HH:mm`. Seconds, fractional seconds, date, offset, and ZoneId are intentionally omitted
from the export; the complete instants and task ZoneId remain stored in Room. Two fall-back
occurrences can therefore display the same clock time even though the app retains distinct
instants.

Duration output truncates any sub-second remainder rather than rounding and never wraps accumulated
hours at 24. Empty optional values are empty fields, not the strings `null` or `N/A`.

Rows sort using retained internal metadata: task creation instant, task ID, interval start (null
last), interval ordinal, and interval ID. Those sort keys are not exported. This preserves stable,
deterministic output across all three destinations. Changing a column, encoding, or sort rule
requires one schema-version decision in this document and one change to the shared builder.

Room and domain models continue retaining every version-1 field that was removed from the external
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

CSV faithfully preserves client, description, and hardware/software-purchases text. Some spreadsheet programs interpret cells beginning with `=`, `+`, `-`, or `@` as formulas when opening CSV. RFC quoting does not prevent that behavior. Silently prefixing text would change exported data, so formula-injection transformation is not part of schema version 2; flag it in release security review and document safe import behavior.

### Schema-version 2 examples

The first record is always the following exact header:

```csv
Work Date,Client Name,Description,Hardware / Software Purchases,Interval Number,Start Local,Stop Local,Interval Duration Formatted,Task Total Duration Formatted
```

A completed interval may serialize as:

```csv
2026-07-24,"Acme, Inc.","Repair ""north"" unit",Laptop,1,09:00,10:00,01:00:00,01:00:00
```

A task without intervals has blank interval number/start/stop/duration and a zero task total:

```csv
2026-07-24,Example Client,Planning,,,,,,00:00:00
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
  `WorqOrder_YYYY-MM-DD`. Row 1 contains the exact nine canonical headers and row 2 onward contains
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
  WorqOrder-managed encryption to local Room/DataStore storage, and optional Milestone 19 would
  not extend local encryption to an exported document. Protecting, sharing, or deleting that
  external file is the user's responsibility.

The implemented package contains these fixed parts in deterministic order:

1. `[Content_Types].xml`
2. `_rels/.rels`
3. `xl/workbook.xml`
4. `xl/_rels/workbook.xml.rels`
5. `xl/styles.xml`
6. `xl/worksheets/sheet1.xml`

`sheet1.xml` declares `A1:I<last-row>`, writes the header with the package's bold text style, and
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
scrolls Settings to the newly revealed connection section.

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
| row 1 | the nine exact canonical column headers |
| row 2 onward | canonical export rows |

The ownership values are stored as sheet-scoped, `PROJECT`-visible developer metadata created by
WorqOrder's Google Cloud project, not as visible cells:

| Metadata key | Required value |
| --- | --- |
| `worqorder_export_marker` | `WORQORDER_EXPORT` |
| `worqorder_export_schema` | `2` |
| `worqorder_export_work_date` | the tab's `YYYY-MM-DD` date |

All three keys must occur exactly once at that sheet location. A missing/different marker makes a
same-named tab unowned. A valid marker with missing, duplicate, or incompatible schema/date
metadata is a schema conflict. This keeps visible content and row positions equivalent to
CSV/XLSX while retaining safe ownership checks. The export timestamp and task ZoneIds remain
internal snapshot/model data; they are intentionally not visible cells or ownership keys in
schema version 2. The table is application-owned. Users are warned that edits inside a marked tab
will be replaced on the next export. Other tabs are never touched.

Google Sheets has no published fixed sheet-count limit, but the connected spreadsheet has a
10-million-cell total limit. Add each date sheet with exactly nine columns and only enough rows for
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
     sheet, or an `AddSheetRequest` with a collision-free non-negative sheet ID, exact title, nine
     columns, and `1 + dataRowCount` rows;
   - three `CreateDeveloperMetadataRequest` entries for the exact keys/values above; and
   - one `UpdateCellsRequest` covering row 1 through the final data row and columns 1 through 9.
5. Every cell is a `userEnteredValue.stringValue`. This is the `UpdateCellsRequest` equivalent of
   a raw literal write: formula-like client/task text is not parsed as a formula, and the gateway
   does not independently format any canonical value.
6. Return success only for a successful batch response whose confirmed `spreadsheetId` exactly
   matches the connected spreadsheet.

### Re-export

1. If the exact tab exists, read its marker/schema/date developer metadata from the sheet resource
   that owns it.
2. If marker is absent/different, return `TabNameConflict`; do not clear/write/rename the tab.
3. If marker is valid but schema/date is incompatible, return a schema conflict; do not overwrite.
4. Send one atomic `spreadsheets.batchUpdate` containing an `UpdateSheetPropertiesRequest` that
   right-sizes the owned grid to exactly nine columns and `1 + dataRowCount` rows, followed by an
   `UpdateCellsRequest` that replaces the complete header/data table. Shrinking removes obsolete
   trailing rows/cells.
5. Write rows in the shared stable sort order; do not independently sort or format at the gateway.
6. Return success only after confirmed update.

Replacement, not append-only merging, is authoritative. This resolves contradictory earlier append wording and ensures edits, deleted intervals, deleted tasks, and changed metadata are reflected without duplicates. Repeating unchanged export produces the same visible table.

Use raw string cell values for all nine canonical fields so client, description, and
hardware/software-purchases text are not evaluated as formulas and CSV/XLSX/Google content remains
equivalent. Do not create a new spreadsheet document at export.

Milestone 11 implements the sequence above through `GoogleSheetsExportCoordinator`, the pure
`GoogleSheetsExportPlanner`, `GoogleSheetsBatchJsonEncoder`, and `RestGoogleSheetsGateway`.
`ExportSnapshotCoordinator` remains the only source of field selection/order/formatting. The
gateway performs no automatic retry. An interrupted mutation response is classified as ambiguous
and never reported as success; retry is safe because the next complete batch replaces the same
marked date tab. The gateway parses sheet-scoped metadata from each `sheets[].developerMetadata`
collection; this is required for a subsequent export to recognize the marker it created.

## 9. Google failure behavior

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

## 10. Google Cloud setup guide

The complete developer procedure is in `GOOGLE_SHEETS_SETUP.md`. Its fixed inputs are:

- package/application ID `worq.order`;
- developer-controlled Google Cloud project under a personal Google account;
- Google Sheets API, Google Drive API, and Google Picker API enabled;
- only `https://www.googleapis.com/auth/drive.file` declared for Google data access;
- one debug Android OAuth client now and one direct-release client created in Milestone 17;
- one Web OAuth client ID for Credential Manager;
- an External audience in Testing during development and In Production for release, where any
  eligible Google Account can authorize without being added to a test-user list;
- no Google Play client, Workspace organization, custom domain, brand-verification dependency,
  Google Cloud billing account, or paid quota increase;
- an uncommitted `worqorder.google.webClientId` local/CI Gradle property; and
- no Firebase, `google-services.json`, API-key authorization, service account, client secret,
  token, signing key, or password in the repository/APK.

All calls must remain inside Google's no-cost standard quota. Quota exhaustion is reported and is
not bypassed through billing, paid capacity, background traffic, or unbounded retries.

## 11. Export tests

Tests must prove:

- exact schema/header/order and zero-task/zero-interval behavior across all three destinations;
- commas, quotes, CR/LF, Unicode, both task text fields, long hours, `HH:mm` task-zone clock
  values, and sub-second duration truncation;
- Main and its event handler reject every export destination while a timer is running;
- picker cancellation writes nothing, changes no local data, and shows no transient status;
- serializer/output failures do not claim success;
- XLSX package/cell-type integrity, literal formula-like text, independent-reader compatibility,
  bounded-memory behavior, and no app-private plaintext staging;
- one connected spreadsheet only;
- URL/ID parsing and validation;
- create marked date tab with invisible metadata, exact nine-column visible table, re-export
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
