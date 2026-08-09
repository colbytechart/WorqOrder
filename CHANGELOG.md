# Changelog

All notable changes to WorqOrder are documented here.

The project follows semantic versioning for public release identifiers.

## [0.2.0] - 2026-08-09

### Added

- Consultant directory management, persisted current Consultant selection, and assignment-time
  Consultant snapshots on tasks.
- Task Work Type, Billing Status, Mileage, and derived Billing Minutes.
- Client-name CSV import that appends/restores valid names without overwriting the existing list.
- One canonical schema-4 export shared by CSV, XLSX, and Google Sheets with 15 columns, including
  Consultant, Work Type, Billing Status, Mileage, and Billing Minutes.
- Right-/Left-handed two-column landscape layouts with an independently scrolling task list.
- Opt-in captured-date automatic Google Sheets export using bounded WorkManager scheduling and
  content-free pending recovery.
- Silent, dismissible running-timer notification with Android's system chronometer and
  private/redacted lock-screen content.
- Settings version footer for `WorqOrder v0.2.0 - stable`.

### Changed

- Room advances non-destructively from schema 2 to schemas 3 and 4; released tasks migrate with
  honest blank/Unspecified defaults for metadata that did not previously exist.
- Interval clock values and export Start/Stop values use 12-hour `hh:mm AM/PM` presentation while
  preserving exact UTC timestamps internally.
- Routine interval cards omit the redundant per-interval Duration row and show Task Total plus
  Billing Minutes once above the interval list.
- Settings separates Client and Consultant management and keeps device time-zone behavior hidden
  but compatible with retained installations.
- Free-text fields request sentence capitalization without rewriting user-entered text.

### Security and Privacy

- Android backup remains disabled; Room remains authoritative.
- Google access remains `drive.file` only, External/In Production, without billing or an
  owner-managed tester list.
- Notification permission is requested only for the optional system surfaces. Pending-export
  notifications contain no work metadata; the running-timer notification uses a blank public
  supporting line when Android hides sensitive content.
- No Firebase, backend, broad storage permission, exact alarm, foreground timer service,
  destructive migration, embedded credential, or paid service was added.

### Known Limitations

- Automatic Google export is best-effort under Android/OEM background and Google authorization
  policy; captured pending dates remain recoverable when unattended completion is unavailable.
- The running-timer notification also appears in the notification shade and can be suppressed or
  redacted by Android/user/OEM settings.
- Existing `0.1.0` tasks cannot invent historical Consultant, Work Type, Billing Status, or
  Mileage values and remain blank/Unspecified until edited.

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
- Natural next-day/midnight device exercises were not a `0.1.0` release gate; the underlying rules
  retain automated real-zone, DST, multi-boundary, and idempotence coverage.
