# Export Specification

## 1. Principles

- Export exactly the date displayed on the main screen.
- Build one immutable logical snapshot from Room, the authoritative source.
- Use the same logical columns, row ordering, and value semantics for CSV and Google Sheets.
- Export is one-way. It never imports, marks tasks exported, deletes local records, or resolves changes made in an external copy.
- A retry is safe. Google re-export is idempotent; CSV export may intentionally create another file.
- No XLSX, Apache POI, Firebase, service account, custom backend, broad storage permission, or embedded secret.

## 2. Snapshot process

`ExportCoordinator.export(displayedDate, destination)`:

1. Capture one `exportInstant` and the effective/pinned zone context.
2. Normalize any active interval across all boundaries through that instant.
3. In one Room read transaction, load tasks for `displayedDate`, retained clients, ordered intervals, and active state.
4. Build rows and task totals evaluated at the same `exportInstant`.
5. Sort rows deterministically.
6. End the Room transaction.
7. Deliver to the chosen destination.
8. Persist a safe last-attempt result in settings; do not modify tasks/intervals.

An empty date still exports the schema/header with no data rows. A task without intervals emits one row. A running interval emits a row with blank stop, state `RUNNING`, and duration through `Exported At UTC`; export does not stop it.

Milestone 8 implements this for CSV as follows: one shared timer-operation lock covers capture of
`exportInstant`, boundary normalization, and the Room snapshot read. The DAO loads every joined
task/client and its ordered intervals for the displayed date inside one Room transaction. The row
builder and serializer then produce one immutable in-memory CSV string before the create-document
picker opens. Changes made after the picker opens cannot change that pending payload.

## 3. Stable schema version 1

Columns appear in this exact order:

| # | Column | Encoding |
| ---: | --- | --- |
| 1 | Schema Version | `1` |
| 2 | Exported At UTC | ISO-8601 instant, same for every row |
| 3 | Work Date | `YYYY-MM-DD` |
| 4 | Time Zone | stored geographical ZoneId |
| 5 | Client ID | stable UUID |
| 6 | Client Name | retained current client name |
| 7 | Task ID | stable daily-task UUID |
| 8 | Task Series ID | stable series UUID |
| 9 | Description | short task description |
| 10 | Hardware / Software Purchases | optional task text; blank when none |
| 11 | Interval ID | UUID or blank for zero-interval task |
| 12 | Interval Number | stable positive ordinal or blank |
| 13 | Interval State | `COMPLETED`, `RUNNING`, or `NO_INTERVAL` |
| 14 | Start Local | ISO zoned date-time including offset and zone, or blank |
| 15 | Stop Local | ISO zoned date-time including offset and zone, or blank |
| 16 | Start UTC | ISO-8601 instant or blank |
| 17 | Stop UTC | ISO-8601 instant or blank |
| 18 | Interval Duration Milliseconds | integer; running uses export snapshot; blank for no interval |
| 19 | Interval Duration Formatted | accumulated `HH:MM:SS.mmm`; blank for no interval |
| 20 | Task Total Duration Milliseconds | integer total at export snapshot |
| 21 | Task Total Duration Formatted | accumulated `HH:MM:SS.mmm` |
| 22 | Task Created UTC | ISO-8601 instant |
| 23 | Task Updated UTC | ISO-8601 instant |
| 24 | Interval Manually Edited | `true`/`false`, blank for no interval |

Local timestamps use the task's stored ZoneId and include the resolved UTC offset so fall-back times are unambiguous. UTC values use `Instant.toString()` semantics. Numeric milliseconds are base-10 with no grouping. Empty optional values are empty fields, not the strings `null` or `N/A`.

Rows sort by task creation instant, task ID, interval start (null last), interval ordinal, and interval ID. This preserves a readable daily creation order and is completely deterministic. Both destinations receive this order; the Google data range is therefore already fully sorted. Changing this order or a column requires a schema-version decision.

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
standard `ACTION_CREATE_DOCUMENT` intent with `CATEGORY_OPENABLE` and the suggested filename. After
a URI is returned, `ContentResolver.openOutputStream(uri, "wt")` writes the already serialized
payload as UTF-8. Provider failure triggers a best-effort `ContentResolver.delete` of that exact
granted URI. The UI distinguishes success, neutral cancellation, retryable write failure, and the
case where a partial provider document could not be removed.

