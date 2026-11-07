# IntentCoach

A privacy-first Android companion for intentional phone use. When you open an app you
chose, it asks one small question — *what did you pick up your phone to do?* — then lets
you continue or go back. No blocking, no shaming, no network. Ever.

**Build awareness, not restrictions.**

Status: **MVP v0.2.0 — code complete, not yet built or device-tested.** See
`docs/DELIVERY.md` for exactly what is done, what is not, and what needs a real phone.

---

## What's in the box

```
app/src/main/java/com/intentcoach/app/
├── IntentCoachApp.kt        App + AppContainer (hand-wired repositories, app-wide scope, delete-all)
├── domain/                  PURE Kotlin — every rule worth testing lives here
│   ├── ForegroundIntervals  Usage events → real on-screen time; session end detection
│   ├── InterventionPolicy   When to ask (cooldown, duplicates, active session) + classification
│   ├── Insights             Overview / per-app / trend / Intentional Usage Rate; TimeMath; Format
│   ├── FocusTimer           Focus timer as timestamps (survives process death)
│   ├── AppCategorizer       Suggested picker groups
│   └── Messages             The companion's words
├── data/                    Room DB v3, repositories, SharedPreferences
│   ├── AppDatabase          v1→v2→v3 migrations (v2 history is converted, never dropped)
│   ├── Sessions.kt          MonitoredApp, AppSession, IntentResponse (+ DAOs)
│   ├── FocusSession.kt      Focus history (+ DAO)
│   ├── SessionRepository    Check-ins, dismissals, reconcile() closes sessions from usage events
│   ├── UsageRepository      The only reader of UsageStatsManager
│   ├── AppsRepository       Launchable apps + monitored selection
│   ├── FocusRepository      Running timer + history
│   ├── Settings             All preferences (SharedPreferences)
│   └── Rewards / TimeOfDay  Companion warmth, streak, mood; part of day
├── detection/               AppWatchService (foreground service, polls every 800 ms), BootReceiver
├── overlay/                 InterruptActivity — the 3-step check-in (auto / manual / practice)
└── ui/                      MainActivity (bottom nav) + screens and ViewModels
    Home · Insights · Focus · Settings · Onboarding · App picker · History · Privacy · Companion
```

## Screens

- **Onboarding** — intro, privacy, pick apps (none pre-selected), Usage Access explainer +
  live check, overlay explainer, a practice check-in. Every optional step can be skipped.
- **Check-in** — *What did you pick up your phone to do?* (Something specific / Just
  checking / I'm bored / Taking a break / Something else + optional words) → *How long?*
  (5/10/15/30 min / No limit, skippable) → **Continue** or **Go back**.
- **Home** — the cozy room and companion (Compose Canvas, four moods, idle breathing),
  gentle message, today's numbers, optional reflection card, manual check-in, quick actions.
- **Insights** — today / last 7 days: monitored time, intentional / unplanned / unknown /
  turned back, average session, proportion bar + Intentional Usage Rate (with its
  definition), daily trend, common intentions, per-app rows → per-app history.
- **Focus** — category + preset (15/25/45/60/custom), start/pause/resume/end, completion
  screen with a companion reaction, history, today/week totals.
- **Settings** — on/off, automatic check-ins, cooldown, default duration, monitored apps,
  permission status, battery card, privacy policy, delete all local data.

## Permissions, plainly

| Permission | Why | Required? |
|---|---|---|
| Usage Access (`PACKAGE_USAGE_STATS`) | Notice a monitored app opening; measure on-screen time | For automatic check-ins and time |
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | Show the check-in on top of the app | For automatic check-ins |
| `FOREGROUND_SERVICE` (+ `SPECIAL_USE` on 34+) | Keep detection running | With automatic check-ins |
| `POST_NOTIFICATIONS` (33+) | The ongoing "IntentCoach is on" notice | Optional |
| `RECEIVE_BOOT_COMPLETED` | Restart after reboot if it was on | — |

No `INTERNET`, no `QUERY_ALL_PACKAGES`, no `AccessibilityService`. App listing uses a
`<queries>` launcher intent. Manual check-ins from Home need no special permission.

## Building

Open the `intentcoach` folder in **Android Studio** and Run, or from a terminal:

```bash
./gradlew assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # JVM unit tests (pure + Robolectric)
.claude/tools/verify.sh        # both, plus the constraint check
```

**AGP 8.5.2 does not accept JDK 25.** If Gradle fails with a bare `25.0.1`, use Android
Studio's bundled JDK:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
```

The first build writes Room's schema to `app/schemas/` — commit that folder.

### Release APK

`./gradlew assembleRelease` produces an **unsigned** APK. To install or publish it, create
a keystore (Android Studio → Build → Generate Signed Bundle / APK) and sign it; for the
Play Store build an `.aab` with `./gradlew bundleRelease`. Never commit the keystore.

## Known limitations

1. **Detection lag (~0.5–1 s).** Android offers no supported way to intercept a launch;
   UsageStatsManager reports after the fact. The app may flash before the check-in.
2. **OEM battery killers** (Xiaomi, Samsung, Oppo…) may stop the service. The battery card
   helps; nothing guarantees it. Missed opens are simply not recorded — never guessed.
3. **Usage events have limited OS retention.** Old days may show "no data".
4. **Split-screen / picture-in-picture** are not modelled in time measurement.
5. **Focus timer** has no completion notification when the app is closed; it is saved
   correctly the next time IntentCoach opens.
