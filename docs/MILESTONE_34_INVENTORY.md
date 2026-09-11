# Milestone 34 Obsolete-Path Inventory

This inventory records Luna's classification pass for Milestone 34A on the `milestone34` branch.
It is intentionally conservative: historical release evidence, schema-4 migration inputs, and
test fixtures that prove upgrade compatibility are not treated as dead code.

## Classification rules

- **A — Historical evidence:** released v0.1/v0.2 behavior, acceptance evidence, or release notes.
  Keep it and label it as historical when a current reader could otherwise mistake it for v0.3.
- **B — Migration or fixture compatibility:** schema 1–4 migration inputs, legacy adapters, or
  tests that make upgrade safety observable. Keep it until the release/migration policy changes.
- **C — Optional Milestone E:** app-lock, at-rest encryption, or privacy-hardening material. Keep
  it deferred and do not touch it in Milestone 34.
- **D — Stale v0.3 contract:** a current production/test/document statement that still promises
  multiple intervals, rollover creation, continuation intervals, or schema-4 export. Only D
  documentation is changed in Luna's phase; D production/test candidates are handed to Terra.

## Documentation reconciled in Task34A

- `README.md`: describes the current zero/one-interval task model, no automatic rollover, and the
  13-column schema-5 export projection.
- `docs/PRIVACY_AND_DATA.md`: distinguishes the released v0.2 15-field history from the current
  v0.3 13-field export and notes that schema 5 does not persist `ordinal`.
- `docs/REQUIREMENTS_TRACEABILITY.md`: marks M30 and M33 as passed, labels v0.2 schema-4 rows as
  historical, and records M34 as in progress.
- `docs/LIFECYCLE_TEST_PLAN.md`: updates the current export-lockout expectation to schema 5.
- `docs/GOOGLE_SHEETS_SETUP.md`: updates the live Google export verification to the 13-column
  schema-5 table and explains safe upgrade of a known owned v0.2 tab.

## Production candidates for Terra (exact symbols)

These are candidates, not authorization to remove them during Luna's phase. Terra must prove each
candidate has no current v0.3 caller before changing it and must preserve migration/history paths.

| Location | Symbol or range | Classification | Evidence and guardrail |
| --- | --- | --- | --- |
| `app/src/main/kotlin/worq/order/data/ActiveTimerRepository.kt` | `TimerSplitBoundary`; `normalizeActiveInterval`; the `closeActiveInterval(expectedIntervalId, boundaries, stop)` overload | D candidate | No production caller remains after `ActiveTimerBoundaryCloser` became the only v0.3 boundary path. Keep the simple stop and exact-boundary APIs. |
| `app/src/main/kotlin/worq/order/data/local/ActiveTimerDao.kt` | `normalizeActiveInterval`; continuation overload of `closeActiveIntervalAndClearTimer`; `applyContinuations`; continuation validation/insert/retarget block | D candidate | Only reachable through the D APIs above. Removing it must not touch schema-4 migration SQL or the singleton/one-interval guards. |
| `app/src/main/kotlin/worq/order/data/local/RoomActiveTimerRepository.kt` | continuation overloads, `normalizeActiveInterval`, and `toEntityInputs` | D candidate | Adapter for the unreachable DAO continuation path; current recovery/stop uses `closeActiveIntervalAtBoundary` or the no-boundary stop. |
| `app/src/main/kotlin/worq/order/data/local/LocalQueryModels.kt` | `TimerContinuationEntityInput` | D candidate | Used only by the DAO continuation path; verify no migration code imports it. |
| `app/src/main/kotlin/worq/order/timer/MidnightBoundaryCalculator.kt` | `boundaries(...)` multi-boundary splitter | D candidate | Current v0.3 uses `firstBoundaryAfter(...)` and closes exactly once. Keep `firstBoundaryAfter`; retain tests until Terra replaces/removes the obsolete splitter coverage. |
| `app/src/main/kotlin/worq/order/model/PersistenceModels.kt` | `WorkInterval.ordinal` presentation property | B compatibility candidate | Schema 5 does not persist the column, but the model/mapping adapter intentionally exposes `ordinal = 1` to legacy UI/test callers. Remove only after all callers are migrated and migration evidence remains intact. |
| `app/src/main/kotlin/worq/order/data/local/RoomMappers.kt` | derived `ordinal = 1` mapping | B compatibility candidate | Same guardrail as `WorkInterval.ordinal`; do not infer that the absence of a Room column permits deleting migration/history evidence. |
| `app/src/main/kotlin/worq/order/data/local/WorqOrderMigrations.kt` | schema-1–4 `ordinal` table/index/query definitions | B — retain | These are required to open installed v0.2 databases and must not be removed in Milestone 34. |

