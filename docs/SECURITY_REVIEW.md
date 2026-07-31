# WorqOrder Security Review

Audit date: 2026-07-29
Scope: required production plan through Milestone 17

## 1. Security posture

WorqOrder is a local-first offline application. Android's application sandbox protects Room and
DataStore from ordinary cross-app access. WorqOrder-managed at-rest encryption is not implemented
and remains optional Milestone 19. CSV/XLSX files and readable Google Sheets cells are intentional
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
| Timer/background privileges | Medium | No service, alarm, wake lock, boot receiver, notification, or background-work dependency |
| Destructive Room recovery/migration | High | None; explicit 1→2 migration and fail-closed invariant handling |
| CSV formula interpretation | Medium | Accepted documented risk: faithful RFC CSV may be interpreted by downstream spreadsheet software; no silent data mutation |
| Google/XLSX formula execution | High | Mitigated by literal string cell APIs/types |
| Published dependency advisories | High | Exact Maven coordinates checked against OSV on 2026-07-29; no known affected package/version returned |
| Gradle wrapper substitution | High | Distribution checksum pinned; checked-in wrapper JAR matches Gradle's published 9.4.1 checksum |
| At-rest encryption | Medium | Not current scope; explicitly optional Milestone 19 |

## 3. Manifest and permission review

The source manifest directly requests only `INTERNET`, needed for explicit Google operations.
Merged manifests also contain:

- `USE_BIOMETRIC` and legacy `USE_FINGERPRINT` from stable
  `androidx.credentials -> androidx.biometric`. WorqOrder does not implement an app access gate;
  these normal permissions support the system Credential Manager dependency and create no
  WorqOrder biometric prompt by themselves.
- an application-signature dynamic-receiver permission from AndroidX Core. It is not a user data
  permission.

There are no storage, notification, location, contacts, calendar, alarm, boot, package-management,
or broad file permissions. The release manifest is not debuggable. Platform backup is disabled.

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
and local properties must remain outside Git during Milestone 17.

## 6. Data handling

- Room stores full client/task/interval metadata and remains authoritative.
- Preferences DataStore stores typed non-authoritative settings and selection/connection hints.
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
- With explicit owner-approved internet access, 29 exact resolved runtime Maven coordinates and
  seven build/test coordinates were queried through OSV's version-aware batch API on 2026-07-29.
  OSV returned zero vulnerability records for all 36 package/version pairs. This is point-in-time
  evidence, not a guarantee against undisclosed or future vulnerabilities.
- Official release indexes were reviewed. The selected Compose BOM, Activity, Credentials,
  DataStore, Navigation, Room, AGP, KSP, and Google authentication releases remain stable/current.
  Newer stable Kotlin and coroutines versions exist, but a late toolchain/concurrency upgrade was
  rejected without a defect or advisory. AndroidX Core 1.19 and Lifecycle 2.11 require compile SDK
  37; the proven 36.1 build therefore retains Core 1.18 and Lifecycle 2.10 until a coordinated SDK
  upgrade milestone.
- Gradle 9.4.1 is the documented version for AGP 9.2. The wrapper distribution SHA-256 is pinned,
  and the checked-in wrapper JAR independently matches Gradle's published SHA-256.
- The permanent RSA-4096 release key is external/ignored, the release APK verifies with one signer,
  the exact SHA-1 is registered to the `worq.order` release Android OAuth client, and the External
  audience is In Production with only `drive.file`, no active verification gate, and no billing.
- The fresh-account live signed-APK exercise and the final device/accessibility/performance matrix
  remain release evidence, not missing architecture.

## 8. Security gate

No known embedded-secret, broad-permission, destructive-data, network-cleartext, unsigned-artifact,
unowned-sheet-overwrite, wrapper-integrity, or published-dependency-advisory blocker remains.
The connected regression, device/accessibility/performance matrix, and fresh-account signed-Google
exercise are complete and documented in `QA_REPORT.md`.
