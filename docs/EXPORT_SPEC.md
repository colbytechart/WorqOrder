# Export Specification

## 1. Principles

- Export exactly the date displayed on the main screen.
- Build one immutable logical snapshot from Room, the authoritative source.
- Use the same logical columns, row ordering, and value semantics for CSV, XLSX, and Google Sheets.
- Export is one-way. It never imports, marks tasks exported, deletes local records, or resolves changes made in an external copy.
- A retry is safe. Google Sheets and persistent-XLSX re-export replace the existing date tab
  idempotently; CSV may intentionally create another file.
- XLSX is an approved focused destination after Google Sheets export. Do not use Apache POI or
  another broad Excel stack without explicit owner approval. Firebase, service accounts, custom
  backends, broad storage permissions, and embedded secrets remain prohibited.

## 2. Snapshot process

`ExportSnapshotCoordinator.prepare(displayedDate)`:

1. Capture one `exportInstant` and the effective/pinned zone context.
2. Normalize any active interval across all boundaries through that instant.
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
with blank interval number/start/stop/duration and a zero task total. A running interval emits a
row with blank Stop Local and durations calculated through the internal snapshot instant; export
does not stop it. The running state and snapshot instant remain internal metadata rather than
visible columns.

Milestone 8 implements this as follows: one shared timer-operation lock covers capture of
`exportInstant`, boundary normalization, and the Room snapshot read. The DAO loads every joined
task/client and its ordered intervals for the displayed date inside one Room transaction. The row
builder produces one immutable destination-neutral dataset. The CSV adapter serializes that
dataset into one in-memory string before the create-document picker opens. Changes made after the
picker opens cannot change that pending payload. Future XLSX and Google adapters consume the same
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
- Picker cancellation before a URI is returned is a neutral `Canceled` outcome and creates no file/partial file.
- On output failure after creation, close the stream, attempt deletion only through the granted document API when supported, and report that a partial provider document may remain if deletion is unsupported.
- Repeating export is permitted. `ACTION_CREATE_DOCUMENT` may disambiguate an existing filename; do not overwrite unrelated files silently.

The Android adapter uses `ActivityResultContracts.CreateDocument("text/csv")`, which creates the
standard `ACTION_CREATE_DOCUMENT` intent with the MIME type and suggested filename. The AndroidX
contract does not promise that `CATEGORY_OPENABLE` is explicitly present when inspecting the raw
intent, so tests assert the action, MIME type, and title rather than an undocumented category.
After a URI is returned, `ContentResolver.openOutputStream(uri, "wt")` writes the already
serialized payload as UTF-8. Provider failure triggers a best-effort `ContentResolver.delete` of
that exact granted URI. The UI distinguishes success, neutral cancellation, retryable write
failure, and the case where a partial provider document could not be removed.

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

XLSX is implemented only in Milestone 12, after the Google Sheets milestones. Until then it is a
documented destination, not an available action. Production uses exactly one connected persistent
workbook at a time; one-off XLSX files are deferred to optional Milestone 18.

