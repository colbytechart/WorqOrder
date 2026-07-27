# Google Sheets Integration Setup

Status: developer setup guide for Milestones 10 and 11  
Application ID/package: `worq.order`  
Selected Google data scope: `https://www.googleapis.com/auth/drive.file`

Do not perform this setup until preparing the Google integration environment. No part
of this guide requires Firebase, a service account, an API key, a client secret, or a
custom backend.

Permanent project policy: WorqOrder is free/open source under GPLv3, uses no Google
Cloud billing or paid quota, requires no Google Workspace organization or custom
domain, and is distributed directly rather than through Google Play.

## 1. Inputs to prepare

The developer must control:

- a Google Cloud project;
- a Google account that can manage Google Auth Platform configuration;
- a stable public support email;
- the debug signing certificate;
- a primary and secondary test Google account; and
- editable and read-only test spreadsheets containing no customer data.

The permanent direct-release signing certificate is deliberately deferred to Milestone
17. No Google Play signing certificate is required. A second production Cloud project
is optional, not a requirement; if one project is used, name every OAuth client clearly.

## 2. Create or select the Google Cloud project

1. Open [Google Cloud Console](https://console.cloud.google.com/).
2. Create or select a project controlled by the developer's personal Google account.
3. Record its project ID, project number, owners, editors, and support contacts in a
   non-secret release runbook.
4. Protect the owning account with strong authentication and keep ownership recoverable.
5. Do not add Firebase to the project for WorqOrder.

## 3. Enable APIs

In **APIs & Services > Library**, enable:

1. **Google Sheets API**
2. **Google Drive API**
3. **Google Picker API**

Why:

- Sheets API validates spreadsheet metadata and performs later worksheet exports.
- Drive API reads the selected file's MIME type and
  `capabilities.canEdit`/`canModifyContent` without modifying it.
- Google Picker API gives the app a user-confirmed per-file `drive.file` grant.

Do not enable unrelated APIs. WorqOrder does not use Drive listing, file creation,
sharing management, or a Drive-wide scope.

## 4. Configure Google Auth Platform

Open **Google Auth Platform** in the Cloud Console.

### Branding

Configure:

- App name: `WorqOrder`
- User support email
- Developer contact email addresses

Do not make an app logo, home page, privacy-policy URL, terms URL, authorized domain, or
brand verification a prerequisite. If the console permits those fields to remain
unset, leave them unset. Keep an accurate privacy notice and Google-use explanation in
the open-source repository. The project accepts less-polished/unverified consent
presentation rather than purchasing a domain.

### Audience

Choose **External**. Do not create or require a Google Workspace/Cloud organization.

During development, leave an External project in **Testing** and add each test account
under **Test users**. Testing mode is limited and non-identity authorization grants can
expire after seven days. Treat reauthorization during testing as expected behavior.

Move the audience to **In Production** only in Milestone 17, after the direct-release
signing identity, privacy/user documentation, and controlled connection/export tests
are ready. This avoids Testing's seven-day grant expiration for ongoing small use; it
does not mean publishing through Google Play or purchasing verified branding.

### Data Access

Declare only:

```text
https://www.googleapis.com/auth/drive.file
```

Do not add:

- `https://www.googleapis.com/auth/spreadsheets`;
- `https://www.googleapis.com/auth/drive`;
- `https://www.googleapis.com/auth/drive.readonly`;
- profile/email scopes to the Sheets authorization request;
- any restricted scope; or
- any scope not used by the reviewed implementation.

Credential Manager's Sign in with Google identity flow remains separate from the
`AuthorizationClient` data-access request.

## 5. Obtain signing fingerprints

Android OAuth clients are bound to an exact package name and SHA-1 signing-certificate
fingerprint. The package is always:

```text
worq.order
```

### Debug fingerprint

From the project root in PowerShell, using Android Studio's Embedded JBR 21:

```powershell
$env:JAVA_HOME = 'S:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = 'C:\Users\colby\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = 'C:\Users\colby\AppData\Local\Android\Sdk'
$env:GRADLE_USER_HOME = 'T:\_SC Video\PROJECTS\2026\DNA Work Order App\app\WorqOrder\.gradle'
.\gradlew.bat signingReport
```

Locate the `debug` variant and record its SHA-1. This—not a release or Play
fingerprint—is the value for `WorqOrder Android Debug`. Retain SHA-256 only as
non-secret diagnostic information.

An alternative is `keytool -list -v` against the debug keystore, but the Gradle signing
report avoids manually exposing a keystore password in shell history.

### Release fingerprint

Deferred to Milestone 17. Do not create a release keystore or register a release OAuth
client during Milestone 10 preparation.

When the owner's permanent direct-release keystore has been created through the secure
release process, inspect it with:

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -list -v -keystore 'PATH_TO_RELEASE_KEYSTORE' -alias 'RELEASE_ALIAS'
```

Let `keytool` prompt for the password. Do not put passwords in the command, scripts,
Gradle files, documentation, or shell history.

### Google Play app-signing fingerprint

Not applicable. WorqOrder is not distributed through Google Play. Do not create a Play
Console app, enable Play App Signing, or register a Play Android OAuth client. If the
owner changes distribution policy in the future, treat that as a new explicit decision.

Never commit a keystore, key password, signing property file, private key, or exported
certificate containing private material.

## 6. Create OAuth clients

Open **Google Auth Platform > Clients**.

### Android debug client

1. Choose **Create client**.
2. Select **Android**.
3. Name it clearly, such as `WorqOrder Android Debug`.
4. Package name: `worq.order`.
5. SHA-1: the debug certificate from section 5.
6. Create the client.

### Android direct-release client

Do not create one before Milestone 17. At that milestone, create exactly one additional
Android client for the permanent directly distributed release APK:

- name: `WorqOrder Android Direct Release`;
- package: `worq.order`; and
- SHA-1: the permanent release certificate.

There is no Google Play Android client. A package/fingerprint mismatch causes
authorization failure even when source code is correct.

### Web application client

Credential Manager's Sign in with Google option requires a Web client ID:

1. Choose **Create client**.
2. Select **Web application**.
3. Name it `WorqOrder Credential Manager`.
4. Do not invent JavaScript origins or redirect URIs for this native-only flow.
5. Copy the generated client ID ending in
   `.apps.googleusercontent.com`.
6. Never place the Web client secret in the Android project or APK.

The Web client ID is a public identifier used as Credential Manager's server client ID.
WorqOrder has no backend and does not exchange authorization codes.

The developer's OAuth clients apply only to APKs signed by the registered certificates.
Anyone building or forking the GPLv3 source with another signing certificate must create
their own no-cost Cloud project, Android clients, and local Web client ID. Do not commit
one universal configuration or a secret for all forks.

## 7. Local project configuration placeholder

Milestone 10 should read the Web client ID from an uncommitted local/CI Gradle property
and expose only that public ID to the application build.

Recommended local placeholder in the existing uncommitted `local.properties`:

```properties
worqorder.google.webClientId=REPLACE_WITH_WEB_CLIENT_ID.apps.googleusercontent.com
```

Before adding the real public client ID, run `git check-ignore -v .\local.properties`.
If Git does not report an ignore rule, stop and fix repository ignore configuration
before saving the value. After saving, `git status --short --untracked-files=all` must
not list `local.properties`.

For CI/release builds, inject the equivalent Gradle property from the controlled build
environment. Fail the Google-enabled build with a clear configuration error if the
placeholder is missing or malformed; never silently ship a dummy ID.

No source code should require an Android OAuth client ID value. Google matches Android
clients using package name and certificate fingerprint registered in the Cloud project.

Do not commit:

- `local.properties`;
- real environment-specific property files;
- OAuth client secrets;
- `credentials.json` or `client_secret.json`;
- `google-services.json`;
- service-account JSON/P12 files;
- access, refresh, or ID tokens;
- authorization codes or headers;
- keystores, signing passwords, or private certificates; or
- captured Google API responses containing user data.

This integration does not use the Google services Gradle plugin and does not require
`google-services.json`.

## 8. Dependency preparation for Milestone 10

The approved proposed stable versions are:

```text
androidx.credentials:credentials:1.6.0
androidx.credentials:credentials-play-services-auth:1.6.0
com.google.android.libraries.identity.googleid:googleid:1.2.0
com.google.android.gms:play-services-auth:21.6.0
org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2
```

Do not add them during Milestone 9. At the beginning of Milestone 10:

1. let the milestone add the approved version-catalog/dependency declarations, then
   pause before the first dependency-resolving build;
2. close Android Studio to avoid concurrent Gradle locks;
3. redeclare JBR/SDK/project-local `GRADLE_USER_HOME` variables;
4. stop the Gradle daemon for that exact cache;
5. let Gradle itself resolve the declared graph online into the project-local cache;
6. immediately repeat the same build with `--offline --no-daemon`;
7. do not copy seed-cache `.lock`, journal, daemon, or live cache files;
8. confirm no preview artifact is selected;
9. inspect the merged manifest and dependency tree for unexpected permissions/components;
10. keep all versions in `gradle/libs.versions.toml`; and
11. stop without deleting caches if the stable versions cannot compile against the
    proven toolchain.

Use a new PowerShell session:

```powershell
Set-Location 'T:\_SC Video\PROJECTS\2026\DNA Work Order App\app\WorqOrder'
$env:JAVA_HOME = 'S:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = 'C:\Users\colby\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = 'C:\Users\colby\AppData\Local\Android\Sdk'
$env:GRADLE_USER_HOME = 'T:\_SC Video\PROJECTS\2026\DNA Work Order App\app\WorqOrder\.gradle'
.\gradlew.bat --stop
.\gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
.\gradlew.bat --offline --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
```

The first build is run by the owner with network access solely to populate the declared
artifacts. The second proves the project-local cache is complete. Do not use
`--refresh-dependencies` unless a specific corrupt/stale artifact has been diagnosed.

Do not substitute the `1.7.0-alpha` Credential Manager version shown by some current
documentation examples.

## 9. Test spreadsheet preparation

Create test-only spreadsheets:

1. **Owner/editor sheet:** owned by the primary test account.
2. **Shared editor sheet:** owned by a second account and shared as Editor to the
   primary test account.
3. **Read-only sheet:** shared as Viewer.

Before Milestone 11, additionally create:

4. **Conflict sheet:** contains a manually created unmarked
   `WorqOrder_YYYY-MM-DD` tab for export-conflict testing.
5. **Marked-test sheet:** a clean disposable spreadsheet reserved for application-created
   markers and idempotent re-export tests.

Do not use production/customer spreadsheets. Record test spreadsheet IDs only in local
test notes, not committed source.

## 10. Connection testing procedure

After Milestone 10 implements the connection UI:

1. Install a debug build signed by the registered debug certificate.
2. Open Settings > Google Sheets.
3. Tap the explicit Sign in with Google button.
4. Select a listed test account.
5. Paste the owner/editor spreadsheet URL.
6. Confirm the Google Picker shows the exact spreadsheet.
7. Select/confirm it.
8. Verify WorqOrder shows the canonical title and ID.
9. Restart the app and verify only safe metadata persists; the app obtains authorization
   again through supported APIs rather than a stored token.
10. Disconnect the spreadsheet and verify the external file remains unchanged.
11. Reconnect, sign out, and verify export is unavailable and another account can be
    selected.

Repeat with:

- a plain spreadsheet ID;
- the shared editor sheet;
- the read-only sheet;
- a malformed URL/ID;
- an inaccessible or deleted file;
- a non-Sheets file if the Picker/provider allows it;
- cancellation at sign-in, consent, and Picker;
- offline mode;
- a revoked grant from the Google Account third-party-connections page;
- an outdated/unavailable Google Play services environment; and
- a different signed-in Google account.

Expected connection behavior:

- exactly one Picker-returned ID must match the parsed input;
- Drive metadata must identify an editable, modifiable, non-trashed Google spreadsheet;
- Sheets metadata must return the same ID and its title;
- no test write occurs during validation;
- no token is persisted; and
- failures leave Room and prior spreadsheet metadata unchanged unless the user explicitly
  disconnects.

## 11. Direct-release and no-cost checklist

Before direct production distribution:

- create/back up the permanent direct-release key through the Milestone 17 secure process;
- register its SHA-1 as `WorqOrder Android Direct Release`;
- move the External audience from Testing to In Production when ready;
- keep support/developer contacts current;
- verify that only `drive.file` appears in Data Access and runtime requests;
- review Google Sign in branding requirements;
- include accurate privacy/Google-use documentation in the GPLv3 repository;
- test the directly release-signed artifact, not only debug;
- verify no secret/configuration files enter the APK or Git; and
- repeat the official-document/version check immediately before release.

Do not create Google Play configuration. Do not make a custom domain or verified
name/logo branding a release dependency. The selected non-sensitive `drive.file` scope
does not require sensitive/restricted-scope verification; the project accepts the
consent presentation and small-user constraints of the personal/unverified path.

Do not attach a Google Cloud billing account, request paid quota, or enable a paid API
tier. Current standard Sheets API use is available at no additional cost; explicit
exports remain within standard quotas. A quota failure is shown to the user and CSV
remains available. If Google changes this policy, stop and revisit the integration
rather than adding charges, broader scopes, a backend, or a Workspace subscription.

## 12. Official setup references

- [Authorize access to Google user data](https://developer.android.com/identity/authorization)
- [Implement Sign in with Google](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation)
- [Manage OAuth clients](https://support.google.com/cloud/answer/15549257)
- [Manage OAuth app audience](https://support.google.com/cloud/answer/15549945)
- [Google Picker for desktop and mobile apps](https://developers.google.com/workspace/drive/picker/guides/desktop-mobile-picker)
- [Choose Google Sheets API scopes](https://developers.google.com/workspace/sheets/api/scopes)
- [Drive `files.get`](https://developers.google.com/workspace/drive/api/reference/rest/v3/files/get)
- [OAuth app verification](https://support.google.com/cloud/answer/13463073)
- [When OAuth verification is not needed](https://support.google.com/cloud/answer/13464323)
- [Sheets API limits and pricing](https://developers.google.com/workspace/sheets/api/limits)
- [Google Workspace standardized API model](https://developers.google.com/workspace/tools-safety)
- [OAuth production policy compliance](https://developers.google.com/identity/protocols/oauth2/production-readiness/policy-compliance)