CSV faithfully preserves client, description, and hardware/software-purchases text. Some spreadsheet programs interpret cells beginning with `=`, `+`, `-`, or `@` as formulas when opening CSV. RFC quoting does not prevent that behavior. Silently prefixing text would change exported data, so formula-injection transformation is not part of schema version 1; flag it in release security review and document safe import behavior.

### Schema-version 1 examples

The first record is always the following exact header:

```csv
Schema Version,Exported At UTC,Work Date,Time Zone,Client ID,Client Name,Task ID,Task Series ID,Description,Hardware / Software Purchases,Interval ID,Interval Number,Interval State,Start Local,Stop Local,Start UTC,Stop UTC,Interval Duration Milliseconds,Interval Duration Formatted,Task Total Duration Milliseconds,Task Total Duration Formatted,Task Created UTC,Task Updated UTC,Interval Manually Edited
```

A completed interval may serialize as:

```csv
1,2026-07-24T20:00:00Z,2026-07-24,America/New_York,client-1,"Acme, Inc.",task-1,series-1,"Repair ""north"" unit",Laptop,interval-1,1,COMPLETED,2026-07-24T09:00:00-04:00[America/New_York],2026-07-24T10:00:00-04:00[America/New_York],2026-07-24T13:00:00Z,2026-07-24T14:00:00Z,3600000,01:00:00.000,3600000,01:00:00.000,2026-07-24T12:00:00Z,2026-07-24T14:00:00Z,false
```

A task without intervals emits one `NO_INTERVAL` row. Its interval ID, number, timestamps,
durations, and manually-edited flag are empty, while its task total is zero:

```csv
1,2026-07-24T20:00:00Z,2026-07-24,America/New_York,client-2,Example Client,task-2,series-2,Planning,,,,NO_INTERVAL,,,,,,,0,00:00:00.000,2026-07-24T15:00:00Z,2026-07-24T15:00:00Z,
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

## 6. Google connection model

Exactly one spreadsheet ID is connected at a time. Connection comprises:

- an authorized Google account/session managed by current Google-supported Android identity/authorization APIs;
- a validated spreadsheet ID;
- its last validated title; and
- a connection timestamp/status (non-secret metadata).

Accept a canonical spreadsheet ID or a supported Sheets URL and parse the ID locally. Reject malformed/oversized inputs. Validation calls `spreadsheets.get` with a narrow field mask sufficient for spreadsheet ID/title and sheet properties. Success proves the authorized account can access the spreadsheet at that moment. It never creates a spreadsheet.

Disconnect clears local ID/title association only. Sign-out invokes the supported Google sign-out/session-clear path and marks authorization unavailable. Neither action edits the spreadsheet or Room. If permissions later change, the next validation/export reports permission/not-found status.

### Authentication and authorization choice

Identity and Sheets authorization are distinct. Before Milestone 9, re-read current official Google documentation and select stable releases only. The planned behavior is:

- use the current Google-supported Android account/identity UI for user sign-in/account choice;
- use the Google Identity authorization client to request permission to call Sheets;
- let Google Play services/libraries manage token acquisition/refresh; never persist raw access/refresh tokens;
- request `https://www.googleapis.com/auth/spreadsheets` only; and
- do not request Drive, profile, email, or broader scopes unless a reviewed implementation need is documented.

The Sheets scope is sensitive and can access all of the consenting user's spreadsheets. It is necessary for the required workflow in which a user types the URL/ID of an arbitrary existing spreadsheet. Google's recommended `drive.file` scope is narrower, but arbitrary ID entry generally does not grant per-file access; adopting it would require a compatible Google file-picker/creation workflow and a product change. This is a documented least-scope tradeoff.

Current official references reviewed on 2026-07-22:

