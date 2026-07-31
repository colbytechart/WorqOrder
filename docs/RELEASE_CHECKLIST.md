# WorqOrder Release Checklist

Status: Milestone 17 release candidate in verification
Initial release: `0.1.0` (`versionCode = 1`)
Application ID: `worq.order`
Distribution: directly signed APK attached to a GitHub Release

## 1. Permanent release policies

- WorqOrder remains free and open source under GPLv3.
- Distribution is a directly signed APK from the public GitHub repository.
- Android application backup remains disabled.
- Room remains authoritative; CSV, XLSX, and Google Sheets are one-way exports.
- No signing key, password, token, client secret, `local.properties`, or real
  `keystore.properties` enters Git.
- Google support uses only the non-sensitive `drive.file` file-data scope and standard
  no-additional-cost quota. No billing, paid quota, organization, Workspace subscription, or
  custom domain is required.

## 2. Release signing configuration

The permanent keystore is generated and backed up by the owner outside the repository. The
repository contains only `keystore.properties.example`.

For a local release build:

1. Copy `keystore.properties.example` to `keystore.properties` in the repository root.
2. Replace every placeholder with the local values:

   ```properties
   storeFile=X:/secure/external/path/worqorder-release.jks
   storePassword=LOCAL_KEYSTORE_PASSWORD
   keyAlias=worqorder-release
   keyPassword=LOCAL_KEY_PASSWORD
   ```

3. Use a forward-slash absolute path on Windows. The keystore itself remains outside the
   repository.
4. Confirm the local properties file is ignored:

   ```powershell
   git check-ignore -v .\keystore.properties
   ```

   The permanent keystore must resolve outside the repository; because it is external, it is not a
   Git candidate and `git check-ignore` does not apply to it.
5. Never paste passwords into a command, tracked file, issue, log, or chat. Restrict access to
   `keystore.properties` on the development machine and remove it before sharing a workspace
   archive.
6. Run `git status --short --untracked-files=all`; neither the real properties file nor keystore
   may appear.

The release signing configuration is always attached to the release variant. When the ignored
properties file is absent, the configuration points to a deliberately nonexistent placeholder so
AGP's built-in `validateSigningRelease` fails instead of producing an unsigned release. An
incomplete properties file fails during configuration, and an invalid path/alias/password fails
signing validation. Debug builds do not require release signing.

## 3. Google production configuration

Confirmed by the owner on 2026-07-29:

- Audience is External and In Production.
- No OAuth branding verification is pending.
- Google Sheets API, Google Drive API, and Google Picker API are enabled.
- `drive.file` is the only configured file-data scope.
- The Android direct-release client uses package `worq.order` and the permanent release SHA-1.
- The existing Web client ID remains supplied only through ignored local/CI configuration.
- No billing account or paid service is required.
- A fresh Google account that was never listed as a test user is ready for the final signed-APK
  connection/export test.

No owner-managed test list gates users in the confirmed production state. Individual access can
still be blocked by user consent refusal, missing spreadsheet edit permission, account/Workspace
policy, Advanced Protection, Google service availability, or future policy changes.

## 4. Release Variant Decisions

- Release version is `0.1.0` / code `1`.
- Release is not debuggable.
- Code/resource shrinking remains disabled for `0.1.0`. The app is small enough for direct
  distribution, and enabling R8 for the first time at the release boundary would add reflection,
  serialization, Google, Room, and OOXML regression risk without an established size requirement.
- Default optimized ProGuard rules remain configured for a later separately tested decision.
- Debug logging is not used for sensitive responses or tokens.
- Android backup remains disabled.
- Android 12+ cloud backup and device transfer explicitly exclude all app-private storage domains.
- The release key remains external and the real `keystore.properties` remains ignored.
- A signed release APK has already passed AGP signing validation and `apksigner` verification;
  final checksum evidence is regenerated after the clean release gate.

## 5. Completed Release Gate

Milestone 17 completed and recorded:

