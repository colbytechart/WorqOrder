# ADR: Google Identity, Authorization, Picker, and Sheets Access

Status: accepted for Milestones 10, 11, and the Milestone 25 automatic-export design
Decision date: 2026-07-26
Official guidance reviewed: 2026-08-05

## 1. Context

WorqOrder is local and offline first. Room remains authoritative, and Google Sheets is a
one-way export destination. Google access is optional and must not introduce Firebase, a
custom backend, a service account, embedded credentials, or manually persisted OAuth
tokens.

The product connects exactly one existing Google spreadsheet. The user signs in, enters
or pastes a Sheets URL or spreadsheet ID, proves that the selected account can edit that
file, and confirms it as the connected spreadsheet. Later exports write one marked
worksheet tab per displayed date.

The owner has fixed a permanent project policy: WorqOrder remains free and open source
under GPLv3, is distributed as an owner-signed APK from GitHub, and must not
require billing, paid API quota, Google Workspace/organization membership, a custom
domain, or verified OAuth branding. CSV remains the no-network fallback if Google's
no-cost policy later changes.

Authentication and authorization are separate:

- authentication identifies the Google account selected in the UI; and
- authorization grants access to Google-hosted data for a specific operation.

Google's current Android guidance explicitly recommends Credential Manager for
authentication and `AuthorizationClient` for authorization.

## 2. Decision summary

Use the following design:

1. Use an explicit **Sign in with Google** button backed by AndroidX Credential Manager
   and the Google ID library.
2. Use Google Identity Services `AuthorizationClient` from Google Play services for
   Google data authorization.
3. Request only:

   ```text
   https://www.googleapis.com/auth/drive.file
   ```

4. During spreadsheet connection, launch the current Android Google Picker authorization
   flow, filtered to Google Sheets and to the locally parsed pasted spreadsheet ID.
5. When the Picker response contains IDs, require exactly the requested ID. If a
   previously retained per-file grant is reused and Google returns no Picker IDs,
   require the same exact-ID Drive and Sheets metadata checks before accepting the
   connection. Never accept a nonempty mismatch.
6. Use direct HTTPS/JSON REST adapters for Drive v3 and Sheets v4 rather than the
   generated Google Java API client.
7. Obtain a short-lived access token from `AuthorizationClient` for each explicit
   validate/export operation. Keep it only in memory for that operation.
8. Do not request offline access, do not obtain a refresh token, and do not store raw
   access, refresh, ID, or authorization-code tokens in DataStore or Room.
9. Use only no-additional-cost standard API quota. Never attach billing or request a
   paid quota increase; report quota failure and preserve CSV.
10. Bind debug and direct-release Android OAuth clients to the exact package/signing SHA-1
    identities that produce those artifacts.

This replaces the earlier provisional plan to request the sensitive
`https://www.googleapis.com/auth/spreadsheets` scope.

## 3. Why `drive.file` now satisfies the workflow

Google classifies `drive.file` as a recommended, non-sensitive, per-file scope. It lets
an app work only with files the user opens or shares with that app. A pasted ID alone
does not grant per-file access, so paste-only validation is not sufficient.

Current official Google Picker guidance for Android supplies the missing grant step:

- `AuthorizationRequest.ResourceParameter.PICKER_OAUTH_TRIGGER` starts Picker
  authorization;
- `PICKER_FILE_IDS` can filter the Picker to the parsed pasted ID;
- `PICKER_MIMETYPES` can restrict the UI to
  `application/vnd.google-apps.spreadsheet`;
- `AuthorizationResult.getTokenResponseParams()` returns `picked_file_ids`; and
- Sheets `spreadsheets.get`, `spreadsheets.batchUpdate`, and values APIs all accept a
  `drive.file` token for a file granted to the app.

The user still enters or pastes the spreadsheet URL/ID. Google then asks the user to
confirm sharing that exact spreadsheet with WorqOrder. This is a deliberate security
confirmation, not a replacement for the input field.

The connection request must:

- request only `drive.file`;
- call `setOptOutIncludingGrantedScopes(true)`;
- set prompt to `CONSENT`;
- optionally combine `SELECT_ACCOUNT` when an explicit account switch is needed;
- disallow multiple selection;
- filter to the Google Sheets MIME type;
- filter to the parsed spreadsheet ID; and
- reject cancellation, multiple IDs, or a returned ID that differs from the input.

