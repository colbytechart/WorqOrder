# Known Limitations

## Timer and lifecycle

- WorqOrder does not execute a stopwatch loop while backgrounded, screen-off, process-killed, or
  rebooting. The open UTC interval remains in Room and elapsed time is reconstructed when the app
  next resumes. The app does not wake exactly at midnight.
- Swiping from Recents is not a guaranteed process kill on every Android/OEM build. Correctness
  does not depend on whether Android retains or kills the process.
- Reboot recovery begins only when the user launches WorqOrder. There is no boot receiver,
  notification, alarm, wake lock, WorkManager stopwatch job, or foreground service.
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
- The reduced CSV/XLSX/Google export schema shows Start/Stop as `HH:mm`. On a fall-back day those
  external values alone cannot distinguish the first and second occurrence of a repeated clock
  time. Persisted UTC instants, stored ZoneId, correct duration, and explicit editor occurrence
  handling remain intact inside the app.

## Durability boundaries

- Data is not promised to survive uninstalling WorqOrder or clearing its app storage.
- The required production build does not add WorqOrder-managed encryption to app-private Room or
  DataStore files; it relies on Android's application sandbox. At-rest encryption is deferred to
  optional Milestone 19 and requires separate owner authorization.
- CSV/XLSX documents and readable Google Sheets cells are external plaintext copies. Optional
  local encryption would not extend to those exports.

## User-interface scope

- Main and all forms are phone-adaptive and remain scrollable on short, landscape, narrow, and
  large-text layouts. No tablet-specific two-pane or expanded-window layout is provided.
- At very large font scales, the complete timer value remains one line and may require horizontal
  scrolling; TalkBack receives the entire formatted duration without scrolling.
- Automated Compose semantics cover labels, selected/running states, validation/error
  announcements, confirmations, and 200% font behavior. A human TalkBack listening pass and
  representative OEM-specific display-scaling pass remain release-audit checks because their
  subjective output cannot be conclusively validated by instrumentation alone.
