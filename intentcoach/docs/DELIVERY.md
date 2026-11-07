# IntentCoach MVP v0.2.0 — Delivery report

_2026-11-07. Implemented against `CLAUDE_CODE_PROMPT.md` after the audit in `AUDIT_AND_PLAN.md`._

**Verification status, honestly:**
- ✅ The pure logic (`domain/` + the unchanged Rewards/TimeOfDay) and their tests were compiled
  with Kotlin (language 1.9) and run: **72 / 72 JUnit tests pass**.
- ❌ The **Android build has not been run.** No Android SDK was reachable from the authoring
  environment. Compose / Room / Android code was carefully reviewed but never compiled — expect
  a few compile errors on the first `./gradlew assembleDebug`. Robolectric tests (MigrationTest,
  SettingsTest, WatchedAppsTest) have not been run.
- ❌ **Nothing has been tested on a real phone.**

Decisions taken (by the user, 2026-11-07): list all launchable apps (no `QUERY_ALL_PACKAGES`);
keep the reward system as-is; keep SharedPreferences (no DataStore); one build at the end.

## 1. Features completed (code-complete, unverified on device)
- **A Onboarding** — 7 steps, skippable, explains each permission before the user opens Settings, live
  permission check on resume, practice check-in (not saved), starts detection only if both permissions.
- **B App selection** — all launchable apps with icon + label, search, grouped into Social /
  Entertainment / Communication / Browsing / Other, multi-select, nothing pre-selected, stored in Room,
  reachable from Home and Settings. v0.1 explicit selections are migrated once.
- **C Intent check-in** — 3 steps (5 intents + optional words → duration → Continue / Go back).
  Cooldown (configurable 0/1/5/15/30 min), duplicate-prompt guard, no prompt during an active session,
  dismissal recorded as DISMISSED/UNKNOWN (never intentional), manual check-in from Home, practice mode.
  Check-in UI is independent of detection.
- **D Session tracking** — `AppSession` with UUID, times, measured duration, type, status, source.
  Durations are reconstructed from usage events (`ForegroundIntervals`): background time excluded,
  screen-off/keyguard, app switching, duplicates, unsorted events, shutdown/startup, window clipping,
  60 s grace for brief interruptions. No evidence → duration UNKNOWN, never 0. Sessions are closed by
  `reconcile()` from the service, app launch and boot, so they survive service death/force-stop.
- **E Reflection** — optional card on Home for the latest finished session (24 h), "Not now", and any
  time from history. Planned vs actual shown as facts.
- **F Home** — companion room, greeting + gentle message, today's monitored time (Android-measured),
  intentional / unplanned / turned back, focus time, honest notes for unmeasured data, quick actions.
- **G Insights** — today / 7 days, unknown kept separate, Intentional Usage Rate with definition and
  "a mirror, not a score", turn-back rate, daily trend (gaps for no data), common intents, per-app rows
  → per-app history.
- **H Focus** — 5 categories (custom name), presets 15/25/45/60 + custom 5–180, pause/resume/end,
  completion screen, history, today/week totals. Timer stored as timestamps; survives process death.
- **I Companion** — one room, companion + pet, 4 moods, breathing idle, day/night window, lamp glow,
  celebration on focus completion. Drawn in Compose Canvas (no assets).
- **J Settings & privacy** — everything in the brief, in-app privacy policy, delete all data
  (stops service, clears all tables and preferences, returns to onboarding).
- **Navigation** — bottom bar Home / Insights / Focus / Settings + pushed screens.

## 2. Partially implemented
- **Notifications:** only the required foreground-service notice; no reminders.
- **Instrumentation tests:** none. Robolectric tests stand in for DB-level flows.
- **Per-app cooldown:** column exists (`MonitoredApp.cooldownMinutes`), UI only sets the global value
  (spec lists advanced cooldown for v1.1).

## 3. Not implemented (by design / out of MVP scope)
Reels counter, AI insights, multiple rooms/companions, shop/currency, social features, app blocking,
Rive/3D companion (project plan exists), Fraunces/Nunito fonts (system serif used — bundling fonts is a
small follow-up), focus-completion notification while the app is closed.