Disconnect intentionally leaves the per-file grant intact. On a later reconnect,
Google may return a valid `drive.file` token without repeating `picked_file_ids`.
WorqOrder treats that empty set only as possible retained-grant reuse; it provides no
connection proof until the exact parsed ID passes both Drive and Sheets validation.

Normal export authorization does not relaunch Picker when the existing grant remains
valid. It makes a normal `authorize()` request for `drive.file`; Google can return a
token without interaction. If the grant was removed, the user must reconnect or
reauthorize the spreadsheet through Picker.

Connected-spreadsheet authorization requests are explicitly bound to the non-secret
persisted account hint. If silent background authorization returns a resolution, the
worker never launches UI and records an actionable pending export without discarding
the spreadsheet metadata or disabling automation. An Activity-backed retry is still
allowed to invoke `AuthorizationClient`, complete Google's resolution, and restore the
validated connection after a successful export.

## 4. Rejected scope alternatives

### Sensitive Sheets-wide scope

`https://www.googleapis.com/auth/spreadsheets` supports a paste-only workflow and can
read and edit every spreadsheet accessible to the consenting user. It is not a fallback
under the accepted no-cost/no-domain policy.

It is not selected because:

- its authority is much broader than one connected file;
- Google classifies it as sensitive;
- public distribution requires sensitive-scope verification;
- an unverified app can show warnings and be subject to user limits; and
- the current stable Picker design makes broader access unnecessary.

If the Picker flow later becomes unusable, stop Google integration work and request a
new owner decision. Do not broaden the scope, enable billing, add a backend, or weaken
CSV while waiting.

### `drive.file` without Picker

A raw pasted ID does not itself make an existing file app-authorized. This option is
unreliable and is rejected.

### App-created spreadsheet

Creating a spreadsheet would work with `drive.file`, but conflicts with the required
workflow of connecting one existing user-selected spreadsheet. WorqOrder does not
create a new spreadsheet document during connection or export.

### Broader Drive scopes

`drive`, `drive.readonly`, and metadata-wide scopes are unnecessary. Some are
restricted and carry substantially greater review/security obligations. They are
prohibited for this workflow.

## 5. Authentication design

Use an explicit Settings action backed by:

- `CredentialManager`;
- `GetSignInWithGoogleOption`; and
- `GoogleIdTokenCredential`.

Do not trigger sign-in on application startup. Local task tracking remains fully
available without a Google account.

Credential Manager requires a Web OAuth client ID. That client ID is a public
identifier, not a client secret. The app must never contain a Web client secret.

WorqOrder has no backend, so it cannot perform the server-side ID-token verification
required before treating an ID token as an application security assertion. Therefore:

- the raw ID token is transient and immediately discarded;
- no ID token is stored or logged;
- an email/name/subject-derived value may be retained only as a non-secret UI hint;
- the hint is not proof of current authorization and is never used to authorize Sheets;
- Google API access is authorized solely by a fresh `AuthorizationClient` result; and
- Room/local functionality never trusts Google identity.

Credential Manager sign-in also establishes the user's default Google account for the
subsequent authorization request. An account change begins with
`CredentialManager.clearCredentialState()` and another explicit sign-in.

## 6. Authorization and token lifecycle

Use `Identity.getAuthorizationClient(activity)` and an
`AuthorizationRequest` containing exactly one `Scope` for `drive.file`.

For each validation or export:

1. Request authorization for `drive.file`.
2. If the result has a resolution, launch its `PendingIntent` and treat cancellation
   neutrally.
3. If authorized, pass the returned access token directly to the gateway.
4. Send it only in the HTTPS `Authorization: Bearer` header.
5. Discard it when the operation finishes.

Access tokens are short lived. The app does not refresh them itself. A later explicit
operation calls `authorize()` again; while the grant remains, Google can provide a
usable token without user interaction.

On an HTTP 401 or an invalid-token condition:

1. call `AuthorizationClient.clearToken()` for that exact in-memory token;
2. discard it;
3. mark authorization as requiring renewal; and
4. allow one user-directed reauthorization/retry.

Do not create retry loops. A 403 must be classified separately because it can mean
read-only access, Workspace policy, or another permission problem rather than token
expiry.

