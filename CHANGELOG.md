# Changelog

All notable changes to WorqOrder are documented here.

The project follows semantic versioning for public release identifiers.

## [0.1.0] - 2026-07-31

### Added

- Offline-first client, daily-task, repeated-interval, and one-active-timer workflows.
- Room persistence with explicit migrations and committed schemas.
- Process/reboot timer reconstruction, monotonic live display, date rollover, midnight splitting,
  DST handling, and manual clock-change protection.
- Task creation/editing/deletion and manual interval validation/editing.
- Client add, rename, archive, and restore management.
- System/Light/Dark appearance and device/manual geographical time-zone settings.
- One canonical nine-column export snapshot shared by CSV, XLSX, and Google Sheets.
- UTF-8 CSV and focused one-off OOXML XLSX export through Android's document interface.
- Google account authorization, one connected spreadsheet, and idempotent per-date worksheet
  replacement with `drive.file`.
- Phone-adaptive Material 3 UI, pinned timer/date controls, newest-first task list, and
  accessibility semantics.
- Clean `HH:MM:SS` accumulated-duration presentation while retaining precise interval storage and
  calculations.
- Direct signed-APK release configuration and public developer/user/privacy documentation.

### Security and Privacy

- Android application backup disabled.
- No Firebase, custom backend, analytics, ads, billing, broad storage permission, embedded OAuth
  secret, service-account key, or destructive Room fallback.
- User-directed external exports documented as plaintext copies.

### Known Limitations

- Local data does not survive uninstall or Clear Storage.
- WorqOrder-managed local at-rest encryption and application access locking are optional,
  separately authorized future work.
- Natural next-day/midnight device tests remain deferred to optional Milestone 18; the underlying
  rules have automated coverage.
