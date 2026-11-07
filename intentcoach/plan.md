# IntentCoach → testable v1 — execution plan

> **Superseded 2026-11-07** by `docs/AUDIT_AND_PLAN.md` and `docs/DELIVERY.md` (MVP v0.2.0).
> Kept for history.

> **Status: all 9 brief tasks are done, executed serially rather than in waves** (the
> brief asked for one task at a time with a diff after each). Tasks 1–9 landed as
> described below; the wave structure is kept as the template for the next batch of work.
> Wave 0 deps: `navigation-compose`, `junit`, `robolectric`, `androidx.test:core-ktx` — all
> added. Verified 2026-09-17: `clean assembleDebug test` green, 25 unit tests × 2 variants.

Source of truth for the parallel-agent run. Each **wave** contains tasks whose **owned
files are disjoint**, so every task in a wave can run at the same time in its own agent.
Dispatch with `/run-wave`; protocol lives in `.claude/skills/parallel-waves/SKILL.md`.

Status vocabulary: `todo` · `in-progress` · `done` · `blocked`.

## Ownership rules

- One writer per file, ever. Two tasks that want the same file belong in different waves.
- **Orchestrator-owned (never delegated):** `app/build.gradle.kts`,
  `app/src/main/AndroidManifest.xml`, `ui/MainActivity.kt`, `README.md`, `plan.md`.
  Changes to these happen in a wave's pre-wave step or in Wave 3.
- **One Gradle runner per wave** — the project lock is exclusive.
- Agents: `kotlin-implementer` · `compose-screen-builder` · `test-author` ·
  `build-verifier` (haiku) · `constraint-auditor` (haiku).

## Already in the tree (uncommitted, verify — do not redo)

- `AppWatchService`: self-package bail-out before the reset, fixing the interrupt loop.
- `InterruptActivity`: "Continue anyway" disabled + hint while the intent field is blank.
- `MainActivity`: permission state re-checked on `ON_RESUME` instead of first composition.
- Gradle wrapper (8.9) and `gradlew`/`gradlew.bat` exist → **Task 2 of the brief is done.**

---

## Wave 0 — pre-wave, orchestrator only

Single edit to `app/build.gradle.kts` adding every dependency later waves need, so no
agent ever has to touch a shared build file:

- `androidx.navigation:navigation-compose:2.7.7`
- `testImplementation("junit:junit:4.13.2")`
- `testImplementation("org.robolectric:robolectric:4.12.2")` + `testOptions { unitTests.isIncludeAndroidResources = true }`
- `testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")`

Then `./gradlew :app:assembleDebug` once to confirm a green baseline before fanning out.

Status: `todo`

---

## Wave 1 — foundations (4 parallel agents)

| id | goal | agent | owned files |
|----|------|-------|-------------|
| W1-A | **Task 1** — fix the minSdk/FGS mismatch: `maxSdkVersion`/`minSdkVersion` on the API-34+ permission declarations, and branch `startForeground(...)` in `AppWatchService.onCreate()` on `Build.VERSION.SDK_INT`, with a comment saying why. Must run on API 26–35. | kotlin-implementer | `app/src/main/AndroidManifest.xml`, `detection/AppWatchService.kt` *(manifest delegated by exception — no other Wave 1 task touches it)* |
| W1-B | **Task 3 (data half)** — `data/Settings.kt`: SharedPreferences-backed `isEnabled` flag, mirroring `WatchedApps.kt`'s style. | kotlin-implementer | `data/Settings.kt` (new) |
| W1-C | **Task 4 (data half)** — curated catalog of ~20 known distracting apps (package + display name) in `WatchedApps.kt`, plus an `installed(context)` helper using per-package `getApplicationInfo(pkg, 0)` in try/catch. No `QUERY_ALL_PACKAGES`. | kotlin-implementer | `data/WatchedApps.kt` |
| W1-D | **Task 5 (data + logic half)** — DAO queries Insights needs (per-app counts since, per-app outcome counts, recent-with-outcome) on `IntentLogDao`, plus `data/Stats.kt`: pure functions computing totals, redirect count, per-app breakdown, and habit rate from a `List<IntentLog>`. Pure = unit-testable without `Context`. | kotlin-implementer | `data/IntentLog.kt`, `data/Stats.kt` (new) |

