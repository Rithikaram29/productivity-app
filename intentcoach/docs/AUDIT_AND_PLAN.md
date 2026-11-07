# IntentCoach — Phase 1 Audit & Phase 2 Implementation Plan

_Audited 2026-11-07 against `CLAUDE_CODE_PROMPT` (master prompt). No code was changed during the audit._

---

## 1. What exists today

**Stack (actual):** Kotlin 1.9.24 · AGP 8.5.2 · Gradle 8.9 · Compose BOM 2024.08 · Material3 ·
Room 2.6.1 (KSP) · navigation-compose 2.7.7 · coroutines 1.8.1 · JUnit + Robolectric 4.12.2.
minSdk 26 / target 35. **No DI, no ViewModels, no DataStore, no repositories** — composables call
DAOs and `SharedPreferences` singletons directly. No network code, no `INTERNET`, no analytics. ✔

~1,800 lines across 17 Kotlin files, 47 JVM unit tests (last verified green 2026-09-17).
**10 files are modified but uncommitted** (the work described in `plan.md`) — commit before new work.

| Layer | Files | Notes |
|---|---|---|
| App | `IntentCoachApp` | Builds Room DB v2 with a real 1→2 migration. |
| data | `IntentLog` (entity+DAO+DB), `FocusSession`, `Stats`, `Rewards`, `TimeOfDay`, `Settings`, `WatchedApps` | Pure, well-tested logic in Stats/Rewards/TimeOfDay. Prefs are SharedPreferences. |
| detection | `AppWatchService`, `BootReceiver` | Foreground service polling `UsageStatsManager.queryEvents` every 800 ms. |
| overlay | `InterruptActivity` | Transparent activity launched over the watched app (needs `SYSTEM_ALERT_WINDOW`). |
| ui | `MainActivity` (nav host + setup screen), `AppPickerScreen`, `InsightsScreen`, `BatteryOptimisationCard`, `Palette` | Stack navigation: home → apps / insights. |
| tooling | `.claude/` agents, skills, `check-constraints.sh`, `verify.sh` | Constraint rules: no network, no `QUERY_ALL_PACKAGES`, no `AccessibilityService`. |

**Manifest permissions:** `PACKAGE_USAGE_STATS`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_SPECIAL_USE` (34+), `POST_NOTIFICATIONS` (33+), `RECEIVE_BOOT_COMPLETED`.
`<queries>` lists 21 specific packages. `allowBackup=false` ✔.

## 2. Feature status vs. the master prompt

| Feature | Status | Gap |
|---|---|---|
| A Onboarding | ❌ Missing | Only a single setup screen with permission rows. No intro/privacy/sample check-in, no completion flag. |
| B App selection | 🟡 Partial | Works for a **21-app curated catalog** only; no categories; **5 apps pre-selected by default** (spec: none by default). |
| C Intent intervention | 🟡 Partial | Works end-to-end but: free-text + 3 chips (not the 5 spec intents), **no duration step**, no cooldown, dismissal (Back) not recorded and reveals the app, no manual fallback, `singleInstance` ignores `onNewIntent`. |
| D Session tracking | ❌ Missing | Only a single `intent_log` row at prompt time. **No start/end, no actual duration, no session states.** |
| E Reflection | ❌ Missing | — |
| F Home dashboard | ❌ Missing | Setup screen only. `Rewards`/`TimeOfDay`/`CompanionMood` logic exists but **no UI uses it**. |
| G Insights | 🟡 Partial | Weekly totals, per-app bars, habit rate, recent intents. No daily view, no durations, no unknown bucket, no trends, no per-app history. |
| H Focus mode | 🟡 Data only | `FocusSession` entity + DAO + reward logic exist. **No timer, no screen.** Fields don't match spec (no category/status enum). |
| I Companion | 🟡 Logic only | Mood logic exists; no visuals. Design + Rive plan exist in project docs (post-MVP). |
| J Settings & privacy | 🟡 Partial | On/off + battery card. No cooldown/default duration/auto-intervention toggles, **no delete-all-data**, no privacy policy. |
| Bottom nav (4 tabs) | ❌ Missing | Stack nav only. |
| Tests | 🟡 | Good JVM coverage of pure logic; nothing for detection/session logic (doesn't exist yet); no instrumentation tests. |

## 3. Bugs and architectural concerns found

1. **Likely lost writes in `InterruptActivity.log()`.** The insert runs in `lifecycleScope`, then
   `finish()` is called immediately; `onDestroy` cancels that scope, so the row can be dropped.
   Fix: write through an application-scoped coroutine scope.
2. **Back / swipe dismissal bypasses the prompt silently** — the watched app is underneath and fully
   usable, and nothing is recorded. Spec wants a *Dismissed* state, not a silent bypass and not a false session.
3. **No cooldown.** Leave Instagram for 5 s and come back → prompted again.
4. **`singleInstance` + no `onNewIntent`.** If a second watched app is detected while the prompt is
   up, the prompt keeps the old package name.
5. **Every untracked open is invisible.** If the service is killed (OEM), nothing marks the gap —
   spec requires "unknown"/"incomplete" rather than silence.
6. **`exportSchema = false`** with hand-written migration SQL — fragile for the v3 migration we need.
7. **Notification permission declared, never requested** — on 33+ the FGS notification is hidden.
8. **Reward system tone vs. spec.** `CUT_PENALTY` and `WILTED` mood are a small game economy with a
   loss mechanic; spec says no game economy and no guilt. (Decision needed — §6.)
9. **"Habit rate"/"you had no reason" copy** leans toward judgement; spec says avoid guilt-based messaging.
10. **Palette drift:** code uses the old palette (pink `#E1306C`); project design doc v1.1 specifies
    pastel-on-dark with Lime/Clay/Ember. Also duplicated in `Palette.kt` and `InterruptActivity.kt`.
