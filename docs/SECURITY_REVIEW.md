# WorqOrder Security Review

Audit date: 2026-08-09
Scope: WorqOrder `0.2.0` through Milestone 29

## 1. Security posture

WorqOrder is a local-first offline application. Android's application sandbox protects Room and
DataStore from ordinary cross-app access. WorqOrder-managed at-rest encryption is not implemented
and remains release-agnostic, unscheduled optional Milestone E outside every release scope until
the owner assigns it. CSV/XLSX files and readable Google Sheets cells are intentional
plaintext external copies.

This review found no embedded credential, destructive migration, broad storage permission,
cleartext network path, sensitive logging path, service-account material, Firebase dependency, or
custom backend.

## 2. Findings

| Finding | Severity | Result |
|---|---:|---|
| Platform backup exposed app-private Room/DataStore to Android backup/transfer policy | Medium | Fixed: `allowBackup=false` plus Android 12+ cloud/transfer exclusion rules |
| Tracked credential/key/token patterns | High | No matches |
| `local.properties`, keystores, logs, APKs, Google service JSON | High | Ignored; none tracked |
| Raw Google access/refresh token persistence | High | None; access token is operation-scoped memory only and its `toString()` is redacted |
| OAuth/service-account secret in APK | High | None; the injected Web OAuth client ID is public identifier metadata, not a secret |
| OAuth scope breadth | Medium | Exact `drive.file` only; no broad Drive/Sheets scope |
| Google authorization header/response logging | High | No production logging calls; typed sanitized failures |
| Cleartext network traffic | High | Gateway uses HTTPS; target-SDK platform policy disallows cleartext by default |
| Storage permissions | Medium | None; CSV/XLSX use user-mediated create-document contracts |
| Timer/background privileges | Medium | No foreground timer service, exact alarm, app-owned wake lock, or tick worker. One post-unlock notification receiver and opt-in Google-only WorkManager schedule are narrowly scoped and documented. |
| Notification privacy | Medium | Running Timer uses a private notification with a blank public supporting line; pending-export notifications contain no client/task/Consultant/date/spreadsheet content. |
| Destructive Room recovery/migration | High | None; explicit `1→2→3→4` migrations and fail-closed invariant handling |
| CSV formula interpretation | Medium | Accepted documented risk: faithful RFC CSV may be interpreted by downstream spreadsheet software; no silent data mutation |
| Google/XLSX formula execution | High | Mitigated by literal string cell APIs/types |
| Published dependency advisories | High | All 180 resolved debug/release Maven coordinates checked against OSV on 2026-08-09; zero vulnerability records returned |
| Preview dependency policy | Medium | Direct pins are stable. D-080 records the owner-approved `play-services-identity-credentials:16.0.0-alpha08` transitive exception introduced by stable AndroidX Credentials 1.6.0. |
| Gradle wrapper substitution | High | Distribution checksum pinned; checked-in wrapper JAR matches Gradle's published 9.4.1 checksum |
| At-rest encryption | Medium | Outside all release scopes; unscheduled optional Milestone E |

## 3. Manifest and permission review

The source manifest directly requests only:

- `INTERNET`, for explicit and opt-in automatic Google operations;
- `POST_NOTIFICATIONS`, requested contextually on API 33+ for pending-export recovery and the
  optional running-timer surface; and
- `RECEIVE_BOOT_COMPLETED`, for one post-unlock running-notification recovery and WorkManager's
  normal persisted scheduling integration.

Merged manifests also contain:

- `USE_BIOMETRIC` and legacy `USE_FINGERPRINT` from stable
  `androidx.credentials -> androidx.biometric`. WorqOrder does not implement an app access gate;
  these normal permissions support the system Credential Manager dependency and create no
  WorqOrder biometric prompt by themselves.
- an application-signature dynamic-receiver permission from AndroidX Core. It is not a user data
  permission; and
- WorkManager's normal scheduling components/permissions, including its bounded execution wake
  lock and library `FOREGROUND_SERVICE`/`SystemForegroundService` declarations. WorqOrder does not
  directly acquire a wake lock, does not promote its automatic-export worker to foreground work,
  and does not use WorkManager's foreground service for timer display.
- WorkManager diagnostics/profile components protected by Android's `DUMP` permission, its job
  service protected by `BIND_JOB_SERVICE`, and Google Auth's revocation service protected by
  Google's revocation permission. They do not expose an unprotected WorqOrder data endpoint.

There are no storage, location, contacts, calendar, exact-alarm, package-management, or broad file
permissions. Both application receivers are non-exported. The release manifest is not debuggable.
Platform backup is disabled.

## 4. Google authorization and network boundary

- Credential Manager handles identity; `AuthorizationClient` grants `drive.file`.
- The app asks the user to paste/select one spreadsheet and validates exact spreadsheet identity
  and edit capability.
