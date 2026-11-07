# Intent Coach — MVP scaffold

An Android app that catches you the moment you open a distracting app and asks one
question: *what did you pick up your phone to do?* No blocking, no shaming, no cloud.
The whole idea is to insert a half-second of awareness between reflex and feed.

This is a **running-start scaffold**, not a finished app. The architecture, the core
screen, and the service wiring are here. The parts that need a real device to finish
are marked with `TODO` and called out below.

---

## What's in the box

```
app/src/main/
├── AndroidManifest.xml              Permissions + service/activity declarations (READ THE COMMENTS)
├── java/com/intentcoach/app/
│   ├── IntentCoachApp.kt            Holds the local Room database
│   ├── ui/
│   │   ├── MainActivity.kt          Nav host + setup screen: permissions, on/off, links
│   │   ├── AppPickerScreen.kt       Choose which apps trigger the interrupt
│   │   ├── InsightsScreen.kt        The week in review                         ← the differentiator
│   │   ├── BatteryOptimisationCard.kt  Dismissible OEM battery-killer prompt
│   │   └── Palette.kt               Shared colour tokens for the app's screens
│   ├── detection/
│   │   ├── AppWatchService.kt       Polls foreground app, fires the interrupt  ← the engine
│   │   └── BootReceiver.kt          Restarts the service after reboot, if allowed
│   ├── overlay/InterruptActivity.kt The interrupt screen (Compose)             ← the product
│   ├── data/
│   │   ├── IntentLog.kt             Room entity + DAO (stays on-device)
│   │   ├── Stats.kt                 Pure weekly arithmetic (no Android, unit-tested)
│   │   ├── Settings.kt              on/off flag + battery-card dismissal
│   │   └── WatchedApps.kt           Curated catalog + the user's watch list
│   └── util/Permissions.kt         Permission checks
└── res/values/                      strings + themes

app/src/test/                        JVM unit tests (Stats, Settings, WatchedApps)
```

## How to open and run

1. Install **Android Studio** (latest stable).
2. `File → Open` this folder. Let Gradle sync (it downloads dependencies; needs internet the first time).
3. Plug in a real Android phone with USB debugging on. **Use a real device, not the
   emulator** — foreground-app detection and overlays behave differently on emulators.
4. Hit Run. The app installs and opens the setup screen.
5. Grant the two permissions when prompted (each deep-links to the right Settings page).
6. Tap Start, then open Instagram (or any app in the watched list). The interrupt appears.

### Building from the terminal

A Gradle wrapper (8.9, matching AGP 8.5.2) is committed, so `./gradlew assembleDebug`
and `./gradlew test` work from a fresh clone.