11. **UI talks to DB/prefs directly** — fine at this size, but the spec's insights/session logic
    needs a repository layer to stay testable.
12. **No device testing has ever happened** (README says so).

## 4. Technical feasibility: intervention mechanism

- **`UsageStatsManager` cannot intercept a launch.** It reports events after the fact. The current
  approach — a foreground service polls `queryEvents()` and, on a watched app's `ACTIVITY_RESUMED`,
  starts an activity on top — is a supported mechanism, with **~0.5–1 s lag** (the app is briefly visible).
- **Starting an activity from the background** is blocked on Android 10+ unless an exemption applies;
  holding a granted `SYSTEM_ALERT_WINDOW` is one. This is why the overlay permission is required.
  Background-launch rules tighten in newer releases — **must be verified on Android 14 and 15 devices.**
- **`AccessibilityService`:** would give instant detection but Play's policy restricts it to
  accessibility use cases (plus declaration + prominent disclosure). **Recommendation: do not use it** —
  consistent with the existing repo rule.
- **`specialUse` foreground service** (API 34+) requires a Play Console declaration with justification.
  Acceptable; document in the listing.
- **Manual fallback** (required): a "Check in" action on Home and a quick-settings-free path so the
  intent flow works with no permissions at all. Check-in logic will be independent of detection.

**Measuring actual foreground time:** reconstruct intervals from `UsageEvents` —
`ACTIVITY_RESUMED`/`ACTIVITY_PAUSED` per package, closed by `SCREEN_NON_INTERACTIVE`,
`KEYGUARD_SHOWN`, `DEVICE_SHUTDOWN`, or another app resuming. This is a **pure function** (events in,
intervals out) and fully unit-testable: duplicates, overlaps, missing pause events, reboots. Events have
limited OS retention, so sessions are closed promptly by the service and reconciled on app start.
If events are missing, duration is stored as **unknown** — never estimated silently.

## 5. Implementation plan (prioritised: intent → session first)

Each milestone ends with: you build in Android Studio + run `./gradlew test`, I fix what fails.

### M0 — Baseline (small)
- Commit the 10 uncommitted files. Turn on Room `exportSchema` (schema dir) for migration safety.
- **Accept:** clean `assembleDebug test` on your Mac.

### M1 — Data layer v3 + repositories
- **Create:** `data/db/` → `MonitoredApp`, `AppSession`, `IntentResponse` entities + DAOs;
  `AppDatabase` v3 with `MIGRATION_2_3` that **converts existing `intent_log` rows**
  (REDIRECTED → turned back; PROCEEDED → intentional, duration unknown; HABIT → "Just checking", unplanned).
  Extend `FocusSession` with `category` + `status` (`COMPLETED`/`ENDED_EARLY`/`ACTIVE`).
- **Create:** `data/repo/SessionRepository`, `FocusRepository`, `AppsRepository`; `data/Prefs` (one
  place for onboarding flag, cooldown, default duration, auto-intervention, enabled).
- **Modify:** `IntentCoachApp` (app-scoped `CoroutineScope`, simple manual container — no Hilt).
- Indexes on `(packageName, startTime)`, FK `IntentResponse.sessionId → AppSession.id` ON DELETE CASCADE.
- **Accept:** migration test (Robolectric/Room) moves sample v2 data into v3 losslessly.
- **Risk:** hand-written migration SQL mismatch → mitigated by exported schemas + test.

