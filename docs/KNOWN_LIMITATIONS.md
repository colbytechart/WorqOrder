# Known Limitations

## Client CSV import limits

Client import is deliberately bounded to a 1 MiB UTF-8 CSV document, 10,000 records, 20,000 cells,
and 1,024 UTF-16 code units per raw cell. A proposed client name must also fit the normal
100-Unicode-code-point limit after whitespace normalization. Providers must report an approved CSV
MIME type and a `.csv` display name. Files outside these safety limits are rejected atomically;
they are not partially imported.

## Timer and lifecycle

- WorqOrder does not execute a stopwatch loop while backgrounded, screen-off, process-killed, or
  rebooting. The open UTC interval remains in Room and elapsed time is reconstructed when the app
  next resumes. The app does not wake exactly at midnight.
- Swiping from Recents is not a guaranteed process kill on every Android/OEM build. Correctness
  does not depend on whether Android retains or kills the process.
- After reboot, a one-shot receiver performs Room recovery after the first unlock and may restore
  an eligible running-timer notification. Before first unlock there is no presentation. A later
  launch performs the same idempotent recovery again. There is no alarm, application-owned wake
  lock, WorkManager stopwatch job, foreground service, or background ticking loop.
- While one process remains alive, Android elapsed realtime keeps the visible timer monotonic and
  includes sleep. After process death/reboot, no prior monotonic reading is meaningful, so UTC wall
  time is the only reconstruction source.
- A negative recovered wall duration is detected and never saved. A large forward wall-clock jump
  that happened entirely while the process was dead cannot always be distinguished from genuine
  elapsed work without a trusted external time source. WorqOrder does not use a network time
  authority or silently invent a replacement stop instant.
- While a valid process-local anchor exists, forward/backward wall-clock corrections do not change
  elapsed work: normalization and Stop use the monotonic projected UTC instant. Consequently, the
  recorded local Stop label may differ from the device's newly corrected clock.
- A negative process-recovery anomaly keeps the interval open. Correcting device time and resuming
  can repair only the provisional display anchor; historical boundaries are unchanged. Manual
  correction is available only after a valid Stop.
- A database state containing an orphan open interval or mismatched active pointer fails closed.
  WorqOrder will not delete or close the interval automatically because doing so could lose known
  time. A later support/recovery workflow may be needed for genuinely corrupted installations.

## Date and time-zone presentation

- An active session keeps the geographical ZoneId captured at Start even if the operating-system
  device zone changes. The new device zone applies after Stop.
- The reduced CSV/XLSX/Google export schema shows Start/Stop as `hh:mm a`. On a fall-back day those
  external values alone cannot distinguish the first and second occurrence of a repeated clock
  time. Persisted UTC instants, stored ZoneId, correct duration, and explicit editor occurrence
  handling remain intact inside the app.

## Durability boundaries

- Data is not promised to survive uninstalling WorqOrder or clearing its app storage.
- Android application backup is disabled, so WorqOrder does not provide platform backup/restore or
  device-transfer recovery for its app-private database and preferences.
- The required production build does not add WorqOrder-managed encryption to app-private Room or
  DataStore files; it relies on Android's application sandbox. At-rest encryption is deferred to
  optional Milestone E, an unscheduled backburner item outside every release scope, and requires
  separate owner assignment and authorization.
- CSV/XLSX documents and readable Google Sheets cells are external plaintext copies. Optional
  local encryption would not extend to those exports.

## User-interface scope

- Main and all forms are phone-adaptive and remain scrollable on short, landscape, narrow, and
  large-text layouts. Landscape uses approximately equal handed content columns below a spanning
  title/settings bar: one independently scrollable task list and one timer/date/action region.
  Right-handed is the default and Left-handed mirrors only those columns. No tablet-specific
  layout beyond this responsive two-column Main is provided.
- At very large font scales, the complete timer value remains one line and may require horizontal
  scrolling; TalkBack receives the entire formatted duration without scrolling.
- Automated Compose semantics cover labels, selected/running states, validation/error
  announcements, confirmations, and 200% font behavior. Human TalkBack and representative
  display-scaling checks passed for `0.1.0`; device/OEM combinations beyond the documented release
  matrix remain best-effort rather than tablet- or OEM-specific layouts.

## Planned v0.2.0 limitations

- Automatic daily export is Google-Sheets-only and best-effort under Android background scheduling.
  The implementation uses stable unique one-time WorkManager jobs and captures the intended work
  date/ZoneId, so a delayed run after midnight exports the prior date. Android/Google
  authorization, connectivity, Doze, force-stop, OEM policy, and user settings may require an
  actionable pending flow rather than guaranteed unattended completion. The oldest unresolved date
  is retained and later dates advance one bounded worker at a time after recovery.
- Unattended Google authorization is possible only while Play services can return an already-granted
  short-lived `drive.file` token without interaction. If Google returns an authorization
  resolution, the user must tap a content-free notification or resume in Settings. WorqOrder does
  not store refresh tokens or add a backend to bypass this limitation.
- Pending-export notification delivery is controlled by Android and the user. API-33+ permission
  denial prevents enabling the feature; later permission/channel revocation or OEM suppression can
  hide a notice, but it does not clear the durable pending target. Successful automatic exports are
  intentionally silent. Settings reports notification, spreadsheet-connection, destination, and
  local-settings enablement blockers separately. WorkManager timing remains best-effort under the
  documented Android/OEM constraints. Milestone 26 automated coverage passed; the owner accepted
  the documented subset of manual timing checks and deferred the remaining OEM/background
  variations to defect-driven follow-up.
- WorkManager's normal library integration includes system-managed scheduling components and
  bounded execution wake locks. WorqOrder does not own a wake lock, exact alarm, foreground export
  service, or automatic-export boot receiver, and automatic work is never used for stopwatch
  ticks. The separate running-notification boot receiver is one-shot after first unlock.
- Google Play services is used as an installed-device authorization API. WorqOrder is not published
  through Google Play Store and has no Play App Signing, Play Console release, or Play verification
  dependency.
- Milestone 28 implements the owner-approved standard running-timer notification. Android provides
  no portable automatically placed lock-screen-only AppWidget across API 26-36: the surface also
  appears in the notification shade.
  Permission/channel/user/OEM privacy settings can suppress it or redact Client and task
  Description.
  After reboot it cannot return before first unlock; after force-stop it cannot return until the
  user launches WorqOrder. The system, not WorqOrder, controls exact notification typography and
  compact chronometer position/formatting. Granting notification permission is reconciled when
  WorqOrder regains window focus; if its process no longer exists, the next launch does so. If
  midnight passes while no WorqOrder component is executing,
  the notification cannot reflect the daily split until the next recovery trigger and may visibly
  reset when Room normalization occurs. See `LOCK_SCREEN_SURFACE_ADR.md`.
- Migrated tasks cannot invent historical Consultant (internally Employee), Work Type, Billing
  Status, or Mileage values; they
  remain blank/Unspecified until explicitly edited.