- Non-secret account hints, spreadsheet ID/title, connection time, and status may be stored in
  DataStore. Tokens are not.
- Disconnect clears spreadsheet metadata without necessarily clearing identity. Sign-out clears
  spreadsheet metadata and requests credential/authorization cleanup.
- The gateway never lists all Drive files, creates arbitrary spreadsheet documents, automatically
  retries, enables billing, or stores full API responses.
- Google writes one atomic batch of literal strings and requires WorqOrder developer metadata
  before replacing an existing date tab. Unowned same-name tabs fail closed.
- Network and malformed-response errors are typed and sanitized; Room remains unchanged.

## 5. Repository and generated-output inspection

Tracked-file scans found:

- no Google API key;
- no OAuth access/refresh token;
- no Web OAuth client ID value;
- no OAuth client secret;
- no service-account/private key;
- no real user email or real spreadsheet ID in fixtures;
- only synthetic `person@example.com`, `spreadsheet-id`, and explicit test-token values;
- no production `Log`, `println`, or stack-trace output of sensitive values.

The current public Web OAuth client ID is supplied from ignored local configuration and becomes a
BuildConfig value. The permanent signing key, its password, release fingerprint configuration,
and local properties must remain outside Git for every release.

## 6. Data handling

- Room stores full client/task/interval metadata and remains authoritative.
- Preferences DataStore stores typed non-authoritative settings, selection/connection hints,
  automatic-export target state, and one dismissed running-interval ID.
- Client/task descriptions and purchases are not logged.
- Snapshot rows live in process memory during explicit export. CSV/XLSX output is sent directly to
  a user-selected document URI; Google data travels over TLS.
- App-private data is not promised to survive uninstall/storage clearing, and disabled Android
  backup plus explicit Android 12+ data-extraction exclusions mean no WorqOrder platform-restore
  path is provided.
- No code silently deletes/reseeds production Room data or falls back to destructive migration.

## 7. Accepted limitations and release work

- Rooted/compromised devices, unlocked physical possession, debugging/forensic privileges, or an
  OS vulnerability may expose unencrypted app-private data.
- External exports are plaintext by product design.
- Some programs can execute formula-like CSV cells after the user opens/imports the file. Safe
  import guidance is required because silently prefixing values would violate canonical fidelity.
- With explicit owner-approved internet access, all 180 unique Maven coordinates resolved across
  the debug and release runtime graphs were queried through OSV's version-aware batch API on
  2026-08-09. OSV returned zero vulnerability records. This is point-in-time evidence, not a
  guarantee against undisclosed or future vulnerabilities.
- Official release indexes were reviewed. The selected Compose BOM, Activity, Credentials,
  DataStore, Navigation, Room, AGP, KSP, and Google authentication releases remain stable/current.
  Newer stable Kotlin and coroutines versions exist, but a late toolchain/concurrency upgrade was
  rejected without a defect or advisory. AndroidX Core 1.19 and Lifecycle 2.11 require compile SDK
  37; the proven 36.1 build therefore retains Core 1.18 and Lifecycle 2.10 until a coordinated SDK
  upgrade milestone.
- The stable AndroidX Credentials Google-provider adapter resolves Google's
  `play-services-identity-credentials:16.0.0-alpha08` internally. WorqOrder does not use it
  directly. The owner approved this one release exception under D-080 after the resolved-graph,
  OSV, clean-build, and regression review; it is not blanket permission for preview dependencies.
- Gradle 9.4.1 is the documented version for AGP 9.2. The wrapper distribution SHA-256 is pinned,
  and the checked-in wrapper JAR independently matches Gradle's published SHA-256.
- The permanent RSA-4096 release key is external/ignored, the release APK verifies with one signer,
  the exact SHA-1 is registered to the `worq.order` release Android OAuth client, and the External
  audience is In Production with only `drive.file`, no active verification gate, and no billing.
- The `0.1.0` fresh-account live signed-APK exercise and device/accessibility/performance matrix are
  retained baseline evidence. Milestone 29 repeated and passed the release-specific signed-update,
  current Google, notification/scheduler, and artifact checks for `0.2.0`.

## 8. Security gate

The repository-local Milestone 29 inspection found no embedded-secret, broad-permission,
destructive-data, network-cleartext, unowned-sheet-overwrite, or wrapper-integrity defect. Final
APK signature/checksum evidence is recorded in `QA_REPORT.md`. The clean automated gate,
API-26/API-36.1 connected and manual gates, merged-manifest inspection, signed `0.1.0` to `0.2.0`
device update, production non-test-account Google/export exercise, and current dependency advisory
check all pass. No known pre-publication security blocker remains; the published APK must still be
downloaded and checked against the recorded checksum and signer after the GitHub Release exists.