Do not call offline-access APIs, request a server authorization code, exchange codes in
the APK, or store a refresh token. Google's current guidance discourages storing refresh
tokens on a device and reserves them for a backend, which WorqOrder intentionally does
not have.

## 7. Spreadsheet connection and validation

### Input parsing

Accept:

- a canonical spreadsheet ID; or
- a supported `https://docs.google.com/spreadsheets/d/{id}/...` URL.

Trim surrounding whitespace, reject unsupported hosts/schemes, reject query/fragment
text as an ID, cap the complete input at 500 characters, and allow only ASCII letters,
digits, underscores, and hyphens in a 20-to-200-character file ID. Parsing is local
and performs no network call.

### Per-file grant

After parsing:

1. launch the Picker authorization request described in section 3;
2. require exactly the parsed ID when `picked_file_ids` is nonempty;
3. reject any nonempty mismatch before a network validation call;
4. allow an empty ID set only to continue through exact-ID retained-grant validation;
   and
5. keep the returned access token only for validation.

### Read/edit validation

Use the same `drive.file` token for:

1. Drive v3 `files.get` with a narrow field mask:

   ```text
   id,name,mimeType,trashed,capabilities(canEdit,canModifyContent)
   ```

2. Verify:
   - returned ID matches;
   - MIME type is `application/vnd.google-apps.spreadsheet`;
   - file is not trashed;
   - `capabilities.canEdit` is true; and
   - `capabilities.canModifyContent` is true when populated.
3. Sheets v4 `spreadsheets.get` with a narrow field mask:

   ```text
   spreadsheetId,properties(title),sheets(properties(sheetId,title))
   ```

4. Verify the Sheets response ID matches and retain the canonical Sheets title.

This is read-only validation. It does not create a spreadsheet, worksheet, marker,
revision, or test cell.

Store the connection only after all checks succeed. A later export still handles
permission changes and remote deletion safely.

## 8. Sheets API transport

Use small, fakeable REST adapters over platform HTTPS:

- `https://www.googleapis.com/drive/v3`
- `https://sheets.googleapis.com/v4`

Use `HttpsURLConnection` (or the platform URL connection returned for an HTTPS URL),
Android's JSON facilities, explicit UTF-8, bounded connect/read timeouts, exact field
masks, and typed HTTP/error parsing. Do not allow redirects to a non-HTTPS scheme.

The official generated Google API Java client is not selected because its Android
support is documented as `@Beta`, it brings a substantially larger dependency graph,
and the app needs only a small set of REST endpoints. Google documents Sheets as
HTTP/JSON and permits a standard HTTP client.

The gateway must:

- never log authorization headers, tokens, spreadsheet IDs, task text, or response
  bodies containing user data;
- close streams deterministically;
- cap error-body reads;
- sanitize user-visible error details;
- use `ValueInputOption.RAW` for user text;
- return typed retryable/non-retryable outcomes; and
- never mutate Room in response to network success or failure.

No API key authorizes a private spreadsheet. This design does not require or use an API
key for Sheets, Drive metadata, or the Android Picker authorization flow.

Google requests occur only during explicit foreground operations, use batching/field
masks, and have bounded retry. Standard-quota exhaustion returns a safe typed failure;
the app never opts into paid capacity or runs background traffic to work around quota.

## 9. Sign-out versus spreadsheet disconnect

### Disconnect spreadsheet

Disconnect:

- clears spreadsheet ID, title, validation/account hint, and connection timestamps from
  DataStore;
- leaves Credential Manager identity state and the Google OAuth grant intact;
- does not revoke account access;
- does not delete or edit the spreadsheet; and
- does not alter Room.

Because the grant remains, reconnecting the same spreadsheet may return no repeated
Picker-ID payload. The exact pasted ID must still pass Drive and Sheets validation
before connection metadata is restored.

### Sign out

Sign out:

1. immediately makes Google export unavailable;
2. attempts `AuthorizationClient.revokeAccess()` for the selected account;
3. clears any in-memory token;
4. calls `CredentialManager.clearCredentialState()`;
5. clears the local Google account hint; and
6. clears spreadsheet ID, title, validation state, and connection timestamp.