One catch: **AGP 8.5.2 does not accept JDK 25.** If `./gradlew` fails with a bare
`* What went wrong: 25.0.1`, point it at a JDK 17–21 — Android Studio ships one:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
```

Builds started from inside Android Studio already use that JDK, so they are unaffected.

## The three screens

**Setup (home).** Explains the two permissions and deep-links to each, shows how many
apps are being watched, and carries the Start/Stop button. Stopping is real: it clears
the enabled flag and stops the service, and `BootReceiver` will not bring it back.

**Apps that ask.** The picker. It offers a curated catalog of ~20 well-known distracting
apps (`WatchedApps.CATALOG`) and shows only the ones actually installed on this phone,
with their real icon and label. It asks the PackageManager about each known package
individually — which is what keeps the app clear of `QUERY_ALL_PACKAGES`. An app missing
from the catalog cannot be watched; widen the catalog rather than widen the permission.

**This week.** The insights screen, and the reason this app is not just another blocker:

- total interrupts, and how many you turned back from — framed as something you did,
  never a score or a streak
- a per-app breakdown, busiest first, drawn with Compose primitives (no chart library)
- the **habit rate** per app: how many of those opens you yourself marked as having no
  reason. Self-reported, not inferred, which is what makes it worth showing.
- the last few things you said you meant to do, with how each ended

All of it reads the local `intent_log` table for the last 7 days. A brand-new user gets
a plain empty state, not an empty chart.

`InterruptActivity` is deliberately outside the navigation graph — the service launches
it on top of whatever app you just opened. It is a separate surface, not a destination.

## The two permissions, plainly

- **Usage access** (`PACKAGE_USAGE_STATS`) — how we notice a watched app came to the front.
- **Draw over other apps** (`SYSTEM_ALERT_WINDOW`) — how we paint the question on top of it.

Both are "special" permissions: the user toggles them in system Settings, not via a
popup. That's normal for this app category and expected by users of focus apps.

You do **not** need `QUERY_ALL_PACKAGES` for the MVP — you watch a small user-chosen
list, not every installed app. Adding that permission later (for an "app prioritizer"
feature) means a Play Console declaration, so avoid it until you actually need it.

---

## Known rough edges (all expected — this is why you test on real devices)

1. **Detection lag.** Polling every ~800ms means the user may glimpse Instagram for a
   beat before the interrupt lands. If that bugs testers, the fix is switching
   `AppWatchService` to an `AccessibilityService` (instant) — but that permission gets
   heavy Play Store scrutiny, so only do it once the concept is proven.

2. **Aggressive OEM battery killers.** Xiaomi, Samsung, Oppo, etc. may still kill the
   background service. The home screen now shows a dismissible card pointing at the
   battery-optimisation exemption when the app is not exempt, which is the best a
   third-party app can do — but an exemption is not a guarantee on every OEM skin. If
   the service is killed, the Start/Stop button will still say "Stop": Android gives
   apps no reliable way to ask whether their own service is alive (see `Settings.kt`).

3. **Nothing is verified on a real phone yet.** Everything below builds and unit-tests
   green on the JVM, but foreground-app detection, overlays, real app icons, and the
   reboot path all behave differently on device. That is the next job.

### Resolved since the scaffold

- ~~No Gradle wrapper.~~ Committed, pinned to 8.9.
- ~~minSdk/foreground-service mismatch.~~ The API 34+ permission and
  `startForeground()` type are now gated by `minSdkVersion` and a `Build.VERSION.SDK_INT`
  branch, so the service starts on API 26–35.
- ~~No "enabled" flag.~~ `data/Settings.kt` holds it; `BootReceiver` restarts the service
  only when the user had it on *and* both permissions survived the reboot.
- ~~App-picker UI not built.~~ See "Apps that ask" above.
- ~~Insights screen not built.~~ See "This week" above.

## Tests

`app/src/test/` holds JVM unit tests only — no instrumented tests.

```bash
./gradlew test
```

`Stats` is pure arithmetic with no Android types, so it is tested as plain JUnit
(habit rate, ordering, empty/single-row weeks, division by zero, the 7-day boundary).
`Settings` and `WatchedApps` are SharedPreferences-backed and genuinely need a `Context`,
so those use Robolectric — pinned to `sdk=33` in `app/src/test/resources/robolectric.properties`
because Robolectric 4.12.2 has no android-all jar for SDK 35 yet.

## Where AI slots in later (don't let it block v1)

Right now the "should I redirect you" logic is a plain branch — and it can stay that way
for a long time. When you're ready, an on-device model (MediaPipe LLM Inference or
LiteRT) could:
- spot patterns in the `intent_log` table ("Instagram, 'just checking', 11pm, stays 40min")
- make the interrupt copy adaptive instead of fixed

Keep it on-device to preserve the privacy promise. The rule-based version is what you
should test first — it's the thing worth validating.

---

## Suggested first moves in Android Studio

1. Get it building and running on your own phone. Feel the interrupt in real life.
2. Check the picker shows the apps *you* actually lose time to — widen
   `WatchedApps.CATALOG` if it does not.
3. Use it for a week, then open "This week". If that screen does not tell you something
   you did not already know, that is the thing to fix before anything else.
4. Put it on 3–5 friends' phones who fit the profile. Watch what they do. That's your
   Step-1 validation happening through the product itself.
