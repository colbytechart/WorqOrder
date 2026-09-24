# Changelog

All notable changes to WorqOrder are documented here.

The project follows semantic versioning for public release identifiers.

## [Unreleased]

- Added the in-development `0.6.0` portable Backup & Restore foundation: bounded versioned
  two-entry ZIP files, strict preflight validation, sensitive/runtime exclusions, atomic Room and
  recoverable Preferences replacement, startup reconciliation, and a one-generation swap restore
  point.
- Added a compact **Backup & Restore** Settings card with scoped Android document pickers,
  destructive-action confirmations, persistent actionable status, timer/busy lockout, picker-race
  guards, and accessible progress/error announcements.
- Hardened current-format import with record-streamed strict JSON decoding, a device-heap safety
  ceiling beneath the absolute archive bounds, single-copy staging, delimiter-safe portable IDs,
  exact transport origins, and a non-dismissible barrier during destructive replacement. Owner-run
  API-26/current connected and current-target archive/recovery/stability verification passed.
  Remaining manual API-26 and physical-device matrices are explicitly deferred in
  `docs/DEFERRED_TESTS.md`; the release audit remains before `0.6.0` publication.

## [0.5.0] - 2026-09-19

- Reusable Description and Hardware / Software Purchase Tag
  catalogs, immutable task snapshots, bounded CSV import, searchable task pickers, centralized
  composition into the unchanged 14-column export schema, and Milestone 47 task-form polish.
- A running timer at the automatic-Google boundary now closes at exact pinned-zone midnight and the
  completed captured date exports automatically; no continuation or duplicate next-day task is
  created, and a durable timer fallback resumes automatically after later Stop/reconciliation.
- Released as `versionName = 0.5.0`, `versionCode = 5`. The owner verified the clean signed
  artifact, permanent signer, package identity, checksum, populated `0.4.0` install-over, fresh
  install, GitHub asset download, and physical-device installation.

## [0.4.0] - 2026-09-15

### Added

- Optional, editable task Notes with a 999-character limit. Notes are blank for migrated and new
  tasks and blank on repeated-Start copies.
- The shared schema-6 projection: 14 visible columns across CSV, one-off XLSX, manual Google, and
  automatic Google export, with Notes appended as column 14.

### Changed

- Room's additive 5-to-6 migration initializes historical Notes to blank while preserving the
  existing task graph and active-timer authority.
- Owned Google schema-5 tabs upgrade safely to schema 6 without clearing another device's rows or
  hidden transport identities; ambiguous tabs fail closed.
- Create Task now starts directly with the date and client selection; Consultant assignment remains
  required but redundant presentation is removed.
- Cancel and Create remain pinned in a bottom action bar while the form scrolls.
- Edit Task omits the visible time-zone line and places Delete Task and Save Task Changes in a
  fixed, equal-width footer. Both task-form footers match the selected theme's page background.

### Release Status

- The clean signed `0.4.0`/code 4 candidate, populated migration, Notes/schema-6 checks, live
  automatic Google export, API-26/API-37 device checks, and non-tester Google access passed. The
  owner published the GitHub release, downloaded its APK, installed it on a physical device, and
  reported expected behavior.

## [0.3.0] - 2026-09-13

### Changed

- Room schema 5 losslessly migrates prior tasks and intervals to the zero-or-one-interval task
  model. Each additional historical interval becomes a distinct task row with retained metadata
  and lineage; no historical interval is discarded or merged.
- Starting a completed task creates and selects a new same-day task carrying the source task's
  user metadata and lineage. Browsing dates or changing the effective ZoneId does not create a
  task.
- A timer that crosses local midnight closes at the exact pinned-ZoneId boundary without creating
  a continuation task or interval.
- CSV, XLSX, and Google Sheets consume the shared schema-5 immutable projection: 13 visible
  columns and one row per task, without interval-number or interval-duration columns.

### Release Status

- Publicly released as `versionCode = 3`. The owner verified the public APK checksum, installed
  it on a physical device over existing data, and confirmed new task timing and intervals.

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
