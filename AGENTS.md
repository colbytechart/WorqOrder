# WorqOrder Repository Rules

- This repository is for a native Android application. Use Kotlin application code and Jetpack Compose UI only; do not add Java application code or XML layouts.
- Use Material 3, Compose Navigation, ViewModel, coroutines/Flow, Room, Preferences DataStore, `java.time`, Gradle Kotlin DSL, a version catalog, and KSP where supported.
- Use stable dependency releases only. Document and obtain approval before using any preview, alpha, beta, or release-candidate dependency.
- Keep Room as the authoritative source of task, client, and interval data. CSV, XLSX, and Google Sheets are one-way exports, not databases or synchronization systems.
- CSV, XLSX, and Google Sheets are the only export destinations. XLSX must use a focused, reviewed implementation; do not add Apache POI or another broad Excel stack without explicit owner approval.
- All three destinations must consume the same immutable canonical export dataset. Do not select,
  order, or format exported task fields independently inside a destination adapter.
- Production XLSX creates one new user-selected workbook per export through
  `ACTION_CREATE_DOCUMENT`; it never opens or updates an existing workbook. Persistent-workbook
  mode and automatic midnight export belong only to optional Milestone 18 and require explicit
  owner permission.
- Do not add Firebase, a custom backend, a web wrapper, embedded credentials, service-account keys, passwords, OAuth client secrets, or unrestricted API credentials.
- WorqOrder must remain free and open source under GPLv3. Do not add billing, paid API tiers, paid
  quota increases, subscriptions, or a Google Workspace/organization requirement.
- Do not prepare Google Play distribution or Play app-signing configuration. The supported release
  path is direct distribution signed by the owner's permanent release key, which is deferred until
  Milestone 17.
- Google export must stay within no-cost standard quotas and fail closed without automatic retries
  or charges. If Google's free `drive.file`/Picker/API policy changes, stop Google integration work
  for a new owner decision; CSV must remain available.
- Do not request broad storage permissions. Use Android user-mediated or scoped storage APIs.
- Do not use destructive Room migrations in release builds. Export Room schemas and add versioned migration tests from database version 1.
- Do not persist the changing stopwatch display or write the database on UI refresh ticks. Persist UTC interval boundaries and calculate display values.
- Apply `ZoneId`-aware date rules. Store UTC instants, the assigned work date, and its geographical zone ID; never infer historical work dates again from the current setting.
- Permit only one globally active timer. Enforce the invariant through the singleton active-timer model and transactions, not UI state alone.
- Do not add a foreground service merely to keep a counter alive. Process recovery comes from the persisted open interval.
- Use manual dependency injection through the application container unless a documented, approved need justifies a DI framework.
- Add or update tests whenever behavior changes, especially timer, date-boundary, migration, client-archive, and export behavior.
- Before declaring an implementation milestone complete, run formatting, lint, unit tests, relevant instrumentation tests, and the applicable debug/release builds.
- Do not silently change product behavior. Update the relevant specification and `docs/DECISIONS.md`, and call out the change for review.
- Treat credentials, signing material, `local.properties`, generated files, and account tokens as local secrets; never commit them.
- Protect app-private Room and DataStore contents with the approved Keystore-backed at-rest
  encryption milestone. Never solve key loss or migration failure by silently deleting local data.
  User-directed CSV/XLSX files and readable Google Sheets exports leave the app's encrypted local
  boundary and are not end-to-end encrypted by WorqOrder.
- Biometric, device-credential, PIN, or account-gated app access belongs only to the optional
  post-project milestone and must not be implemented without explicit owner permission.
- Do not start a later milestone unless the user explicitly requests it. In particular, do not scaffold or implement the app during the planning milestone.