`revokeAccess()` revokes all Google scopes granted to this application for the account,
not merely the scope listed in the revoke request. WorqOrder requests only
`drive.file`, which bounds that consequence.

If remote revocation cannot be confirmed, the UI must not claim it succeeded. It may
complete local sign-out, explain how to remove WorqOrder from the Google Account
third-party connections page, and keep export unavailable. Local account and
spreadsheet metadata are cleared even in this partial-revocation state. Signing in
again, especially with another account, requires a spreadsheet to be explicitly
connected, Picker-granted, and revalidated before export.

## 10. Local storage classification

Preferences DataStore may store:

- connected spreadsheet ID;
- connected spreadsheet title;
- validation timestamp;
- non-secret Google account display hint;
- connection state/version;
- last safe Google operation outcome/category; and
- the chosen export destination.

DataStore and Room must not store:

- raw access tokens;
- refresh tokens;
- ID tokens;
- authorization codes;
- authorization headers;
- passwords;
- OAuth client secrets;
- service-account credentials;
- Picker callback payloads beyond the validated file ID; or
- raw Google error/response bodies.

The credential provider and Google Play services manage their own credential/token
state outside WorqOrder's DataStore. An OAuth client ID and signing-certificate
fingerprint are identifiers, not secrets, but environment-specific values still follow
the configuration and review process in `GOOGLE_SHEETS_SETUP.md`.

## 11. Implemented stable dependencies

Milestone 10 uses:

| Purpose | Artifact | Proposed stable version |
| --- | --- | --- |
| Credential Manager | `androidx.credentials:credentials` | `1.6.0` |
| Pre-Android-14 Google provider bridge | `androidx.credentials:credentials-play-services-auth` | `1.6.0` |
| Sign in with Google credential types | `com.google.android.libraries.identity.googleid:googleid` | `1.2.0` |
| AuthorizationClient and Android Picker parameters | `com.google.android.gms:play-services-auth` | `21.6.0` |
| Coroutine bridge for Google `Task` | `org.jetbrains.kotlinx:kotlinx-coroutines-play-services` | `1.10.2` |

All are stable releases as of the decision date. The current Android documentation
shows `androidx.credentials:1.7.0-alpha02` in some examples, but the official AndroidX
stable channel lists `1.6.0`; WorqOrder selects `1.6.0` to preserve the stable-only
rule. The Google ID release notes list `1.2.0`, and Google Play services release notes
list `play-services-auth:21.6.0`.

No generated Sheets library, Google services Gradle plugin, Firebase BOM, Firebase Auth,
Drive client library, general networking stack, or JSON code-generation plugin is
proposed.

Milestone 10 must prove dependency resolution and compile/minimum-SDK compatibility
before implementation. If the project-local offline cache lacks these artifacts, pause
and let the owner populate the cache under the established repository guardrails.

## 12. Error model

Identity/authorization outcomes:

- `SignedIn`
- `SignInCanceled`
- `NoCredential`
- `CredentialProviderUnavailable`
- `AuthorizationRequired`
- `AuthorizationCanceled`
- `AuthorizationRevoked`
- `PlayServicesUnavailableOrOutdated`
- `SignOutPartiallyCompleted`

Connection/gateway outcomes:

- `InvalidSpreadsheetInput`
- `PickerCanceled`
- `PickerReturnedDifferentFile`
- `NotFoundOrNotGranted`
- `NotGoogleSpreadsheet`
- `ReadOnly`
- `ContentModificationRestricted`
- `Offline`
- `Timeout`
- `Unauthorized`
- `WorkspacePolicyBlocked`
- `RateLimited(retryAfter)`
- `ServerFailure`
- `MalformedResponse`
- `Success`

Do not expose raw HTTP bodies, OAuth tokens, or account internals in user messages.
Because Google can intentionally return not-found for inaccessible resources, avoid
claiming whether a private file exists.

## 13. Test strategy for Milestones 10 and 11

Use interfaces around identity, authorization, token delivery, Picker launch/result,
Drive metadata, and Sheets requests.

Tests must cover:

- sign-in success, cancellation, no credential, and clear-credential-state;
- account change and default-account handoff;
- Picker request has only `drive.file`, opts out of granted-scope inclusion, and uses
  exact MIME/file-ID filters;