- MIME type:
  `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.
- Suggested initial workbook name: `worqorder.xlsx`.
- Connection: create a workbook through `ACTION_CREATE_DOCUMENT` or select an existing `.xlsx`
  document through the appropriate SAF open-document flow, retain only the user-granted
  persistable URI permission, validate the OOXML package, and show its display name/status in
  Settings. Disconnect clears local URI metadata and never deletes the external file.
- Workbook: one visible worksheet named `WorqOrder_YYYY-MM-DD` for every exported date. Each tab
  begins at row 1 with the exact nine headers followed by the same canonical rows used by CSV and
  Google Sheets.
- User text, dates, times, interval numbers, and durations are literal text cells. Formula-like values
  beginning with `=`, `+`, `-`, or `@` must not become formulas. Duration milliseconds may be
  retained internally but are not workbook columns; formatted durations remain text so
  accumulated hours do not wrap at 24.
- The workbook contains no macros, external links, hidden worksheets, credentials, account
  metadata, or application-private encryption keys.
- Store the WorqOrder marker, internal schema version, and owned-date-tab mapping in reviewed
  non-visible workbook/package metadata rather than visible cells. If a same-named worksheet lacks
  the expected marker, report a conflict and do not overwrite it.
- If a date tab is absent, add it. If a correctly marked date tab exists, replace its entire
  application-owned table with the current authoritative snapshot, removing obsolete rows.
  Never append/merge interval rows; unchanged re-export produces no duplicates.
- Build the output with a focused, deterministic, Android-compatible OOXML writer. Dependency
  choice is a Milestone 12 gate covering stable status, GPLv3 compatibility, transitive size,
  minimum API, memory, and security. Apache POI remains prohibited absent a new owner decision.
- The implementation gate must prove a provider-safe read/modify/rewrite strategy that preserves
  unrelated worksheets and the last valid workbook if a write is interrupted. Do not stage an
  unencrypted workbook in an app-private temporary file.
- If the connected URI is moved, deleted, revoked, malformed, or no longer writable, invalidate
  the stale connection without crashing and launch `ACTION_CREATE_DOCUMENT`. Once the user selects
  a destination, create a fresh workbook containing the currently requested date tab. Cancellation
  leaves XLSX safely disconnected and does not claim export success or modify Room. Android cannot
  silently choose an arbitrary replacement location without this user-mediated step.
- XLSX output is an unencrypted user-controlled file. Local Keystore-backed encryption ends at the
  document handoff; protecting, sharing, or deleting that external file is the user's
  responsibility.

Milestone 12 verification must parse every generated workbook with an independent test reader and
manually open representative outputs in Microsoft Excel and LibreOffice. Golden tests cover the
unified schema, empty/zero/multiple/running intervals, stable order, clock-only local values,
long durations, Unicode and multiline/formula-prefixed text, package integrity, preservation of
unrelated tabs, stale/missing/revoked URI recovery, interrupted rewrite, large-workbook
performance, picker cancellation/failure, repeated date replacement, and no Room mutation.

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
MIME type. The returned `picked_file_ids` must contain exactly the parsed ID.

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
Credential Manager state, and clears the account hint. Spreadsheet ID/title may remain as stale
metadata but must be Picker-granted and revalidated after another sign-in. Neither action edits the
spreadsheet or Room. If permissions later change, the next validation/export reports
permission/not-found status.

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

The exact marker `WORQORDER_EXPORT`, internal schema version `2`, and work date are stored as
sheet-scoped developer metadata created by WorqOrder's Google Cloud project, not as visible cells.
This keeps visible content and row positions equivalent to CSV/XLSX while retaining safe ownership
checks. The table is application-owned. Users are warned that edits inside a marked tab will be
replaced on the next export. Other tabs are never touched.

Google Sheets has no published fixed sheet-count limit, but the connected spreadsheet has a
10-million-cell total limit. Add each date sheet with exactly nine columns and only enough rows for
its header/data, and resize it on re-export so unused grid allocation does not consume the
spreadsheet unnecessarily.

### First export for a date

1. Fetch spreadsheet metadata and find exact tab name.
2. If absent, use `AddSheetRequest` with a chosen sheet ID, nine columns, and the required row
   count; add the sheet-scoped marker/schema/date developer metadata and header/data.
3. Prefer one atomic `spreadsheets.batchUpdate` containing structural/cell update requests when supported by the selected stable client.
4. Return success only after Google confirms the update.

### Re-export

1. If exact tab exists, search/read its sheet-scoped marker/schema/date developer metadata.
2. If marker is absent/different, return `TabNameConflict`; do not clear/write/rename the tab.
3. If marker is valid but schema/date is incompatible, return a schema conflict; do not overwrite.
4. Replace the entire nine-column application-owned grid with the current snapshot, clearing
   obsolete trailing rows/cells and resizing to the required bounds.
5. Write rows in the shared stable sort order; do not independently sort or format at the gateway.
6. Return success only after confirmed update.

Replacement, not append-only merging, is authoritative. This resolves contradictory earlier append wording and ensures edits, deleted intervals, deleted tasks, and changed metadata are reflected without duplicates. Repeating unchanged export produces the same visible table.

Use raw string cell values for all nine canonical fields so client, description, and
hardware/software-purchases text are not evaluated as formulas and CSV/XLSX/Google content remains
equivalent. Do not create a new spreadsheet document at export.

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

## 10. Google Cloud setup guide

The complete developer procedure is in `GOOGLE_SHEETS_SETUP.md`. Its fixed inputs are:

- package/application ID `worq.order`;
- developer-controlled Google Cloud project under a personal Google account;
- Google Sheets API, Google Drive API, and Google Picker API enabled;
- only `https://www.googleapis.com/auth/drive.file` declared for Google data access;
- one debug Android OAuth client now and one direct-release client created in Milestone 17;
- one Web OAuth client ID for Credential Manager;
- an External audience in Testing during development and In Production for small ongoing use;
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
- one consistent snapshot for a running interval;
- picker cancellation writes nothing;
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
- persistent XLSX connection, unrelated-tab preservation, stale/missing/revoked URI recovery
  through user-mediated replacement creation, and repeated date-tab replacement;
- offline, auth expiration, permission, rate-limit, server, and ambiguous-response states are useful/retryable; and
- all failures leave Room task data unchanged.