## Test and fixture candidates for Terra

| Location | Symbol or test group | Classification | Required treatment |
| --- | --- | --- | --- |
| `app/src/test/kotlin/worq/order/testing/DomainFakes.kt` | `findLegacyContinuationTask`, `findOrCreateLegacyContinuationTask`, `applyBoundaries`, and the continuation branch in `FakeActiveTimerRepository` | A/D mixed | The helpers support old timer tests and explicit no-continuation assertions. Replace with direct no-task/no-interval assertions first; remove only unused helpers after the tests compile and pass. |
| `app/src/test/kotlin/worq/order/timer/TimerRecoveryCoordinatorTest.kt` | `liveWallJumpUsesMonotonicProjectionAndClosesAtRealMidnight` lookup through `findLegacyContinuationTask` | D test-shape candidate | Keep the behavior assertion, but query current fake state directly instead of naming a legacy continuation helper. |
| `app/src/test/kotlin/worq/order/export/automatic/AutomaticGoogleExportManagerTest.kt` | fake `ActiveTimerRepository` overrides for boundary-list methods | D compile-fallout candidate | Remove only when the production interface methods are removed; retain captured-date/close-before-snapshot assertions. |
| `app/src/androidTest/kotlin/worq/order/data/preferences/PreferencesSettingsRepositoryTest.kt` | fake `ActiveTimerRepository` boundary-list overrides | D compile-fallout candidate | Same interface cleanup as above; no preference behavior is obsolete. |
| `app/src/test/kotlin/worq/order/timer/MidnightBoundaryCalculatorTest.kt` | `boundaries(...)` multi-midnight/DST splitter tests | A/B evidence candidate | Preserve as historical compatibility evidence or explicitly replace with one-boundary tests; do not delete without recording why migration/release evidence is still covered. |
| `app/src/androidTest/kotlin/worq/order/data/local/WorqOrderDatabaseTest.kt` | `normalizeActiveInterval` and multi-midnight continuation tests | A/B evidence | These exercise the released continuation transaction and rollback safety. Keep unless Terra can demonstrate equivalent migration/compatibility coverage elsewhere. |
| `app/src/androidTest/kotlin/worq/order/data/local/Schema5MigrationCoreTest.kt` | ordinal-bearing schema-4 fixture and migration assertions | B — retain | This is the populated 4→5 upgrade proof; never remove the legacy ordinal fixture merely because schema 5 omits the column. |

## Document occurrences not changed

`docs/V0_2_MILESTONE_PROMPTS.md`, historical sections of `docs/ACCEPTANCE_TESTS.md`,
`docs/EXPORT_SPEC.md`, `docs/PRODUCT_SPEC.md`, `docs/TIMER_AND_DATE_RULES.md`, the v0.2 section
15 of `docs/ARCHITECTURE.md`, historical milestone entries in `docs/IMPLEMENTATION_PLAN.md`,
`docs/DATA_MODEL.md`, `docs/DECISIONS.md`, `docs/QA_REPORT.md`, `docs/DEVELOPER_HANDOFF.md`,
`docs/RELEASE_CHECKLIST.md`, and `docs/LOCK_SCREEN_SURFACE_ADR.md` contain explicit v0.2 history
or already state that v0.3 supersedes the old contract. They are classified A or B and were not
rewritten to erase history. Optional Milestone E material is C and remains deferred.

Terra's phase should use this list as a bounded checklist, not as permission to remove any item
marked A or B.

## Task34B outcome — Terra

Terra removed the proven-unreachable live continuation contract and its compile-only test coupling:

- `TimerSplitBoundary`, the boundary-list repository methods, the Room DAO continuation writer,
  `TimerContinuationEntityInput`, and the multi-boundary calculator were removed.
- Stop retains its expected-interval compare-and-clear transaction, and boundary close remains a
  separate exact-boundary compare-and-clear transaction. Neither operation creates another task or
  interval.
- The obsolete continuation-only database tests and fake helpers were removed. Their relevant
  upgrade evidence remains in `Schema5MigrationCoreTest`; the current atomic no-continuation Room
  test remains in `WorqOrderDatabaseTest`.
- Fakes and test stubs now implement only the current timer API. The recovery regression now
  asserts directly that no same-series task exists on the later date.

Retained for Luna/Sol review: the derived `WorkInterval.ordinal = 1` presentation adapter and all
schema-1–4 migration SQL/fixtures remain B compatibility material.

## Task34C outcome — Luna

The current-contract proof pass reconciled the unscoped statements that could be mistaken for live
v0.3 behavior:

- `PRODUCT_SPEC.md` now identifies the released versions as history, describes exact midnight
  closure without continuation, and records Milestones 30–33 as implemented while M34/M35 remain
  in their respective phases.
