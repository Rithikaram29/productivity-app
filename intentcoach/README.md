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
│   ├── ui/MainActivity.kt          Onboarding: explains + requests the 2 permissions
│   ├── detection/
│   │   ├── AppWatchService.kt       Polls foreground app, fires the interrupt  ← the engine
│   │   └── BootReceiver.kt          Restarts the service after reboot
│   ├── overlay/InterruptActivity.kt The interrupt screen (Compose)             ← the product
│   ├── data/
│   │   ├── IntentLog.kt             Room entity + DAO (stays on-device)
│   │   └── WatchedApps.kt           The short list of apps to watch
│   └── util/Permissions.kt         Permission checks
└── res/values/                      strings + themes
```

## How to open and run

1. Install **Android Studio** (latest stable).
2. `File → Open` this folder. Let Gradle sync (it downloads dependencies; needs internet the first time).
3. Plug in a real Android phone with USB debugging on. **Use a real device, not the
   emulator** — foreground-app detection and overlays behave differently on emulators.
4. Hit Run. The app installs and opens the setup screen.
5. Grant the two permissions when prompted (each deep-links to the right Settings page).
6. Tap Start, then open Instagram (or any app in the watched list). The interrupt appears.

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

2. **Aggressive OEM battery killers.** Xiaomi, Samsung, Oppo, etc. may kill the
   background service. You'll likely need to guide users to disable battery optimisation
   for the app. `TODO`: add a battery-optimisation-exemption prompt.

3. **No "enabled" flag yet.** `BootReceiver` restarts the service unconditionally. Add a
   SharedPreferences flag so it only restarts if the user had it running. `TODO`.

4. **App-picker UI not built.** `WatchedApps` has sensible defaults but there's no screen
   to edit the list yet. `TODO`: a simple settings screen (this is where you'd want the
   user to pick from *their* installed apps — and the moment you list all of them is when
   the `QUERY_ALL_PACKAGES` question returns).

5. **Insights screen not built.** Every interrupt is logged locally to `intent_log`. The
   weekly-report / "you opened this 14 times out of habit" screen reads from that table.
   The DAO queries you need (`countForAppSince`, `redirectsSince`) are already written.

---

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
2. Build the app-picker settings screen (edge #4).
3. Build the simple insights screen from `intent_log` (edge #5) — this is your
   differentiator: *insight*, not just blocking.
4. Put it on 3–5 friends' phones who fit the profile. Watch what they do. That's your
   Step-1 validation happening through the product itself.