- Picker cancellation, wrong/multiple IDs, and empty-ID retained-grant reconnect;
- no API call before per-file grant;
- editable, read-only, trashed, wrong-MIME, missing, and shared-drive spreadsheets;
- Drive and Sheets field masks;
- only safe metadata reaches DataStore;
- no token reaches DataStore, Room, logs, saved state, or exceptions;
- 401 token clearing and user-directed reauthorization;
- external revocation and a different signed-in account;
- disconnect versus sign-out semantics;
- offline, timeout, 403, 404, 429, 5xx, and malformed JSON;
- fake gateway ViewModel and Compose flows;
- no Firebase, service-account, API-key authorization, broader scope, or cleartext
  network configuration; and
- later idempotent marked-tab export behavior from `EXPORT_SPEC.md`.

Controlled integration testing must use test accounts and test spreadsheets, never
production customer data.

## 14. Distribution and verification consequences

`drive.file` is currently non-sensitive and recommended. It avoids sensitive- or
restricted-scope verification and does not require the restricted-scope security
assessment. Google states that verification is not mandatory for an app using only
non-sensitive scopes; brand verification is required only to display a verified app
name/logo. WorqOrder does not make that branding path a dependency.

While an External OAuth app is in Testing:

- only listed test users can authorize non-identity scopes;
- the project supports at most 100 listed test users; and
- grants that include `drive.file` can expire after seven days, so reauthorization
  during development is expected.

The selected no-cost public path is:

1. an individual developer-controlled Google Cloud project;
2. External/Testing during Milestones 10 and 11 with named test users;
3. External/In Production when the direct-release build is ready, removing the
   test-user allowlist and avoiding the seven-day Testing expiration;
4. no Google Workspace/Cloud organization, custom domain, verified brand, marketplace
   dependency, or paid service.

As rechecked against official Google documentation on 2026-07-29, External/In Production projects
are available to any Google Account. The 100-new-user cap applies to OAuth clients that present an
unverified-app warning for unapproved sensitive/restricted scopes; WorqOrder's sole authorization
scope, `drive.file`, is non-sensitive. Brand verification is optional for apps using only
non-sensitive scopes unless a verified app name/logo is desired.

Milestone 17 must verify the release-signed APK using an account that was never on the test-user
list. After that one-time project setup, the owner does not manually add or approve users. A
Workspace administrator or Advanced Protection policy can still block a particular account, and
users must have permission to edit their chosen spreadsheet.

The project accepts that consent may show less-polished project identity.
It still keeps support contact information current and supplies accurate privacy/user
documentation in the open-source repository.

The developer's Android OAuth identity covers only APKs signed by the registered debug
or direct-release certificate. Independent GPLv3 builders/forks use their own Google
Cloud project, signing fingerprint, OAuth clients, and local Web client ID; no universal
secret or paid shared service is distributed with the source.

If a future change requests the sensitive `spreadsheets` scope, sensitive-scope
verification and updated consent/privacy materials would be required, so implementation
must stop for a new owner decision rather than proceed.

Google currently provides standard Sheets API use at no additional cost and is
introducing paid usage only above standard thresholds later in 2026. WorqOrder does not
attach a billing account or request increased quota. No external provider can guarantee
unchanged terms forever; any policy change is handled by disabling/failing Google
export safely while retaining CSV until the owner revisits the decision.

## 15. Official references