- representative multi-hour CPU, memory, and thermal observation;
- APK signature, certificate fingerprint, artifact size, and SHA-256 verification;
- final user, privacy, developer handoff, changelog, README, and GitHub installation guidance.

The current dependency/advisory gate is complete: 36 exact runtime/build/test Maven
package-version pairs returned no known OSV vulnerabilities on 2026-07-29. Official release
indexes were reviewed, the proven stable dependency matrix was retained, and Gradle wrapper
distribution/JAR integrity is pinned or verified against Gradle's published SHA-256 values.

The clean device-independent build, JVM/lint/release checks, and the post-backup-rule connected
instrumentation gate are complete. After the final landscape/large-scale refinement, the complete
API 36.1 connected report contains 79 tests with zero failures, errors, or skips.

The minimum-SDK API 26 Pixel 1 gate is complete after correcting two viewport-dependent assertions
and one real short-landscape usability defect. The focused 17-test Main suite and complete
79-test suite passed with zero failures/errors/skips, followed by a successful release smoke and
manual landscape check.

The complete 79-test suite also passes on the Pixel 10 Android 17/API 37.1 emulator. This is
forward-compatibility evidence. The distinct Pixel 10 API 36.1 current-target suite also passes
all 79 tests, followed by successful portrait/landscape task-creation and timer smoke checks.
Minimum, target, and next-API runtime gates are complete.

Human TalkBack, large text, display scaling, and the final compact-landscape visual checks pass.
Landscape keeps WorqOrder left, centers the responsive Export/Add task pair, anchors Settings
right, pins timer/date controls, and leaves the task list independently scrollable.

The owner-signed `0.1.0` release passed sign-in with a fresh account that had never been listed as
an OAuth tester. Spreadsheet connection, first export, same-date duplicate-free re-export,
connection restoration after restart, sign-out/disconnect, and local/remote data preservation all
passed without owner intervention.

The owner-signed release APK also passed a populated same-signature package replacement with a
running timer. Client, task, purchases, appearance preference, selected/running state, interval,
and accumulated total survived without duplicate tasks or intervals. Explicit Room migration
instrumentation separately covers the version 1-to-2 schema transition.

The three natural date-boundary device checks remain explicitly deferred to optional Milestone 18
and are not `0.1.0` release gates: reopening on the next real day, crossing one natural midnight,
and recovering across multiple real midnights. Automated real-zone, DST, multi-boundary,
idempotence, process-recovery, and transaction tests continue to cover the rules.

Physical release profiling initially found excessive visible-timer CPU at the original 50 ms
cadence. After moving presentation refresh to 200 ms, approximately 20 minutes of foreground
timing consumed 30.27 seconds of process CPU (about 2.5% average), one battery percentage point,
and no temperature increase. Android later reclaimed the locked/background process as expected;
Room remains the active-timer authority.

The final no-fraction presentation regression completed on API 36.1 with 79 connected tests, zero
failures/errors/skips, plus 154 passing JVM tests and passing debug/release lint. The final signed
APK is 16,059,788 bytes and verifies with one RSA-4096 APK-v2 signer:

```text
SHA-256  93F83CB4A089425A739AED92C8817BD6A93D210190A97F57D1085B51E6999995
Cert SHA-1  57:51:0C:CB:30:01:A7:E7:0C:43:91:C9:16:A8:0A:0C:C6:03:BB:19
```

## 6. Publish Checklist

After every remaining gate passes:

1. Confirm `git diff --check` and review every tracked change.
2. Copy the verified APK to the release asset name `WorqOrder-0.1.0.apk` without modifying bytes.
3. Publish the final SHA-256 alongside that asset.
4. Commit with `chore: prepare WorqOrder 0.1.0 release`.
5. Create annotated tag `v0.1.0` on the verified commit.
6. Create the GitHub Release and attach only the signed APK, checksum, release notes, and source
   archives GitHub derives from the tag.
7. Download the public asset and repeat signature/checksum/install verification.