- `DATA_MODEL.md` now states that schema-5 `seriesId` is lineage only, current writes never copy
  task metadata to another date, and boundary closure is a domain operation without continuation
  rows. Its migration and historical sections remain intact.
- `ARCHITECTURE.md`, `DECISIONS.md`, `IMPLEMENTATION_PLAN.md`, and `ACCEPTANCE_TESTS.md` now
  identify their historical sections and current v0.3 authority explicitly. Stale references to
  same-series date copies in schema-5 evidence were changed to lineage-row evidence.
- `REQUIREMENTS_TRACEABILITY.md` now separates the released v0.2 audit from the current v0.3
  Section 10 matrix and marks old schema-4/multi-midnight rows historical or superseded.
- `QA_REPORT.md` and `SECURITY_REVIEW.md` are explicitly labeled historical release evidence;
  neither is presented as the current v0.3 gate.

Focused proof searches were run against production, test, and documentation paths. Production and
current test code contain no `TimerSplitBoundary`, `normalizeActiveInterval`,
`TimerContinuationEntityInput`, `applyContinuations`, `MidnightBoundaryCalculator.boundaries`,
or legacy continuation-helper references. Remaining matches for “continuation”, “rollover”, old
interval counts, and schema 4/15 columns occur only in explicitly historical, migration, or
Milestone-34 inventory evidence. Existing SelectionCoordinator and timer-recovery regressions
already assert no task creation on date/ZoneId reconciliation and no continuation after boundary
closure; no additional production behavior was required by the traceability pass.

Task34C is complete. Sol Task34D is the next and final Milestone-34 phase: audit the combined diff,
confirm migration/history retention and scope boundaries, run the complete owner-performed gate,
and accept or correct the milestone.

## Task34D audit corrections — Sol

Sol's combined review found two bounded cleanup/evidence gaps and corrected both without changing
schema-5 behavior:

- The unreachable `NormalizeTimerResult.Normalized` branch and the obsolete `splitCount`/
  `normalizedSplitCount` diagnostic fields still exposed continuation-era vocabulary even though
  no current implementation could produce a continuation. They were removed with their exhaustive
  no-op branches and stale assertions.
- Removing the multi-boundary calculator had also removed direct 23-hour spring-forward, 25-hour
  fall-back, and non-DST boundary checks. Focused tests now exercise those cases through the current
  `firstBoundaryAfter` API, alongside the retained skipped-local-date regression.

The review confirms that no Room entity, schema JSON, migration, dependency, permission, UI,
export adapter, OAuth/signing configuration, or backup rule changed. `MIGRATION_4_5` and its
populated zero-/one-/many-interval and active-non-first fixture remain intact. The current Room
boundary test still proves transactional compare-and-close behavior, one concurrent winner,
idempotence, singleton clearing, one retained interval, and no continuation task.

Retained intentionally: schema-1–4 SQL/fixtures and labeled v0.1/v0.2 documentation; schema-4
Google-tab upgrade handling; the derived `WorkInterval.ordinal = 1` presentation adapter used by
legacy model/test construction; and optional Milestone E material. None is a current schema-5
storage or product contract.

## Task34C owner-run verification command set

The focused compile gate was run by the owner with the project-local Gradle user home and passed:

```powershell
Set-Location 'T:\_SC Video\PROJECTS\2026\DNA Work Order App\app\WorqOrder'
$env:JAVA_HOME = 'S:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = 'C:\Users\colby\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = (Resolve-Path -LiteralPath '.gradle').Path
$projectUserHome = (Resolve-Path -LiteralPath '.android-user').Path

.\gradlew.bat "-Duser.home=$projectUserHome" --offline --no-daemon `
    :app:kspDebugKotlin `
    :app:compileDebugKotlin `
    :app:compileDebugUnitTestKotlin `
    :app:compileDebugAndroidTestKotlin
```

The final Sol gate should run the complete permitted local set with an active emulator:

```powershell
.\gradlew.bat "-Duser.home=$projectUserHome" --offline --no-daemon `
    :app:testDebugUnitTest `
    :app:lintDebug `
    :app:assembleDebug `
    :app:assembleRelease `
    :app:connectedDebugAndroidTest
```

The owner must report each task's exact result; no instrumentation or release result is inferred
from the focused compile pass.

## Task34D final gate result

The owner ran the complete command above with the project-local Gradle/Android user homes and an
active emulator. Result: `BUILD SUCCESSFUL in 5m 31s`; 138 actionable tasks, 31 executed and 107
up-to-date. This verifies the JVM suite, debug lint, debug and release assembly, and the complete
connected debug instrumentation suite after Sol's final corrections. Milestone 34 is complete;
Milestone 35 remains the separate release-readiness and signed-upgrade gate.