## 4. Files
**Created:** `data/AppDatabase.kt`, `data/Model.kt`, `data/Sessions.kt`, `data/SessionRepository.kt`,
`data/UsageRepository.kt`, `data/AppsRepository.kt`, `data/FocusRepository.kt`, `domain/*` (6 files),
`ui/Components.kt`, `ui/Lifecycle.kt`, `ui/Companion.kt`, `ui/HomeScreen.kt`, `ui/HomeViewModel.kt`,
`ui/InsightsViewModel.kt`, `ui/HistoryScreen.kt`, `ui/FocusScreen.kt`, `ui/SettingsScreen.kt`,
`ui/OnboardingScreen.kt`, `ui/PermissionRow.kt`, `ui/Reflection.kt`, tests in `test/.../domain/*` and
`test/.../data/MigrationTest.kt`, `docs/AUDIT_AND_PLAN.md`, `docs/DELIVERY.md`.
**Modified:** `IntentCoachApp.kt`, `data/FocusSession.kt`, `data/Settings.kt`, `data/WatchedApps.kt`,
`detection/AppWatchService.kt`, `detection/BootReceiver.kt`, `overlay/InterruptActivity.kt`,
`ui/AppPickerScreen.kt`, `ui/InsightsScreen.kt`, `ui/MainActivity.kt`, `ui/Palette.kt`,
`util/Permissions.kt`, `AndroidManifest.xml`, `app/build.gradle.kts`, `README.md`, `plan.md`,
`.claude/skills/intentcoach-conventions/SKILL.md`.
**Removed (replaced):** `data/IntentLog.kt`, `data/Stats.kt`, `test/.../StatsTest.kt`.

## 5. Dependencies added
`androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4` (one dependency). No network, analytics or DI
libraries. KSP arg `room.schemaLocation` added. versionCode 2 / versionName 0.2.0.

> Note: `collectAsStateWithLifecycle` was deliberately **not** used — lifecycle 2.8 expects Compose UI
> 1.7 for it, and this project's BOM (2024.08.00) is Compose 1.6.8.

## 6. Permissions required
Unchanged set: `PACKAGE_USAGE_STATS`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_SPECIAL_USE` (34+), `POST_NOTIFICATIONS` (33+, now requested when turning on),
`RECEIVE_BOOT_COMPLETED`. `<queries>` changed from 21 packages to MAIN/LAUNCHER + VIEW https intents.
**Play Console:** declare the `specialUse` foreground service with the subtype text in the manifest;
complete the Data safety form ("no data collected/shared").

## 7. Known bugs / risks
1. **First Android compile will likely need small fixes** (never compiled — see top).
2. **Room migration SQL is hand-written**; `MigrationTest` validates it against Room on open — run it.
3. **Returning to the app after Continue** uses the app's launch intent (same as v0.1). Most apps resume
   where they were; a few may restart at their home screen.
4. **Screen turning off during a check-in** counts as a dismissal.
5. **Robolectric start-up** builds the app container (Room + a background coroutine) for every
   Robolectric test — harmless, but if tests become flaky, make `AppsRepository` start lazily.
6. **Pre-existing crash fixed:** `Permissions.hasUsageAccess` called an API 29 method on API 26–28.
7. **Pre-existing data-loss fixed:** check-in writes used `lifecycleScope` and were cancelled by `finish()`.

## 8. Device testing required (none done yet)
Use one stock device (Pixel, Android 14 or 15) and one OEM device (Samsung or Xiaomi).
- [ ] Onboarding end to end; skip every optional step; deny Usage Access; deny overlay.
- [ ] Grant permissions, return to app → status updates without restart.
- [ ] Open a monitored app → check-in appears (note the lag). Each path (Continue / Go back / Not now /
      Back key / Home key) creates exactly **one** history row with the right status.
- [ ] Continue → use 2 min → leave → wait 1 min → History shows ~2 min measured. Compare with
      `adb shell dumpsys usagestats`.
- [ ] Reopen within cooldown → no prompt. After cooldown → prompt. Cooldown "Every time" works.
- [ ] Screen off 30 s mid-session and back → still one session; screen-off time not counted.
- [ ] Switch between two monitored apps → two sessions, no overlap.
- [ ] Reboot mid-session → session closes (Interrupted or Completed); service restarts only if it was on.
- [ ] Force-stop IntentCoach mid-session → reopen app → session closed from events.
- [ ] Revoke Usage Access while running → Home shows "—", no crash; Settings warns.
- [ ] Reflection card appears after a session; Save / Not now; reflect later from History.
- [ ] Focus: start, pause, resume, end early, complete; kill app mid-timer and reopen; rotate.
- [ ] Dashboard numbers match History rows for the same day.
- [ ] Delete all local data → onboarding, empty history, service stopped.
- [ ] Rotate during check-in → answers kept.
- [ ] Upgrade test: install v0.1 APK, create a few interrupts, install v0.2 → history converted.
- [ ] Android 8/9 device or emulator (API 26–28): app opens, Usage Access check doesn't crash.
- [ ] Battery: leave on for a day; check battery usage in system settings.

## 9. Build instructions
See README → Building. Summary: open `intentcoach/` in Android Studio, or
`JAVA_HOME=<JDK 17–21> ./gradlew assembleDebug testDebugUnitTest`. Commit the generated `app/schemas/`.

## 10. APK generation
- Debug: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
  (install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`).
- Release: Android Studio → Build → Generate Signed Bundle / APK (create a keystore, keep it out of git),
  or `./gradlew bundleRelease` for Play (`.aab`), then sign.
