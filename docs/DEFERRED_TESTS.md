# Deferred Tests

This register records tests the owner has explicitly removed from the current release gate. A
deferred test is **not** a passing result. It preserves the unexecuted procedure and evidence gap so
it can be revisited without misrepresenting release confidence.

The owner reconfirmed during Milestone 55C that both entries remain deferred after the `0.6.0`
release. Publication does not reopen them; only a new explicit owner instruction does.

## DT-001 — `0.6.0` manual API 26 Backup & Restore lifecycle/performance matrix

- **Deferred:** 2026-09-24 by explicit owner decision during Milestone 54C.
- **Release effect:** Not required for the `0.6.0` release. The complete automated API 26 connected
  suite remains required and passed by owner report.
- **Existing substitute evidence:** The complete current-target connected suite and current-target
  malformed/valid archive, cancellation, replacement, rotation/background, forced-stop recovery,
  repeated Restore, and ten-swap stability matrix passed by owner report.
- **Procedure retained:** On an API 26 emulator, reject the known malformed fixture without
  mutation; import the valid fixture; force-stop/reopen; Restore; force-stop/reopen; Restore again;
  then complete at least three additional swaps while sampling PSS/RSS and checking that only one
  complete generation is retained after every reopen.
- **Revisit when:** The owner requests it, an API 26-specific report is received, minimum-SDK or ZIP
  parsing behavior changes, or a future release materially changes replacement/recovery behavior.

## DT-002 — `0.6.0` manual physical-device matrix

- **Deferred:** 2026-09-24 by explicit owner decision during Milestone 54C.
- **Release effect:** Remaining manual physical-device checks are not required for the `0.6.0`
  release. This includes the Milestone 54C physical lifecycle/performance matrix and the final
  signed/public-asset physical-device smoke that had been planned for Milestone 55.
- **Existing substitute evidence:** Current-target emulator archive/recovery/stability checks passed.
  Final signed install-over, fresh-install, signer, package/version, checksum, Google, and public-
  asset checks remain required, but may be performed on suitable emulators.
- **Procedure retained:** Install the signed candidate over populated public `0.5.0`; verify data
  preservation; create/import/restore a populated backup across reboot/reopen; reconnect Google and
  prove new-origin append behavior; run a timer for at least one hour split between foreground and
  locked/background states; record CPU, PSS/RSS, battery, and temperature; then validate a backup.
  After publication, independently download and inspect/install the unchanged public APK.
- **Revisit when:** The owner requests it, an OEM/device-specific report is received, Android runtime
  behavior changes, or a future release changes signing, migration, timer, Google, or backup logic.

## Recording rule

Release documentation must cite this file when omitting either matrix. It must say **deferred by
owner decision**, never “passed,” “not applicable,” or “verified.” Any future execution should append
the environment, app version/commit, commands, results, and date without deleting the original
deferral record.