- [Authorize access to Google user data](https://developer.android.com/identity/authorization)
- [About Sign in with Google and Credential Manager](https://developer.android.com/identity/sign-in/credential-manager-siwg)
- [Implement Sign in with Google](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation)
- [AndroidX Credentials releases](https://developer.android.com/jetpack/androidx/releases/credentials)
- [Google ID SDK release notes](https://developers.google.com/identity/android-credential-manager/releases)
- [Google Play services release notes](https://developers.google.com/android/guides/releases)
- [AuthorizationClient reference](https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationClient)

## 16. `0.2.0` automatic-export research addendum

The owner approved an opt-in Google-Sheets-only automatic daily export. CSV and XLSX remain manual.
The job captures an intended work date/ZoneId near 11:59 PM and may execute shortly after midnight
while still exporting that captured prior date. It uses the same `drive.file` grant, connected
spreadsheet, current canonical schema-4 snapshot, and marked-tab replacement as manual export. It never
stores a raw access/refresh token.

Milestone 25 rechecked current official Android and Google documentation on 2026-08-05 and selects
the following design for owner approval. This section is design authority for Milestone 26; it does
not mean scheduling has been implemented.

### 16.1 Scheduler selection

Use stable AndroidX WorkManager `2.11.2` with a non-expedited `CoroutineWorker` and
`NetworkType.CONNECTED`. Enqueue one uniquely named `OneTimeWorkRequest` with a calculated initial
delay to the next effective-zone near-end-of-day target. Do not use a 24-hour
`PeriodicWorkRequest`: periodic timing is inexact and a fixed interval drifts across daylight-saving
and manual/device-zone changes. After one captured target reaches a terminal state, calculate the
next target from geographical `ZoneId` rules and enqueue another unique one-time request.

WorkManager is the official persistent-work recommendation, stores its schedule durably, restores
it across app restarts and device reboot, and honors Doze. Its delay is a minimum, not an exact
wall-clock appointment. A run may therefore occur after midnight; the worker must use its durable
captured epoch day and ZoneId rather than recomputing `today` when it starts.

Use one stable unique-work name and `ExistingWorkPolicy.REPLACE` only for an explicit schedule
reconciliation (enable, disable, destination/connection change, or a newer not-yet-pending target).
Never replace an older unresolved pending target. Manual and automatic Google writes must share one
application export mutex/coordinator and the same marker-checked batch replacement, so races
converge without duplicate rows.

Do not use AlarmManager, an exact-alarm permission, expedited work, a foreground service, an
application-owned boot receiver, or application-owned wake locks. WorkManager itself contributes
its normal internal network/reboot/wake-lock manifest support and acquires a bounded system/library
wake lock only while a worker executes; Milestone 26 must inspect and document the merged manifest.
This is background-export infrastructure, never a stopwatch tick mechanism.

### 16.2 Durable target and backlog rule

The stored automatic-export state represents the oldest unresolved target and includes:

- target epoch day;
- captured canonical ZoneId;
- an expected connected-spreadsheet association/fingerprint;
- scheduled versus typed pending state; and
- non-sensitive attempt/error metadata only.

It never contains task rows or credentials. A failed or blocked target is not overwritten by the
next day. After it succeeds, the coordinator advances one local date at a time; if later target
dates became due while the oldest target was unresolved, it schedules the next due date
immediately and catches up through the same bounded, one-date-per-worker protocol. This prevents a
multi-day running timer or several offline days from silently losing a daily target without
creating an unbounded loop inside one worker. Disabling automation, selecting CSV/XLSX,
disconnecting, or signing out is an explicit user decision that cancels future unique work and
clears automatic pending state without changing Room or the spreadsheet.

### 16.3 Background authorization boundary

`Identity.getAuthorizationClient(Context)` is valid from a worker. Call `authorize()` for the one
existing `drive.file` scope. If the eligible account and grant remain available, Google can return
a short-lived access token without interaction and the worker may export. Keep that token only in
memory and discard it after the operation.

Unattended authorization is opportunistic, not guaranteed. If `authorize()` returns a
`PendingIntent`, a worker must not launch it. Preserve the captured target as
authorization-required and post the approved content-free notification. Its tap opens WorqOrder,
where an Activity can launch the Google resolution and retry the same captured date. A 401 clears
the exact token and permits at most one silent reauthorization attempt; another resolution or
failure becomes pending. No refresh token, server authorization code, service account, backend,
broad scope, Firebase dependency, or token in DataStore/Room is allowed.

This use of Google Play services is an installed-device API dependency. It does not introduce
Google Play Store publication, Play App Signing, Play Console setup, or a Play release/verification
workflow. Direct GitHub APK distribution, the production External OAuth audience, and the existing
non-sensitive `drive.file` configuration remain unchanged.

### 16.4 Notification and recovery policy

Create one **Pending Google Export** notification channel on API 26+ and declare
`POST_NOTIFICATIONS`; request that runtime permission only on API 33+ when the user enables
the **Auto Export** switch. The switch appears at the bottom of the conditional Google content in
the Export Destination card, below sign-in and spreadsheet-connection controls, and uses supporting
text **Automatically export tasks at the end of each day.** It may become enabled only after the app can post the notification
needed for blocked work. Before posting, check whether app/channel notifications remain enabled.

Notification text contains no client, task, consultant, spreadsheet, account, or exported row
content. It says only that a WorqOrder export needs attention and that tapping will continue it.
Use one stable notification ID so repeated recovery states replace rather than multiply notices.
Dismissal never clears pending state. If permission/channel access is later revoked, automatic
success may still remain silent, but any blocked target stays recoverable in Google Settings on the
next foreground launch; WorqOrder must not claim that Android can guarantee a notification the user
or OEM has suppressed.

An active Room timer is checked before authorization or snapshot creation. If present, export
nothing and store `TIMER_RUNNING`. After Stop commits, post the recovery notification. Offline,
authorization-required, permission, rate-limit, server, timeout, and ambiguous failures also keep
the target with a typed safe reason. Use `Result.success()` after persisting such a terminal pending
state rather than WorkManager automatic retry. User action is the retry boundary; there is no
unbounded retry, paid quota, or repeated background network loop.

### 16.5 Limits and alternatives rejected

- Execution near 11:59 PM is best effort and can be delayed by Doze, constraints, force-stop, OEM
  restrictions, or lack of connectivity.
- User force-stop prevents dependable background execution until WorqOrder is opened again;
  startup reconciliation must restore the oldest target.
- AlarmManager was rejected because exact execution is not required and exact alarms are more
  disruptive to Doze/battery policy.
- A fixed periodic worker was rejected because it cannot preserve local-wall-time intent across DST
  and zone changes as clearly as recalculated one-time work.
- A backend/refresh-token/service-account design was rejected by the free, local-first, no-backend
  product policy.
- `Result.retry()` was rejected for operation failures because the project prohibits uncontrolled
  automatic retries and paid quota risk.

### 16.6 Approved implementation dependencies and permissions

Milestone 26 may add only these new aliases after the owner approves this design and prepares the
offline Gradle cache:

- `androidx.work:work-runtime-ktx:2.11.2`;
- `androidx.work:work-testing:2.11.2` for Android tests.

The explicit application permission addition is `android.permission.POST_NOTIFICATIONS` for API
33+ runtime use. WorkManager's merged normal permissions/components must be audited as described
above. Do not add `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, a foreground-service permission, broad
storage permission, or any Google scope beyond `drive.file`.

### 16.7 Official sources reviewed for this addendum

- [Android persistent task scheduling](https://developer.android.com/develop/background-work/background-tasks/persistent)
- [Define WorkManager requests and constraints](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)
- [Manage unique WorkManager work](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work)
- [WorkManager stable releases](https://developer.android.com/jetpack/androidx/releases/work)
- [WorkManager platform interactions and permissions](https://developer.android.com/reference/androidx/work/package-summary)
- [Android notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [Android notification channels](https://developer.android.com/develop/ui/compose/notifications/channels)
- [Google `AuthorizationClient`](https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationClient)
- [Google Identity Android client entry points](https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/Identity)
- [Google Drive scope selection](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)
- [Choose Google Sheets API scopes](https://developers.google.com/workspace/sheets/api/scopes)
- [Choose Google Drive API scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)
- [Google Picker for desktop and mobile apps](https://developers.google.com/workspace/drive/picker/guides/desktop-mobile-picker)
- [Drive `files.get`](https://developers.google.com/workspace/drive/api/reference/rest/v3/files/get)
- [Drive file capabilities](https://developers.google.com/workspace/drive/api/reference/rest/v3/files)
- [Sheets REST API](https://developers.google.com/workspace/sheets/api/reference/rest)
- [Sheets `spreadsheets.batchUpdate`](https://developers.google.com/workspace/sheets/api/reference/rest/v4/spreadsheets/batchUpdate)
- [OAuth app verification](https://support.google.com/cloud/answer/13463073)
- [When OAuth verification is not needed](https://support.google.com/cloud/answer/13464323)
- [Manage OAuth app audience](https://support.google.com/cloud/answer/15549945)
- [Sheets API limits and pricing](https://developers.google.com/workspace/sheets/api/limits)
- [Google Workspace standardized API model](https://developers.google.com/workspace/tools-safety)
- [OAuth production policy compliance](https://developers.google.com/identity/protocols/oauth2/production-readiness/policy-compliance)