- [About Sign in with Google and the distinction for authorization](https://developer.android.com/identity/sign-in/credential-manager-siwg)
- [Google OAuth 2.0 overview](https://developers.google.com/identity/protocols/oauth2)
- [Choose Google Sheets API scopes](https://developers.google.com/workspace/sheets/api/scopes)
- [Sheets `spreadsheets.batchUpdate`](https://developers.google.com/workspace/sheets/api/reference/rest/v4/spreadsheets/batchUpdate)

If current official samples require only preview identity dependencies, stop and document the reason/options rather than violating the stable-only rule silently.

## 7. Google worksheet contract

For displayed date `YYYY-MM-DD`, the application-owned tab name is exactly:

```text
WorqOrder_YYYY-MM-DD
```

Reserved layout:

| Cell/range | Content |
| --- | --- |
| `A1` | exact marker `WORQORDER_EXPORT` |
| `B1` | schema version integer `1` |
| `C1` | work date `YYYY-MM-DD` |
| `A2` | label `Exported At UTC` |
| `B2` | snapshot instant |
| row 4 | the 24 exact column headers |
| row 5 onward | logical export rows |

Rows 1–3 and the table are application-owned. Users are warned that edits inside a marked tab will be replaced on the next export. Other tabs are never touched.

### First export for a date

1. Fetch spreadsheet metadata and find exact tab name.
2. If absent, add the tab with a chosen sheet ID and write marker/metadata/header/data.
3. Prefer one atomic `spreadsheets.batchUpdate` containing structural/cell update requests when supported by the selected stable client.
4. Return success only after Google confirms the update.

### Re-export

1. If exact tab exists, read the marker/schema/date cells.
2. If marker is absent/different, return `TabNameConflict`; do not clear/write/rename the tab.
3. If marker is valid but schema/date is incompatible, return a schema conflict; do not overwrite.
4. Replace the entire application-owned grid with the current snapshot, clearing obsolete trailing rows/cells.
5. Write rows in the stable sort order (and apply a data-range sort request if needed to guarantee it).
6. Return success only after confirmed update.

Replacement, not append-only merging, is authoritative. This resolves contradictory earlier append wording and ensures edits, deleted intervals, deleted tasks, and changed metadata are reflected without duplicates. Repeating unchanged export produces the same logical tab contents apart from the explicit export timestamp.

Use raw cell values so client, description, and hardware/software-purchases text fields are not
evaluated as formulas. IDs/timestamps are strings; duration milliseconds can be numeric. Do not
create a new spreadsheet document at export.

## 8. Google failure behavior

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

## 9. Google Cloud setup guide

Perform this only for the Google milestone:

1. Use the fixed Android `applicationId` and namespace `worq.order` in the project and all Google configuration.
2. Create/select a Google Cloud project owned by the publisher organization.
3. Enable **Google Sheets API**. Do not enable Firebase. Enable Drive API only if a separately approved picker-based scope design requires it.
4. Configure Google Auth Platform/OAuth consent branding, support contacts, privacy links, audience, and test users. Declare only the Sheets scope above. Complete required sensitive-scope/brand verification before production distribution.
5. Create an **Android OAuth client** for each required application ID + signing certificate combination according to current official guidance.
6. Register debug and release/upload signing-certificate fingerprints as separate authorized clients/configurations. Capture at least SHA-1 and also SHA-256 wherever the console/current identity stack supports or requires it. Never commit private keystores or passwords.
7. If the selected official stable sign-in flow explicitly requires a Web OAuth client ID, create it in the same project and embed only the client ID (public identifier), never a client secret. Because there is no backend, do not design a server-token-validation session; use authorization solely for Sheets access.
8. Configure debug/release builds to reference non-secret client identifiers through reviewed build configuration. No `google-services.json` is needed merely to use Sheets and Firebase must not be added.
9. In the app, sign in/authorize, paste a spreadsheet URL/ID owned or shared with that same account, validate title/ID, connect, export, re-export, expire/revoke access, and sign out.
10. Test with debug and release signing identities, a non-owner shared sheet, read-only access, missing sheet, offline mode, consent cancellation, and an unmarked conflicting tab.
11. Record console ownership, verification status, package name, certificate fingerprints (public hashes), OAuth client IDs, scope justification, and rotation/release procedure in a non-secret release runbook.

An OAuth Android client identifier is not a password, but it must still be restricted to the exact package/fingerprint. No service-account key or OAuth client secret belongs in an APK.

## 10. Export tests

Tests must prove:

- exact schema/header/order and zero-task/zero-interval behavior;
- commas, quotes, CR/LF, Unicode, both task text fields, long hours, DST-offset local timestamps;
- one consistent snapshot for a running interval;
- picker cancellation writes nothing;
- serializer/output failures do not claim success;
- one connected spreadsheet only;
- URL/ID parsing and validation;
- create marked date tab, re-export replacement, obsolete-row clearing, and stable sorting;
- unchanged re-export has no duplicate rows;
- local edit/delete is reflected by replacement;
- unmarked tab and incompatible marker are untouched;
- offline, auth expiration, permission, rate-limit, server, and ambiguous-response states are useful/retryable; and
- all failures leave Room task data unchanged.