### M2 — Intent check-in flow (core)
- **Rewrite UI of** `overlay/InterruptActivity` into 3 steps: intent (5 options + optional note) →
  duration (5/10/15/30/no limit, skippable) → Continue / Go back.
- **Create:** `domain/InterventionPolicy` (pure): cooldown, duplicate-suppression, "already in an
  active session" checks. `domain/Classification` (pure): intent → Intentional/Unplanned.
- Back/dismiss → `DISMISSED` status, no intentional record. `onNewIntent` handled. Writes via app scope.
- Manual check-in reachable from Home (no permissions needed).
- **Accept:** unit tests for policy + classification; on device: open watched app → prompt → each
  path produces exactly one session row.

### M3 — Session tracking
- **Create:** `domain/ForegroundIntervals` (pure reconstruction from usage events) + tests for every
  edge case in §7 of the prompt.
- **Modify:** `AppWatchService` → opens session on Continue, closes it when the app leaves the
  foreground/screen-off, records measured duration; marks `INTERRUPTED` after reboot/force-stop
  with duration from events if available, else unknown. `BootReceiver` reconciles.
- **Accept:** durations in DB match `adb shell dumpsys usagestats` for scripted scenarios.

### M4 — Reflection + history
- Optional reflection prompt (notification-free: shown next time IntentCoach opens, or from history;
  never forced). Session history list + per-app history + "reflect later".

### M5 — Navigation shell, onboarding, settings
- Bottom nav: Home · Insights · Focus · Settings. Onboarding (intro → privacy → pick apps →
  usage access explainer → verify → overlay explainer → sample check-in → home), all skippable.
- Settings: enable, monitored apps, cooldown, default duration, auto-intervention, permission status,
  privacy policy screen, **delete all local data** (clears DB + prefs, stops service).
- App picker gains categories (Social / Entertainment / Communication / Browsing / Other) and starts
  with **nothing selected**.

### M6 — Home dashboard + companion
- Today's overview (monitored time, intentional, unplanned, turn-backs, focus time) from repository.
- Companion drawn with Compose Canvas: one room, 4 expressions, idle breathing, focus-complete reaction,
  time-of-day tint. Gentle message rotation. Adopt v1.1 palette in one shared theme file.
- Rive/3D plan stays post-MVP.

### M7 — Insights
- Daily + weekly views, proportion bar (intentional / unplanned / unknown kept separate),
  per-app table → per-app history, 7-day trend bars, common intents, turn-back rate.
- **Intentional Usage Rate** = intentional ÷ (intentional + unplanned) × 100; unknown, dismissed and
  turned-back excluded from the denominator; labelled "a reflection aid, not a score".

### M8 — Focus mode
- Category + preset picker, start/pause/resume/end, completion screen, history.
- Timer state persisted as timestamps (start, paused-total) so it survives rotation/process death.
- Daily/weekly focus time on Home.

### M9 — Validation & delivery
- Constraint script, unit tests, Room migration test, a small set of instrumentation tests
  (check-in → DB, delete-all).
- **Device test checklist** (you run): Pixel/stock Android 14–15 + one Samsung/Xiaomi; permission
  deny/revoke, reboot, force-stop, screen-off mid-session, rapid app switching, battery killers.
- Delivery report in the format of §10 Phase 5.

## 6. Decisions needed before M1

1. **App selection scope.** Spec wants *all launchable apps*. Can be done **without `QUERY_ALL_PACKAGES`**
   via a `<queries>` launcher-intent filter (not a restricted permission) — but it replaces the curated
   catalog approach the repo deliberately chose.
2. **Reward system.** Keep warmth/streak with cut penalty + wilt, soften it (no penalty, no wilt), or
   drop warmth and keep only companion reactions.
3. **Preferences.** Keep SharedPreferences behind one `Prefs` class (no new dependency), or migrate to DataStore.
4. **Build loop.** I can't compile here; you build after each milestone and paste errors back.

## 7. Classification rules (proposed)

| Check-in | Decision | Type | Status |
|---|---|---|---|
| Something specific / Taking a break / Something else | Continue | Intentional | Active → Completed |
| Just checking / I'm bored | Continue | Unplanned | Active → Completed |
| any | Go back | — (counted as turn-back) | Turned back |
| prompt dismissed (Back/home) | — | Unknown | Dismissed |
| no prompt (service dead, cooldown, manual mode off) | — | Unknown | — (not recorded as unplanned) |
| session cut by reboot/force-stop | — | keeps its type | Interrupted |

"Taking a break" counts as intentional: the user named a purpose. Exceeding the planned duration
is never labelled failure; planned and actual are stored separately.