Close the wave: `build-verifier`, then `constraint-auditor`.

---

## Wave 2 — surfaces (4 parallel agents) — depends on Wave 1

| id | goal | agent | owned files |
|----|------|-------|-------------|
| W2-A | **Task 4 (UI half)** — `ui/AppPickerScreen.kt`: pick which apps trigger the interrupt, real icon + label per installed app, writes via `WatchedApps.set()`. Entry point `AppPickerScreen(onBack: () -> Unit)`. Does not touch navigation. | compose-screen-builder | `ui/AppPickerScreen.kt` (new) |
| W2-B | **Task 5 (UI half)** — `ui/InsightsScreen.kt`: last 7 days — total interrupts, redirects framed warmly, per-app breakdown, per-app habit rate, recent stated intents with outcome. Compose primitives only. Graceful empty state. Entry point `InsightsScreen(onBack: () -> Unit)`. | compose-screen-builder | `ui/InsightsScreen.kt` (new) |
| W2-C | **Task 7 (component half)** — `ui/BatteryOptimisationCard.kt`: dismissible card shown only when `PowerManager.isIgnoringBatteryOptimizations` is false, deep-linking to the exemption setting; dismissal persists. Explains plainly why it's needed, does not nag. Standalone composable; Wave 3 places it. | compose-screen-builder | `ui/BatteryOptimisationCard.kt` (new) |
| W2-D | **Task 3 (lifecycle half)** — `AppWatchService.stop(context)` beside `start()`, and `BootReceiver` starting the service only when `Settings.isEnabled` **and** both permissions are still granted. | kotlin-implementer | `detection/BootReceiver.kt`, `detection/AppWatchService.kt` *(W1-A must be `done` first — same file, different wave, by design)* |

Close the wave: `build-verifier`, then `constraint-auditor`.

---

## Wave 3 — integration, serial (orchestrator, one writer) — depends on Wave 2

**Task 6 + Task 3 (UI half) + Task 7 (placement)** in a single pass over the shared files:

- Add `navigation-compose` graph in `MainActivity`: main → App Picker → Insights.
  `InterruptActivity` stays entirely outside the graph.
- Main button toggles Start/Stop against real service + `Settings.isEnabled` state.
- Place `BatteryOptimisationCard` on the main screen.

Owned files: `ui/MainActivity.kt` (+ a new `ui/NavGraph.kt` if the graph earns its own file).
Status: `todo`

---

## Wave 4 — tests (3 parallel agents) — depends on Wave 3

| id | goal | agent | owned files |
|----|------|-------|-------------|
| W4-A | Unit tests for `Settings` and `WatchedApps` (Robolectric only where `Context` is genuinely needed). | test-author | `app/src/test/java/com/intentcoach/app/data/SettingsTest.kt`, `.../WatchedAppsTest.kt` |
| W4-B | Unit tests for `Stats` — habit rate, empty set, single row, zero-division, 7-day boundary timestamps. | test-author | `app/src/test/java/com/intentcoach/app/data/StatsTest.kt` |
| W4-C | Full audit pass: constraints + a read of every file changed in Waves 1–3 against `intentcoach-conventions`. | constraint-auditor | *(read-only)* |

---

## Wave 5 — verify and document (orchestrator) — depends on Wave 4

- `.claude/tools/verify.sh` → `assembleDebug` and `testDebugUnitTest` both PASS. Fix failures.
- **Task 9** — update `README.md`: move resolved items out of "Known rough edges", document
  the three screens.
- Produce the closing report: what needs a **real phone** to verify (usage-access grant,
  overlay draw, OEM battery kills on Xiaomi/Samsung/Oppo, real app icons, boot restart),
  and everything any agent flagged as uncertain.

Status: `todo`
